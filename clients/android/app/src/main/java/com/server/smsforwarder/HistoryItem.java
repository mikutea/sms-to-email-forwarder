package com.server.smsforwarder;

final class HistoryItem {
    final String id;
    final long receivedAt;
    final String sender;
    final String body;
    final int simSlot;
    final String status;
    final int attempts;
    final String detail;

    HistoryItem(
            String id,
            long receivedAt,
            String sender,
            String body,
            int simSlot,
            String status,
            int attempts,
            String detail) {
        this.id = id;
        this.receivedAt = receivedAt;
        this.sender = sender;
        this.body = body;
        this.simSlot = simSlot;
        this.status = status;
        this.attempts = attempts;
        this.detail = detail;
    }
}
