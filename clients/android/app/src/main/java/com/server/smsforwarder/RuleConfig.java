package com.server.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

final class RuleConfig {
    static final String MODE_ALL = "ALL";
    static final String MODE_OTP_ONLY = "OTP_ONLY";
    static final String MODE_NON_OTP = "NON_OTP";
    static final String MODE_MATCH = "MATCH";
    static final String CONTENT_FULL = "FULL";
    static final String CONTENT_CODE_ONLY = "CODE_ONLY";
    static final String CONTENT_MASKED = "MASKED";
    static final String CONTENT_METADATA = "METADATA";

    private static final String PREFS = "sms_forwarder_rules";
    private static final String KEY_MODE = "mode";
    private static final String KEY_SENDER_ALLOW = "sender_allow";
    private static final String KEY_SENDER_BLOCK = "sender_block";
    private static final String KEY_BODY_INCLUDE = "body_include";
    private static final String KEY_BODY_EXCLUDE = "body_exclude";
    private static final String KEY_BODY_REGEX = "body_regex";
    private static final String KEY_INCLUDE_ALL = "include_all";
    private static final String KEY_SIM_SLOT = "sim_slot";
    private static final String KEY_SCHEDULE_ENABLED = "schedule_enabled";
    private static final String KEY_START_MINUTE = "start_minute";
    private static final String KEY_END_MINUTE = "end_minute";
    private static final String KEY_WEEKDAY_MASK = "weekday_mask";
    private static final String KEY_CONTENT_MODE = "content_mode";

    final String mode;
    final String senderAllow;
    final String senderBlock;
    final String bodyInclude;
    final String bodyExclude;
    final String bodyRegex;
    final boolean includeAll;
    final int simSlot;
    final boolean scheduleEnabled;
    final int startMinute;
    final int endMinute;
    final int weekdayMask;
    final String contentMode;

    RuleConfig(
            String mode,
            String senderAllow,
            String senderBlock,
            String bodyInclude,
            String bodyExclude,
            String bodyRegex,
            boolean includeAll,
            int simSlot,
            boolean scheduleEnabled,
            int startMinute,
            int endMinute,
            int weekdayMask,
            String contentMode) {
        this.mode = normalizeMode(mode);
        this.senderAllow = nullToEmpty(senderAllow);
        this.senderBlock = nullToEmpty(senderBlock);
        this.bodyInclude = nullToEmpty(bodyInclude);
        this.bodyExclude = nullToEmpty(bodyExclude);
        this.bodyRegex = nullToEmpty(bodyRegex);
        this.includeAll = includeAll;
        this.simSlot = simSlot;
        this.scheduleEnabled = scheduleEnabled;
        this.startMinute = clampMinute(startMinute);
        this.endMinute = clampMinute(endMinute);
        this.weekdayMask = weekdayMask & 0x7f;
        this.contentMode = normalizeContentMode(contentMode);
    }

    static RuleConfig load(Context context) {
        SharedPreferences preferences = prefs(context);
        AppConfig legacy = AppConfig.load(context);
        return new RuleConfig(
                preferences.getString(KEY_MODE, legacy.skipOtp ? MODE_NON_OTP : MODE_ALL),
                preferences.getString(KEY_SENDER_ALLOW, legacy.senderAllowlist),
                preferences.getString(KEY_SENDER_BLOCK, ""),
                preferences.getString(KEY_BODY_INCLUDE, ""),
                preferences.getString(KEY_BODY_EXCLUDE, ""),
                preferences.getString(KEY_BODY_REGEX, ""),
                preferences.getBoolean(KEY_INCLUDE_ALL, false),
                preferences.getInt(KEY_SIM_SLOT, -1),
                preferences.getBoolean(KEY_SCHEDULE_ENABLED, false),
                preferences.getInt(KEY_START_MINUTE, 0),
                preferences.getInt(KEY_END_MINUTE, 0),
                preferences.getInt(KEY_WEEKDAY_MASK, 0x7f),
                preferences.getString(KEY_CONTENT_MODE, CONTENT_FULL));
    }

    static void save(Context context, RuleConfig config) {
        prefs(context).edit()
                .putString(KEY_MODE, config.mode)
                .putString(KEY_SENDER_ALLOW, config.senderAllow.trim())
                .putString(KEY_SENDER_BLOCK, config.senderBlock.trim())
                .putString(KEY_BODY_INCLUDE, config.bodyInclude.trim())
                .putString(KEY_BODY_EXCLUDE, config.bodyExclude.trim())
                .putString(KEY_BODY_REGEX, config.bodyRegex.trim())
                .putBoolean(KEY_INCLUDE_ALL, config.includeAll)
                .putInt(KEY_SIM_SLOT, config.simSlot)
                .putBoolean(KEY_SCHEDULE_ENABLED, config.scheduleEnabled)
                .putInt(KEY_START_MINUTE, config.startMinute)
                .putInt(KEY_END_MINUTE, config.endMinute)
                .putInt(KEY_WEEKDAY_MASK, config.weekdayMask)
                .putString(KEY_CONTENT_MODE, config.contentMode)
                .apply();
    }

    String validate() {
        if (bodyRegex.length() > 512 || senderAllow.length() > 4096 || senderBlock.length() > 4096
                || bodyInclude.length() > 4096 || bodyExclude.length() > 4096) {
            return "规则内容过长";
        }
        if (!bodyRegex.trim().isEmpty()) {
            try {
                Pattern.compile(bodyRegex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            } catch (PatternSyntaxException error) {
                return "正文正则表达式无效：" + error.getDescription();
            }
        }
        String senderRegexError = validateSenderRegex(senderAllow + "\n" + senderBlock);
        if (senderRegexError != null) {
            return senderRegexError;
        }
        if (scheduleEnabled && weekdayMask == 0) {
            return "生效时段至少选择一天";
        }
        return null;
    }

    private static String validateSenderRegex(String rules) {
        for (String token : rules.split("[\\r\\n,;，；]+")) {
            String trimmed = token.trim();
            if (trimmed.startsWith("re:")) {
                if (trimmed.length() > 515) {
                    return "发送方正则表达式过长";
                }
                try {
                    Pattern.compile(trimmed.substring(3), Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException error) {
                    return "发送方正则表达式无效：" + error.getDescription();
                }
            }
        }
        return null;
    }

    boolean isActiveAt(long timestamp) {
        if (!scheduleEnabled) {
            return true;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        int dayBit = 1 << ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7);
        int minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        if (startMinute == endMinute) {
            return (weekdayMask & dayBit) != 0;
        }
        if (startMinute < endMinute) {
            return (weekdayMask & dayBit) != 0 && minute >= startMinute && minute < endMinute;
        }
        if (minute >= startMinute) {
            return (weekdayMask & dayBit) != 0;
        }
        int previousBit = dayBit == 1 ? (1 << 6) : (dayBit >> 1);
        return minute < endMinute && (weekdayMask & previousBit) != 0;
    }

    private static int clampMinute(int value) {
        return Math.max(0, Math.min(1439, value));
    }

    private static String normalizeMode(String value) {
        if (MODE_OTP_ONLY.equals(value) || MODE_NON_OTP.equals(value) || MODE_MATCH.equals(value)) {
            return value;
        }
        return MODE_ALL;
    }

    private static String normalizeContentMode(String value) {
        if (CONTENT_CODE_ONLY.equals(value)
                || CONTENT_MASKED.equals(value)
                || CONTENT_METADATA.equals(value)) {
            return value;
        }
        return CONTENT_FULL;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
