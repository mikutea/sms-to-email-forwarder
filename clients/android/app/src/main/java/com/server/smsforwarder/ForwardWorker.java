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
                    20);
            if (result.hasPending) {
                // Append the follow-up behind this running worker. Replacing the
                // unique work here would cancel the worker before it can finish.
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
