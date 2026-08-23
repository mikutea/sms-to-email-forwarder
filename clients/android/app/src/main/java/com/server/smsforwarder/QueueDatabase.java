package com.server.smsforwarder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class QueueDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "forward_queue.db";
    private static final int DATABASE_VERSION = 1;
    private static volatile QueueDatabase instance;

    static QueueDatabase get(Context context) {
        if (instance == null) {
            synchronized (QueueDatabase.class) {
                if (instance == null) {
                    instance = new QueueDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private QueueDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pending_messages ("
                + "id TEXT PRIMARY KEY,"
                + "kind TEXT NOT NULL,"
                + "received_at INTEGER NOT NULL,"
                + "sender_encrypted TEXT NOT NULL,"
                + "body_encrypted TEXT NOT NULL,"
                + "sim_slot INTEGER NOT NULL,"
                + "attempts INTEGER NOT NULL DEFAULT 0,"
                + "next_attempt_at INTEGER NOT NULL,"
                + "created_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX pending_messages_next_idx "
                + "ON pending_messages(next_attempt_at, created_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unexpected database version " + oldVersion + " -> " + newVersion);
    }

    synchronized boolean enqueueSms(String sender, String body, long receivedAt, int simSlot) {
        String id = stableSmsId(sender, body, receivedAt, simSlot);
        return insert(id, QueueItem.KIND_SMS, sender, body, receivedAt, simSlot);
    }

    synchronized boolean enqueueTest() {
        long now = System.currentTimeMillis();
        return insert(
                UUID.randomUUID().toString(),
                QueueItem.KIND_TEST,
                "设备自检",
                "如果你收到这封邮件，说明短信转邮箱 App 的 SMTP 配置可以正常发送邮件。",
                now,
                -1);
    }

    private boolean insert(
            String id,
            String kind,
            String sender,
            String body,
            long receivedAt,
            int simSlot) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("kind", kind);
        values.put("received_at", receivedAt);
        values.put("sender_encrypted", CryptoStore.encrypt(sender == null ? "未知发件人" : sender));
        values.put("body_encrypted", CryptoStore.encrypt(body == null ? "" : body));
        values.put("sim_slot", simSlot);
        values.put("attempts", 0);
        values.put("next_attempt_at", System.currentTimeMillis());
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict(
                "pending_messages",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    synchronized List<QueueItem> ready(long now, int limit) {
        List<QueueItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "pending_messages",
                new String[]{"id", "kind", "received_at", "sender_encrypted", "body_encrypted", "sim_slot", "attempts"},
                "next_attempt_at <= ?",
                new String[]{Long.toString(now)},
                null,
                null,
                "created_at ASC",
                Integer.toString(limit))) {
            while (cursor.moveToNext()) {
                items.add(new QueueItem(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getLong(2),
                        CryptoStore.decrypt(cursor.getString(3)),
                        CryptoStore.decrypt(cursor.getString(4)),
                        cursor.getInt(5),
                        cursor.getInt(6)));
            }
        }
        return items;
    }

    synchronized void markRetry(String id, int attempts, long nextAttemptAt) {
        ContentValues values = new ContentValues();
        values.put("attempts", attempts);
        values.put("next_attempt_at", nextAttemptAt);
        getWritableDatabase().update(
                "pending_messages",
                values,
                "id = ?",
                new String[]{id});
    }

    synchronized void remove(String id) {
        getWritableDatabase().delete("pending_messages", "id = ?", new String[]{id});
    }

    synchronized int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM pending_messages",
                null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    synchronized long earliestNextAttempt() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT MIN(next_attempt_at) FROM pending_messages",
                null)) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
            return 0L;
        }
    }

    synchronized void clear() {
        getWritableDatabase().delete("pending_messages", null, null);
    }

    private static String stableSmsId(String sender, String body, long receivedAt, int simSlot) {
        String input = receivedAt + "\n" + simSlot + "\n" + sender + "\n" + body;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

