package com.server.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
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
    static final String CHANNEL_STABLE = "stable";
    static final String CHANNEL_BETA = "beta";
    private static final String RELEASE_API =
            "https://api.github.com/repos/mikutea/sms-to-email-forwarder/releases?per_page=20";
    private static final String PREFS = "yanjian_updates";
    private static final String KEY_AUTOMATIC = "automatic";
    private static final String KEY_CHANNEL = "channel";
    private static final String KEY_LAST_AUTOMATIC_ATTEMPT = "last_automatic_attempt";
    private static final long AUTOMATIC_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final long MAX_APK_BYTES = 100L * 1024L * 1024L;

    private UpdateChecker() {
    }

    static boolean isAutomaticEnabled(Context context) {
        return preferences(context).getBoolean(KEY_AUTOMATIC, true);
    }

    static void setAutomaticEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_AUTOMATIC, enabled).apply();
    }

    static String channel(Context context) {
        String value = preferences(context).getString(KEY_CHANNEL, CHANNEL_STABLE);
        return CHANNEL_BETA.equals(value) ? CHANNEL_BETA : CHANNEL_STABLE;
    }

    static void setChannel(Context context, String channel) {
        preferences(context).edit()
                .putString(KEY_CHANNEL, CHANNEL_BETA.equals(channel) ? CHANNEL_BETA : CHANNEL_STABLE)
                .apply();
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

    static ReleaseInfo fetchLatest(Context context) throws IOException, JSONException {
        return fetchLatest(CHANNEL_BETA.equals(channel(context)));
    }

    static ReleaseInfo fetchLatest(boolean includeBeta) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
        connection.setConnectTimeout(6_000);
        connection.setReadTimeout(8_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "Yanjian-Android/" + BuildConfig.VERSION_NAME);
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null;
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub Releases 返回 HTTP " + status);
            }
            int declaredLength = connection.getContentLength();
            if (declaredLength > MAX_RESPONSE_BYTES) {
                throw new IOException("更新响应超过安全上限");
            }
            try (InputStream input = connection.getInputStream()) {
                return parseReleases(readLimited(input), includeBeta);
            }
        } finally {
            connection.disconnect();
        }
    }

    static ReleaseInfo parseReleases(String json, boolean includeBeta)
            throws JSONException, IOException {
        JSONArray releases = new JSONArray(json);
        ReleaseInfo latest = null;
        for (int index = 0; index < releases.length(); index++) {
            JSONObject root = releases.optJSONObject(index);
            if (root == null || root.optBoolean("draft", true)) continue;
            boolean prerelease = root.optBoolean("prerelease", false);
            if (prerelease && !includeBeta) continue;
            ReleaseInfo candidate;
            try {
                candidate = parseReleaseObject(root, prerelease);
            } catch (IOException ignored) {
                continue;
            }
            if (latest == null || isNewer(candidate.version, latest.version)) {
                latest = candidate;
            }
        }
        return latest;
    }

    static ReleaseInfo parseRelease(String json) throws JSONException, IOException {
        JSONObject root = new JSONObject(json);
        if (root.optBoolean("draft", true) || root.optBoolean("prerelease", true)) {
            throw new IOException("最新发布不是稳定版本");
        }
        return parseReleaseObject(root, false);
    }

    private static ReleaseInfo parseReleaseObject(JSONObject root, boolean prerelease)
            throws IOException {
        String tag = root.optString("tag_name", "").trim();
        String releaseUrl = root.optString("html_url", "").trim();
        if (!isOfficialReleaseUrl(releaseUrl)) {
            throw new IOException("发布地址不是项目官方 GitHub Release");
        }
        String version = normalizeVersion(tag);
        if (!version.matches("[0-9]+(?:\\.[0-9]+){1,3}(?:-[0-9a-z.-]+)?")) {
            throw new IOException("发布版本号格式无效");
        }
        if (prerelease && !version.contains("-")) {
            throw new IOException("测试版标签缺少预发布标识");
        }
        String title = singleLine(root.optString("name", "雁笺 " + tag), 100);
        String notes = root.optString("body", "").trim();
        if (notes.length() > 4_000) notes = notes.substring(0, 4_000) + "\n…";

        String apkName = "yanjian-v" + version + ".apk";
        String apkUrl = "";
        String apkSha256 = "";
        long apkSize = 0L;
        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.optJSONObject(index);
                if (asset == null || !apkName.equals(asset.optString("name", ""))) continue;
                String candidateUrl = asset.optString("browser_download_url", "").trim();
                String digest = asset.optString("digest", "").trim().toLowerCase(Locale.ROOT);
                long size = asset.optLong("size", 0L);
                if (isOfficialAssetUrl(candidateUrl, tag, apkName)
                        && digest.matches("sha256:[0-9a-f]{64}")
                        && size > 0L && size <= MAX_APK_BYTES) {
                    apkUrl = candidateUrl;
                    apkSha256 = digest.substring("sha256:".length());
                    apkSize = size;
                }
                break;
            }
        }
        return new ReleaseInfo(version, title, notes, releaseUrl, prerelease,
                apkName, apkUrl, apkSha256, apkSize);
    }

    static boolean isNewer(String remote, String current) {
        String left = normalizeVersion(remote);
        String right = normalizeVersion(current);
        int[] leftCore = numericParts(left);
        int[] rightCore = numericParts(right);
        int count = Math.max(leftCore.length, rightCore.length);
        for (int index = 0; index < count; index++) {
            int l = index < leftCore.length ? leftCore[index] : 0;
            int r = index < rightCore.length ? rightCore[index] : 0;
            if (l != r) return l > r;
        }
        String leftPre = prereleasePart(left);
        String rightPre = prereleasePart(right);
        if (leftPre.isEmpty() || rightPre.isEmpty()) {
            return leftPre.isEmpty() && !rightPre.isEmpty();
        }
        return comparePrerelease(leftPre, rightPre) > 0;
    }

    private static int comparePrerelease(String left, String right) {
        String[] l = left.split("\\.");
        String[] r = right.split("\\.");
        int count = Math.max(l.length, r.length);
        for (int index = 0; index < count; index++) {
            if (index >= l.length) return -1;
            if (index >= r.length) return 1;
            boolean ln = l[index].matches("[0-9]+");
            boolean rn = r[index].matches("[0-9]+");
            int comparison;
            if (ln && rn) comparison = Integer.compare(parsePart(l[index]), parsePart(r[index]));
            else if (ln != rn) comparison = ln ? -1 : 1;
            else comparison = l[index].compareTo(r[index]);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static String prereleasePart(String version) {
        int separator = version.indexOf('-');
        if (separator < 0) return "";
        int metadata = version.indexOf('+', separator);
        return version.substring(separator + 1, metadata < 0 ? version.length() : metadata);
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

    static boolean isOfficialAssetUrl(String value, String tag, String name) {
        try {
            URI uri = URI.create(value);
            String expectedPath = "/mikutea/sms-to-email-forwarder/releases/download/"
                    + tag + "/" + name;
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == -1
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && expectedPath.equals(uri.getRawPath());
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static int[] numericParts(String version) {
        String stable = version.split("[-+]", 2)[0];
        String[] tokens = stable.split("\\.");
        int[] result = new int[tokens.length];
        for (int index = 0; index < tokens.length; index++) {
            result[index] = parsePart(tokens[index]);
        }
        return result;
    }

    private static int parsePart(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    static String normalizeVersion(String value) {
        if (value == null) return "";
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
        final boolean prerelease;
        final String apkName;
        final String apkUrl;
        final String apkSha256;
        final long apkSize;

        ReleaseInfo(String version, String title, String notes, String releaseUrl,
                    boolean prerelease, String apkName, String apkUrl,
                    String apkSha256, long apkSize) {
            this.version = version;
            this.title = title;
            this.notes = notes;
            this.releaseUrl = releaseUrl;
            this.prerelease = prerelease;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.apkSha256 = apkSha256;
            this.apkSize = apkSize;
        }

        boolean hasDownload() {
            return !apkName.isEmpty() && !apkUrl.isEmpty()
                    && apkSha256.matches("[0-9a-f]{64}") && apkSize > 0L;
        }
    }
}
