package com.server.smsforwarder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MessageFilter {
    enum Decision {
        FORWARD,
        SENDER_NOT_ALLOWED,
        SENDER_BLOCKED,
        POSSIBLE_OTP,
        MODE_NOT_MATCHED,
        BODY_NOT_MATCHED,
        BODY_BLOCKED,
        SIM_NOT_MATCHED,
        OUTSIDE_SCHEDULE
    }

    private static final Pattern OTP_CODE = Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)");
    private static final Pattern OTP_KEYWORD = Pattern.compile(
            "验证码|校验码|动态码|登录码|安全码|一次性密码|verification\\s*code|one[- ]?time|\\botp\\b",
            Pattern.CASE_INSENSITIVE);

    private MessageFilter() {
    }

    static Decision decide(String sender, String body, String allowlist, boolean skipOtp) {
        if (!senderAllowed(sender, allowlist)) {
            return Decision.SENDER_NOT_ALLOWED;
        }
        if (skipOtp && isLikelyOtp(body)) {
            return Decision.POSSIBLE_OTP;
        }
        return Decision.FORWARD;
    }

    static Decision decide(
            String sender,
            String body,
            int simSlot,
            long receivedAt,
            RuleConfig rules) {
        if (!rules.isActiveAt(receivedAt)) {
            return Decision.OUTSIDE_SCHEDULE;
        }
        if (rules.simSlot >= 0 && rules.simSlot != simSlot) {
            return Decision.SIM_NOT_MATCHED;
        }
        if (matchesSender(sender, rules.senderBlock)) {
            return Decision.SENDER_BLOCKED;
        }
        if (!rules.senderAllow.trim().isEmpty() && !matchesSender(sender, rules.senderAllow)) {
            return Decision.SENDER_NOT_ALLOWED;
        }

        boolean otp = isLikelyOtp(body);
        if (RuleConfig.MODE_OTP_ONLY.equals(rules.mode) && !otp) {
            return Decision.MODE_NOT_MATCHED;
        }
        if (RuleConfig.MODE_NON_OTP.equals(rules.mode) && otp) {
            return Decision.POSSIBLE_OTP;
        }
        if (matchesAny(body, splitTokens(rules.bodyExclude))) {
            return Decision.BODY_BLOCKED;
        }

        List<String> includes = splitTokens(rules.bodyInclude);
        boolean includesMatched = includes.isEmpty()
                || (rules.includeAll ? matchesAll(body, includes) : matchesAny(body, includes));
        boolean regexMatched = rules.bodyRegex.trim().isEmpty()
                || com.google.re2j.Pattern.compile(
                        rules.bodyRegex,
                        com.google.re2j.Pattern.CASE_INSENSITIVE
                                | com.google.re2j.Pattern.DOTALL)
                        .matcher(nullToEmpty(body)).find();
        if (RuleConfig.MODE_MATCH.equals(rules.mode) && includes.isEmpty()
                && rules.bodyRegex.trim().isEmpty() && rules.senderAllow.trim().isEmpty()) {
            return Decision.BODY_NOT_MATCHED;
        }
        if (!includesMatched || !regexMatched) {
            return Decision.BODY_NOT_MATCHED;
        }
        return Decision.FORWARD;
    }

    static String transformBody(String body, RuleConfig rules) {
        String safeBody = nullToEmpty(body);
        if (RuleConfig.CONTENT_METADATA.equals(rules.contentMode)) {
            return "（已按隐私设置隐藏短信正文）";
        }
        if (RuleConfig.CONTENT_CODE_ONLY.equals(rules.contentMode)) {
            Matcher matcher = OTP_CODE.matcher(safeBody);
            return matcher.find() ? "验证码：" + matcher.group() : "（未识别到验证码）";
        }
        if (RuleConfig.CONTENT_MASKED.equals(rules.contentMode)) {
            return safeBody.replaceAll("(?<!\\d)\\d{4,}(?!\\d)", "••••");
        }
        return safeBody;
    }

    static boolean isLikelyOtp(String body) {
        return body != null && OTP_KEYWORD.matcher(body).find() && OTP_CODE.matcher(body).find();
    }

    static boolean senderAllowed(String sender, String allowlist) {
        return allowlist == null || allowlist.trim().isEmpty() || matchesSender(sender, allowlist);
    }

    private static boolean matchesSender(String sender, String rulesText) {
        String canonical = canonicalSender(sender);
        for (String rawRule : splitTokens(rulesText)) {
            String rule = rawRule.trim();
            if (rule.startsWith("re:")) {
                if (com.google.re2j.Pattern.compile(
                        rule.substring(3),
                        com.google.re2j.Pattern.CASE_INSENSITIVE)
                        .matcher(nullToEmpty(sender)).find()) {
                    return true;
                }
            } else if (rule.endsWith("*")) {
                if (canonical.startsWith(canonicalSender(rule.substring(0, rule.length() - 1)))) {
                    return true;
                }
            } else if (canonical.equals(canonicalSender(rule))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAny(String value, List<String> tokens) {
        String safe = nullToEmpty(value).toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (safe.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAll(String value, List<String> tokens) {
        String safe = nullToEmpty(value).toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (!safe.contains(token.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitTokens(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String token : value.split("[\\r\\n,;，；]+")) {
            if (!token.trim().isEmpty()) {
                result.add(token.trim());
            }
        }
        return result;
    }

    private static String canonicalSender(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (!digits.isEmpty() && trimmed.matches("[+0-9()\\s.-]+")) {
            if (digits.startsWith("86") && digits.length() == 13) {
                return digits.substring(2);
            }
            return digits;
        }
        return trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
