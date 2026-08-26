package com.server.smsforwarder;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import javax.mail.MessagingException;

public final class SmtpFailureAndroidTest {
    @Test
    public void testSmtpReplyClassifierLoadsOnAndroidRuntime() {
        String message = SmtpFailure.describe(
                new MessagingException("421 temporary service failure"));

        assertTrue(message.contains("暂时拒绝"));
    }
}
