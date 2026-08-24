package com.server.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class DeviceStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TravelGuard.isEnabled(context) || intent.getAction() == null) {
            return;
        }
        if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())) {
            TravelGuard.enqueueHeartbeatNow(context, "手机电量偏低", true);
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
            TravelGuard.enqueueHeartbeatNow(context, "手机已停止充电", true);
        }
    }
}
