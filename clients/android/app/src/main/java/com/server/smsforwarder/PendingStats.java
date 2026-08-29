package com.server.smsforwarder;

final class PendingStats {
    final int sms;
    final int heartbeat;
    final int alert;
    final int other;
    final int total;

    PendingStats(int sms, int heartbeat, int alert, int other) {
        this.sms = Math.max(0, sms);
        this.heartbeat = Math.max(0, heartbeat);
        this.alert = Math.max(0, alert);
        this.other = Math.max(0, other);
        this.total = this.sms + this.heartbeat + this.alert + this.other;
    }

    String compactLabel() {
        String label = "短信 " + sms + " · 心跳 " + heartbeat + " · 提醒 " + alert;
        return other > 0 ? label + " · 其他 " + other : label;
    }
}
