package com.server.smsforwarder;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.activation.CommandInfo;
import javax.activation.CommandMap;
import javax.activation.DataContentHandler;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

public final class SmtpFailureAndroidTest {
    @Test
    public void testSmtpReplyClassifierLoadsOnAndroidRuntime() {
        String message = SmtpFailure.describe(
                new MessagingException("421 temporary service failure"));

        assertTrue(message.contains("暂时拒绝"));
    }

    @Test
    public void testStageDiagnosticsStayUsefulAndRedactedOnAndroidRuntime() {
        String record = SmtpFailure.describeForRecord(SmtpStageException.connect(
                new MessagingException(
                        "Exception reading response for user@example.test private-token-123")));

        assertTrue(record.contains("CONNECT-AUTH"));
        assertTrue(record.contains("RESPONSE-READ"));
        assertFalse(record.contains("user@example.test"));
        assertFalse(record.contains("private-token-123"));
    }

    @Test
    public void testMultipartAlternativeMessageSerializesOnAndroidRuntime() throws Exception {
        CommandMap original = CommandMap.getDefaultCommandMap();
        try {
            CommandMap.setDefaultCommandMap(new EmptyCommandMap());
            Session session = Session.getInstance(new Properties());
            SmtpProfile profile = new SmtpProfile(
                    "主通道", "smtp.example.test", 465, AppConfig.SECURITY_SSL_TLS,
                    "sender@example.test", "test-secret", "sender@example.test",
                    "recipient@example.test");
            QueueItem item = new QueueItem(
                    "android-mailcap-test", QueueItem.KIND_TEST, System.currentTimeMillis(),
                    "设备自检", "Android multipart serialization test", -1, 0);
            MimeMessage message = SmtpMailer.createMessage(session, profile, item);
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            message.writeTo(output);

            String serialized = output.toString(StandardCharsets.UTF_8.name());
            assertTrue(serialized.contains("multipart/alternative"));
            assertTrue(serialized.contains("Android multipart serialization test"));
        } finally {
            CommandMap.setDefaultCommandMap(original);
        }
    }

    private static final class EmptyCommandMap extends CommandMap {
        @Override
        public CommandInfo[] getPreferredCommands(String mimeType) {
            return new CommandInfo[0];
        }

        @Override
        public CommandInfo[] getAllCommands(String mimeType) {
            return new CommandInfo[0];
        }

        @Override
        public CommandInfo getCommand(String mimeType, String commandName) {
            return null;
        }

        @Override
        public DataContentHandler createDataContentHandler(String mimeType) {
            return null;
        }
    }
}
