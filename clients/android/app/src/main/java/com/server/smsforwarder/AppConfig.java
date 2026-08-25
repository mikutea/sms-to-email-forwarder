package com.server.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

final class AppConfig {
    static final String SECURITY_SSL_TLS = "SSL_TLS";
    static final String SECURITY_STARTTLS = "STARTTLS";
    static final String STRATEGY_PRIMARY_ONLY = "PRIMARY_ONLY";
    static final String STRATEGY_FAILOVER = "FAILOVER";
    static final String STRATEGY_ALL = "ALL";

    private static final String PREFS = "sms_forwarder_config";
    private static final String KEY_SMTP_HOST = "smtp_host";
    private static final String KEY_SMTP_PORT = "smtp_port";
    private static final String KEY_SMTP_SECURITY = "smtp_security";
    private static final String KEY_SMTP_USERNAME = "smtp_username";
    private static final String KEY_SMTP_PASSWORD = "smtp_password_encrypted";
    private static final String KEY_FROM_ADDRESS = "from_address";
    private static final String KEY_RECIPIENT = "recipient";
    private static final String KEY_BACKUP_ENABLED = "backup_enabled";
    private static final String KEY_BACKUP_SMTP_HOST = "backup_smtp_host";
    private static final String KEY_BACKUP_SMTP_PORT = "backup_smtp_port";
    private static final String KEY_BACKUP_SMTP_SECURITY = "backup_smtp_security";
    private static final String KEY_BACKUP_SMTP_USERNAME = "backup_smtp_username";
    private static final String KEY_BACKUP_SMTP_PASSWORD = "backup_smtp_password_encrypted";
    private static final String KEY_BACKUP_FROM_ADDRESS = "backup_from_address";
    private static final String KEY_BACKUP_RECIPIENT = "backup_recipient";
    private static final String KEY_DISPATCH_STRATEGY = "dispatch_strategy";
    private static final String KEY_ALLOWLIST = "sender_allowlist";
    private static final String KEY_SKIP_OTP = "skip_otp";
    private static final String KEY_CONSENT = "privacy_consent";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_STATUS = "last_status";
    private static final String KEY_LAST_STATUS_AT = "last_status_at";
    private static final String KEY_LAST_SUCCESS_AT = "last_success_at";
    private static final String KEY_SMTP_VERIFICATION_STATE = "smtp_verification_state";
    private static final String KEY_LAST_SMS_RECEIVED_AT = "last_sms_received_at";
    private static final String KEY_LAST_SMS_FORWARDED_AT = "last_sms_forwarded_at";

