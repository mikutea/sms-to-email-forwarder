package com.server.smsforwarder;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.core.app.NotificationManagerCompat;

final class SmsReadFeature {
    private static final String PREFS = "sms_read_feature";
    private static final String KEY_ENABLED = "enabled";
    private static final long REQUEST_TTL_MS = 10L * 60L * 1000L;

    private SmsReadFeature() {
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (!enabled) {
            SmsNotificationListener.onFeatureDisabled();
            QueueDatabase database = QueueDatabase.get(context);
            database.cancelReadReceipts(
                    "SMTP 服务器已接受邮件 · 已读联动已关闭");
            database.clearPendingReadMatchClues();
        }
    }

    static boolean hasNotificationAccess(Context context) {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.getPackageName());
    }

    static ComponentName listenerComponent(Context context) {
        return new ComponentName(context, SmsNotificationListener.class);
    }

    static String onForwardSuccess(Context context, QueueDatabase database, QueueItem item) {
        if (!QueueItem.KIND_SMS.equals(item.kind) || !isEnabled(context)) {
            return "SMTP 服务器已接受邮件";
        }
        if (!isEligibleForReadLink(item)) {
            return "SMTP 服务器已接受邮件 · 手动重新转发不执行系统短信已读联动";
        }
        if (!hasNotificationAccess(context)) {
            return "SMTP 服务器已接受邮件 · 系统短信未标记已读（通知使用权未授权）";
        }
        long now = System.currentTimeMillis();
        long expiresAt = requestExpiry(now);
        database.enqueueReadReceipt(item, expiresAt);
        ReadReceiptCleanupWorker.schedule(context);
        return "SMTP 服务器已接受邮件 · 正在请求系统短信标记已读";
    }

    static long requestExpiry(long forwardingSucceededAt) {
        // The SMS timestamp is untrusted carrier input and must never extend local retention.
        return forwardingSucceededAt + REQUEST_TTL_MS;
    }

    static boolean isEligibleForReadLink(QueueItem item) {
        return QueueItem.KIND_SMS.equals(item.kind) && item.localReceivedAt > 0L;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
