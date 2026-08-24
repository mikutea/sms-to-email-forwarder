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
    private static final int DATABASE_VERSION = 4;
    private static final long CLAIM_LEASE_MS = 2L * 60L * 1000L;
    private static final int MAX_PENDING_ROWS = 500;
    private static final long MAX_PENDING_CIPHERTEXT_CHARS = 8L * 1024L * 1024L;
    private static volatile QueueDatabase instance;

    enum EnqueueResult {
        INSERTED,
        DUPLICATE,
        CAPACITY_REACHED
    }

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
                + "lease_until INTEGER NOT NULL DEFAULT 0,"
                + "delivered_mask INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX pending_messages_next_idx "
                + "ON pending_messages(next_attempt_at, created_at)");
        createHistoryTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE pending_messages ADD COLUMN lease_until INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE pending_messages ADD COLUMN delivered_mask INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 4) {
            createHistoryTable(db);
        }
    }

    synchronized EnqueueResult enqueueSms(String sender, String body, long receivedAt, int simSlot) {
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
                -1) == EnqueueResult.INSERTED;
    }

    synchronized boolean enqueueStatus(String kind, String title, String body) {
        long now = System.currentTimeMillis();
        return insert(UUID.randomUUID().toString(), kind, title, body, now, -1)
                == EnqueueResult.INSERTED;
    }

    private EnqueueResult insert(
            String id,
            String kind,
            String sender,
            String body,
            long receivedAt,
            int simSlot) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            if (containsPending(database, id)) {
                database.setTransactionSuccessful();
                return EnqueueResult.DUPLICATE;
            }

            String encryptedSender = CryptoStore.encrypt(sender == null ? "未知发件人" : sender);
            String encryptedBody = CryptoStore.encrypt(body == null ? "" : body);
            long[] usage = pendingUsage(database);
            long newCiphertextChars = encryptedSender.length() + encryptedBody.length();
            if (wouldExceedCapacity(usage[0], usage[1], newCiphertextChars)) {
                database.setTransactionSuccessful();
                return EnqueueResult.CAPACITY_REACHED;
            }

            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("kind", kind);
            values.put("received_at", receivedAt);
            values.put("sender_encrypted", encryptedSender);
            values.put("body_encrypted", encryptedBody);
            values.put("sim_slot", simSlot);
            values.put("attempts", 0);
            values.put("next_attempt_at", System.currentTimeMillis());
            values.put("lease_until", 0L);
            values.put("delivered_mask", 0);
            values.put("created_at", System.currentTimeMillis());
            boolean inserted = database.insertWithOnConflict(
                    "pending_messages",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE) != -1;
            if (inserted) {
                upsertHistory(id, receivedAt, sender, body, simSlot, "QUEUED", 0, "等待发送");
            }
            database.setTransactionSuccessful();
            return inserted ? EnqueueResult.INSERTED : EnqueueResult.DUPLICATE;
        } finally {
            database.endTransaction();
        }
    }

    synchronized List<QueueItem> claimReady(long now, int limit) {
        List<QueueItem> items = new ArrayList<>();
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try (Cursor cursor = database.query(
                "pending_messages",
                new String[]{"id", "kind", "received_at", "sender_encrypted", "body_encrypted", "sim_slot", "attempts", "delivered_mask"},
                "next_attempt_at <= ? AND lease_until <= ?",
                new String[]{Long.toString(now), Long.toString(now)},
                null,
                null,
                "created_at ASC",
                Integer.toString(limit))) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                ContentValues claim = new ContentValues();
                claim.put("lease_until", now + CLAIM_LEASE_MS);
                int updated = database.update(
                        "pending_messages",
                        claim,
                        "id = ? AND lease_until <= ?",
                        new String[]{id, Long.toString(now)});
                if (updated != 1) {
                    continue;
                }
                updateHistory(id, "SENDING", cursor.getInt(6), "正在连接 SMTP");
                items.add(new QueueItem(
                        id,
                        cursor.getString(1),
                        cursor.getLong(2),
                        CryptoStore.decrypt(cursor.getString(3)),
                        CryptoStore.decrypt(cursor.getString(4)),
                        cursor.getInt(5),
                        cursor.getInt(6),
                        cursor.getInt(7)));
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return items;
    }

    synchronized void markRetry(String id, int attempts, long nextAttemptAt) {
        markRetry(id, attempts, nextAttemptAt, "等待自动重试");
    }

    synchronized void markRetry(String id, int attempts, long nextAttemptAt, String detail) {
        ContentValues values = new ContentValues();
        values.put("attempts", attempts);
        values.put("next_attempt_at", nextAttemptAt);
        values.put("lease_until", 0L);
        getWritableDatabase().update(
                "pending_messages",
                values,
                "id = ?",
                new String[]{id});
        updateHistory(id, "RETRY_WAIT", attempts, detail);
    }

    synchronized void markDelivered(String id, int deliveredMask) {
        ContentValues values = new ContentValues();
        values.put("delivered_mask", deliveredMask);
        getWritableDatabase().update("pending_messages", values, "id = ?", new String[]{id});
    }

    synchronized void markSuccess(String id, int attempts, String detail) {
        updateHistory(id, "SUCCESS", attempts, detail);
    }

    synchronized void recordFiltered(
            String id,
            long receivedAt,
            String sender,
            String body,
            int simSlot,
            String detail) {
        upsertHistory(id, receivedAt, sender, body, simSlot, "FILTERED", 0, detail);
    }

    synchronized List<HistoryItem> recentHistory(int limit) {
        List<HistoryItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "message_history",
                new String[]{"id", "received_at", "sender_encrypted", "body_encrypted", "sim_slot", "status", "attempts", "detail"},
                null,
                null,
                null,
                null,
                "updated_at DESC",
                Integer.toString(Math.max(1, Math.min(limit, 200))))) {
            while (cursor.moveToNext()) {
                items.add(new HistoryItem(
                        cursor.getString(0),
                        cursor.getLong(1),
                        CryptoStore.decrypt(cursor.getString(2)),
                        CryptoStore.decrypt(cursor.getString(3)),
                        cursor.getInt(4),
                        cursor.getString(5),
                        cursor.getInt(6),
                        cursor.getString(7)));
            }
        }
        return items;
    }

    synchronized void clearHistory() {
        getWritableDatabase().delete("message_history", null, null);
    }

    synchronized boolean requeue(HistoryItem item) {
        return insert(
                item.id,
                QueueItem.KIND_SMS,
                item.sender,
                item.body,
                item.receivedAt,
                item.simSlot) == EnqueueResult.INSERTED;
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

    synchronized long earliestRunnableAt() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT MIN(CASE WHEN lease_until > next_attempt_at THEN lease_until ELSE next_attempt_at END) FROM pending_messages",
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

    private synchronized void upsertHistory(
            String id,
            long receivedAt,
            String sender,
            String body,
            int simSlot,
            String status,
            int attempts,
            String detail) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("received_at", receivedAt);
        values.put("sender_encrypted", CryptoStore.encrypt(sender == null ? "未知发件人" : sender));
        values.put("body_encrypted", CryptoStore.encrypt(body == null ? "" : body));
        values.put("sim_slot", simSlot);
        values.put("status", status);
        values.put("attempts", attempts);
        values.put("detail", sanitizeDetail(detail));
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(
                "message_history", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        getWritableDatabase().delete(
                "message_history",
                "id NOT IN (SELECT id FROM message_history ORDER BY updated_at DESC LIMIT 500)",
                null);
        getWritableDatabase().delete(
                "message_history",
                "updated_at < ?",
                new String[]{Long.toString(System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L)});
    }

    private synchronized void updateHistory(String id, String status, int attempts, String detail) {
        ContentValues values = new ContentValues();
        values.put("status", status);
        values.put("attempts", attempts);
        values.put("detail", sanitizeDetail(detail));
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("message_history", values, "id = ?", new String[]{id});
    }

    private static void createHistoryTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS message_history ("
                + "id TEXT PRIMARY KEY,"
                + "received_at INTEGER NOT NULL,"
                + "sender_encrypted TEXT NOT NULL,"
                + "body_encrypted TEXT NOT NULL,"
                + "sim_slot INTEGER NOT NULL,"
                + "status TEXT NOT NULL,"
                + "attempts INTEGER NOT NULL DEFAULT 0,"
                + "detail TEXT NOT NULL DEFAULT '',"
                + "updated_at INTEGER NOT NULL"
                + ")");
        database.execSQL("CREATE INDEX IF NOT EXISTS message_history_updated_idx "
                + "ON message_history(updated_at DESC)");
    }

    private static boolean containsPending(SQLiteDatabase database, String id) {
        try (Cursor cursor = database.query(
                "pending_messages",
                new String[]{"id"},
                "id = ?",
                new String[]{id},
                null,
                null,
                null,
                "1")) {
            return cursor.moveToFirst();
        }
    }

    private static long[] pendingUsage(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(LENGTH(sender_encrypted) + LENGTH(body_encrypted)), 0) "
                        + "FROM pending_messages",
                null)) {
            if (cursor.moveToFirst()) {
                return new long[]{cursor.getLong(0), cursor.getLong(1)};
            }
            return new long[]{0L, 0L};
        }
    }

    static boolean wouldExceedCapacity(long rows, long ciphertextChars, long newCiphertextChars) {
        if (rows >= MAX_PENDING_ROWS) {
            return true;
        }
        if (ciphertextChars < 0L || newCiphertextChars < 0L) {
            return true;
        }
        return ciphertextChars > MAX_PENDING_CIPHERTEXT_CHARS - newCiphertextChars;
    }

    private static String sanitizeDetail(String detail) {
        if (detail == null) {
            return "";
        }
        String safe = detail.replace('\r', ' ').replace('\n', ' ');
        return safe.length() > 240 ? safe.substring(0, 240) : safe;
    }

    static String stableSmsId(String sender, String body, long receivedAt, int simSlot) {
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
