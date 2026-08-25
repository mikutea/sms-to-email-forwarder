package com.server.smsforwarder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URL;

public final class AppUpdaterTest {
    @Test
    public void allowsOnlyExpectedHttpsDownloadHosts() throws Exception {
        assertTrue(AppUpdater.isAllowedNetworkUrl(new URL(
                "https://github.com/mikutea/sms-to-email-forwarder/releases/download/v1/app.apk")));
        assertTrue(AppUpdater.isAllowedNetworkUrl(new URL(
                "https://release-assets.githubusercontent.com/github-production-release-asset/app.apk")));
        assertFalse(AppUpdater.isAllowedNetworkUrl(new URL(
                "http://release-assets.githubusercontent.com/app.apk")));
        assertFalse(AppUpdater.isAllowedNetworkUrl(new URL(
                "https://release-assets.githubusercontent.com.example/app.apk")));
        assertFalse(AppUpdater.isAllowedNetworkUrl(new URL(
                "https://user@release-assets.githubusercontent.com/app.apk")));
    }
}
