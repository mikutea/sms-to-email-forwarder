package com.server.smsforwarder;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.PowerManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

final class DeviceHealth {
    final boolean smsPermission;
    final boolean forwardingEnabled;
    final boolean smtpValid;
    final boolean smtpVerified;
    final boolean smtpFailed;
    final String smtpLabel;
    final boolean connected;
    final boolean batteryExempt;
    final boolean backgroundConfirmed;
    final int batteryPercent;
    final boolean charging;
    final int pendingCount;
    final PendingStats pendingStats;
    final long lastSuccessAt;
    final long lastSmsReceivedAt;
    final long lastSmsForwardedAt;
    final String networkLabel;
    final boolean backgroundDataRestricted;

    private DeviceHealth(
            boolean smsPermission,
            boolean forwardingEnabled,
            boolean smtpValid,
            boolean smtpVerified,
            boolean smtpFailed,
            String smtpLabel,
            boolean connected,
            boolean batteryExempt,
            boolean backgroundConfirmed,
            int batteryPercent,
            boolean charging,
            PendingStats pendingStats,
            long lastSuccessAt,
            long lastSmsReceivedAt,
            long lastSmsForwardedAt,
            String networkLabel,
            boolean backgroundDataRestricted) {
        this.smsPermission = smsPermission;
        this.forwardingEnabled = forwardingEnabled;
        this.smtpValid = smtpValid;
        this.smtpVerified = smtpVerified;
        this.smtpFailed = smtpFailed;
        this.smtpLabel = smtpLabel;
        this.connected = connected;
        this.batteryExempt = batteryExempt;
        this.backgroundConfirmed = backgroundConfirmed;
        this.batteryPercent = batteryPercent;
        this.charging = charging;
        this.pendingStats = pendingStats;
        this.pendingCount = pendingStats.total;
        this.lastSuccessAt = lastSuccessAt;
        this.lastSmsReceivedAt = lastSmsReceivedAt;
        this.lastSmsForwardedAt = lastSmsForwardedAt;
        this.networkLabel = networkLabel;
        this.backgroundDataRestricted = backgroundDataRestricted;
    }

    static DeviceHealth inspect(Context context) {
        AppConfig config = AppConfig.load(context);
        BatterySnapshot battery = readBattery(context);
        NetworkState.Snapshot network = NetworkState.inspect(context);
        boolean smtpConfigured = config.validateForForwarding() == null;
        SmtpHealthState smtpState = SmtpHealthState.from(
                smtpConfigured,
                AppConfig.getSmtpVerificationState(context));
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean exempt = powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        PendingStats pendingStats = QueueDatabase.get(context).pendingStats();
        return new DeviceHealth(
                context.checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                        == PackageManager.PERMISSION_GRANTED,
                config.enabled,
                smtpConfigured,
                smtpState.verified,
                smtpState.failed,
                smtpState.label,
                network.usableForBackground,
                exempt,
                TravelGuard.isBackgroundConfirmed(context),
                battery.percent,
                battery.charging,
                pendingStats,
                AppConfig.getLastSuccessAt(context),
                AppConfig.getLastSmsReceivedAt(context),
                AppConfig.getLastSmsForwardedAt(context),
                network.label,
                network.backgroundRestricted);
    }

    boolean readyForTravel() {
        return travelBlockers().isEmpty();
    }

    List<String> travelBlockers() {
        List<String> blockers = new ArrayList<>();
        if (!smsPermission) blockers.add("允许接收短信");
        if (!forwardingEnabled) blockers.add("启用自动转发");
        if (!smtpValid) {
            blockers.add("完成 SMTP 配置");
        } else if (!smtpVerified) {
            blockers.add(smtpFailed ? "修复并重新测试 SMTP" : "测试并验证 SMTP");
        }
        if (!batteryExempt) blockers.add("允许忽略电池优化");
        if (!backgroundConfirmed) blockers.add("确认厂商后台启动设置");
        if (lastSmsForwardedAt <= 0L) blockers.add("完成一次真实短信闭环测试");
        return blockers;
    }

    String summary() {
        String lastSuccess = lastSuccessAt == 0L
                ? "尚未成功"
                : new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(new Date(lastSuccessAt));
        String realSms = lastSmsForwardedAt == 0L
                ? "尚未通过"
                : new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(new Date(lastSmsForwardedAt));
        return "短信权限：" + yesNo(smsPermission)
                + "\n自动转发：" + yesNo(forwardingEnabled)
                + "\nSMTP 状态：" + smtpLabel
                + "\n网络：" + networkLabel
                + "\n电量：" + (batteryPercent < 0 ? "未知" : batteryPercent + "%")
                + (charging ? "（充电中）" : "（未充电）")
                + "\n电池优化豁免：" + yesNo(batteryExempt)
                + "\n华为后台设置：" + (backgroundConfirmed ? "用户已确认" : "尚未确认")
                + "\n待发送：" + pendingCount + " 条（" + pendingStats.compactLabel() + "）"
                + "\n最近 SMTP 成功：" + lastSuccess
                + "\n真实短信闭环：" + realSms;
    }

    private static BatterySnapshot readBattery(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null) {
            return new BatterySnapshot(-1, false);
        }
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int percent = level < 0 || scale <= 0 ? -1 : Math.round(level * 100f / scale);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        return new BatterySnapshot(percent, charging);
    }

    private static String yesNo(boolean value) {
        return value ? "正常" : "未完成";
    }

    private static final class BatterySnapshot {
        final int percent;
        final boolean charging;

        BatterySnapshot(int percent, boolean charging) {
            this.percent = percent;
            this.charging = charging;
        }
    }
}
