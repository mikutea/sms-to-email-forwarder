package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SmsNotificationMatcherTest {
    @Test
    public void recognizesSemanticAndKnownLocalizedMarkReadActions() {
        assertTrue(SmsNotificationMatcher.isMarkReadAction(2, "anything"));
        assertTrue(SmsNotificationMatcher.isMarkReadAction(0, "标记已读"));
        assertTrue(SmsNotificationMatcher.isMarkReadAction(0, "标记为已读"));
        assertTrue(SmsNotificationMatcher.isMarkReadAction(0, "Mark as read"));
        assertFalse(SmsNotificationMatcher.isMarkReadAction(1, "回复"));
        assertFalse(SmsNotificationMatcher.isMarkReadAction(0, "删除"));
    }

    @Test
    public void timeOnlyCandidateIsNeverConfident() {
        assertFalse(SmsNotificationMatcher.isConfident(2, -1, 1));
        assertTrue(SmsNotificationMatcher.isConfident(6, -1, 1));
    }

    @Test
    public void senderAndBodyCluesBeatTimeOnlyCandidate() {
        long receivedAt = 1_000_000L;
        int matching = SmsNotificationMatcher.score(
                receivedAt, "10690001", "您的服务已经受理", receivedAt + 5_000L,
                "10690001 您的服务已经受理");
        int timeOnly = SmsNotificationMatcher.score(
                receivedAt, "10690001", "您的服务已经受理", receivedAt + 4_000L,
                "另一条会话和完全不同的内容");

        assertTrue(matching >= 8);
        assertEquals(2, timeOnly);
        assertTrue(SmsNotificationMatcher.isConfident(matching, timeOnly, 2));
        assertFalse(SmsNotificationMatcher.isConfident(timeOnly, timeOnly, 2));
    }

    @Test
    public void rejectsNotificationsOutsideSafetyWindow() {
        long receivedAt = 1_000_000L;

        assertEquals(-1, SmsNotificationMatcher.score(
                receivedAt, "10086", "测试", receivedAt - 31_000L, "10086 测试"));
        assertEquals(-1, SmsNotificationMatcher.score(
                receivedAt, "10086", "测试", receivedAt + 601_000L, "10086 测试"));
    }

    @Test
    public void strongBodyClueMatchesARefreshedNotificationAfterLongQueueDelay() {
        long receivedAt = 1_000_000L;
        String clue = "您的旅行验证码123456";

        int refreshed = SmsNotificationMatcher.score(
                receivedAt,
                "10690001",
                clue,
                receivedAt + 24L * 60L * 60L * 1000L,
                "10690001 您的旅行验证码123456");
        int senderOnly = SmsNotificationMatcher.score(
                receivedAt,
                "10690001",
                clue,
                receivedAt + 24L * 60L * 60L * 1000L,
                "10690001 另一条会话");

        assertTrue(refreshed >= 8);
        assertTrue(SmsNotificationMatcher.isConfident(refreshed, -1, 1));
        assertEquals(-1, senderOnly);
    }

    @Test
    public void originalBodyClueIsBoundedBeforePrivacyTransformation() {
        String original = "【示例服务】您的业务已经办理成功，请留意后续通知";

        String clue = SmsNotificationMatcher.bodyMatchClue(original);

        assertEquals(16, clue.length());
        assertTrue(SmsNotificationMatcher.score(
                1_000_000L,
                "10690001",
                clue,
                1_005_000L,
                "示例联系人 " + original) >= 8);
        assertEquals("", SmsNotificationMatcher.bodyMatchClue("太短"));
    }
}
