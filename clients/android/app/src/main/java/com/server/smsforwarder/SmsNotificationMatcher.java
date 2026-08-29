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
        return score(
                receivedAt,
                sender,
                bodyMatchClue,
                notificationAt,
                notificationText,
                notificationText);
    }

    static int score(
            long receivedAt,
            String sender,
            String bodyMatchClue,
            long notificationAt,
            String notificationText,
            String currentEventText) {
        long delta = notificationAt - receivedAt;
        if (delta < -EARLY_TOLERANCE_MS) {
            return -1;
        }
        // Only current-event fields may authorize a mark-read action. Expanded, line-list,
        // subtext, and historic payloads can retain an older SMS after the default app refreshes
        // the notification for a newer message in the same conversation.
        String haystack = normalize(currentEventText);
        String normalizedBodyClue = normalize(bodyMatchClue);
        boolean hasBodyClue = normalizedBodyClue.length() >= MIN_BODY_CLUE_CHARS;
        boolean bodyMatches = hasBodyClue && haystack.contains(normalizedBodyClue);
        // When an original-body clue exists it is stronger than sender/time evidence. A default
        // SMS app can replace the active notification with a newer message from the same sender,
        // so a clue mismatch must disqualify that candidate instead of merely lowering its score.
        if (hasBodyClue && !bodyMatches) {
            return -1;
        }
        // Some default SMS apps refresh an existing notification's post time when their group is
        // updated. After a long offline SMTP delay, accept such an active notification only when
        // the bounded original-body clue still identifies it; sender or time alone is not enough.
        if (delta > LATE_TOLERANCE_MS && !bodyMatches) {
            return -1;
        }
        int score = Math.abs(delta) <= 90_000L ? 2 : delta <= LATE_TOLERANCE_MS ? 1 : 0;
        String normalizedSender = normalize(sender);
        if (normalizedSender.length() >= 3 && haystack.contains(normalizedSender)) {
            score += 6;
        } else {
            String senderTail = tailDigits(sender, 4);
            if (senderTail.length() == 4 && haystack.contains(senderTail)) {
                score += 4;
            }
        }
        if (bodyMatches) {
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
