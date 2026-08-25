package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public final class SmtpMailerTest {
    @Test
    public void buildsMultipartAlternativeWithStableHeaders() throws Exception {
        SmtpProfile profile = new SmtpProfile(
                "主通道", "smtp.example.com", 465, AppConfig.SECURITY_SSL_TLS,
                "sender@example.com", "test-password", "sender@example.com",
                "one@example.com,two@example.com");
        QueueItem item = new QueueItem(
                "stable-test-id", QueueItem.KIND_SMS, 1_700_000_000_000L,
                "10086", "验证码 123456", 0, 0);

        MimeMessage message = SmtpMailer.createMessage(
                Session.getInstance(new Properties()), profile, item);
        message.saveChanges();

        assertEquals("[雁笺短信] 10086", message.getSubject());
        assertEquals("stable-test-id", message.getHeader("X-SMS-Forwarder-ID", null));
        assertEquals(2, message.getAllRecipients().length);
        assertTrue(message.getContentType().startsWith("multipart/alternative"));

        MimeMultipart multipart = (MimeMultipart) message.getContent();
        assertEquals(2, multipart.getCount());
        BodyPart plain = multipart.getBodyPart(0);
        BodyPart html = multipart.getBodyPart(1);
        assertTrue(plain.getContentType().startsWith("text/plain"));
        assertTrue(html.getContentType().startsWith("text/html"));
        assertTrue(html.getContent().toString().contains("验证码 123456"));
    }
}
