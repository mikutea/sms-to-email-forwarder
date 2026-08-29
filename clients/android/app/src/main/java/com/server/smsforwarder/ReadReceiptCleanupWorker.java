package com.server.smsforwarder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.ExecutionException;
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
        try {
            QueueDatabase database = QueueDatabase.get(context);
            database.expireReadReceipts(System.currentTimeMillis());
            schedule(context, ExistingWorkPolicy.APPEND_OR_REPLACE);
            return Result.success();
        } catch (RuntimeException error) {
            try {
                // Prefer privacy over the optional read action. If the transient database error
                // has cleared, remove every matching clue now instead of retaining it past TTL.
                handleSchedulingFailure(context);
                return Result.success();
            } catch (RuntimeException cleanupError) {
                // The database is still unavailable. Keep WorkManager's durable retry path alive.
                return Result.retry();
            }
        }
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
        Context applicationContext = context.getApplicationContext();
        try {
            Operation operation = WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    WORK_NAME,
                    policy,
                    request);
            operation.getResult().addListener(() -> {
                try {
                    operation.getResult().get();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    handleSchedulingFailure(applicationContext);
                } catch (ExecutionException | RuntimeException error) {
                    handleSchedulingFailure(applicationContext);
                }
            }, Runnable::run);
        } catch (RuntimeException error) {
            handleSchedulingFailure(applicationContext);
        }
    }

    static void handleSchedulingFailure(Context context) {
        synchronized (SmsReadFeature.operationLock()) {
            QueueDatabase.get(context).cancelReadReceipts(
                    "SMTP 服务器已接受邮件 · 系统短信未标记已读"
                            + "（清理任务调度失败，已清除临时匹配线索）");
        }
    }
}
