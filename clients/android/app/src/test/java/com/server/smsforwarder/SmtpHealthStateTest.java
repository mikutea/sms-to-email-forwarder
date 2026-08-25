package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SmtpHealthStateTest {
    @Test
    public void validConfigurationWithoutProbeIsOnlyConfigured() {
        SmtpHealthState state = SmtpHealthState.from(true, SmtpHealthState.UNKNOWN);
        assertEquals("已配置", state.label);
        assertFalse(state.verified);
        assertFalse(state.failed);
    }

    @Test
    public void latestFailedProbeOverridesAnOlderSuccess() {
        assertEquals(
                SmtpHealthState.FAILED,
                SmtpHealthState.inferLegacy(
                        100L,
                        200L,
                        "主通道测试失败：SMTP 会话未完成"));
    }

    @Test
    public void verifiedStateIsRequiredForTravelReadiness() {
        SmtpHealthState state = SmtpHealthState.from(true, SmtpHealthState.VERIFIED);
        assertEquals("已验证", state.label);
        assertTrue(state.verified);
        assertFalse(state.failed);
    }

    @Test
    public void failedStateIsVisibleInsteadOfPretendingConnected() {
        SmtpHealthState state = SmtpHealthState.from(true, SmtpHealthState.FAILED);
        assertEquals("验证失败", state.label);
        assertFalse(state.verified);
        assertTrue(state.failed);
    }
}
