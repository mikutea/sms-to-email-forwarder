package com.server.smsforwarder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

final class ForwardScheduler {
    private static final long MIN_BACKOFF_MS = 30_000L;
    private static final String WORK_NAME = "sms_forwarding_queue";
    static final String URGENT_WORK_NAME = "sms_forwarding_queue_urgent";
    static final String RETRY_WAKEUP_WORK_NAME = "sms_forwarding_queue_urgent_retry_wakeup";
    private static final String KEY_URGENT = "urgent_queue_lane";
    private static final String KEY_URGENT_RESERVATION_TOKEN = "urgent_reservation_token";
    private static final String SCHEDULER_PREFS = "forward_scheduler";
    private static final String KEY_PENDING_URGENT_TOKEN = "pending_urgent_token";
    private static final String KEY_PENDING_URGENT_OWNER = "pending_urgent_owner";
    private static final Object URGENT_SCHEDULE_LOCK = new Object();
    private static final String PROCESS_URGENT_TOKEN = UUID.randomUUID().toString();

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

    static void scheduleUrgentRetryFromQueue(Context context) {
        long runnableAt = QueueDatabase.get(context).earliestRunnableAt();
        if (runnableAt == 0L) {
            return;
        }
        long delay = Math.max(0L, runnableAt - System.currentTimeMillis());
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(
                ForwardRetryWakeupWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_MS, TimeUnit.MILLISECONDS);
        if (delay > 0L) {
            builder.setInitialDelay(delay, TimeUnit.MILLISECONDS);
        }
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                RETRY_WAKEUP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                builder.build());
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
        Context applicationContext = context.getApplicationContext();
        String reservationToken = reserveUrgentWork(applicationContext);
        if (reservationToken.isEmpty()) {
            return;
        }
        try {
            Operation operation = WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    URGENT_WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    buildRequest(0L, true, reservationToken));
            operation.getResult().addListener(() -> {
                try {
                    operation.getResult().get();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    acknowledgeUrgentWork(applicationContext, reservationToken);
                } catch (ExecutionException | RuntimeException error) {
                    // Only this request's token is cleared. A newer reservation remains intact.
                    acknowledgeUrgentWork(applicationContext, reservationToken);
                }
            }, Runnable::run);
        } catch (RuntimeException error) {
            acknowledgeUrgentWork(applicationContext, reservationToken);
            throw error;
        }
    }

    private static void scheduleNormal(
            Context context,
            long minimumLatencyMs,
            ExistingWorkPolicy policy) {
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_NAME,
                policy,
                buildRequest(minimumLatencyMs, false, ""));
    }

    private static OneTimeWorkRequest buildRequest(
            long minimumLatencyMs, boolean urgent, String urgentReservationToken) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        Data.Builder input = new Data.Builder().putBoolean(KEY_URGENT, urgent);
        if (urgent) {
            input.putString(KEY_URGENT_RESERVATION_TOKEN, urgentReservationToken);
        }
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(ForwardWorker.class)
                .setConstraints(constraints)
                .setInputData(input.build())
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

    static String urgentReservationToken(Data inputData) {
        String token = inputData.getString(KEY_URGENT_RESERVATION_TOKEN);
        return token == null ? "" : token;
    }

    @SuppressLint("ApplySharedPref")
    static String reserveUrgentWork(Context context) {
        synchronized (URGENT_SCHEDULE_LOCK) {
            SharedPreferences preferences = schedulerPreferences(context);
            String pendingToken = preferences.getString(KEY_PENDING_URGENT_TOKEN, "");
            String pendingOwner = preferences.getString(KEY_PENDING_URGENT_OWNER, "");
            if (!pendingToken.isEmpty() && PROCESS_URGENT_TOKEN.equals(pendingOwner)) {
                return "";
            }
            // A new app process owns a different token and may enqueue one recovery worker even
            // if the old process died between reserving and persisting WorkManager state.
            String reservationToken = UUID.randomUUID().toString();
            preferences.edit()
                    .putString(KEY_PENDING_URGENT_TOKEN, reservationToken)
                    .putString(KEY_PENDING_URGENT_OWNER, PROCESS_URGENT_TOKEN)
                    .commit();
            return reservationToken;
        }
    }

    @SuppressLint("ApplySharedPref")
    static void acknowledgeUrgentWork(Context context, String reservationToken) {
        synchronized (URGENT_SCHEDULE_LOCK) {
            SharedPreferences preferences = schedulerPreferences(context);
                if (reservationToken != null && !reservationToken.isEmpty()
                    && reservationToken.equals(
                    preferences.getString(KEY_PENDING_URGENT_TOKEN, ""))) {
                preferences.edit()
                        .remove(KEY_PENDING_URGENT_TOKEN)
                        .remove(KEY_PENDING_URGENT_OWNER)
                        .commit();
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    static void clearUrgentWorkReservation(Context context) {
        synchronized (URGENT_SCHEDULE_LOCK) {
            schedulerPreferences(context).edit()
                    .remove(KEY_PENDING_URGENT_TOKEN)
                    .remove(KEY_PENDING_URGENT_OWNER)
                    .commit();
        }
    }

    private static SharedPreferences schedulerPreferences(Context context) {
        return context.getSharedPreferences(SCHEDULER_PREFS, Context.MODE_PRIVATE);
    }
}
