package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SmtpProfileTest {
    @Test
    public void parsesMultipleRecipientsAndRejectsInvalidAddress() {
        SmtpProfile valid = new SmtpProfile(
                "主通道",
                "smtp.example.com",
                465,
                AppConfig.SECURITY_SSL_TLS,
                "sender@example.com",
                "secret",
                "sender@example.com",
                "first@example.com, second@example.com\nthird@example.com");
        assertEquals(3, valid.recipients().size());
        assertEquals(null, valid.validate());

        SmtpProfile invalid = new SmtpProfile(
                "主通道",
                "smtp.example.com",
                465,
                AppConfig.SECURITY_SSL_TLS,
                "sender@example.com",
                "secret",
                "sender@example.com",
                "not-an-email");
        assertEquals("主通道收件邮箱格式不正确：not-an-email", invalid.validate());
    }

    @Test
    public void importValidationAllowsOmittedPasswordButStillChecksStructure() {
        SmtpProfile imported = new SmtpProfile(
                "主通道", "smtp.feishu.cn", 465, AppConfig.SECURITY_SSL_TLS,
                "sender@example.com", "", "sender@example.com", "inbox@example.com");
        assertEquals(null, imported.validateForImport());
        assertEquals("请填写主通道 SMTP 授权码或应用专用密码", imported.validate());
    }
}
