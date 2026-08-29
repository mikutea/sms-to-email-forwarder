package com.server.smsforwarder;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

final class ForwardScheduler {
    private static final long MIN_BACKOFF_MS = 30_000L;
    private static final String WORK_NAME = "sms_forwarding_queue";
    private static final String URGENT_WORK_NAME = "sms_forwarding_queue_urgent";
    private static final String KEY_URGENT = "urgent_queue_lane";

    private ForwardScheduler() {
    }

    static void schedule(Context context) {
        scheduleUrgent(context);
    }

    static int retryAllNow(Context context) {
        QueueDatabase database = QueueDatabase.get(context);
        int released = database.forceReadyNow(System.currentTimeMillis());
        if (released > 0) {
            schedule(context);
        }
        return released;
    }

    static boolean retryNow(Context context, String id) {
        boolean released = QueueDatabase.get(context).forceReadyNow(id, System.currentTimeMillis());
        if (released) {
            schedule(context);
        }
        return released;
    }

    static void reconcile(Context context) {
        AppConfig config = AppConfig.load(context);
        if (config.enabled && QueueDatabase.get(context).count() > 0) {
            scheduleFromQueue(context);
        }
    }

    static void scheduleFromQueue(Context context) {
        scheduleNormalFromQueue(context, ExistingWorkPolicy.KEEP);
    }

    static void scheduleSuccessorFromQueue(Context context) {
        scheduleNormalFromQueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    static void scheduleUrgentSuccessorFromQueue(Context context) {
        if (QueueDatabase.get(context).hasReady(System.currentTimeMillis())) {
            scheduleUrgent(context);
        }
    }

    private static void scheduleNormalFromQueue(
            Context context, ExistingWorkPolicy policy) {
        long runnableAt = QueueDatabase.get(context).earliestRunnableAt();
        if (runnableAt == 0L) {
            return;
        }
        scheduleNormal(
                context,
                Math.max(0L, runnableAt - System.currentTimeMillis()),
                policy);
    }

    private static void scheduleUrgent(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                URGENT_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                buildRequest(0L, true));
    }

    private static void scheduleNormal(
            Context context,
            long minimumLatencyMs,
            ExistingWorkPolicy policy) {
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_NAME,
                policy,
                buildRequest(minimumLatencyMs, false));
    }

    private static OneTimeWorkRequest buildRequest(
            long minimumLatencyMs, boolean urgent) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(ForwardWorker.class)
                .setConstraints(constraints)
                .setInputData(new Data.Builder().putBoolean(KEY_URGENT, urgent).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_MS, TimeUnit.MILLISECONDS);
        if (minimumLatencyMs > 0L) {
            builder.setInitialDelay(minimumLatencyMs, TimeUnit.MILLISECONDS);
        } else {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        }
        return builder.build();
    }

    static boolean isUrgent(Data inputData) {
        return inputData.getBoolean(KEY_URGENT, false);
    }
}
