package com.server.smsforwarder;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;

final class MailcapSupport {
    private static CommandMap installedMap;

    private MailcapSupport() {
    }

    static synchronized void ensureInstalled() {
        CommandMap current = CommandMap.getDefaultCommandMap();
        if (current == installedMap) return;

        MailcapCommandMap mailcap = current instanceof MailcapCommandMap
                ? (MailcapCommandMap) current
                : new MailcapCommandMap();
        mailcap.addMailcap(
                "text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain");
        mailcap.addMailcap(
                "text/html;; x-java-content-handler=com.sun.mail.handlers.text_html");
        mailcap.addMailcap(
                "text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml");
        mailcap.addMailcap(
                "multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed");
        mailcap.addMailcap(
                "message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822");
        CommandMap.setDefaultCommandMap(mailcap);
        installedMap = mailcap;
    }
}
