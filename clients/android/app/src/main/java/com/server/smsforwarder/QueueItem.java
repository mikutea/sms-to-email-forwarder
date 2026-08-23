package com.server.smsforwarder;

final class QueueItem {
    static final String KIND_SMS = "sms";
    static final String KIND_TEST = "test";

    final String id;
    final String kind;
    final long receivedAt;
    final String sender;
    final String body;
    final int simSlot;
    final int attempts;

    QueueItem(
            String id,
            String kind,
            long receivedAt,
            String sender,
            String body,
            int simSlot,
            int attempts) {
        this.id = id;
        this.kind = kind;
        this.receivedAt = receivedAt;
        this.sender = sender;
        this.body = body;
        this.simSlot = simSlot;
        this.attempts = attempts;
    }
}

