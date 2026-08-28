package com.server.smsforwarder;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import javax.mail.MessagingException;

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
}
