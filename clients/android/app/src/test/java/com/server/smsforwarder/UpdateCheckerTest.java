package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;

public final class UpdateCheckerTest {
    @Test
    public void comparesStableVersionsNumerically() {
        assertTrue(UpdateChecker.isNewer("v1.0.0", "0.2.0-beta.1"));
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"));
        assertTrue(UpdateChecker.isNewer("1.0.0", "1.0.0-rc.1"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("0.9.9", "1.0.0"));
    }

    @Test
    public void parsesOnlyOfficialStableRelease() throws Exception {
        String json = "{"
                + "\"tag_name\":\"v1.0.0\","
                + "\"name\":\"雁笺 1.0\","
                + "\"body\":\"正式版本\","
                + "\"draft\":false,\"prerelease\":false,"
                + "\"html_url\":\"https://github.com/mikutea/sms-to-email-forwarder/releases/tag/v1.0.0\"}";
        UpdateChecker.ReleaseInfo release = UpdateChecker.parseRelease(json);
        assertEquals("1.0.0", release.version);
        assertEquals("雁笺 1.0", release.title);
    }

    @Test
    public void rejectsRedirectOrLookalikeReleaseUrl() {
        String json = "{"
                + "\"tag_name\":\"v1.0.0\","
                + "\"draft\":false,\"prerelease\":false,"
                + "\"html_url\":\"https://github.example/mikutea/sms-to-email-forwarder/releases/tag/v1.0.0\"}";
        assertThrows(IOException.class, () -> UpdateChecker.parseRelease(json));
    }

    @Test
    public void rejectsDraftAndPrereleaseResponses() throws JSONException {
        String json = "{"
                + "\"tag_name\":\"v1.0.0\","
                + "\"draft\":false,\"prerelease\":true,"
                + "\"html_url\":\"https://github.com/mikutea/sms-to-email-forwarder/releases/tag/v1.0.0\"}";
        assertThrows(IOException.class, () -> UpdateChecker.parseRelease(json));
    }

    @Test
    public void betaChannelSelectsNewestPrerelease() throws Exception {
        String json = "[{"
                + "\"tag_name\":\"v1.1.0-beta.2\",\"draft\":false,\"prerelease\":true,"
                + "\"html_url\":\"https://github.com/mikutea/sms-to-email-forwarder/releases/tag/v1.1.0-beta.2\""
                + "},{\"tag_name\":\"v1.0.0\",\"draft\":false,\"prerelease\":false,"
                + "\"html_url\":\"https://github.com/mikutea/sms-to-email-forwarder/releases/tag/v1.0.0\"}]";
        UpdateChecker.ReleaseInfo release = UpdateChecker.parseReleases(json, true);
        assertEquals("1.1.0-beta.2", release.version);
        assertTrue(release.prerelease);
        assertEquals("1.0.0", UpdateChecker.parseReleases(json, false).version);
    }

    @Test
    public void comparesPrereleaseSequence() {
        assertTrue(UpdateChecker.isNewer("1.1.0-beta.10", "1.1.0-beta.2"));
        assertTrue(UpdateChecker.isNewer("1.1.0-rc.1", "1.1.0-beta.10"));
        assertFalse(UpdateChecker.isNewer("1.1.0-beta.1", "1.1.0"));
    }

    @Test
    public void parsesVerifiedOfficialApkAsset() throws Exception {
        String json = "{"
                + "\"tag_name\":\"v1.1.0-beta.1\","
                + "\"draft\":false,\"prerelease\":true,"
                + "\"html_url\":\"https://github.com/mikutea/sms-to-email-forwarder/releases/tag/v1.1.0-beta.1\","
                + "\"assets\":[{"
                + "\"name\":\"yanjian-v1.1.0-beta.1.apk\","
                + "\"size\":2825000,"
                + "\"digest\":\"sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\","
                + "\"browser_download_url\":\"https://github.com/mikutea/sms-to-email-forwarder/releases/download/v1.1.0-beta.1/yanjian-v1.1.0-beta.1.apk\"}]}";
        UpdateChecker.ReleaseInfo release = UpdateChecker.parseReleases("[" + json + "]", true);
        assertTrue(release.hasDownload());
        assertEquals(2825000L, release.apkSize);
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                release.apkSha256);
    }

    @Test
    public void rejectsLookalikeApkAsset() throws Exception {
        String json = "{"
                + "\"tag_name\":\"v1.1.0\",\"draft\":false,\"prerelease\":false,"
                + "\"html_url\":\"https://github.com/mikutea/sms-to-email-forwarder/releases/tag/v1.1.0\","
                + "\"assets\":[{"
                + "\"name\":\"yanjian-v1.1.0.apk\",\"size\":1234,"
                + "\"digest\":\"sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\","
                + "\"browser_download_url\":\"https://github.example/mikutea/sms-to-email-forwarder/releases/download/v1.1.0/yanjian-v1.1.0.apk\"}]}";
        assertFalse(UpdateChecker.parseRelease(json).hasDownload());
    }
}
