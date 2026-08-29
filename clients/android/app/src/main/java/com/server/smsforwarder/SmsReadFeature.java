package com.server.smsforwarder;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.core.app.NotificationManagerCompat;

final class SmsReadFeature {
    private static final String PREFS = "sms_read_feature";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_GENERATION = "generation";
    private static final long REQUEST_TTL_MS = 10L * 60L * 1000L;
    private static final Object OPERATION_LOCK = new Object();

    private SmsReadFeature() {
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        synchronized (OPERATION_LOCK) {
            SharedPreferences preferences = prefs(context);
            boolean wasEnabled = preferences.getBoolean(KEY_ENABLED, false);
            long generation = preferences.getLong(KEY_GENERATION, 1L);
            if (!enabled && wasEnabled) {
                generation = generation == Long.MAX_VALUE ? 1L : generation + 1L;
            }
            preferences.edit()
                    .putBoolean(KEY_ENABLED, enabled)
                    .putLong(KEY_GENERATION, generation)
                    .commit();
            if (!enabled) {
                SmsNotificationListener.onFeatureDisabled();
                QueueDatabase database = QueueDatabase.get(context);
                database.cancelReadReceipts(
                        "SMTP 服务器已接受邮件 · 已读联动已关闭");
                database.clearPendingReadMatchClues();
            }
        }
    }

    static boolean hasNotificationAccess(Context context) {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.getPackageName());
    }

    static ComponentName listenerComponent(Context context) {
        return new ComponentName(context, SmsNotificationListener.class);
    }

    static QueueDatabase.EnqueueResult enqueueIncomingSms(
            Context context,
            QueueDatabase database,
            String sender,
            String transformedBody,
            String originalBody,
            long receivedAt,
            long localReceivedAt,
            int simSlot) {
        synchronized (OPERATION_LOCK) {
            String clue = isEnabled(context)
                    ? SmsNotificationMatcher.bodyMatchClue(originalBody) : "";
            return database.enqueueSms(
                    sender,
                    transformedBody,
                    clue,
                    receivedAt,
                    localReceivedAt,
                    simSlot,
                    isEnabled(context) ? currentGeneration(context) : 0L);
        }
    }

    static void onForwardSuccess(Context context, QueueDatabase database, QueueItem item) {
        synchronized (OPERATION_LOCK) {
            String detail = "SMTP 服务器已接受邮件";
            boolean receiptInserted = false;
            if (QueueItem.KIND_SMS.equals(item.kind) && isEnabled(context)) {
                if (!isEligibleForReadLink(item)) {
                    detail = "SMTP 服务器已接受邮件 · 手动重新转发不执行系统短信已读联动";
                } else if (!hasCurrentReadLinkGeneration(context, item)) {
                    detail = "SMTP 服务器已接受邮件 · 系统短信未标记已读（发送期间曾关闭已读联动）";
                } else if (!hasNotificationAccess(context)) {
                    detail = "SMTP 服务器已接受邮件 · 系统短信未标记已读（通知使用权未授权）";
                } else {
                    long now = System.currentTimeMillis();
                    database.enqueueReadReceipt(item, requestExpiry(now));
                    detail = "SMTP 服务器已接受邮件 · 正在请求系统短信标记已读";
                    receiptInserted = true;
                }
            }
            database.updateReadReceiptOutcome(item.id, detail);
            if (receiptInserted) {
                ReadReceiptCleanupWorker.schedule(context);
            }
        }
    }

    static void recordReadLinkUnavailable(
            Context context, QueueDatabase database, String id) {
        synchronized (OPERATION_LOCK) {
            database.updateReadReceiptOutcome(
                    id,
                    isEnabled(context)
                            ? "SMTP 服务器已接受邮件 · 已读联动暂未启动（本机服务暂时不可用）"
                            : "SMTP 服务器已接受邮件 · 已读联动已关闭");
        }
    }

    static boolean dispatchReadActionIfAllowed(
            Context context,
            QueueDatabase database,
            String id,
            PendingIntent actionIntent) throws PendingIntent.CanceledException {
        synchronized (OPERATION_LOCK) {
            if (!canDispatchReadAction(
                    isEnabled(context),
                    hasNotificationAccess(context),
                    database.hasReadReceipt(id))) {
                return false;
            }
            actionIntent.send();
            database.removeReadReceipt(id);
            database.updateReadReceiptOutcome(
                    id,
                    "SMTP 服务器已接受邮件 · 已请求默认短信应用标记已读（结果由短信应用处理）");
            return true;
        }
    }

    static long requestExpiry(long forwardingSucceededAt) {
        // The SMS timestamp is untrusted carrier input and must never extend local retention.
        return forwardingSucceededAt + REQUEST_TTL_MS;
    }

    static boolean isEligibleForReadLink(QueueItem item) {
        return QueueItem.KIND_SMS.equals(item.kind) && item.localReceivedAt > 0L;
    }

    static boolean hasCurrentReadLinkGeneration(Context context, QueueItem item) {
        return item.readLinkGeneration > 0L
                && item.readLinkGeneration == currentGeneration(context);
    }

    @android.annotation.SuppressLint("ApplySharedPref")
    static void invalidateReadLinkGeneration(Context context) {
        synchronized (OPERATION_LOCK) {
            SharedPreferences preferences = prefs(context);
            long generation = preferences.getLong(KEY_GENERATION, 1L);
            preferences.edit()
                    .putLong(KEY_GENERATION, generation == Long.MAX_VALUE ? 1L : generation + 1L)
                    .commit();
        }
    }

    static boolean canDispatchReadAction(
            boolean featureEnabled, boolean notificationAccess, boolean receiptPresent) {
        return featureEnabled && notificationAccess && receiptPresent;
    }

    static Object operationLock() {
        return OPERATION_LOCK;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static long currentGeneration(Context context) {
        return prefs(context).getLong(KEY_GENERATION, 1L);
    }
}
