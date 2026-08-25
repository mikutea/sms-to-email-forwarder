package com.server.smsforwarder;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

final class SmtpFailure {
    private SmtpFailure() {
    }

    static String describe(Throwable error) {
        switch (diagnosticCode(error)) {
            case "AUTH":
                return "SMTP 认证失败：请确认已开启第三方客户端，并使用邮箱服务商生成的专用密码";
            case "TLS":
                return "TLS 安全连接失败：请核对手机时间、服务器地址和加密方式";
            case "DNS":
                return "无法解析 SMTP 服务器：请检查主机名、私人 DNS 和当前网络";
            case "TIMEOUT":
                return "连接 SMTP 服务器超时：请检查移动网络信号、VPN、端口和加密方式";
            case "NET-BLOCKED":
                return "系统限制了雁笺联网：请在应用联网中允许移动数据，并加入省流量白名单";
            case "NO-ROUTE":
                return "当前网络无法到达 SMTP 服务器：请检查移动数据、VPN/私人 DNS，或改用 587 + STARTTLS 测试";
            case "CONNECT":
                return "无法连接 SMTP 服务器：端口与加密方式可能不匹配，移动网络也可能限制该端口";
            case "CONNECTION-RESET":
                return "SMTP 网络连接被中断：请检查信号、VPN/私人 DNS 或运营商端口限制";
            case "ADDRESS":
                return "邮件地址被服务器拒绝：请核对发件邮箱和收件邮箱";
            default:
                return "SMTP 会话未完成：服务器拒绝或中断了连接，请查看诊断码后重试";
        }
    }

    static String describeForRecord(Throwable error) {
        return describe(error) + "（诊断码 " + diagnosticCode(error) + "）";
    }

    static boolean isAuthenticationFailure(Throwable error) {
        return hasCause(error, AuthenticationFailedException.class);
    }

    private static String diagnosticCode(Throwable error) {
        if (hasCause(error, AuthenticationFailedException.class)) return "AUTH";
        if (hasCause(error, SSLHandshakeException.class) || hasCause(error, SSLException.class)) return "TLS";
        if (hasCause(error, UnknownHostException.class)) return "DNS";
        if (hasCause(error, SocketTimeoutException.class)) return "TIMEOUT";
        if (messagesContain(error, "eperm", "eacces", "permission denied", "operation not permitted")) {
            return "NET-BLOCKED";
        }
        if (hasCause(error, NoRouteToHostException.class)
                || messagesContain(error, "enetunreach", "network is unreachable", "no route to host")) {
            return "NO-ROUTE";
        }
        if (hasCause(error, ConnectException.class)
                || messagesContain(error, "could not connect to smtp host", "connection refused")) {
            return "CONNECT";
        }
        if (hasCause(error, SocketException.class)) return "CONNECTION-RESET";
        if (hasCause(error, SendFailedException.class)) return "ADDRESS";
        if (hasCause(error, MessagingException.class)) return "SMTP-PROTOCOL";
        return "UNKNOWN";
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 12; depth++) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    private static boolean messagesContain(Throwable error, String... needles) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 12; depth++) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                for (String needle : needles) {
                    if (lower.contains(needle)) return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
