package com.server.smsforwarder;

import android.content.Context;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DiagnosticReport {
    private DiagnosticReport() {
    }

    static String create(Context context) {
        DeviceHealth health = DeviceHealth.inspect(context);
        AppConfig app = AppConfig.load(context);
        RuleConfig rules = RuleConfig.load(context);
        return "雁笺脱敏诊断报告"
                + "\n生成时间：" + format(System.currentTimeMillis())
                + "\n应用版本：" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + "\n系统：Android API " + Build.VERSION.SDK_INT
                + "\n设备：" + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL)
                + "\n\n运行状态"
                + "\n" + health.summary()
                + "\n旅行守护：" + yesNo(TravelGuard.isEnabled(context))
                + "\n\n转发设置"
                + "\n备用通道：" + yesNo(app.backupEnabled)
                + "\n投递策略：" + app.dispatchStrategy
                + "\n规则模式：" + rules.mode
                + "\n正文模式：" + rules.contentMode
                + "\n时段规则：" + yesNo(rules.scheduleEnabled)
                + "\nSIM 限制：" + (rules.simSlot < 0 ? "不限" : "SIM " + (rules.simSlot + 1))
                + "\n\n隐私说明：本报告不包含 SMTP 主机、邮箱地址、授权码、短信发件人或短信正文。";
    }

    private static String format(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private static String yesNo(boolean value) {
        return value ? "已启用" : "未启用";
    }

    private static String safe(String value) {
        if (value == null) {
            return "未知";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
