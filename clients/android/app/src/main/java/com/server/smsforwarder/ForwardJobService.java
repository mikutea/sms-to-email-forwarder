package com.server.smsforwarder;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

public final class ForwardJobService extends JobService {
    private static final long MAX_RETRY_MS = 6L * 60L * 60L * 1000L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean stopped;

    @Override
    public boolean onStartJob(JobParameters params) {
        stopped = false;
        executor.execute(() -> runQueue(params));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true;
        return true;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runQueue(JobParameters params) {
        boolean needsSystemReschedule = false;
        try {
            AppConfig config = AppConfig.load(this);
            if (!config.enabled) {
                return;
            }
            String validationError = AppConfig.validate(
                    config.smtpHost,
                    Integer.toString(config.smtpPort),
                    config.smtpSecurity,
                    config.smtpUsername,
                    config.smtpPassword,
                    config.fromAddress,
                    config.recipient,
                    config.privacyConsent);
            if (validationError != null) {
                AppConfig.setStatus(this, "配置无效，发送已暂停：" + validationError);
                return;
            }

            QueueDatabase database = QueueDatabase.get(this);
            List<QueueItem> ready = database.ready(System.currentTimeMillis(), 20);
            for (QueueItem item : ready) {
                if (stopped) {
                    needsSystemReschedule = true;
                    break;
                }
                try {
                    SmtpMailer.send(config, item);
                    database.remove(item.id);
                    AppConfig.setSuccess(
                            this,
                            QueueItem.KIND_TEST.equals(item.kind)
                                    ? "SMTP 测试邮件已发送"
                                    : "短信已发送到邮箱");
                } catch (MessagingException e) {
                    int attempts = Math.min(item.attempts + 1, 1000);
                    long delay = retryDelay(attempts, e instanceof AuthenticationFailedException);
                    database.markRetry(item.id, attempts, System.currentTimeMillis() + delay);
                    AppConfig.setStatus(this, classifyError(e) + "，已保留在加密队列中重试");
                }
            }

            if (!stopped && database.count() > 0) {
                needsSystemReschedule = true;
            }
        } catch (RuntimeException e) {
            AppConfig.setStatus(this, "后台发送发生本地错误：" + safeMessage(e));
            needsSystemReschedule = true;
        } finally {
            if (!stopped) {
                jobFinished(params, needsSystemReschedule);
            }
        }
    }

    private static long retryDelay(int attempts, boolean authenticationFailure) {
        if (authenticationFailure) {
            return MAX_RETRY_MS;
        }
        int shift = Math.min(attempts - 1, 10);
        return Math.min(MAX_RETRY_MS, 30_000L * (1L << Math.max(shift, 0)));
    }

    private static String classifyError(MessagingException error) {
        if (error instanceof AuthenticationFailedException) {
            return "SMTP 认证失败，请检查授权码和服务开关";
        }
        return "SMTP 发送失败：" + safeMessage(error);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() > 160 ? message.substring(0, 160) : message;
    }
}
