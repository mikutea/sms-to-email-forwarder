package com.server.smsforwarder;

import android.content.Context;

import java.util.List;

import javax.mail.MessagingException;

final class ForwardProcessor {
    private static final long MAX_RETRY_MS = 6L * 60L * 60L * 1000L;

    private ForwardProcessor() {
    }

    static ProcessResult processReady(Context context, int limit) {
        AppConfig config = AppConfig.load(context);
        if (!config.enabled) {
            // Keep queued data intact, but do not create an immediate WorkManager loop
            // while the owner has deliberately paused forwarding.
            return new ProcessResult(0, false, false);
        }
        String validationError = config.validateForForwarding();
        if (validationError != null) {
            AppConfig.setSmtpFailure(context, "配置无效，发送已暂停：" + validationError);
            // Saving a valid configuration or enabling forwarding schedules the queue again.
            return new ProcessResult(0, false, false);
        }

        QueueDatabase database = QueueDatabase.get(context);
        List<QueueItem> ready = database.claimReady(System.currentTimeMillis(), limit);
        int processed = 0;
        for (QueueItem item : ready) {
            try {
                dispatch(database, config, item);
            } catch (MessagingException | RuntimeException error) {
                int attempts = Math.min(item.attempts + 1, 1000);
                boolean authenticationFailure = SmtpFailure.isAuthenticationFailure(error);
                String classified = SmtpFailure.describeForRecord(error)
                        + " · " + NetworkState.diagnosticSummary(context);
                database.markRetry(
                        item.id,
                        attempts,
                        System.currentTimeMillis() + retryDelay(attempts, authenticationFailure),
                        classified);
                AppConfig.setSmtpFailure(
                        context,
                        classified + "，已保留在加密队列中重试");
                // Counts claimed rows that completed an SMTP attempt, whether that attempt
                // succeeded or was moved to a later retry window.
                processed++;
                continue;
            }
            // Nothing after this point belongs to the SMTP failure catch. Once dispatch returns,
            // optional local bookkeeping can never turn an accepted email into a retry.
            completeAcceptedDelivery(context, database, item);
            processed++;
        }
        long finishedAt = System.currentTimeMillis();
        return new ProcessResult(
                processed,
                database.count() > 0,
                database.hasReady(finishedAt));
    }

    private static void completeAcceptedDelivery(
            Context context, QueueDatabase database, QueueItem item) {
        // Preserve a useful history record when possible, but removal from the SMTP queue is the
        // authoritative delivery transition. A history-only failure must not retain the email.
        try {
            database.markSuccess(item.id, item.attempts, "SMTP 服务器已接受邮件");
        } catch (RuntimeException ignored) {
            // Continue to the authoritative queue removal below.
        }
        database.remove(item.id);
        if (QueueItem.KIND_SMS.equals(item.kind)) {
            startReadLinkBestEffort(context, database, item);
            AppConfig.setSmsForwarded(context);
        }
        AppConfig.setSuccess(
                context,
                QueueItem.KIND_TEST.equals(item.kind)
                        ? "SMTP 测试邮件已发送"
                        : "短信已被 SMTP 服务器接受");
    }

    private static void startReadLinkBestEffort(
            Context context, QueueDatabase database, QueueItem item) {
        try {
            String detail = SmsReadFeature.onForwardSuccess(context, database, item);
            database.updateReadReceiptOutcome(item.id, detail);
        } catch (RuntimeException ignored) {
            // Delivery is final. Read linkage is optional and must never requeue an email that
            // the SMTP server already accepted. The base success detail remains authoritative.
            try {
                database.updateReadReceiptOutcome(
                        item.id,
                        "SMTP 服务器已接受邮件 · 已读联动暂未启动（本机服务暂时不可用）");
            } catch (RuntimeException databaseUnavailable) {
                // There is no safe local recovery action here; importantly, do not retry SMTP.
            }
        }
        try {
            // If receipt insertion succeeded but cleanup scheduling failed, still give the
            // listener an immediate chance to process the durable request.
            SmsNotificationListener.requestProcessing(context);
        } catch (RuntimeException ignored) {
            // Notification linkage remains best-effort after final SMTP acceptance.
        }
    }

    private static void dispatch(QueueDatabase database, AppConfig config, QueueItem item)
            throws MessagingException {
        final int primaryBit = 1;
        final int backupBit = 2;
        int deliveredMask = item.deliveredMask;
        MessagingException primaryError = null;

        if ((deliveredMask & primaryBit) == 0) {
            try {
                SmtpMailer.send(config.primaryProfile(), item);
                deliveredMask |= primaryBit;
                database.markDelivered(item.id, deliveredMask);
            } catch (MessagingException error) {
                primaryError = error;
            }
        }

        if (AppConfig.STRATEGY_PRIMARY_ONLY.equals(config.dispatchStrategy)
                || !config.backupEnabled) {
            if ((deliveredMask & primaryBit) != 0) {
                return;
            }
            throw primaryError == null ? new MessagingException("主 SMTP 通道发送失败") : primaryError;
        }

        if (AppConfig.STRATEGY_FAILOVER.equals(config.dispatchStrategy)) {
            if ((deliveredMask & primaryBit) != 0) {
                return;
            }
            if ((deliveredMask & backupBit) != 0) {
                return;
            }
            try {
                SmtpMailer.send(config.backupProfile(), item);
                database.markDelivered(item.id, deliveredMask | backupBit);
                return;
            } catch (MessagingException backupError) {
                if (primaryError != null) {
                    backupError.setNextException(primaryError);
                }
                throw backupError;
            }
        }

        if ((deliveredMask & backupBit) == 0) {
            try {
                SmtpMailer.send(config.backupProfile(), item);
                deliveredMask |= backupBit;
                database.markDelivered(item.id, deliveredMask);
            } catch (MessagingException backupError) {
                if (primaryError != null) {
                    backupError.setNextException(primaryError);
                }
                throw backupError;
            }
        }
        if ((deliveredMask & primaryBit) == 0) {
            throw primaryError == null ? new MessagingException("主 SMTP 通道发送失败") : primaryError;
        }
    }

    static long retryDelay(int attempts, boolean authenticationFailure) {
        if (authenticationFailure) {
            return MAX_RETRY_MS;
        }
        int shift = Math.min(Math.max(attempts - 1, 0), 10);
        return Math.min(MAX_RETRY_MS, 30_000L * (1L << shift));
    }

    static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    static final class ProcessResult {
        final int processed;
        final boolean hasPending;
        final boolean hasReady;

        ProcessResult(int processed, boolean hasPending, boolean hasReady) {
            this.processed = processed;
            this.hasPending = hasPending;
            this.hasReady = hasReady;
        }
    }
}
