package com.server.smsforwarder;

final class QueueItem {
    static final String KIND_SMS = "sms";
    static final String KIND_TEST = "test";
    static final String KIND_HEARTBEAT = "heartbeat";
    static final String KIND_ALERT = "alert";

    final String id;
    final String kind;
    final long receivedAt;
    final String sender;
    final String body;
    final int simSlot;
    final int attempts;
    final int deliveredMask;
    final String bodyMatchClue;
    final long localReceivedAt;
    final long readLinkGeneration;
    final long smtpAcceptedAt;

    QueueItem(
            String id,
            String kind,
            long receivedAt,
            String sender,
            String body,
            int simSlot,
            int attempts) {
        this(id, kind, receivedAt, sender, body, simSlot, attempts, 0, "", receivedAt);
    }

    QueueItem(
            String id,
            String kind,
            long receivedAt,
            String sender,
            String body,
            int simSlot,
            int attempts,
            int deliveredMask) {
        this(id, kind, receivedAt, sender, body, simSlot, attempts, deliveredMask, "", receivedAt);
    }

    QueueItem(
            String id,
            String kind,
            long receivedAt,
            String sender,
            String body,
            int simSlot,
            int attempts,
            int deliveredMask,
            String bodyMatchClue) {
        this(id, kind, receivedAt, sender, body, simSlot, attempts, deliveredMask,
                bodyMatchClue, receivedAt);
    }

    QueueItem(
            String id,
            String kind,
            long receivedAt,
            String sender,
            String body,
            int simSlot,
            int attempts,
            int deliveredMask,
            String bodyMatchClue,
            long localReceivedAt) {
        this(id, kind, receivedAt, sender, body, simSlot, attempts, deliveredMask,
                bodyMatchClue, localReceivedAt, 0L);
    }

    QueueItem(
            String id,
            String kind,
            long receivedAt,
            String sender,
            String body,
            int simSlot,
            int attempts,
            int deliveredMask,
            String bodyMatchClue,
            long localReceivedAt,
            long readLinkGeneration) {
        this(id, kind, receivedAt, sender, body, simSlot, attempts, deliveredMask,
                bodyMatchClue, localReceivedAt, readLinkGeneration, 0L);
    }

    QueueItem(
            String id,
            String kind,
            long receivedAt,
            String sender,
            String body,
            int simSlot,
            int attempts,
            int deliveredMask,
            String bodyMatchClue,
            long localReceivedAt,
            long readLinkGeneration,
            long smtpAcceptedAt) {
        this.id = id;
        this.kind = kind;
        this.receivedAt = receivedAt;
        this.sender = sender;
        this.body = body;
        this.simSlot = simSlot;
        this.attempts = attempts;
        this.deliveredMask = deliveredMask;
        this.bodyMatchClue = bodyMatchClue == null ? "" : bodyMatchClue;
        this.localReceivedAt = localReceivedAt;
        this.readLinkGeneration = readLinkGeneration;
        this.smtpAcceptedAt = smtpAcceptedAt;
    }
}
