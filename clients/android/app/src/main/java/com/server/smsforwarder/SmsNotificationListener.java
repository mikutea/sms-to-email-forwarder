package com.server.smsforwarder;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SmsNotificationListener extends NotificationListenerService {
    private static final long ACTION_SETTLE_MS = 3_000L;
    // Use the stable wire keys directly so API 23 can inspect MessagingStyle extras without
    // referencing constants added in API 24/26.
    private static final String EXTRA_MESSAGES_COMPAT = "android.messages";
    private static final String EXTRA_HISTORIC_MESSAGES_COMPAT = "android.messages.historic";
    private static volatile SmsNotificationListener connectedInstance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long scheduledAt;
    private final Runnable scheduledProcessing = () -> {
        scheduledAt = 0L;
        processPendingAsync();
    };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        if (!SmsReadFeature.isEnabled(this)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                // Android 6 has no requestRebind/requestUnbind API. The system may deliver the
                // first binding before MainActivity observes the grant and enables the feature,
                // so retain the instance while every disabled callback remains guarded.
                connectedInstance = this;
            } else {
                requestUnbind();
            }
            return;
        }
        connectedInstance = this;
        processPendingAsync();
    }

    @Override
    public void onListenerDisconnected() {
        if (connectedInstance == this) {
            connectedInstance = null;
        }
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        super.onNotificationPosted(notification);
        if (!SmsReadFeature.isEnabled(this)) {
            return;
        }
        String defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this);
        if (defaultSmsPackage != null && defaultSmsPackage.equals(notification.getPackageName())) {
            processPendingAsync();
        }
    }

    @Override
    public void onDestroy() {
        if (connectedInstance == this) {
            connectedInstance = null;
        }
        cancelScheduledProcessing();
        executor.shutdownNow();
        super.onDestroy();
    }

    static void requestProcessing(Context context) {
        if (!SmsReadFeature.isEnabled(context)) {
            return;
        }
        SmsNotificationListener instance = connectedInstance;
        if (instance != null) {
            instance.processPendingAsync();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && SmsReadFeature.hasNotificationAccess(context)) {
            requestRebind(SmsReadFeature.listenerComponent(context));
        }
    }

    static void onFeatureDisabled() {
        SmsNotificationListener instance = connectedInstance;
        if (instance == null) {
            return;
        }
        instance.cancelScheduledProcessing();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectedInstance = null;
            instance.requestUnbind();
        }
        // API 23 cannot request an unbind or rebind. Keep the already-bound instance so a later
        // opt-in can process the post-SMTP receipt; disabled callbacks still return immediately.
    }

    static boolean retainDisabledBinding(int sdkInt) {
        return sdkInt < Build.VERSION_CODES.N;
    }

    private void processPendingAsync() {
        if (executor.isShutdown()) {
            return;
        }
        try {
            executor.execute(this::processPending);
        } catch (RuntimeException ignored) {
            // The system can destroy the listener while a notification callback is in flight.
        }
    }

    private void processPending() {
        synchronized (SmsReadFeature.operationLock()) {
            processPendingWhileOptInCannotChange();
        }
    }

    private void processPendingWhileOptInCannotChange() {
        if (!SmsReadFeature.isEnabled(this) || !SmsReadFeature.hasNotificationAccess(this)) {
            return;
        }
        long now = System.currentTimeMillis();
        QueueDatabase database = QueueDatabase.get(this);
        List<ReadReceiptRequest> requests = database.readReceiptRequests(now);
        if (requests.isEmpty()) {
            return;
        }
        String defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this);
        if (defaultSmsPackage == null || defaultSmsPackage.isBlank()) {
            scheduleExpiry(requests, now);
            return;
        }
        StatusBarNotification[] active;
        try {
            active = getActiveNotifications();
        } catch (RuntimeException error) {
            scheduleExpiry(requests, now);
            return;
        }
        if (active == null || active.length == 0) {
            scheduleExpiry(requests, now);
            return;
        }
        for (ReadReceiptRequest request : requests) {
            Candidate candidate = bestCandidate(defaultSmsPackage, active, request);
            if (candidate == null) {
                continue;
            }
            if (candidate.action == null || candidate.action.actionIntent == null) {
                long age = Math.max(0L, now - candidate.postTime);
                if (age < ACTION_SETTLE_MS) {
                    scheduleProcessing(ACTION_SETTLE_MS - age);
                    continue;
                }
                database.removeReadReceipt(request.id);
                database.updateReadReceiptOutcome(
                        request.id,
                        "SMTP 服务器已接受邮件 · 默认短信应用未提供安全的标记已读动作");
                continue;
            }
            try {
                boolean dispatched = SmsReadFeature.dispatchReadActionIfAllowed(
                        this,
                        database,
                        request.id,
                        candidate.action.actionIntent);
                if (!dispatched) {
                    continue;
                }
            } catch (PendingIntent.CanceledException | RuntimeException error) {
                database.removeReadReceipt(request.id);
                database.updateReadReceiptOutcome(
                        request.id, "SMTP 服务器已接受邮件 · 系统短信标记已读失败");
            }
        }
        long finishedAt = System.currentTimeMillis();
        scheduleExpiry(database.readReceiptRequests(finishedAt), finishedAt);
    }

    private void scheduleExpiry(List<ReadReceiptRequest> requests, long now) {
        long earliest = Long.MAX_VALUE;
        for (ReadReceiptRequest request : requests) {
            earliest = Math.min(earliest, request.expiresAt);
        }
        if (earliest != Long.MAX_VALUE) {
            scheduleProcessing(Math.max(250L, earliest - now + 100L));
        }
    }

    private void scheduleProcessing(long delayMs) {
        long target = System.currentTimeMillis() + Math.max(0L, delayMs);
        handler.post(() -> {
            if (scheduledAt != 0L && scheduledAt <= target) {
                return;
            }
            handler.removeCallbacks(scheduledProcessing);
            scheduledAt = target;
            handler.postDelayed(
                    scheduledProcessing,
                    Math.max(0L, target - System.currentTimeMillis()));
        });
    }

    private Candidate bestCandidate(
            String defaultSmsPackage,
            StatusBarNotification[] active,
            ReadReceiptRequest request) {
        List<Candidate> candidates = new ArrayList<>();
        for (StatusBarNotification status : active) {
            if (status == null || !defaultSmsPackage.equals(status.getPackageName())) {
                continue;
            }
            Notification notification = status.getNotification();
            if (notification == null
                    || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
                continue;
            }
            int score = SmsNotificationMatcher.score(
                    request.receivedAt,
                    request.sender,
                    request.bodyMatchClue,
                    status.getPostTime(),
                    searchableText(notification),
                    currentEventText(notification));
            if (score >= 0) {
                candidates.add(new Candidate(
                        markReadAction(notification), score, status.getPostTime()));
            }
        }
        Candidate best = null;
        int secondScore = -1;
        for (Candidate candidate : candidates) {
            if (best == null || candidate.score > best.score) {
                secondScore = best == null ? -1 : best.score;
                best = candidate;
            } else if (candidate.score > secondScore) {
                secondScore = candidate.score;
            }
        }
        return best != null
                && SmsNotificationMatcher.isConfident(best.score, secondScore, candidates.size())
                ? best : null;
    }

    private static Notification.Action markReadAction(Notification notification) {
        if (notification.actions == null) {
            return null;
        }
        for (Notification.Action action : notification.actions) {
            if (action == null) {
                continue;
            }
            int semantic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? action.getSemanticAction() : 0;
            if (SmsNotificationMatcher.isMarkReadAction(
                    semantic, action.title == null ? "" : action.title.toString())) {
                return action;
            }
        }
        return null;
    }

    private static String searchableText(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        append(result, extras.getCharSequence(Notification.EXTRA_TITLE));
        append(result, extras.getCharSequence(Notification.EXTRA_TEXT));
        append(result, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        append(result, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                append(result, line);
            }
        }
        appendMessages(result, extras.getParcelableArray(EXTRA_MESSAGES_COMPAT));
        appendMessages(result, extras.getParcelableArray(EXTRA_HISTORIC_MESSAGES_COMPAT));
        return result.toString();
    }

    private static String currentEventText(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        append(result, extras.getCharSequence(Notification.EXTRA_TITLE));
        append(result, extras.getCharSequence(Notification.EXTRA_TEXT));
        // Expanded, line-list, subtext, and historic payloads can describe older messages in the
        // same conversation. A long-window exception may use only the current summary plus the
        // newest MessagingStyle entry.
        appendLatestMessage(result, extras.getParcelableArray(EXTRA_MESSAGES_COMPAT));
        return result.toString();
    }

    private static void appendLatestMessage(StringBuilder result, Parcelable[] messages) {
        if (messages == null) {
            return;
        }
        for (int i = messages.length - 1; i >= 0; i--) {
            if (!(messages[i] instanceof Bundle)) {
                continue;
            }
            Bundle message = (Bundle) messages[i];
            append(result, message.getCharSequence("sender"));
            append(result, message.getCharSequence("text"));
            return;
        }
    }

    private void cancelScheduledProcessing() {
        handler.removeCallbacks(scheduledProcessing);
        scheduledAt = 0L;
    }

    private static void appendMessages(StringBuilder result, Parcelable[] messages) {
        if (messages == null) {
            return;
        }
        for (Parcelable parcelable : messages) {
            if (!(parcelable instanceof Bundle)) {
                continue;
            }
            Bundle message = (Bundle) parcelable;
            append(result, message.getCharSequence("sender"));
            append(result, message.getCharSequence("text"));
        }
    }

    private static void append(StringBuilder result, CharSequence value) {
        if (value != null && value.length() > 0) {
            result.append(' ').append(value);
        }
    }

    private static final class Candidate {
        final Notification.Action action;
        final int score;
        final long postTime;

        Candidate(Notification.Action action, int score, long postTime) {
            this.action = action;
            this.score = score;
            this.postTime = postTime;
        }
    }
}
