package com.server.smsforwarder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class QueueDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "forward_queue.db";
    private static final int DATABASE_VERSION = 7;
    private static final long CLAIM_LEASE_MS = 5L * 60L * 1000L;
    private static final int MAX_SMS_ROWS = 500;
    private static final int MAX_NON_SMS_ROWS = 50;
    private static final int MAX_PENDING_ROWS = MAX_SMS_ROWS + MAX_NON_SMS_ROWS;
    private static final int MAX_READ_RECEIPTS = 50;
    private static final int MAX_HISTORY_ROWS = MAX_PENDING_ROWS + MAX_READ_RECEIPTS;
    private static final long MAX_PENDING_CIPHERTEXT_CHARS = 8L * 1024L * 1024L;
    private static volatile QueueDatabase instance;
    private final Context applicationContext;

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
        applicationContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pending_messages ("
                + "id TEXT PRIMARY KEY,"
                + "kind TEXT NOT NULL,"
                + "received_at INTEGER NOT NULL,"
                + "sender_encrypted TEXT NOT NULL,"
                + "body_encrypted TEXT NOT NULL,"
                + "match_clue_encrypted TEXT NOT NULL DEFAULT '',"
                + "sim_slot INTEGER NOT NULL,"
                + "attempts INTEGER NOT NULL DEFAULT 0,"
                + "next_attempt_at INTEGER NOT NULL,"
                + "lease_until INTEGER NOT NULL DEFAULT 0,"
                + "delivered_mask INTEGER NOT NULL DEFAULT 0,"
                + "read_link_generation INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX pending_messages_next_idx "
                + "ON pending_messages(next_attempt_at, created_at)");
        createHistoryTable(db);
        createReadReceiptTable(db);
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
        if (oldVersion < 5) {
            createReadReceiptTable(db);
        }
        if (oldVersion < 6) {
            db.execSQL(
                    "ALTER TABLE pending_messages "
                            + "ADD COLUMN match_clue_encrypted TEXT NOT NULL DEFAULT ''");
            if (oldVersion >= 5) {
                db.execSQL(
                        "ALTER TABLE pending_read_receipts "
                                + "ADD COLUMN match_clue_encrypted TEXT NOT NULL DEFAULT ''");
            }
            coalescePendingHeartbeats(db);
        }
        if (oldVersion < 7) {
            trimPendingNonSms(db);
            db.execSQL(
                    "ALTER TABLE pending_messages "
                            + "ADD COLUMN read_link_generation INTEGER NOT NULL DEFAULT 0");
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            purgeExpiredReadReceipts(db, System.currentTimeMillis());
        }
    }

    synchronized EnqueueResult enqueueSms(String sender, String body, long receivedAt, int simSlot) {
        return enqueueSms(sender, body, "", receivedAt, System.currentTimeMillis(), simSlot, 0L);
    }

    synchronized EnqueueResult enqueueSms(
            String sender,
            String body,
            String bodyMatchClue,
            long receivedAt,
            int simSlot) {
        return enqueueSms(
                sender, body, bodyMatchClue, receivedAt, System.currentTimeMillis(), simSlot, 0L);
    }

    synchronized EnqueueResult enqueueSms(
            String sender,
            String body,
            String bodyMatchClue,
            long receivedAt,
            long localReceivedAt,
            int simSlot) {
        return enqueueSms(
                sender, body, bodyMatchClue, receivedAt, localReceivedAt, simSlot, 0L);
    }

    synchronized EnqueueResult enqueueSms(
            String sender,
            String body,
            String bodyMatchClue,
            long receivedAt,
            long localReceivedAt,
            int simSlot,
            long readLinkGeneration) {
        String id = stableSmsId(sender, body, receivedAt, simSlot);
        return insert(
                id, QueueItem.KIND_SMS, sender, body, bodyMatchClue,
                receivedAt, localReceivedAt, simSlot, readLinkGeneration);
    }

    synchronized boolean enqueueTest() {
        long now = System.currentTimeMillis();
        return insert(
                UUID.randomUUID().toString(),
                QueueItem.KIND_TEST,
                "设备自检",
                "如果你收到这封邮件，说明短信转邮箱 App 的 SMTP 配置可以正常发送邮件。",
                "",
                now,
                now,
                -1,
                0L) == EnqueueResult.INSERTED;
    }

    synchronized boolean enqueueStatus(String kind, String title, String body) {
        long now = System.currentTimeMillis();
        if (QueueItem.KIND_HEARTBEAT.equals(kind) && hasPendingKind(getReadableDatabase(), kind)) {
            return refreshPendingHeartbeat(title, body, now);
        }
        return insert(UUID.randomUUID().toString(), kind, title, body, "", now, now, -1, 0L)
                == EnqueueResult.INSERTED;
    }

    private boolean refreshPendingHeartbeat(String title, String body, long now) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try (Cursor cursor = database.query(
                "pending_messages",
                new String[]{"id", "sender_encrypted", "body_encrypted", "match_clue_encrypted"},
                "kind = ? AND lease_until <= ?",
                new String[]{QueueItem.KIND_HEARTBEAT, Long.toString(now)},
                null,
                null,
                "created_at DESC",
                "1")) {
            if (!cursor.moveToFirst()) {
                return false;
            }

            String id = cursor.getString(0);
            long replacedCiphertextChars = cursor.getString(1).length()
                    + cursor.getString(2).length()
                    + cursor.getString(3).length();
            String safeTitle = title == null ? "未知状态" : title;
            String safeBody = body == null ? "" : body;
            String encryptedTitle = CryptoStore.encrypt(safeTitle);
            String encryptedBody = CryptoStore.encrypt(safeBody);
            String encryptedClue = CryptoStore.encrypt("");
            long replacementCiphertextChars = encryptedTitle.length()
                    + encryptedBody.length()
                    + encryptedClue.length();
            long[] usage = pendingUsage(database);
            long retainedCiphertextChars = Math.max(0L, usage[1] - replacedCiphertextChars);
            if (retainedCiphertextChars
                    > MAX_PENDING_CIPHERTEXT_CHARS - replacementCiphertextChars) {
                return false;
            }

            ContentValues values = new ContentValues();
            values.put("received_at", now);
            values.put("sender_encrypted", encryptedTitle);
            values.put("body_encrypted", encryptedBody);
            values.put("match_clue_encrypted", encryptedClue);
            values.put("sim_slot", -1);
            values.put("attempts", 0);
            values.put("next_attempt_at", now);
            values.put("lease_until", 0L);
            values.put("delivered_mask", 0);
            values.put("created_at", now);
            int updated = database.update(
                    "pending_messages",
                    values,
                    "id = ? AND lease_until <= ?",
                    new String[]{id, Long.toString(now)});
            if (updated != 1) {
                return false;
            }
            upsertHistory(id, now, safeTitle, safeBody, -1, "QUEUED", 0, "等待发送");
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    private EnqueueResult insert(
            String id,
            String kind,
            String sender,
            String body,
            String bodyMatchClue,
            long receivedAt,
            long localReceivedAt,
            int simSlot,
            long readLinkGeneration) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            if (containsPending(database, id)) {
                database.setTransactionSuccessful();
                return EnqueueResult.DUPLICATE;
            }

            String encryptedSender = CryptoStore.encrypt(sender == null ? "未知发件人" : sender);
            String encryptedBody = CryptoStore.encrypt(body == null ? "" : body);
            String encryptedMatchClue = CryptoStore.encrypt(
                    bodyMatchClue == null ? "" : bodyMatchClue);
            long[] usage = pendingUsage(database);
            long newCiphertextChars = encryptedSender.length()
                    + encryptedBody.length()
                    + encryptedMatchClue.length();
            if (wouldExceedCapacity(usage[0], usage[1], newCiphertextChars)
                    || wouldExceedKindCapacity(kind, usage[2], usage[3])) {
                database.setTransactionSuccessful();
                return EnqueueResult.CAPACITY_REACHED;
            }

            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("kind", kind);
            values.put("received_at", receivedAt);
            values.put("sender_encrypted", encryptedSender);
            values.put("body_encrypted", encryptedBody);
            values.put("match_clue_encrypted", encryptedMatchClue);
            values.put("sim_slot", simSlot);
            values.put("attempts", 0);
            values.put("next_attempt_at", System.currentTimeMillis());
            values.put("lease_until", 0L);
            values.put("delivered_mask", 0);
            values.put("read_link_generation", readLinkGeneration);
            values.put("created_at", localReceivedAt);
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
        return claimReady(now, limit, false);
    }

    synchronized List<QueueItem> claimReady(long now, int limit, boolean prioritizeSms) {
        List<QueueItem> items = new ArrayList<>();
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try (Cursor cursor = database.query(
                "pending_messages",
                new String[]{"id", "kind", "received_at", "sender_encrypted", "body_encrypted", "sim_slot", "attempts", "delivered_mask", "match_clue_encrypted", "created_at", "read_link_generation"},
                "next_attempt_at <= ? AND lease_until <= ?",
                new String[]{Long.toString(now), Long.toString(now)},
                null,
                null,
                prioritizeSms
                        ? "CASE WHEN kind = '" + QueueItem.KIND_SMS
                        + "' THEN 0 ELSE 1 END, created_at ASC"
                        : "created_at ASC",
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
                        cursor.getInt(7),
                        CryptoStore.decrypt(cursor.getString(8)),
                        cursor.getLong(9),
                        cursor.getLong(10)));
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
        updateHistory(
                id,
                "RETRY_WAIT",
                attempts,
                detail + " · 下次自动重试 " + new java.text.SimpleDateFormat(
                        "MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date(nextAttemptAt)));
    }

    synchronized void markDelivered(String id, int deliveredMask) {
        ContentValues values = new ContentValues();
        values.put("delivered_mask", deliveredMask);
        getWritableDatabase().update("pending_messages", values, "id = ?", new String[]{id});
    }

    synchronized void markSuccess(String id, int attempts, String detail) {
        updateHistory(id, "SUCCESS", attempts, detail);
    }

    synchronized void completeAcceptedDelivery(String id, int attempts, String detail) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            updateHistoryLocked(database, id, "SUCCESS", attempts, detail);
            database.delete("pending_messages", "id = ?", new String[]{id});
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
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

    void clearHistory() {
        synchronized (SmsReadFeature.operationLock()) {
            synchronized (this) {
                SQLiteDatabase database = getWritableDatabase();
                database.beginTransaction();
                try {
                    cancelReadReceiptsLocked(
                            database,
                            "SMTP 服务器已接受邮件 · 历史与已读联动数据已由用户清除");
                    database.delete("message_history", null, null);
                    database.setTransactionSuccessful();
                } finally {
                    database.endTransaction();
                }
            }
        }
    }

    boolean requeue(HistoryItem item) {
        synchronized (SmsReadFeature.operationLock()) {
            synchronized (this) {
                removeReadReceipt(item.id);
                return insert(
                        item.id,
                        QueueItem.KIND_SMS,
                        item.sender,
                        item.body,
                        "",
                        item.receivedAt,
                        0L,
                        item.simSlot,
                        0L) == EnqueueResult.INSERTED;
            }
        }
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

    synchronized PendingStats pendingStats() {
        int sms = 0;
        int heartbeat = 0;
        int alert = 0;
        int other = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT kind, COUNT(*) FROM pending_messages GROUP BY kind",
                null)) {
            while (cursor.moveToNext()) {
                String kind = cursor.getString(0);
                int count = cursor.getInt(1);
                if (QueueItem.KIND_SMS.equals(kind)) {
                    sms += count;
                } else if (QueueItem.KIND_HEARTBEAT.equals(kind)) {
                    heartbeat += count;
                } else if (QueueItem.KIND_ALERT.equals(kind)) {
                    alert += count;
                } else {
                    other += count;
                }
            }
        }
        return new PendingStats(sms, heartbeat, alert, other);
    }

    synchronized int forceReadyNow(long now) {
        ContentValues values = new ContentValues();
        values.put("next_attempt_at", now);
        values.put("lease_until", 0L);
        int updated = getWritableDatabase().update(
                "pending_messages",
                values,
                "lease_until <= ?",
                new String[]{Long.toString(now)});
        ContentValues history = new ContentValues();
        history.put("status", "QUEUED");
        history.put("detail", "已请求立即重试");
        history.put("updated_at", now);
        getWritableDatabase().update(
                "message_history",
                history,
                "id IN (SELECT id FROM pending_messages WHERE next_attempt_at = ? AND lease_until = 0)",
                new String[]{Long.toString(now)});
        return updated;
    }

    synchronized boolean forceReadyNow(String id, long now) {
        ContentValues values = new ContentValues();
        values.put("next_attempt_at", now);
        values.put("lease_until", 0L);
        int updated = getWritableDatabase().update(
                "pending_messages",
                values,
                "id = ? AND lease_until <= ?",
                new String[]{id, Long.toString(now)});
        if (updated == 1) {
            updateHistory(id, "QUEUED", currentAttempts(id), "已请求立即重试");
        }
        return updated == 1;
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

    synchronized boolean hasReady(long now) {
        try (Cursor cursor = getReadableDatabase().query(
                "pending_messages",
                new String[]{"id"},
                "next_attempt_at <= ? AND lease_until <= ?",
                new String[]{Long.toString(now), Long.toString(now)},
                null,
                null,
                null,
                "1")) {
            return cursor.moveToFirst();
        }
    }

    void clear() {
        synchronized (SmsReadFeature.operationLock()) {
            SmsReadFeature.invalidateReadLinkGeneration(applicationContext);
            synchronized (this) {
                SQLiteDatabase database = getWritableDatabase();
                database.beginTransaction();
                try {
                    database.delete("pending_messages", null, null);
                    cancelReadReceiptsLocked(
                            database,
                            "SMTP 服务器已接受邮件 · 已读联动请求已由用户清除");
                    database.setTransactionSuccessful();
                } finally {
                    database.endTransaction();
                }
            }
        }
    }

    synchronized void enqueueReadReceipt(QueueItem item, long expiresAt) {
        if (!QueueItem.KIND_SMS.equals(item.kind)) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("id", item.id);
        // pending_messages.created_at is the device-local broadcast/queue time. Carrier SMS
        // timestamps remain in message metadata but are not trusted for notification matching.
        values.put("received_at", item.localReceivedAt);
        values.put("sender_encrypted", CryptoStore.encrypt(item.sender));
        // Keep the legacy column empty. Only the bounded original-body clue is needed for
        // matching and it remains encrypted with the device Keystore.
        values.put("body_encrypted", "");
        values.put("match_clue_encrypted", CryptoStore.encrypt(item.bodyMatchClue));
        values.put("expires_at", expiresAt);
        getWritableDatabase().insertWithOnConflict(
                "pending_read_receipts", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        trimReadReceipts();
    }

    List<ReadReceiptRequest> readReceiptRequests(long now) {
        synchronized (SmsReadFeature.operationLock()) {
            synchronized (this) {
                expireReadReceiptsLocked(now);
                List<ReadReceiptRequest> requests = new ArrayList<>();
                try (Cursor cursor = getReadableDatabase().query(
                        "pending_read_receipts",
                        new String[]{"id", "received_at", "sender_encrypted", "match_clue_encrypted", "expires_at"},
                        null, null, null, null, "received_at ASC", Integer.toString(MAX_READ_RECEIPTS))) {
                    while (cursor.moveToNext()) {
                        requests.add(new ReadReceiptRequest(
                                cursor.getString(0),
                                cursor.getLong(1),
                                CryptoStore.decrypt(cursor.getString(2)),
                                CryptoStore.decrypt(cursor.getString(3)),
                                cursor.getLong(4)));
                    }
                }
                return requests;
            }
        }
    }

    int expireReadReceipts(long now) {
        synchronized (SmsReadFeature.operationLock()) {
            synchronized (this) {
                return expireReadReceiptsLocked(now);
            }
        }
    }

    private int expireReadReceiptsLocked(long now) {
        List<String> expiredIds = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "pending_read_receipts",
                new String[]{"id"},
                "expires_at <= ?",
                new String[]{Long.toString(now)},
                null, null, null)) {
            while (cursor.moveToNext()) {
                expiredIds.add(cursor.getString(0));
            }
        }
        for (String id : expiredIds) {
            updateReadReceiptOutcome(
                    id,
                    "SMTP 服务器已接受邮件 · 系统短信未标记已读（未找到可安全调用的系统动作）");
        }
        if (!expiredIds.isEmpty()) {
            getWritableDatabase().delete(
                    "pending_read_receipts", "expires_at <= ?", new String[]{Long.toString(now)});
        }
        return expiredIds.size();
    }

    synchronized long earliestReadReceiptExpiry() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT MIN(expires_at) FROM pending_read_receipts",
                null)) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getLong(0) : 0L;
        }
    }

    synchronized void removeReadReceipt(String id) {
        getWritableDatabase().delete(
                "pending_read_receipts", "id = ?", new String[]{id});
    }

    synchronized boolean hasReadReceipt(String id) {
        try (Cursor cursor = getReadableDatabase().query(
                "pending_read_receipts",
                new String[]{"id"},
                "id = ?",
                new String[]{id},
                null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    synchronized boolean hasUnexpiredReadReceipt(String id, long now) {
        try (Cursor cursor = getReadableDatabase().query(
                "pending_read_receipts",
                new String[]{"id"},
                "id = ? AND expires_at > ?",
                new String[]{id, Long.toString(now)},
                null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    synchronized boolean consumeUnexpiredReadReceipt(String id, long now) {
        return getWritableDatabase().delete(
                "pending_read_receipts",
                "id = ? AND expires_at > ?",
                new String[]{id, Long.toString(now)}) == 1;
    }

    synchronized void cancelReadReceipts(String detail) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            cancelReadReceiptsLocked(database, detail);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private static void cancelReadReceiptsLocked(SQLiteDatabase database, String detail) {
        ContentValues values = new ContentValues();
        values.put("detail", sanitizeDetail(detail));
        values.put("updated_at", System.currentTimeMillis());
        database.update(
                "message_history",
                values,
                "id IN (SELECT id FROM pending_read_receipts)",
                null);
        database.delete("pending_read_receipts", null, null);
    }

    synchronized int clearPendingReadMatchClues() {
        ContentValues values = new ContentValues();
        values.put("match_clue_encrypted", "");
        values.put("read_link_generation", 0L);
        return getWritableDatabase().update(
                "pending_messages",
                values,
                "kind = ? AND (match_clue_encrypted <> '' OR read_link_generation <> 0)",
                new String[]{QueueItem.KIND_SMS});
    }

    synchronized void updateReadReceiptOutcome(String id, String detail) {
        ContentValues values = new ContentValues();
        values.put("detail", sanitizeDetail(detail));
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("message_history", values, "id = ?", new String[]{id});
    }

    private void trimReadReceipts() {
        List<String> overflowIds = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "pending_read_receipts", new String[]{"id"},
                null, null, null, null, "expires_at DESC")) {
            int position = 0;
            while (cursor.moveToNext()) {
                if (position++ >= MAX_READ_RECEIPTS) {
                    overflowIds.add(cursor.getString(0));
                }
            }
        }
        for (String id : overflowIds) {
            updateReadReceiptOutcome(
                    id,
                    "SMTP 服务器已接受邮件 · 系统短信未标记已读（临时匹配队列容量已满）");
            removeReadReceipt(id);
        }
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
                "id NOT IN (SELECT id FROM pending_messages) "
                        + "AND id NOT IN (SELECT id FROM pending_read_receipts) "
                        + "AND id NOT IN (SELECT id FROM message_history "
                        + "ORDER BY updated_at DESC LIMIT " + MAX_HISTORY_ROWS + ")",
                null);
        getWritableDatabase().delete(
                "message_history",
                "updated_at < ? "
                        + "AND id NOT IN (SELECT id FROM pending_messages) "
                        + "AND id NOT IN (SELECT id FROM pending_read_receipts)",
                new String[]{Long.toString(System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L)});
    }

    private synchronized void updateHistory(String id, String status, int attempts, String detail) {
        updateHistoryLocked(getWritableDatabase(), id, status, attempts, detail);
    }

    private static void updateHistoryLocked(
            SQLiteDatabase database,
            String id,
            String status,
            int attempts,
            String detail) {
        ContentValues values = new ContentValues();
        values.put("status", status);
        values.put("attempts", attempts);
        values.put("detail", sanitizeDetail(detail));
        values.put("updated_at", System.currentTimeMillis());
        database.update("message_history", values, "id = ?", new String[]{id});
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

    private static void createReadReceiptTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS pending_read_receipts ("
                + "id TEXT PRIMARY KEY,"
                + "received_at INTEGER NOT NULL,"
                + "sender_encrypted TEXT NOT NULL,"
                + "body_encrypted TEXT NOT NULL,"
                + "match_clue_encrypted TEXT NOT NULL DEFAULT '',"
                + "expires_at INTEGER NOT NULL"
                + ")");
        database.execSQL("CREATE INDEX IF NOT EXISTS pending_read_receipts_expiry_idx "
                + "ON pending_read_receipts(expires_at)");
    }

    private static void purgeExpiredReadReceipts(SQLiteDatabase database, long now) {
        ContentValues history = new ContentValues();
        history.put(
                "detail",
                "SMTP 服务器已接受邮件 · 系统短信未标记已读（未找到可安全调用的系统动作）");
        history.put("updated_at", now);
        database.update(
                "message_history",
                history,
                "id IN (SELECT id FROM pending_read_receipts WHERE expires_at <= ?)",
                new String[]{Long.toString(now)});
        database.delete(
                "pending_read_receipts",
                "expires_at <= ?",
                new String[]{Long.toString(now)});
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

    private static boolean hasPendingKind(SQLiteDatabase database, String kind) {
        try (Cursor cursor = database.query(
                "pending_messages", new String[]{"id"}, "kind = ?",
                new String[]{kind}, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    static int coalescePendingHeartbeats(SQLiteDatabase database) {
        String keepId = null;
        try (Cursor cursor = database.query(
                "pending_messages",
                new String[]{"id"},
                "kind = ?",
                new String[]{QueueItem.KIND_HEARTBEAT},
                null,
                null,
                "created_at DESC",
                "1")) {
            if (cursor.moveToFirst()) {
                keepId = cursor.getString(0);
            }
        }
        if (keepId == null) {
            return 0;
        }
        String where = "kind = ? AND id <> ?";
        String[] args = {QueueItem.KIND_HEARTBEAT, keepId};
        database.delete(
                "message_history",
                "id IN (SELECT id FROM pending_messages WHERE " + where + ")",
                args);
        return database.delete("pending_messages", where, args);
    }

    static int trimPendingNonSms(SQLiteDatabase database) {
        List<String> overflowIds = new ArrayList<>();
        try (Cursor cursor = database.query(
                "pending_messages",
                new String[]{"id"},
                "kind <> ?",
                new String[]{QueueItem.KIND_SMS},
                null,
                null,
                "created_at DESC, id DESC")) {
            int position = 0;
            while (cursor.moveToNext()) {
                if (position++ >= MAX_NON_SMS_ROWS) {
                    overflowIds.add(cursor.getString(0));
                }
            }
        }
        for (String id : overflowIds) {
            database.delete("message_history", "id = ?", new String[]{id});
            database.delete("pending_messages", "id = ?", new String[]{id});
        }
        return overflowIds.size();
    }

    private int currentAttempts(String id) {
        try (Cursor cursor = getReadableDatabase().query(
                "pending_messages", new String[]{"attempts"}, "id = ?",
                new String[]{id}, null, null, null, "1")) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static long[] pendingUsage(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(LENGTH(sender_encrypted) "
                        + "+ LENGTH(body_encrypted) + LENGTH(match_clue_encrypted)), 0) "
                        + "FROM pending_messages",
                null)) {
            if (cursor.moveToFirst()) {
                long smsRows;
                long nonSmsRows;
                try (Cursor kindCursor = database.rawQuery(
                        "SELECT "
                                + "SUM(CASE WHEN kind = ? THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN kind <> ? THEN 1 ELSE 0 END) "
                                + "FROM pending_messages",
                        new String[]{QueueItem.KIND_SMS, QueueItem.KIND_SMS})) {
                    if (kindCursor.moveToFirst()) {
                        smsRows = kindCursor.getLong(0);
                        nonSmsRows = kindCursor.getLong(1);
                    } else {
                        smsRows = 0L;
                        nonSmsRows = 0L;
                    }
                }
                return new long[]{cursor.getLong(0), cursor.getLong(1), smsRows, nonSmsRows};
            }
            return new long[]{0L, 0L, 0L, 0L};
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

    static boolean wouldExceedKindCapacity(String kind, long smsRows, long nonSmsRows) {
        return QueueItem.KIND_SMS.equals(kind)
                ? smsRows >= MAX_SMS_ROWS
                : nonSmsRows >= MAX_NON_SMS_ROWS;
    }

    static int pendingRowCapacity() {
        return MAX_PENDING_ROWS;
    }

    static int historyRowCapacity() {
        return MAX_HISTORY_ROWS;
    }

    private static String sanitizeDetail(String detail) {
        if (detail == null) {
            return "";
        }
        String safe = detail.replace('\r', ' ').replace('\n', ' ');
        return safe.length() > 240 ? safe.substring(0, 240) : safe;
    }

    static String stableSmsId(String sender, String body, long receivedAt, int simSlot) {
        String safeSender = sender == null ? "" : sender;
        String safeBody = body == null ? "" : body;
        String input = "yanjian-sms-dedup-v1\n"
                + receivedAt + "\n" + simSlot + "\n"
                + safeSender.length() + ":" + safeSender
                + safeBody.length() + ":" + safeBody;
        byte[] digest = CryptoStore.hmac(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }
}
