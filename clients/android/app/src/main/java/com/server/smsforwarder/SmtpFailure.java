package com.server.smsforwarder;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

final class SmtpFailure {
    private static final Pattern SMTP_REPLY_CODE = Pattern.compile(
            "(?im)(?:^\\s*|\\b(?:response|reply|return code)\\s*[:=]\\s*)([45]\\d{2})(?:\\s|:|-|$)");

    private SmtpFailure() {
    }

    static String describe(Throwable error) {
        String code = diagnosticCode(error);
        if (code.startsWith("SMTP-4")) {
            return "SMTP 服务器暂时拒绝会话：可能触发频率限制或服务繁忙，请稍后重试";
        }
        if (code.startsWith("SMTP-5")) {
            return "SMTP 服务器拒绝邮件：请核对授权码、发件邮箱、收件邮箱和服务商客户端开关";
        }
        switch (code) {
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
            case "CONNECTION-CLOSED":
                return "SMTP 服务器在会话中提前断开：移动网络可能干扰当前端口，请尝试 587 + STARTTLS，并检查 VPN/私人 DNS";
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
        return "AUTH".equals(diagnosticCode(error));
    }

    private static String diagnosticCode(Throwable error) {
        if (hasCause(error, AuthenticationFailedException.class)) return "AUTH";
        int replyCode = smtpReplyCode(error);
        if (replyCode == 530 || replyCode == 534 || replyCode == 535) return "AUTH";
        if (hasCause(error, SSLHandshakeException.class) || hasCause(error, SSLException.class)
                || messagesContain(error, "could not convert socket to tls", "starttls is required")) {
            return "TLS";
        }
        if (hasCause(error, UnknownHostException.class)) return "DNS";
        if (hasCause(error, SocketTimeoutException.class)
                || messagesContain(error, "read timed out", "connect timed out")) return "TIMEOUT";
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
        if (hasCause(error, EOFException.class)
                || messagesContain(error, "eof on socket", "response: -1", "unexpected end of stream")) {
            return "CONNECTION-CLOSED";
        }
        if (hasCause(error, SocketException.class)) return "CONNECTION-RESET";
        if (hasCause(error, SendFailedException.class)) return "ADDRESS";
        if (replyCode >= 400 && replyCode <= 599) return "SMTP-" + replyCode;
        if (hasCause(error, MessagingException.class)) return "SMTP-PROTOCOL";
        return "UNKNOWN";
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current : exceptionGraph(error)) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private static boolean messagesContain(Throwable error, String... needles) {
        for (Throwable current : exceptionGraph(error)) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                for (String needle : needles) {
                    if (lower.contains(needle)) return true;
                }
            }
        }
        return false;
    }

    private static int smtpReplyCode(Throwable error) {
        for (Throwable current : exceptionGraph(error)) {
            String message = current.getMessage();
            if (message == null) continue;
            Matcher matcher = SMTP_REPLY_CODE.matcher(message);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static List<Throwable> exceptionGraph(Throwable error) {
        List<Throwable> result = new ArrayList<>();
        if (error == null) return result;
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        IdentityHashMap<Throwable, Boolean> seen = new IdentityHashMap<>();
        pending.add(error);
        while (!pending.isEmpty() && result.size() < 24) {
            Throwable current = pending.removeFirst();
            if (seen.put(current, Boolean.TRUE) != null) continue;
            result.add(current);
            Throwable cause = current.getCause();
            if (cause != null && !seen.containsKey(cause)) pending.addLast(cause);
            if (current instanceof MessagingException) {
                Exception next = ((MessagingException) current).getNextException();
                if (next != null && !seen.containsKey(next)) pending.addLast(next);
            }
        }
        return result;
    }
}
