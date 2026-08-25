package com.server.smsforwarder;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.mail.AuthenticationFailedException;
import javax.mail.SendFailedException;
import javax.net.ssl.SSLHandshakeException;

final class SmtpFailure {
    private SmtpFailure() {
    }

    static String describe(Throwable error) {
        if (hasCause(error, AuthenticationFailedException.class)) {
            return "SMTP 认证失败：请确认已开启第三方客户端，并使用邮箱服务商生成的专用密码";
        }
        if (hasCause(error, SSLHandshakeException.class)) {
            return "TLS 安全连接失败：请核对手机时间、服务器地址和加密方式";
        }
        if (hasCause(error, UnknownHostException.class)) {
            return "无法解析 SMTP 服务器：请检查主机名和当前网络";
        }
        if (hasCause(error, SocketTimeoutException.class)) {
            return "连接 SMTP 服务器超时：请核对端口、加密方式和网络限制";
        }
        if (hasCause(error, ConnectException.class)) {
            return "无法连接 SMTP 服务器：端口与加密方式可能不匹配";
        }
        if (hasCause(error, SendFailedException.class)) {
            return "邮件地址被服务器拒绝：请核对发件邮箱和收件邮箱";
        }
        return "SMTP 发送失败：请核对服务器、端口、加密方式和服务商开关";
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 12; depth++) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }
}
