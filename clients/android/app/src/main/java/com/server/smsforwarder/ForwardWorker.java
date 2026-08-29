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
            boolean urgent = ForwardScheduler.isUrgent(getInputData());
            if (urgent) {
                // The worker now owns the one reserved urgent slot. A burst that arrives while it
                // runs can reserve exactly one successor; the worker's own drain request will
                // coalesce with that same slot.
                ForwardScheduler.acknowledgeUrgentWork(
                        getApplicationContext(),
                        ForwardScheduler.urgentReservationToken(getInputData()));
            }
            ForwardProcessor.ProcessResult result = ForwardProcessor.processReady(
                    getApplicationContext(),
                    1,
                    urgent);
            if (urgent) {
                if (result.processed > 0 && result.hasReady) {
                    // Urgent work never carries a delayed retry, so new SMS can only wait behind
                    // another active SMTP attempt rather than an old backoff timer.
                    ForwardScheduler.scheduleUrgentSuccessorFromQueue(getApplicationContext());
                } else if (result.hasPending && !result.hasReady) {
                    // A tiny wake-up worker owns the delay and later appends an immediate urgent
                    // worker. It never joins the normal SMTP chain, where an older six-hour
                    // backoff request could otherwise block this SMS's 30-second retry.
                    ForwardScheduler.scheduleUrgentRetryFromQueue(getApplicationContext());
                }
            } else if (result.hasPending) {
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
