package com.server.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Patterns;

final class AppConfig {
    static final String SECURITY_SSL_TLS = "SSL_TLS";
    static final String SECURITY_STARTTLS = "STARTTLS";

    private static final String PREFS = "sms_forwarder_config";
    private static final String KEY_SMTP_HOST = "smtp_host";
    private static final String KEY_SMTP_PORT = "smtp_port";
    private static final String KEY_SMTP_SECURITY = "smtp_security";
    private static final String KEY_SMTP_USERNAME = "smtp_username";
    private static final String KEY_SMTP_PASSWORD = "smtp_password_encrypted";
    private static final String KEY_FROM_ADDRESS = "from_address";
    private static final String KEY_RECIPIENT = "recipient";
    private static final String KEY_ALLOWLIST = "sender_allowlist";
    private static final String KEY_SKIP_OTP = "skip_otp";
    private static final String KEY_CONSENT = "privacy_consent";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_STATUS = "last_status";
    private static final String KEY_LAST_STATUS_AT = "last_status_at";
    private static final String KEY_LAST_SUCCESS_AT = "last_success_at";

    final String smtpHost;
    final int smtpPort;
    final String smtpSecurity;
    final String smtpUsername;
    final String smtpPassword;
    final String fromAddress;
    final String recipient;
    final String senderAllowlist;
    final boolean skipOtp;
    final boolean privacyConsent;
    final boolean enabled;

    private AppConfig(
            String smtpHost,
            int smtpPort,
            String smtpSecurity,
            String smtpUsername,
            String smtpPassword,
            String fromAddress,
            String recipient,
            String senderAllowlist,
            boolean skipOtp,
            boolean privacyConsent,
            boolean enabled) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpSecurity = smtpSecurity;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.fromAddress = fromAddress;
        this.recipient = recipient;
        this.senderAllowlist = senderAllowlist;
        this.skipOtp = skipOtp;
        this.privacyConsent = privacyConsent;
        this.enabled = enabled;
    }

    static AppConfig load(Context context) {
        SharedPreferences prefs = prefs(context);
        String password = "";
        try {
            password = CryptoStore.decrypt(prefs.getString(KEY_SMTP_PASSWORD, ""));
        } catch (IllegalStateException e) {
            setStatus(context, "SMTP 授权码无法解密，请重新配置");
        }
        return new AppConfig(
                prefs.getString(KEY_SMTP_HOST, ""),
                prefs.getInt(KEY_SMTP_PORT, 465),
                prefs.getString(KEY_SMTP_SECURITY, SECURITY_SSL_TLS),
                prefs.getString(KEY_SMTP_USERNAME, ""),
                password,
                prefs.getString(KEY_FROM_ADDRESS, ""),
                prefs.getString(KEY_RECIPIENT, ""),
                prefs.getString(KEY_ALLOWLIST, ""),
                prefs.getBoolean(KEY_SKIP_OTP, true),
                prefs.getBoolean(KEY_CONSENT, false),
                prefs.getBoolean(KEY_ENABLED, false));
    }

    static void save(
            Context context,
            String smtpHost,
            int smtpPort,
            String smtpSecurity,
            String smtpUsername,
            String smtpPassword,
            String fromAddress,
            String recipient,
            String senderAllowlist,
            boolean skipOtp,
            boolean privacyConsent,
            boolean enabled) {
        prefs(context).edit()
                .putString(KEY_SMTP_HOST, smtpHost.trim())
                .putInt(KEY_SMTP_PORT, smtpPort)
                .putString(KEY_SMTP_SECURITY, smtpSecurity)
                .putString(KEY_SMTP_USERNAME, smtpUsername.trim())
                .putString(KEY_SMTP_PASSWORD, CryptoStore.encrypt(smtpPassword))
                .putString(KEY_FROM_ADDRESS, fromAddress.trim())
                .putString(KEY_RECIPIENT, recipient.trim())
                .putString(KEY_ALLOWLIST, senderAllowlist.trim())
                .putBoolean(KEY_SKIP_OTP, skipOtp)
                .putBoolean(KEY_CONSENT, privacyConsent)
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }

    static String validate(
            String smtpHost,
            String smtpPortText,
            String smtpSecurity,
            String smtpUsername,
            String smtpPassword,
            String fromAddress,
            String recipient,
            boolean privacyConsent) {
        if (!privacyConsent) {
            return "请先确认短信隐私转发风险";
        }
        String host = smtpHost.trim();
        if (host.isEmpty() || host.length() > 253 || host.matches(".*\\s.*") || host.contains("://")) {
            return "SMTP 主机格式不正确，只填写主机名或 IP";
        }
        int port;
        try {
            port = Integer.parseInt(smtpPortText.trim());
        } catch (NumberFormatException e) {
            return "SMTP 端口必须是数字";
        }
        if (port < 1 || port > 65535) {
            return "SMTP 端口范围应为 1–65535";
        }
        if (!SECURITY_SSL_TLS.equals(smtpSecurity) && !SECURITY_STARTTLS.equals(smtpSecurity)) {
            return "必须选择 SSL/TLS 或 STARTTLS";
        }
        if (smtpUsername.trim().isEmpty()) {
            return "请填写 SMTP 用户名";
        }
        if (smtpPassword.isEmpty()) {
            return "请填写 SMTP 授权码或应用专用密码";
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(fromAddress.trim()).matches()) {
            return "发件邮箱格式不正确";
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(recipient.trim()).matches()) {
            return "收件邮箱格式不正确";
        }
        return null;
    }

    static void setStatus(Context context, String status) {
        prefs(context).edit()
                .putString(KEY_LAST_STATUS, status)
                .putLong(KEY_LAST_STATUS_AT, System.currentTimeMillis())
                .apply();
    }

    static void setSuccess(Context context, String status) {
        prefs(context).edit()
                .putString(KEY_LAST_STATUS, status)
                .putLong(KEY_LAST_STATUS_AT, System.currentTimeMillis())
                .putLong(KEY_LAST_SUCCESS_AT, System.currentTimeMillis())
                .apply();
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static String getLastStatus(Context context) {
        SharedPreferences prefs = prefs(context);
        String status = prefs.getString(KEY_LAST_STATUS, "尚无转发记录");
        long at = prefs.getLong(KEY_LAST_STATUS_AT, 0L);
        if (at == 0L) {
            return status;
        }
        return status + "\n" + new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date(at));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
