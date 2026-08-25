package com.server.smsforwarder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.ConnectException;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

public final class SmtpFailureTest {
    @Test
    public void authenticationFailureIsActionableAndDoesNotEchoSecret() {
        String secret = "do-not-display-this-secret";
        AuthenticationFailedException error = new AuthenticationFailedException(secret);
        String message = SmtpFailure.describe(error);
        assertTrue(message.contains("专用密码"));
        assertFalse(message.contains(secret));
    }

    @Test
    public void nestedConnectionFailureSuggestsPortAndEncryption() {
        MessagingException wrapper = new MessagingException(
                "wrapper", new ConnectException("connection refused"));
        assertTrue(SmtpFailure.describe(wrapper).contains("端口与加密方式"));
    }
}
