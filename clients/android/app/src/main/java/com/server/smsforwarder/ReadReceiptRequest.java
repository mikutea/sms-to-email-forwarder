package com.server.smsforwarder;

final class ReadReceiptRequest {
    final String id;
    final long receivedAt;
    final String sender;
    final String bodyMatchClue;
    final long expiresAt;

    ReadReceiptRequest(
            String id,
            long receivedAt,
            String sender,
            String bodyMatchClue,
            long expiresAt) {
        this.id = id;
        this.receivedAt = receivedAt;
        this.sender = sender;
        this.bodyMatchClue = bodyMatchClue;
        this.expiresAt = expiresAt;
    }
}
