package com.server.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class UpdateChecker {
    private static final String RELEASE_API =
            "https://api.github.com/repos/mikutea/sms-to-email-forwarder/releases/latest";
    private static final String PREFS = "yanjian_updates";
    private static final String KEY_AUTOMATIC = "automatic";
    private static final String KEY_LAST_AUTOMATIC_ATTEMPT = "last_automatic_attempt";
    private static final long AUTOMATIC_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private UpdateChecker() {
    }

    static boolean isAutomaticEnabled(Context context) {
        return preferences(context).getBoolean(KEY_AUTOMATIC, true);
    }

    static void setAutomaticEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_AUTOMATIC, enabled).apply();
    }

    static boolean beginAutomaticCheck(Context context, long now) {
        if (!isAutomaticEnabled(context) || !AppConfig.load(context).privacyConsent) {
            return false;
        }
        SharedPreferences preferences = preferences(context);
        long previous = preferences.getLong(KEY_LAST_AUTOMATIC_ATTEMPT, 0L);
        if (previous > 0L && now >= previous && now - previous < AUTOMATIC_INTERVAL_MS) {
            return false;
        }
        preferences.edit().putLong(KEY_LAST_AUTOMATIC_ATTEMPT, now).apply();
        return true;
    }

    static ReleaseInfo fetchLatest() throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
        connection.setConnectTimeout(6_000);
        connection.setReadTimeout(8_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "Yanjian-Android/" + BuildConfig.VERSION_NAME);
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub Releases 返回 HTTP " + status);
            }
            int declaredLength = connection.getContentLength();
            if (declaredLength > MAX_RESPONSE_BYTES) {
                throw new IOException("更新响应超过安全上限");
            }
            try (InputStream input = connection.getInputStream()) {
                return parseRelease(readLimited(input));
            }
        } finally {
            connection.disconnect();
        }
    }

    static ReleaseInfo parseRelease(String json) throws JSONException, IOException {
        JSONObject root = new JSONObject(json);
        if (root.optBoolean("draft", true) || root.optBoolean("prerelease", true)) {
            throw new IOException("最新发布不是稳定版本");
        }
        String tag = root.optString("tag_name", "").trim();
        String releaseUrl = root.optString("html_url", "").trim();
        if (!isOfficialReleaseUrl(releaseUrl)) {
            throw new IOException("发布地址不是项目官方 GitHub Release");
        }
        String version = normalizeVersion(tag);
        if (version.isEmpty() || !version.matches("[0-9]+(?:\\.[0-9]+){1,3}")) {
            throw new IOException("发布版本号格式无效");
        }
        String title = singleLine(root.optString("name", "雁笺 " + tag), 100);
        String notes = root.optString("body", "").trim();
        if (notes.length() > 4_000) {
            notes = notes.substring(0, 4_000) + "\n…";
        }
        return new ReleaseInfo(version, title, notes, releaseUrl);
    }

    static boolean isNewer(String remote, String current) {
        String normalizedRemote = normalizeVersion(remote);
        String normalizedCurrent = normalizeVersion(current);
        int[] remoteParts = numericParts(normalizedRemote);
        int[] currentParts = numericParts(normalizedCurrent);
        int count = Math.max(remoteParts.length, currentParts.length);
        for (int index = 0; index < count; index++) {
            int left = index < remoteParts.length ? remoteParts[index] : 0;
            int right = index < currentParts.length ? currentParts[index] : 0;
            if (left != right) {
                return left > right;
            }
        }
        return !normalizedRemote.contains("-") && normalizedCurrent.contains("-");
    }

    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_RESPONSE_BYTES) {
                throw new IOException("更新响应超过安全上限");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean isOfficialReleaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            String path = uri.getPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == -1
                    && uri.getUserInfo() == null
                    && path != null
                    && path.startsWith("/mikutea/sms-to-email-forwarder/releases/tag/");
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static int[] numericParts(String version) {
        String stable = version.split("[-+]", 2)[0];
        String[] tokens = stable.split("\\.");
        int[] result = new int[tokens.length];
        for (int index = 0; index < tokens.length; index++) {
            try {
                result[index] = Integer.parseInt(tokens[index]);
            } catch (NumberFormatException error) {
                result[index] = 0;
            }
        }
        return result;
    }

    private static String normalizeVersion(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("v") ? normalized.substring(1) : normalized;
    }

    private static String singleLine(String value, int limit) {
        String safe = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return safe.length() > limit ? safe.substring(0, limit) : safe;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static final class ReleaseInfo {
        final String version;
        final String title;
        final String notes;
        final String releaseUrl;

        ReleaseInfo(String version, String title, String notes, String releaseUrl) {
            this.version = version;
            this.title = title;
            this.notes = notes;
            this.releaseUrl = releaseUrl;
        }
    }
}
