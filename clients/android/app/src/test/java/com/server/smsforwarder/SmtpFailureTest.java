package com.server.smsforwarder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

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

    @Test
    public void mobileNetworkPolicyFailureExplainsHuaweiDataAccess() {
        MessagingException wrapper = new MessagingException(
                "Could not connect", new SocketException("EPERM (Operation not permitted)"));
        assertTrue(SmtpFailure.describe(wrapper).contains("移动数据"));
        assertTrue(SmtpFailure.describeForRecord(wrapper).contains("NET-BLOCKED"));
    }

    @Test
    public void noRouteFailurePointsToCurrentNetworkInsteadOfCredentials() {
        MessagingException wrapper = new MessagingException(
                "Could not connect", new NoRouteToHostException("Network is unreachable"));
        assertTrue(SmtpFailure.describe(wrapper).contains("当前网络无法到达"));
        assertTrue(SmtpFailure.describeForRecord(wrapper).contains("NO-ROUTE"));
    }

    @Test
    public void javaMailNextExceptionSocketResetIsNotLostAsGenericProtocolFailure() {
        MessagingException wrapper = new MessagingException("Could not send command to SMTP host");
        wrapper.setNextException(new SocketException("Connection reset"));
        assertTrue(SmtpFailure.describe(wrapper).contains("连接被中断"));
        assertTrue(SmtpFailure.describeForRecord(wrapper).contains("CONNECTION-RESET"));
    }

    @Test
    public void javaMailNextExceptionTimeoutIsClassified() {
        MessagingException wrapper = new MessagingException("SMTP transport failed");
        wrapper.setNextException(new SocketTimeoutException("Read timed out"));
        assertTrue(SmtpFailure.describe(wrapper).contains("超时"));
        assertTrue(SmtpFailure.describeForRecord(wrapper).contains("TIMEOUT"));
    }

    @Test
    public void javaMailNextExceptionAuthenticationControlsRetryPolicy() {
        MessagingException wrapper = new MessagingException("SMTP transport failed");
        wrapper.setNextException(new AuthenticationFailedException("private server response"));
        assertTrue(SmtpFailure.isAuthenticationFailure(wrapper));
        assertTrue(SmtpFailure.describeForRecord(wrapper).contains("AUTH"));
        assertFalse(SmtpFailure.describe(wrapper).contains("private server response"));
    }

    @Test
    public void eofDuringSmtpConversationExplainsEarlyServerClose() {
        MessagingException wrapper = new MessagingException("SMTP protocol failed");
        wrapper.setNextException(new EOFException("EOF on socket"));
        assertTrue(SmtpFailure.describe(wrapper).contains("提前断开"));
        assertTrue(SmtpFailure.describeForRecord(wrapper).contains("CONNECTION-CLOSED"));
    }

    @Test
    public void smtpAuthenticationReplyCodeIsClassifiedWithoutEchoingResponse() {
        String serverResponse = "535 5.7.8 private authentication response";
        MessagingException error = new MessagingException(serverResponse);
        assertTrue(SmtpFailure.isAuthenticationFailure(error));
        assertTrue(SmtpFailure.describeForRecord(error).contains("AUTH"));
        assertFalse(SmtpFailure.describe(error).contains(serverResponse));
    }

    @Test
    public void smtpTemporaryReplyCodeIsPreservedAsSafeDiagnosticCode() {
        MessagingException error = new MessagingException("421 temporary service failure");
        assertTrue(SmtpFailure.describe(error).contains("暂时拒绝"));
        assertTrue(SmtpFailure.describeForRecord(error).contains("SMTP-421"));
        assertFalse(SmtpFailure.describe(error).contains("temporary service failure"));
    }

    @Test
    public void smtpPortNumberIsNotMistakenForReplyCode() {
        MessagingException error = new MessagingException("Could not open smtp.example.test, port 465");
        assertFalse(SmtpFailure.describeForRecord(error).contains("SMTP-465"));
    }
}
