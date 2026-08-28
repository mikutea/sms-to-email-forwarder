package com.server.smsforwarder;

import javax.mail.MessagingException;

final class SmtpStageException extends MessagingException {
    private final String diagnosticStage;

    private SmtpStageException(String diagnosticStage, MessagingException cause) {
        super("SMTP stage failed: " + diagnosticStage, cause);
        this.diagnosticStage = diagnosticStage;
    }

    static SmtpStageException connect(MessagingException cause) {
        return new SmtpStageException("CONNECT-AUTH", cause);
    }

    static SmtpStageException send(MessagingException cause) {
        return new SmtpStageException("SEND", cause);
    }

    String diagnosticStage() {
        return diagnosticStage;
    }
}
