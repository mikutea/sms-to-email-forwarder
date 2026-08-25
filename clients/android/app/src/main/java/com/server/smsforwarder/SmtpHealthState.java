package com.server.smsforwarder;

import java.util.Locale;

final class SmtpHealthState {
    static final String UNKNOWN = "UNKNOWN";
    static final String VERIFIED = "VERIFIED";
    static final String FAILED = "FAILED";

    final String state;
    final String label;
    final boolean configured;
    final boolean verified;
    final boolean failed;

    private SmtpHealthState(
            String state,
            String label,
            boolean configured,
            boolean verified,
            boolean failed) {
        this.state = state;
        this.label = label;
        this.configured = configured;
        this.verified = verified;
        this.failed = failed;
    }

    static SmtpHealthState from(boolean configured, String storedState) {
        if (!configured) {
            return new SmtpHealthState(UNKNOWN, "待配置", false, false, false);
        }
        if (VERIFIED.equals(storedState)) {
            return new SmtpHealthState(VERIFIED, "已验证", true, true, false);
        }
        if (FAILED.equals(storedState)) {
            return new SmtpHealthState(FAILED, "验证失败", true, false, true);
        }
        return new SmtpHealthState(UNKNOWN, "已配置", true, false, false);
    }

    static String inferLegacy(long lastSuccessAt, long lastStatusAt, String lastStatus) {
        if (lastStatusAt > lastSuccessAt && looksLikeSmtpFailure(lastStatus)) {
            return FAILED;
        }
        return lastSuccessAt > 0L ? VERIFIED : UNKNOWN;
    }

    private static boolean looksLikeSmtpFailure(String status) {
        if (status == null) return false;
        String normalized = status.toLowerCase(Locale.ROOT);
        boolean smtpRelated = normalized.contains("smtp") || normalized.contains("主通道")
                || normalized.contains("备用通道") || normalized.contains("邮件");
        boolean failed = normalized.contains("失败") || normalized.contains("超时")
                || normalized.contains("拒绝") || normalized.contains("中断")
                || normalized.contains("无法") || normalized.contains("受限")
                || normalized.contains("重试");
        return smtpRelated && failed;
    }
}
