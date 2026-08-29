package com.server.smsforwarder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Owns only an urgent retry delay. SMTP is always performed by the serialized urgent chain,
 * so replacing this wake-up request cannot interrupt an in-flight delivery.
 */
public final class ForwardRetryWakeupWorker extends Worker {
    public ForwardRetryWakeupWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        ForwardScheduler.schedule(getApplicationContext());
        return Result.success();
    }
}
