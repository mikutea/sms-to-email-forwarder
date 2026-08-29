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
    private static final int MAX_SMS_BODY_CHARS = 20_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }
        final long localReceivedAt = System.currentTimeMillis();
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                handle(appContext, intent, localReceivedAt);
            } catch (RuntimeException e) {
                AppConfig.setStatus(appContext, "接收短信时发生本地错误：" + safeMessage(e));
            } finally {
                pendingResult.finish();
            }
        });
    }

    private static void handle(Context context, Intent intent, long localReceivedAt) {
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
        boolean truncated = false;
        for (SmsMessage part : parts) {
            if (part != null && part.getDisplayMessageBody() != null) {
                String partBody = part.getDisplayMessageBody();
                int remaining = MAX_SMS_BODY_CHARS - body.length();
                if (remaining <= 0) {
                    truncated = true;
                    break;
                }
                if (partBody.length() > remaining) {
                    body.append(partBody, 0, remaining);
                    truncated = true;
                    break;
                }
                body.append(partBody);
            }
        }
        if (truncated) {
            body.append("\n（短信正文过长，已在本机截断）");
        }
        long receivedAt = parts[0].getTimestampMillis();
        if (receivedAt <= 0L) {
            receivedAt = System.currentTimeMillis();
        }
        AppConfig.setSmsReceived(context, receivedAt);

        int simSlot = extractSimSlot(intent);
        RuleConfig rules = RuleConfig.load(context);
        MessageFilter.Decision decision = MessageFilter.decide(
                sender,
                body.toString(),
                simSlot,
                receivedAt,
                rules);
        if (decision != MessageFilter.Decision.FORWARD) {
            String id = QueueDatabase.stableSmsId(sender, body.toString(), receivedAt, simSlot);
            String reason = decisionText(decision);
            QueueDatabase.get(context).recordFiltered(
                    id, receivedAt, sender, body.toString(), simSlot, reason);
            AppConfig.setStatus(context, reason);
            return;
        }

        QueueDatabase database = QueueDatabase.get(context);
        QueueDatabase.EnqueueResult result = SmsReadFeature.enqueueIncomingSms(
                context,
                database,
                sender,
                MessageFilter.transformBody(body.toString(), rules),
                body.toString(),
                receivedAt,
                localReceivedAt,
                simSlot);
        if (result == QueueDatabase.EnqueueResult.INSERTED) {
            AppConfig.setStatus(context, "新短信已进入本机加密发送队列");
        } else if (result == QueueDatabase.EnqueueResult.DUPLICATE) {
            AppConfig.setStatus(context, "重复短信广播已忽略");
        } else {
            String id = QueueDatabase.stableSmsId(sender, body.toString(), receivedAt, simSlot);
            database.recordFiltered(
                    id,
                    receivedAt,
                    sender,
                    "（正文未保存：待发送队列已达到安全上限）",
                    simSlot,
                    "待发送队列已满，本条未入队；请恢复网络或清理队列");
            AppConfig.setStatus(context, "待发送队列已达到安全上限，本条短信未入队");
        }
        if (result == QueueDatabase.EnqueueResult.INSERTED) {
            // A new SMS must not wait behind an older delayed backoff request.
            // Urgent work is serialized separately from the normal delayed-retry chain.
            ForwardScheduler.schedule(context);
        }
    }

    private static String decisionText(MessageFilter.Decision decision) {
        switch (decision) {
            case SENDER_NOT_ALLOWED:
                return "短信因发送方未命中白名单而跳过";
            case SENDER_BLOCKED:
                return "短信因发送方命中黑名单而跳过";
            case POSSIBLE_OTP:
                return "疑似验证码短信已按隐私规则跳过";
            case MODE_NOT_MATCHED:
                return "短信类型未命中转发模式";
            case BODY_NOT_MATCHED:
                return "短信正文未命中关键词或正则规则";
            case BODY_BLOCKED:
                return "短信正文命中排除关键词";
            case SIM_NOT_MATCHED:
                return "短信 SIM 卡槽未命中转发规则";
            case OUTSIDE_SCHEDULE:
                return "短信到达时间不在规则生效时段";
            default:
                return "短信已按规则跳过";
        }
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
