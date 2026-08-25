package com.server.smsforwarder;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class AppUpdater {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_APK_BYTES = 100L * 1024L * 1024L;
    private static final String UPDATE_DIRECTORY = "updates";
    private static final String UPDATE_FILE = "yanjian-update.apk";

    private AppUpdater() {
    }

    interface ProgressListener {
        void onProgress(long downloaded, long total);

        boolean isCancelled();
    }

    static File download(Context context, UpdateChecker.ReleaseInfo release,
                         ProgressListener listener) throws IOException {
        if (!release.hasDownload()) {
            throw new IOException("发布版本没有可验证的 APK 资产");
        }
        if (release.apkSize <= 0L || release.apkSize > MAX_APK_BYTES) {
            throw new IOException("APK 大小超出安全范围");
        }

        File directory = new File(context.getCacheDir(), UPDATE_DIRECTORY);
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory()) {
            throw new IOException("无法创建更新缓存目录");
        }
        File partial = new File(directory, UPDATE_FILE + ".part");
        File completed = new File(directory, UPDATE_FILE);
        deleteIfPresent(partial);
        deleteIfPresent(completed);

        HttpURLConnection connection = null;
        boolean success = false;
        try {
            connection = openDownload(release.apkUrl);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("更新下载返回 HTTP " + status);
            }
            int declaredLength = connection.getContentLength();
            if (declaredLength > 0 && declaredLength != release.apkSize) {
                throw new IOException("APK 下载大小与发布记录不一致");
            }

            MessageDigest digest = sha256Digest();
            long downloaded = 0L;
            byte[] buffer = new byte[32 * 1024];
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream output = new BufferedOutputStream(
                         new FileOutputStream(partial))) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || listener.isCancelled()) {
                        throw new InterruptedIOException("下载已取消");
                    }
                    downloaded += read;
                    if (downloaded > release.apkSize || downloaded > MAX_APK_BYTES) {
                        throw new IOException("APK 下载内容超过发布记录");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                    listener.onProgress(downloaded, release.apkSize);
                }
                output.flush();
            }
            if (downloaded != release.apkSize) {
                throw new IOException("APK 下载不完整");
            }
            String actualDigest = hex(digest.digest());
            if (!actualDigest.equals(release.apkSha256)) {
                throw new IOException("APK SHA-256 校验失败");
            }
            if (!partial.renameTo(completed)) {
                throw new IOException("无法完成更新文件写入");
            }
            success = true;
            return completed;
        } finally {
            if (connection != null) connection.disconnect();
            if (!success) deleteIfPresent(partial);
        }
    }

    static void verifyPackage(Context context, File apk,
                              UpdateChecker.ReleaseInfo release) throws IOException {
        if (apk == null || !apk.isFile() || apk.length() != release.apkSize) {
            throw new IOException("更新 APK 不存在或大小不正确");
        }
        PackageManager manager = context.getPackageManager();
        PackageInfo archive = packageArchiveInfo(manager, apk);
        if (archive == null) throw new IOException("系统无法解析更新 APK");
        if (!context.getPackageName().equals(archive.packageName)) {
            throw new IOException("更新 APK 包名不匹配");
        }
        String archiveVersion = UpdateChecker.normalizeVersion(archive.versionName);
        if (!release.version.equals(archiveVersion)) {
            throw new IOException("更新 APK 版本与发布记录不匹配");
        }
        long archiveCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? archive.getLongVersionCode() : archive.versionCode;
        if (archiveCode <= BuildConfig.VERSION_CODE) {
            throw new IOException("更新 APK 的版本代码没有提升");
        }

        PackageInfo installed;
        try {
            installed = installedPackageInfo(manager, context.getPackageName());
        } catch (PackageManager.NameNotFoundException error) {
            throw new IOException("无法读取当前应用签名", error);
        }
        Set<String> installedSigners = signerDigests(installed);
        Set<String> archiveSigners = signerDigests(archive);
        if (installedSigners.isEmpty() || !installedSigners.equals(archiveSigners)) {
            throw new IOException("更新 APK 正式签名不匹配");
        }
    }

    static File downloadedApk(Context context) {
        return new File(new File(context.getCacheDir(), UPDATE_DIRECTORY), UPDATE_FILE);
    }

    static void discardDownloadedApk(Context context) {
        deleteIfPresent(downloadedApk(context));
        deleteIfPresent(new File(new File(context.getCacheDir(), UPDATE_DIRECTORY),
                UPDATE_FILE + ".part"));
    }

    static boolean isAllowedNetworkUrl(URL url) {
        if (url == null || !"https".equalsIgnoreCase(url.getProtocol())) return false;
        if (url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443)) {
            return false;
        }
        String host = url.getHost().toLowerCase(Locale.ROOT);
        return "github.com".equals(host)
                || "release-assets.githubusercontent.com".equals(host)
                || "objects.githubusercontent.com".equals(host);
    }

    static String hex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) output.append(String.format(Locale.ROOT, "%02x", value));
        return output.toString();
    }

    private static HttpURLConnection openDownload(String source) throws IOException {
        URL current = new URL(source);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            if (!isAllowedNetworkUrl(current)) {
                throw new IOException("更新下载跳转到了不受信任的地址");
            }
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("User-Agent", "Yanjian-Android/"
                    + BuildConfig.VERSION_NAME);
            int status = connection.getResponseCode();
            if (!isRedirect(status)) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.isBlank()) {
                throw new IOException("更新下载跳转地址为空");
            }
            current = new URL(current, location);
        }
        throw new IOException("更新下载跳转次数过多");
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307 || status == 308;
    }

    private static PackageInfo packageArchiveInfo(PackageManager manager, File apk) {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        return manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
    }

    private static PackageInfo installedPackageInfo(PackageManager manager, String packageName)
            throws PackageManager.NameNotFoundException {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        return manager.getPackageInfo(packageName, flags);
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signerDigests(PackageInfo info) throws IOException {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        Set<String> digests = new HashSet<>();
        if (signatures == null) return digests;
        for (Signature signature : signatures) {
            digests.add(hex(sha256Digest().digest(signature.toByteArray())));
        }
        return digests;
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("系统不支持 SHA-256", error);
        }
    }

    private static void deleteIfPresent(File file) {
        if (file.exists() && !file.delete()) file.deleteOnExit();
    }
}
