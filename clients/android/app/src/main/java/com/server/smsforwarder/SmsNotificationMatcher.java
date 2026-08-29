package com.server.smsforwarder;

import java.util.Locale;

final class SmsNotificationMatcher {
    private static final long EARLY_TOLERANCE_MS = 30_000L;
    private static final long LATE_TOLERANCE_MS = 10L * 60L * 1000L;
    private static final int MIN_BODY_CLUE_CHARS = 6;
    private static final int MAX_BODY_CLUE_CHARS = 16;

    private SmsNotificationMatcher() {
    }

    static boolean isMarkReadAction(int semanticAction, String title) {
        if (semanticAction == 2) {
            return true;
        }
        String normalized = normalize(title);
        return "标记已读".equals(normalized)
                || "标记为已读".equals(normalized)
                || "标为已读".equals(normalized)
                || "设为已读".equals(normalized)
                || "markasread".equals(normalized);
    }

    static int score(
            long receivedAt,
            String sender,
            String bodyMatchClue,
            long notificationAt,
            String notificationText) {
        long delta = notificationAt - receivedAt;
        if (delta < -EARLY_TOLERANCE_MS || delta > LATE_TOLERANCE_MS) {
            return -1;
        }
        String haystack = normalize(notificationText);
        int score = Math.abs(delta) <= 90_000L ? 2 : 1;
        String normalizedSender = normalize(sender);
        if (normalizedSender.length() >= 3 && haystack.contains(normalizedSender)) {
            score += 6;
        } else {
            String senderTail = tailDigits(sender, 4);
            if (senderTail.length() == 4 && haystack.contains(senderTail)) {
                score += 4;
            }
        }
        String normalizedBodyClue = normalize(bodyMatchClue);
        if (normalizedBodyClue.length() >= MIN_BODY_CLUE_CHARS
                && haystack.contains(normalizedBodyClue)) {
            score += 8;
        }
        return score;
    }

    static String bodyMatchClue(String originalBody) {
        String normalized = normalize(originalBody);
        if (normalized.length() < MIN_BODY_CLUE_CHARS) {
            return "";
        }
        return normalized.substring(0, Math.min(MAX_BODY_CLUE_CHARS, normalized.length()));
    }

    static boolean isConfident(int bestScore, int secondScore, int candidateCount) {
        if (bestScore >= 8 && bestScore > secondScore) {
            return true;
        }
        return candidateCount == 1 && bestScore >= 6;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        String lower = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char valueChar = lower.charAt(i);
            if (Character.isLetterOrDigit(valueChar) || isCjk(valueChar)) {
                result.append(valueChar);
            }
        }
        return result.toString();
    }

    private static String tailDigits(String value, int count) {
        StringBuilder digits = new StringBuilder();
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char valueChar = value.charAt(i);
                if (Character.isDigit(valueChar)) {
                    digits.append(valueChar);
                }
            }
        }
        return digits.length() < count
                ? ""
                : digits.substring(digits.length() - count);
    }

    private static boolean isCjk(char value) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(value);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
