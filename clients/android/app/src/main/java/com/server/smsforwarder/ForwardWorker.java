package com.server.smsforwarder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class ForwardWorker extends Worker {
    public ForwardWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ForwardProcessor.ProcessResult result = ForwardProcessor.processReady(
                    getApplicationContext(),
                    1);
            if (ForwardScheduler.isUrgent(getInputData())
                    && result.processed > 0
                    && result.hasReady) {
                // Urgent work never carries a delayed retry, so new SMS can only wait behind
                // another active SMTP attempt rather than an old backoff timer.
                ForwardScheduler.scheduleUrgentSuccessorFromQueue(getApplicationContext());
            }
            if (result.hasPending) {
                // Delayed retries stay in the normal chain. APPEND_OR_REPLACE appends behind an
                // active normal worker instead of replacing an SMTP delivery in flight.
                ForwardScheduler.scheduleSuccessorFromQueue(getApplicationContext());
            }
            return Result.success();
        } catch (RuntimeException error) {
            AppConfig.setStatus(
                    getApplicationContext(),
                    "后台发送发生本地错误：" + ForwardProcessor.safeMessage(error));
            return Result.retry();
        }
    }
}