    final String smtpHost;
    final int smtpPort;
    final String smtpSecurity;
    final String smtpUsername;
    final String smtpPassword;
    final String fromAddress;
    final String recipient;
    final boolean backupEnabled;
    final String backupSmtpHost;
    final int backupSmtpPort;
    final String backupSmtpSecurity;
    final String backupSmtpUsername;
    final String backupSmtpPassword;
    final String backupFromAddress;
    final String backupRecipient;
    final String dispatchStrategy;
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
            boolean backupEnabled,
            String backupSmtpHost,
            int backupSmtpPort,
            String backupSmtpSecurity,
            String backupSmtpUsername,
            String backupSmtpPassword,
            String backupFromAddress,
            String backupRecipient,
            String dispatchStrategy,
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
        this.backupEnabled = backupEnabled;
        this.backupSmtpHost = backupSmtpHost;
        this.backupSmtpPort = backupSmtpPort;
        this.backupSmtpSecurity = backupSmtpSecurity;
        this.backupSmtpUsername = backupSmtpUsername;
        this.backupSmtpPassword = backupSmtpPassword;
        this.backupFromAddress = backupFromAddress;
        this.backupRecipient = backupRecipient;
        this.dispatchStrategy = dispatchStrategy;
        this.senderAllowlist = senderAllowlist;
        this.skipOtp = skipOtp;
        this.privacyConsent = privacyConsent;
        this.enabled = enabled;
    }

    static AppConfig load(Context context) {
        SharedPreferences prefs = prefs(context);
        String password = "";
        String backupPassword = "";
        try {
            password = CryptoStore.decrypt(prefs.getString(KEY_SMTP_PASSWORD, ""));
            backupPassword = CryptoStore.decrypt(prefs.getString(KEY_BACKUP_SMTP_PASSWORD, ""));
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
                prefs.getBoolean(KEY_BACKUP_ENABLED, false),
                prefs.getString(KEY_BACKUP_SMTP_HOST, ""),
                prefs.getInt(KEY_BACKUP_SMTP_PORT, 465),
                prefs.getString(KEY_BACKUP_SMTP_SECURITY, SECURITY_SSL_TLS),
                prefs.getString(KEY_BACKUP_SMTP_USERNAME, ""),
                backupPassword,
                prefs.getString(KEY_BACKUP_FROM_ADDRESS, ""),
                prefs.getString(KEY_BACKUP_RECIPIENT, ""),
                normalizeStrategy(prefs.getString(KEY_DISPATCH_STRATEGY, STRATEGY_FAILOVER)),
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
        save(
                context,
                smtpHost,
                smtpPort,
                smtpSecurity,
                smtpUsername,
                smtpPassword,
                fromAddress,
                recipient,
                false,
                "",
                465,
                SECURITY_SSL_TLS,
                "",
                "",
                "",
                "",
                STRATEGY_FAILOVER,
                senderAllowlist,
                skipOtp,
                privacyConsent,
                enabled);
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
            boolean backupEnabled,
            String backupSmtpHost,
            int backupSmtpPort,
            String backupSmtpSecurity,
            String backupSmtpUsername,
            String backupSmtpPassword,
            String backupFromAddress,
            String backupRecipient,
            String dispatchStrategy,
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
                .putBoolean(KEY_BACKUP_ENABLED, backupEnabled)
                .putString(KEY_BACKUP_SMTP_HOST, backupSmtpHost.trim())
                .putInt(KEY_BACKUP_SMTP_PORT, backupSmtpPort)
                .putString(KEY_BACKUP_SMTP_SECURITY, backupSmtpSecurity)
                .putString(KEY_BACKUP_SMTP_USERNAME, backupSmtpUsername.trim())
                .putString(KEY_BACKUP_SMTP_PASSWORD, CryptoStore.encrypt(backupSmtpPassword))
                .putString(KEY_BACKUP_FROM_ADDRESS, backupFromAddress.trim())
                .putString(KEY_BACKUP_RECIPIENT, backupRecipient.trim())
                .putString(KEY_DISPATCH_STRATEGY, normalizeStrategy(dispatchStrategy))
                .putString(KEY_ALLOWLIST, senderAllowlist.trim())
                .putBoolean(KEY_SKIP_OTP, skipOtp)
                .putBoolean(KEY_CONSENT, privacyConsent)
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }

    SmtpProfile primaryProfile() {
        return new SmtpProfile(
                "主通道",
                smtpHost,
                smtpPort,
                smtpSecurity,
                smtpUsername,
                smtpPassword,
                fromAddress,
                recipient);
    }

    SmtpProfile backupProfile() {
        return new SmtpProfile(
                "备用通道",
                backupSmtpHost,
                backupSmtpPort,
                backupSmtpSecurity,
                backupSmtpUsername,
                backupSmtpPassword,
                backupFromAddress,
                backupRecipient);
    }

    String validateForForwarding() {
        if (!privacyConsent) {
            return "请先确认短信隐私转发风险";
        }
        String primaryError = primaryProfile().validate();
        if (primaryError != null) {
            return primaryError;
        }
        if (backupEnabled) {
            return backupProfile().validate();
        }
        return null;
    }

    private static String normalizeStrategy(String strategy) {
        if (STRATEGY_PRIMARY_ONLY.equals(strategy)
                || STRATEGY_FAILOVER.equals(strategy)
                || STRATEGY_ALL.equals(strategy)) {
            return strategy;
        }
        return STRATEGY_FAILOVER;
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
                .putString(KEY_SMTP_VERIFICATION_STATE, SmtpHealthState.VERIFIED)
                .apply();
    }

    static void setSmtpFailure(Context context, String status) {
        prefs(context).edit()
                .putString(KEY_LAST_STATUS, status)
                .putLong(KEY_LAST_STATUS_AT, System.currentTimeMillis())
                .putString(KEY_SMTP_VERIFICATION_STATE, SmtpHealthState.FAILED)
                .apply();
    }

    static void resetSmtpVerification(Context context) {
        prefs(context).edit()
                .putString(KEY_SMTP_VERIFICATION_STATE, SmtpHealthState.UNKNOWN)
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

    static long getLastSuccessAt(Context context) {
        return prefs(context).getLong(KEY_LAST_SUCCESS_AT, 0L);
    }

    static String getSmtpVerificationState(Context context) {
        SharedPreferences prefs = prefs(context);
        String stored = prefs.getString(KEY_SMTP_VERIFICATION_STATE, null);
        if (SmtpHealthState.UNKNOWN.equals(stored)
                || SmtpHealthState.VERIFIED.equals(stored)
                || SmtpHealthState.FAILED.equals(stored)) {
            return stored;
        }
        return SmtpHealthState.inferLegacy(
                prefs.getLong(KEY_LAST_SUCCESS_AT, 0L),
                prefs.getLong(KEY_LAST_STATUS_AT, 0L),
                prefs.getString(KEY_LAST_STATUS, ""));
    }

    static void setSmsReceived(Context context, long receivedAt) {
        prefs(context).edit().putLong(KEY_LAST_SMS_RECEIVED_AT, receivedAt).apply();
    }

    static void setSmsForwarded(Context context) {
        prefs(context).edit().putLong(KEY_LAST_SMS_FORWARDED_AT, System.currentTimeMillis()).apply();
    }

    static long getLastSmsReceivedAt(Context context) {
        return prefs(context).getLong(KEY_LAST_SMS_RECEIVED_AT, 0L);
    }

    static long getLastSmsForwardedAt(Context context) {
        return prefs(context).getLong(KEY_LAST_SMS_FORWARDED_AT, 0L);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
