package com.server.smsforwarder;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

final class ForwardScheduler {
    private static final long MIN_BACKOFF_MS = 30_000L;
    private static final String WORK_NAME = "sms_forwarding_queue";

    private ForwardScheduler() {
    }

    static void schedule(Context context) {
        schedule(context, 0L);
    }

    static void scheduleFromQueue(Context context) {
        long runnableAt = QueueDatabase.get(context).earliestRunnableAt();
        if (runnableAt == 0L) {
            return;
        }
        schedule(context, Math.max(0L, runnableAt - System.currentTimeMillis()));
    }

    static void schedule(Context context, long minimumLatencyMs) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(ForwardWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_MS, TimeUnit.MILLISECONDS);
        if (minimumLatencyMs > 0L) {
            builder.setInitialDelay(minimumLatencyMs, TimeUnit.MILLISECONDS);
        } else {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        }
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                builder.build());
    }
}
