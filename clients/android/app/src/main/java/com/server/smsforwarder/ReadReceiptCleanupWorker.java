package com.server.smsforwarder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
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
    static final String INPUT_FORCE_PRIVACY_CLEANUP = "force_privacy_cleanup";

    public ReadReceiptCleanupWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParameters) {
        super(appContext, workerParameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        boolean disabledCleanup = shouldRunDisabledCleanup(
                getInputData().getBoolean(INPUT_FORCE_PRIVACY_CLEANUP, false),
                SmsReadFeature.isEnabled(context),
                SmsReadFeature.hasNotificationAccess(context),
                SmsReadFeature.isCleanupPending(context));
        try {
            if (disabledCleanup) {
                SmsReadFeature.reconcileDisabledLinkageData(context);
                return Result.success();
            }
            QueueDatabase database = QueueDatabase.get(context);
            database.expireReadReceipts(System.currentTimeMillis());
            schedule(context, ExistingWorkPolicy.APPEND_OR_REPLACE);
            return Result.success();
        } catch (RuntimeException error) {
            if (disabledCleanup) {
                // Do not downgrade a failed full privacy cleanup to receipt-only cleanup.
                return Result.retry();
            }
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
        if (needsDisabledCleanup(
                SmsReadFeature.isEnabled(context),
                SmsReadFeature.hasNotificationAccess(context),
                SmsReadFeature.isCleanupPending(context))) {
            schedulePrivacyCleanup(context);
            return;
        }
        try {
            schedule(context, ExistingWorkPolicy.KEEP);
        } catch (RuntimeException error) {
            scheduleReceiptReconcile(context);
        }
    }

    static void schedulePrivacyCleanup(Context context) {
        scheduleImmediate(context, true);
    }

    static void scheduleReceiptReconcile(Context context) {
        scheduleImmediate(context, false);
    }

    private static void scheduleImmediate(Context context, boolean disabledCleanup) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                ReadReceiptCleanupWorker.class)
                .setInputData(immediateInputData(disabledCleanup))
                .build();
        Context applicationContext = context.getApplicationContext();
        try {
            Operation operation = WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request);
            operation.getResult().addListener(() -> {
                try {
                    operation.getResult().get();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    attemptImmediateFailureCleanup(applicationContext, disabledCleanup);
                } catch (ExecutionException | RuntimeException error) {
                    attemptImmediateFailureCleanup(applicationContext, disabledCleanup);
                }
            }, Runnable::run);
        } catch (RuntimeException error) {
            attemptImmediateFailureCleanup(applicationContext, disabledCleanup);
        }
    }

    static boolean needsDisabledCleanup(
            boolean featureEnabled, boolean accessGranted, boolean cleanupPending) {
        return cleanupPending || !featureEnabled || !accessGranted;
    }

    static boolean shouldRunDisabledCleanup(
            boolean forcedByWorkRequest,
            boolean featureEnabled,
            boolean accessGranted,
            boolean cleanupPending) {
        return forcedByWorkRequest
                || needsDisabledCleanup(featureEnabled, accessGranted, cleanupPending);
    }

    static Data immediateInputData(boolean disabledCleanup) {
        return new Data.Builder()
                .putBoolean(INPUT_FORCE_PRIVACY_CLEANUP, disabledCleanup)
                .build();
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

    private static void attemptImmediateFailureCleanup(
            Context context, boolean disabledCleanup) {
        try {
            if (disabledCleanup) {
                SmsReadFeature.reconcileDisabledLinkageData(context);
            } else {
                handleSchedulingFailure(context);
            }
        } catch (RuntimeException ignored) {
            // A later app start, package replacement, or boot calls reconcile() again. If the
            // WorkManager database itself is unavailable there is no second durable scheduler to
            // rely on here, so keep this callback non-crashing and preserve the fail-closed pref.
        }
    }
}
