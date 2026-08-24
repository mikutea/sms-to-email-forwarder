package com.server.smsforwarder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class HeartbeatWorker extends Worker {
    public HeartbeatWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        AppConfig config = AppConfig.load(context);
        if (!TravelGuard.isEnabled(context) || !config.enabled) {
            return Result.success();
        }
        TravelGuard.enqueueHeartbeatNow(context, "旅行守护定时心跳", false);
        return Result.success();
    }
}
