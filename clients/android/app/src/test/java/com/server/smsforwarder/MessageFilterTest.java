package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MessageFilterTest {
    @Test
    public void emptyAllowlistAllowsAnySender() {
        assertTrue(MessageFilter.senderAllowed("10690000", ""));
    }

    @Test
    public void allowlistAcceptsChineseCountryPrefix() {
        assertTrue(MessageFilter.senderAllowed("+86 138-0013-8000", "13800138000"));
    }

    @Test
    public void allowlistRejectsUnknownSender() {
        assertFalse(MessageFilter.senderAllowed("10690001", "10690000\n10086"));
    }

    @Test
    public void detectsChineseOtpOnlyWhenKeywordAndCodeExist() {
        assertTrue(MessageFilter.isLikelyOtp("您的验证码为 482913，五分钟内有效。"));
        assertFalse(MessageFilter.isLikelyOtp("订单金额为 482913 元。"));
        assertFalse(MessageFilter.isLikelyOtp("验证码将在另一台设备显示。"));
    }

    @Test
    public void detectsEnglishOtp() {
        assertTrue(MessageFilter.isLikelyOtp("Your verification code is 471922."));
    }

    @Test
    public void otpCanBeForwardedWhenFilterDisabled() {
        assertEquals(
                MessageFilter.Decision.FORWARD,
                MessageFilter.decide("10086", "验证码 123456", "", false));
    }

    @Test
    public void otpIsSkippedWhenFilterEnabled() {
        assertEquals(
                MessageFilter.Decision.POSSIBLE_OTP,
                MessageFilter.decide("10086", "验证码 123456", "", true));
    }
}

