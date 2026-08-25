package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class NetworkStateTest {
    @Test
    public void validatedCellularNetworkIsReportedAsUsable() {
        assertEquals("移动网络可用",
                NetworkState.describe(true, true, true, true, false));
    }

    @Test
    public void restrictedMeteredNetworkNamesTheBackgroundBlocker() {
        assertEquals("移动网络已连接 · 后台流量受限",
                NetworkState.describe(true, true, true, true, true));
    }

    @Test
    public void unvalidatedCellularNetworkDoesNotPretendInternetWorks() {
        assertEquals("移动网络已连接 · 互联网未验证",
                NetworkState.describe(true, false, true, true, false));
    }
}
