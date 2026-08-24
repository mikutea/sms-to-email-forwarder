package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RoutingRulesTest {
    private static RuleConfig rules(
            String mode,
            String allow,
            String block,
            String include,
            String exclude,
            String regex,
            boolean includeAll,
            int simSlot,
            String contentMode) {
        return new RuleConfig(
                mode,
                allow,
                block,
                include,
                exclude,
                regex,
                includeAll,
                simSlot,
                false,
                0,
                0,
                0x7f,
                contentMode);
    }

    @Test
    public void blacklistWinsBeforeOtherMatches() {
        RuleConfig config = rules(
                RuleConfig.MODE_ALL,
                "1069*",
                "10690001",
                "",
                "",
                "",
                false,
                -1,
                RuleConfig.CONTENT_FULL);
        assertEquals(
                MessageFilter.Decision.SENDER_BLOCKED,
                MessageFilter.decide("10690001", "普通短信", 0, 1L, config));
    }

    @Test
    public void otpOnlyAndSimRoutingMustBothMatch() {
        RuleConfig config = rules(
                RuleConfig.MODE_OTP_ONLY,
                "",
                "",
                "",
                "",
                "",
                false,
                1,
                RuleConfig.CONTENT_CODE_ONLY);
        assertEquals(
                MessageFilter.Decision.SIM_NOT_MATCHED,
                MessageFilter.decide("10086", "验证码 123456", 0, 1L, config));
        assertEquals(
                MessageFilter.Decision.FORWARD,
                MessageFilter.decide("10086", "验证码 123456", 1, 1L, config));
        assertEquals("验证码：123456", MessageFilter.transformBody("验证码 123456", config));
    }

    @Test
    public void includeAllAndExcludeAreDeterministic() {
        RuleConfig config = rules(
                RuleConfig.MODE_MATCH,
                "",
                "",
                "取件码,驿站",
                "广告",
                "\\d{4,8}",
                true,
                -1,
                RuleConfig.CONTENT_MASKED);
        assertEquals(
                MessageFilter.Decision.FORWARD,
                MessageFilter.decide("菜鸟", "驿站取件码 7788", 0, 1L, config));
        assertEquals(
                MessageFilter.Decision.BODY_BLOCKED,
                MessageFilter.decide("菜鸟", "广告：驿站取件码 7788", 0, 1L, config));
        assertEquals("驿站取件码 ••••", MessageFilter.transformBody("驿站取件码 7788", config));
    }

    @Test(timeout = 1000L)
    public void userRegexUsesLinearTimeEngineForAdversarialInput() {
        RuleConfig config = rules(
                RuleConfig.MODE_MATCH,
                "",
                "",
                "",
                "",
                "(a+)+$",
                false,
                -1,
                RuleConfig.CONTENT_FULL);
        String nearMatch = "a".repeat(20_000) + "!";
        assertEquals(
                MessageFilter.Decision.BODY_NOT_MATCHED,
                MessageFilter.decide("10086", nearMatch, 0, 1L, config));
    }
}
