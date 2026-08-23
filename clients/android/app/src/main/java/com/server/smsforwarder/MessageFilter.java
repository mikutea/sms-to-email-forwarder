package com.server.smsforwarder;

import java.util.Locale;
import java.util.regex.Pattern;

final class MessageFilter {
    enum Decision {
        FORWARD,
        SENDER_NOT_ALLOWED,
        POSSIBLE_OTP
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

    static boolean isLikelyOtp(String body) {
        if (body == null) {
            return false;
        }
        return OTP_KEYWORD.matcher(body).find() && OTP_CODE.matcher(body).find();
    }

    static boolean senderAllowed(String sender, String allowlist) {
        if (allowlist == null || allowlist.trim().isEmpty()) {
            return true;
        }
        String canonicalSender = canonicalSender(sender);
        String[] entries = allowlist.split("[\\r\\n,;，；]+");
        for (String entry : entries) {
            if (!entry.trim().isEmpty() && canonicalSender.equals(canonicalSender(entry))) {
                return true;
            }
        }
        return false;
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
}
