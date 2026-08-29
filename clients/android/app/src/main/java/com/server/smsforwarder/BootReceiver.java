package com.server.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        AppConfig config = AppConfig.load(context);
        if (config.enabled && QueueDatabase.get(context).count() > 0) {
            if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                    && SmtpHealthState.VERIFIED.equals(
                    AppConfig.getSmtpVerificationState(context))) {
                ForwardScheduler.retryAllNow(context);
            } else {
                ForwardScheduler.scheduleFromQueue(context);
            }
        }
        if (TravelGuard.isEnabled(context)) {
            TravelGuard.scheduleHeartbeat(context);
        }
        ReadReceiptCleanupWorker.reconcile(context);
    }
}
