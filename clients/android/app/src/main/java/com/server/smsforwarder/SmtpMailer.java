package com.server.smsforwarder;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

final class SmtpMailer {
    private SmtpMailer() {
    }

    static void send(AppConfig config, QueueItem item) throws MessagingException {
        send(config.primaryProfile(), item);
    }

    static void send(SmtpProfile profile, QueueItem item) throws MessagingException {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", profile.host);
        properties.put("mail.smtp.port", Integer.toString(profile.port));
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", "6000");
        properties.put("mail.smtp.timeout", "8000");
        properties.put("mail.smtp.writetimeout", "8000");
        properties.put("mail.smtp.ssl.checkserveridentity", "true");

        if (AppConfig.SECURITY_SSL_TLS.equals(profile.security)) {
            properties.put("mail.smtp.ssl.enable", "true");
        } else {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
        }

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(profile.username, profile.password);
            }
        });
        session.setDebug(false);

        MimeMessage message = new StableIdMimeMessage(session, item.id);
        message.setFrom(new InternetAddress(profile.fromAddress));
        for (String recipient : profile.recipients()) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
        }
        message.setSentDate(new Date());
        message.setSubject(subject(item), StandardCharsets.UTF_8.name());
        message.setText(body(item), StandardCharsets.UTF_8.name());
        message.setHeader("X-SMS-Forwarder-Version", BuildConfig.VERSION_NAME);
        message.setHeader("X-SMS-Forwarder-ID", item.id);
        Transport.send(message);
    }

    private static String subject(QueueItem item) {
        if (QueueItem.KIND_TEST.equals(item.kind)) {
            return "[短信转邮箱] SMTP 测试成功";
        }
        if (QueueItem.KIND_HEARTBEAT.equals(item.kind)) {
            return "[雁笺] 旅行守护心跳正常";
        }
        if (QueueItem.KIND_ALERT.equals(item.kind)) {
            return "[雁笺提醒] " + safeSubject(item.sender);
        }
        return "[新短信] " + safeSubject(item.sender);
    }

    private static String safeSubject(String value) {
        String sender = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (sender.length() > 40) {
            sender = sender.substring(0, 40);
        }
        return sender;
    }

    private static String body(QueueItem item) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
                .format(new Date(item.receivedAt));
        if (QueueItem.KIND_TEST.equals(item.kind)
                || QueueItem.KIND_HEARTBEAT.equals(item.kind)
                || QueueItem.KIND_ALERT.equals(item.kind)) {
            return item.body + "\n\n发送时间：" + time;
        }
        String sim = item.simSlot >= 0 ? "SIM " + (item.simSlot + 1) : "未知";
        return "短信接收时间：" + time
                + "\n卡槽：" + sim
                + "\n发件人：" + item.sender
                + "\n\n短信正文：\n" + item.body
                + "\n\n---\n由短信转邮箱 App 自动发送";
    }

    private static final class StableIdMimeMessage extends MimeMessage {
        private final String stableId;

        StableIdMimeMessage(Session session, String stableId) {
            super(session);
            this.stableId = stableId.replaceAll("[^A-Za-z0-9._-]", "");
        }

        @Override
        protected void updateMessageID() throws MessagingException {
            setHeader("Message-ID", "<sms-forwarder-" + stableId + "@device.local>");
        }
    }
}
