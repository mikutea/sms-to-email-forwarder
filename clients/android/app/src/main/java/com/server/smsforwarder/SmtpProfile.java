package com.server.smsforwarder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

final class SmtpProfile {
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$",
            Pattern.CASE_INSENSITIVE);
    final String name;
    final String host;
    final int port;
    final String security;
    final String username;
    final String password;
    final String fromAddress;
    final String recipientsText;

    SmtpProfile(
            String name,
            String host,
            int port,
            String security,
            String username,
            String password,
            String fromAddress,
            String recipientsText) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.security = security;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
        this.recipientsText = recipientsText;
    }

    List<String> recipients() {
        if (recipientsText == null || recipientsText.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String candidate : recipientsText.split("[\\r\\n,;，；]+")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    String validate() {
        String structuralError = validateForImport();
        if (structuralError != null) {
            return structuralError;
        }
        if (password == null || password.isEmpty()) {
            return "请填写" + name + " SMTP 授权码或应用专用密码";
        }
        return null;
    }

    String validateForImport() {
        String safeHost = host == null ? "" : host.trim();
        if (safeHost.isEmpty() || safeHost.length() > 253
                || safeHost.matches(".*\\s.*") || safeHost.contains("://")) {
            return name + " SMTP 主机格式不正确";
        }
        if (port < 1 || port > 65535) {
            return name + " SMTP 端口范围应为 1–65535";
        }
        if (!AppConfig.SECURITY_SSL_TLS.equals(security)
                && !AppConfig.SECURITY_STARTTLS.equals(security)) {
            return name + " 必须选择 SSL/TLS 或 STARTTLS";
        }
        if (username == null || username.trim().isEmpty()) {
            return "请填写" + name + " SMTP 用户名";
        }
        if (fromAddress == null
                || !EMAIL.matcher(fromAddress.trim()).matches()) {
            return name + "发件邮箱格式不正确";
        }
        List<String> recipients = recipients();
        if (recipients.isEmpty()) {
            return "请至少填写一个" + name + "收件邮箱";
        }
        for (String recipient : recipients) {
            if (!EMAIL.matcher(recipient).matches()) {
                return name + "收件邮箱格式不正确：" + recipient;
            }
        }
        return null;
    }
}
