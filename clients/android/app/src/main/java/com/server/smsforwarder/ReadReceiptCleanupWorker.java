package com.server.smsforwarder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public final class ReadReceiptCleanupWorker extends Worker {
    static final String WORK_NAME = "sms_read_receipt_cleanup";

    public ReadReceiptCleanupWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParameters) {
        super(appContext, workerParameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        QueueDatabase database = QueueDatabase.get(context);
        database.expireReadReceipts(System.currentTimeMillis());
        schedule(context, ExistingWorkPolicy.APPEND_OR_REPLACE);
        return Result.success();
    }

    static void schedule(Context context) {
        schedule(context, ExistingWorkPolicy.REPLACE);
    }

    static void reconcile(Context context) {
        schedule(context, ExistingWorkPolicy.KEEP);
    }

    private static void schedule(Context context, ExistingWorkPolicy policy) {
        long expiry = QueueDatabase.get(context).earliestReadReceiptExpiry();
        if (expiry == 0L) {
            return;
        }
        long delay = Math.max(0L, expiry - System.currentTimeMillis());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                ReadReceiptCleanupWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_NAME,
                policy,
                request);
    }
}
