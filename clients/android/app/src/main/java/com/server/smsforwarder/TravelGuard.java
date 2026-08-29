package com.server.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

final class TravelGuard {
    private static final String PREFS = "travel_guard";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_BACKGROUND_CONFIRMED = "background_confirmed";
    private static final String KEY_HEARTBEAT_HOURS = "heartbeat_hours";
    private static final String WORK_NAME = "travel_guard_heartbeat";

    private TravelGuard() {
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            scheduleHeartbeat(context);
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        }
    }

    static boolean isBackgroundConfirmed(Context context) {
        return prefs(context).getBoolean(KEY_BACKGROUND_CONFIRMED, false);
    }

    static void setBackgroundConfirmed(Context context, boolean confirmed) {
        prefs(context).edit().putBoolean(KEY_BACKGROUND_CONFIRMED, confirmed).apply();
    }

    static int heartbeatHours(Context context) {
        int value = prefs(context).getInt(KEY_HEARTBEAT_HOURS, 12);
        return value == 6 || value == 24 ? value : 12;
    }

    static void setHeartbeatHours(Context context, int hours) {
        int safeHours = hours == 6 || hours == 24 ? hours : 12;
        prefs(context).edit().putInt(KEY_HEARTBEAT_HOURS, safeHours).apply();
        if (isEnabled(context)) {
            scheduleHeartbeat(context);
        }
    }

    static void scheduleHeartbeat(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                HeartbeatWorker.class,
                heartbeatHours(context),
                TimeUnit.HOURS,
                30,
                TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request);
    }

    static void enqueueHeartbeatNow(Context context, String title, boolean alert) {
        String kind = alert ? QueueItem.KIND_ALERT : QueueItem.KIND_HEARTBEAT;
        if (QueueDatabase.get(context).enqueueStatus(
                kind, title, DeviceHealth.inspect(context).summary())) {
            ForwardScheduler.schedule(context);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
