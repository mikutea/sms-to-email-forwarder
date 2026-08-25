package com.server.smsforwarder;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

final class ConfigBackup {
    private static final int FORMAT_VERSION = 1;

    private ConfigBackup() {
    }

    static String exportJson(Context context) throws JSONException {
        AppConfig app = AppConfig.load(context);
        RuleConfig rules = RuleConfig.load(context);
        JSONObject root = new JSONObject();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("exportedAt", System.currentTimeMillis());
        root.put("containsSecrets", false);
        root.put("primary", profileJson(app.primaryProfile()));
        root.put("backupEnabled", app.backupEnabled);
        root.put("backup", profileJson(app.backupProfile()));
        root.put("dispatchStrategy", app.dispatchStrategy);

        JSONObject ruleJson = new JSONObject();
        ruleJson.put("mode", rules.mode);
        ruleJson.put("senderAllow", rules.senderAllow);
        ruleJson.put("senderBlock", rules.senderBlock);
        ruleJson.put("bodyInclude", rules.bodyInclude);
        ruleJson.put("bodyExclude", rules.bodyExclude);
        ruleJson.put("bodyRegex", rules.bodyRegex);
        ruleJson.put("includeAll", rules.includeAll);
        ruleJson.put("simSlot", rules.simSlot);
        ruleJson.put("scheduleEnabled", rules.scheduleEnabled);
        ruleJson.put("startMinute", rules.startMinute);
        ruleJson.put("endMinute", rules.endMinute);
        ruleJson.put("weekdayMask", rules.weekdayMask);
        ruleJson.put("contentMode", rules.contentMode);
        root.put("rules", ruleJson);
        root.put("heartbeatHours", TravelGuard.heartbeatHours(context));
        return root.toString(2);
    }

    static void importJson(Context context, String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("formatVersion", -1) != FORMAT_VERSION) {
            throw new JSONException("不支持的备份格式版本");
        }
        JSONObject primary = root.getJSONObject("primary");
        JSONObject backup = root.optJSONObject("backup");
        if (backup == null) {
            backup = new JSONObject();
        }
        SmtpProfile importedPrimary = profileFromJson("主通道", primary);
        boolean backupEnabled = root.optBoolean("backupEnabled", false);
        SmtpProfile importedBackup = profileFromJson("备用通道", backup);
        String profileError = importedPrimary.validateForImport();
        if (profileError == null && backupEnabled) {
            profileError = importedBackup.validateForImport();
        }
        if (profileError != null) {
            throw new JSONException(profileError);
        }

        JSONObject rules = root.optJSONObject("rules");
        RuleConfig importedRules = null;
        if (rules != null) {
            importedRules = new RuleConfig(
                    rules.optString("mode", RuleConfig.MODE_ALL),
                    rules.optString("senderAllow"),
                    rules.optString("senderBlock"),
                    rules.optString("bodyInclude"),
                    rules.optString("bodyExclude"),
                    rules.optString("bodyRegex"),
                    rules.optBoolean("includeAll", false),
                    rules.optInt("simSlot", -1),
                    rules.optBoolean("scheduleEnabled", false),
                    rules.optInt("startMinute", 0),
                    rules.optInt("endMinute", 0),
                    rules.optInt("weekdayMask", 0x7f),
                    rules.optString("contentMode", RuleConfig.CONTENT_FULL));
            String error = importedRules.validate();
            if (error != null) {
                throw new JSONException(error);
            }
        }

        int heartbeatHours = root.optInt("heartbeatHours", 12);
        if (heartbeatHours != 6 && heartbeatHours != 12 && heartbeatHours != 24) {
            throw new JSONException("状态心跳间隔必须为 6、12 或 24 小时");
        }

        // All untrusted JSON is parsed and validated before the first preference is
        // changed. A malformed backup therefore cannot erase an existing password
        // or leave forwarding in a partially imported state.
        AppConfig old = AppConfig.load(context);
        AppConfig.save(
                context,
                importedPrimary.host, importedPrimary.port, importedPrimary.security,
                importedPrimary.username, "", importedPrimary.fromAddress,
                importedPrimary.recipientsText,
                backupEnabled,
                importedBackup.host, importedBackup.port, importedBackup.security,
                importedBackup.username, "", importedBackup.fromAddress,
                importedBackup.recipientsText,
                root.optString("dispatchStrategy", AppConfig.STRATEGY_FAILOVER),
                old.senderAllowlist, true, false, false);
        if (importedRules != null) {
            RuleConfig.save(context, importedRules);
        }
        TravelGuard.setHeartbeatHours(context, heartbeatHours);
        TravelGuard.setEnabled(context, false);
        TravelGuard.setBackgroundConfirmed(context, false);
        AppConfig.setStatus(context, "配置已导入；授权码、隐私确认和后台确认需要重新填写");
    }

    private static SmtpProfile profileFromJson(String name, JSONObject json) {
        return new SmtpProfile(
                name,
                json.optString("host"),
                json.optInt("port", 465),
                json.optString("security", AppConfig.SECURITY_SSL_TLS),
                json.optString("username"),
                "",
                json.optString("fromAddress"),
                json.optString("recipients"));
    }

    private static JSONObject profileJson(SmtpProfile profile) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("host", profile.host);
        json.put("port", profile.port);
        json.put("security", profile.security);
        json.put("username", profile.username);
        json.put("fromAddress", profile.fromAddress);
        json.put("recipients", profile.recipientsText);
        return json;
    }
}
