package com.server.smsforwarder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

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
        properties.put("mail.smtp.connectiontimeout", "20000");
        properties.put("mail.smtp.timeout", "30000");
        properties.put("mail.smtp.writetimeout", "30000");
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

        MimeMessage message = createMessage(session, profile, item);
        Transport transport;
        try {
            transport = session.getTransport("smtp");
        } catch (MessagingException error) {
            throw SmtpStageException.connect(error);
        }
        try {
            try {
                transport.connect(profile.host, profile.port, profile.username, profile.password);
            } catch (MessagingException error) {
                throw SmtpStageException.connect(error);
            }
            try {
                transport.sendMessage(message, message.getAllRecipients());
            } catch (MessagingException error) {
                throw SmtpStageException.send(error);
            }
        } finally {
            if (transport.isConnected()) {
                try {
                    transport.close();
                } catch (MessagingException ignored) {
                    // The server already accepted or rejected sendMessage; closing must not
                    // overwrite that definitive outcome with a less useful transport error.
                }
            }
        }
    }

    static MimeMessage createMessage(Session session, SmtpProfile profile, QueueItem item)
            throws MessagingException {
        MailcapSupport.ensureInstalled();
        MimeMessage message = new StableIdMimeMessage(session, item.id);
        message.setFrom(new InternetAddress(profile.fromAddress));
        for (String recipient : profile.recipients()) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
        }
        message.setSentDate(new Date());
        EmailFormatter.Content content = EmailFormatter.format(item);
        message.setSubject(content.subject, StandardCharsets.UTF_8.name());
        MimeBodyPart plainPart = new MimeBodyPart();
        plainPart.setText(content.plainText, StandardCharsets.UTF_8.name());
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(content.html, "text/html; charset=UTF-8");
        MimeMultipart alternatives = new MimeMultipart("alternative");
        alternatives.addBodyPart(plainPart);
        alternatives.addBodyPart(htmlPart);
        message.setContent(alternatives);
        message.setHeader("X-SMS-Forwarder-Version", BuildConfig.VERSION_NAME);
        message.setHeader("X-SMS-Forwarder-ID", item.id);
        return message;
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
