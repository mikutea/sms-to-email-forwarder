package com.server.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SmsReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                handle(appContext, intent);
            } catch (RuntimeException e) {
                AppConfig.setStatus(appContext, "接收短信时发生本地错误：" + safeMessage(e));
            } finally {
                pendingResult.finish();
            }
        });
    }

    private static void handle(Context context, Intent intent) {
        AppConfig config = AppConfig.load(context);
        if (!config.enabled || !config.privacyConsent) {
            return;
        }

        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) {
            AppConfig.setStatus(context, "系统通知了新短信，但未提供可解析的短信内容");
            return;
        }

        String sender = parts[0].getDisplayOriginatingAddress();
        if (sender == null || sender.trim().isEmpty()) {
            sender = "未知发件人";
        }
        StringBuilder body = new StringBuilder();
        for (SmsMessage part : parts) {
            if (part != null && part.getDisplayMessageBody() != null) {
                body.append(part.getDisplayMessageBody());
            }
        }
        long receivedAt = parts[0].getTimestampMillis();
        if (receivedAt <= 0L) {
            receivedAt = System.currentTimeMillis();
        }

        MessageFilter.Decision decision = MessageFilter.decide(
                sender,
                body.toString(),
                config.senderAllowlist,
                config.skipOtp);
        if (decision == MessageFilter.Decision.SENDER_NOT_ALLOWED) {
            AppConfig.setStatus(context, "新短信因发件人不在白名单中而跳过");
            return;
        }
        if (decision == MessageFilter.Decision.POSSIBLE_OTP) {
            AppConfig.setStatus(context, "疑似验证码短信已按隐私设置跳过");
            return;
        }

        int simSlot = extractSimSlot(intent);
        boolean inserted = QueueDatabase.get(context).enqueueSms(
                sender,
                body.toString(),
                receivedAt,
                simSlot);
        if (inserted) {
            AppConfig.setStatus(context, "新短信已进入本机加密发送队列");
        }
        ForwardScheduler.schedule(context);
    }

    private static int extractSimSlot(Intent intent) {
        String[] keys = {"slot", "slot_id", "simSlot", "phone"};
        for (String key : keys) {
            int value = intent.getIntExtra(key, Integer.MIN_VALUE);
            if (value != Integer.MIN_VALUE) {
                return value;
            }
        }
        return -1;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() > 120 ? message.substring(0, 120) : message;
    }
}

