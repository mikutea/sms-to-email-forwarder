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
}
