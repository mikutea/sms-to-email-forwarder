package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class QueueRecoveryAndroidTest {
    private QueueDatabase database;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = QueueDatabase.get(context);
        database.clear();
        database.clearHistory();
        SmsReadFeature.setEnabled(context, false);
        ForwardScheduler.clearUrgentWorkReservation(context);
        ForwardScheduler.cancelEnqueueRecovery(context);
    }

    @After
    public void tearDown() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkManager.getInstance(context)
                .cancelUniqueWork(ForwardScheduler.RETRY_WAKEUP_WORK_NAME)
                .getResult()
                .get(5L, TimeUnit.SECONDS);
        WorkManager.getInstance(context)
                .cancelUniqueWork(ForwardScheduler.URGENT_WORK_NAME)
                .getResult()
                .get(5L, TimeUnit.SECONDS);
        ForwardScheduler.clearUrgentWorkReservation(context);
        ForwardScheduler.cancelEnqueueRecovery(context);
        database.clear();
        database.clearHistory();
    }

    @Test
    public void forceRetryReleasesBackoffWithoutTouchingActiveLease() {
        long receivedAt = System.currentTimeMillis() - 60_000L;
        assertEquals(QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms("10086", "测试队列恢复", receivedAt, 0));
        List<QueueItem> claimed = database.claimReady(System.currentTimeMillis(), 1);
        assertEquals(1, claimed.size());
        QueueItem item = claimed.get(0);
        database.markRetry(item.id, 8, System.currentTimeMillis() + 6L * 60L * 60L * 1000L);

        assertEquals(1, database.forceReadyNow(System.currentTimeMillis()));
        assertEquals(1, database.claimReady(System.currentTimeMillis() + 1L, 1).size());
    }

    @Test
    public void repeatedHeartbeatIsCoalescedAndStatsStayTyped() {
        assertTrue(database.enqueueStatus(QueueItem.KIND_HEARTBEAT, "心跳一", "状态一"));
        assertTrue(database.enqueueStatus(QueueItem.KIND_HEARTBEAT, "心跳二", "状态二"));
        assertTrue(database.enqueueStatus(QueueItem.KIND_ALERT, "低电量", "状态三"));
        assertEquals(QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms("10010", "测试分类", System.currentTimeMillis(), 0));

        PendingStats stats = database.pendingStats();
        assertEquals(1, stats.sms);
        assertEquals(1, stats.heartbeat);
        assertEquals(1, stats.alert);
        assertEquals(3, stats.total);

        List<QueueItem> ready = database.claimReady(System.currentTimeMillis() + 1_000L, 3);
        QueueItem heartbeat = null;
        for (QueueItem item : ready) {
            if (QueueItem.KIND_HEARTBEAT.equals(item.kind)) {
                heartbeat = item;
                break;
            }
        }
        assertTrue(heartbeat != null);
        assertEquals("心跳二", heartbeat.sender);
        assertEquals("状态二", heartbeat.body);
    }

    @Test
    public void readReceiptRequestRoundTripsEncryptedLocalData() {
        long carrierTimestamp = System.currentTimeMillis() - 60L * 60L * 1000L;
        long localReceivedAt = System.currentTimeMillis();
        long expiresAt = System.currentTimeMillis() + 60_000L;
        QueueItem item = new QueueItem(
                "read-receipt-test", QueueItem.KIND_SMS, carrierTimestamp,
                "10000", "隐私模式转换后的邮件正文", 0, 0, 0,
                SmsNotificationMatcher.bodyMatchClue("仅用于设备测试的虚构原始短信"),
                localReceivedAt);
        database.enqueueReadReceipt(item, expiresAt);

        List<ReadReceiptRequest> requests = database.readReceiptRequests(System.currentTimeMillis());
        assertEquals(1, requests.size());
        assertEquals(item.id, requests.get(0).id);
        assertEquals(item.sender, requests.get(0).sender);
        assertEquals(item.bodyMatchClue, requests.get(0).bodyMatchClue);
        assertEquals(localReceivedAt, requests.get(0).receivedAt);
        assertEquals(expiresAt, database.earliestReadReceiptExpiry());
        database.removeReadReceipt(item.id);
        assertFalse(database.readReceiptRequests(System.currentTimeMillis()).iterator().hasNext());
        assertEquals(0L, database.earliestReadReceiptExpiry());
    }

    @Test
    public void disablingReadLinkErasesOriginalBodyCluesStillInTheQueue() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        SmsReadFeature.setEnabled(context, true);
        assertEquals(QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms(
                        "10007",
                        "隐私模式转换后的正文",
                        SmsNotificationMatcher.bodyMatchClue("虚构的原始短信线索用于关闭测试"),
                        now,
                        now,
                        0));

        SmsReadFeature.setEnabled(context, false);
        assertEquals(QueueDatabase.EnqueueResult.INSERTED,
                SmsReadFeature.enqueueIncomingSms(
                        context,
                        database,
                        "10011",
                        "关闭后的隐私模式正文",
                        "关闭后到达的虚构原始短信不得保留线索",
                        now + 1L,
                        now + 1L,
                        0));

        List<QueueItem> items = database.claimReady(now + 1_000L, 2);
        assertEquals(2, items.size());
        assertEquals("", items.get(0).bodyMatchClue);
        assertEquals("", items.get(1).bodyMatchClue);
    }

    @Test
    public void disablingAndReenablingInvalidatesAnAlreadyClaimedBodyClue() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        SmsReadFeature.setEnabled(context, true);
        assertEquals(
                QueueDatabase.EnqueueResult.INSERTED,
                SmsReadFeature.enqueueIncomingSms(
                        context,
                        database,
                        "10012",
                        "隐私模式转换后的正文",
                        "虚构的在途原始短信线索用于授权代次测试",
                        now,
                        now,
                        0));
        QueueItem claimed = database.claimReady(now + 1_000L, 1).get(0);
        assertTrue(SmsReadFeature.hasCurrentReadLinkGeneration(context, claimed));

        SmsReadFeature.setEnabled(context, false);
        SmsReadFeature.setEnabled(context, true);

        assertFalse(SmsReadFeature.hasCurrentReadLinkGeneration(context, claimed));
    }

    @Test
    public void manualHistoryResendCannotMasqueradeAsANewLocalSmsReceipt() {
        long receivedAt = System.currentTimeMillis() - 60L * 60L * 1000L;
        HistoryItem history = new HistoryItem(
                "manual-resend", receivedAt, "10008", "虚构的手动重发正文", 0,
                "SUCCESS", 0, "SMTP 服务器已接受邮件");
        database.enqueueReadReceipt(
                new QueueItem(
                        history.id, QueueItem.KIND_SMS, receivedAt,
                        history.sender, history.body, history.simSlot, 0, 0,
                        SmsNotificationMatcher.bodyMatchClue("虚构的旧投递匹配线索"),
                        System.currentTimeMillis()),
                System.currentTimeMillis() + 60_000L);
        assertEquals(1, database.readReceiptRequests(System.currentTimeMillis()).size());

        assertTrue(database.requeue(history));

        assertTrue(database.readReceiptRequests(System.currentTimeMillis()).isEmpty());
        QueueItem item = database.claimReady(System.currentTimeMillis() + 1_000L, 1).get(0);
        assertEquals(0L, item.localReceivedAt);
        assertFalse(SmsReadFeature.isEligibleForReadLink(item));
    }

    @Test
    public void agePruningKeepsHistoryForAStillPendingMessage() {
        long now = System.currentTimeMillis();
        String sender = "10009";
        String body = "虚构的长期待发历史保留测试";
        assertEquals(QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms(sender, body, now, 0));
        String id = QueueDatabase.stableSmsId(sender, body, now, 0);
        ContentValues old = new ContentValues();
        old.put("updated_at", now - 100L * 24L * 60L * 60L * 1000L);
        database.getWritableDatabase().update(
                "message_history", old, "id = ?", new String[]{id});

        database.recordFiltered(
                "newer-filtered-record", now, "10010", "虚构的新记录", 0, "测试过滤");

        try (android.database.Cursor cursor = database.getReadableDatabase().query(
                "message_history", new String[]{"id"}, "id = ?", new String[]{id},
                null, null, null, "1")) {
            assertTrue(cursor.moveToFirst());
        }
    }

    @Test
    public void claimLeaseProtectsAFullSingleMessageSmtpWindow() {
        long now = System.currentTimeMillis();
        assertEquals(QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms("10005", "虚构的租约测试", now, 0));
        long claimedAt = System.currentTimeMillis() + 1_000L;
        assertEquals(1, database.claimReady(claimedAt, 1).size());

        assertTrue(database.claimReady(claimedAt + 2L * 60L * 1000L, 1).isEmpty());
        assertEquals(1, database.claimReady(
                claimedAt + 5L * 60L * 1000L + 1L, 1).size());
    }

    @Test
    public void urgentRetryUsesADedicatedWakeupInsteadOfTheNormalSmtpChain() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        assertEquals(QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms("10006", "虚构的紧急重试唤醒测试", now, 0));
        QueueItem item = database.claimReady(now + 1_000L, 1).get(0);
        database.markRetry(item.id, 1, now + 60_000L);

        ForwardScheduler.scheduleUrgentRetryFromQueue(context);

        List<WorkInfo> work = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(ForwardScheduler.RETRY_WAKEUP_WORK_NAME)
                .get(5L, TimeUnit.SECONDS);
        assertFalse(work.isEmpty());
        assertFalse(work.get(0).getState().isFinished());
    }

    @Test
    public void urgentRequestGateCoalescesABurstIntoOnePendingSuccessor() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ForwardScheduler.clearUrgentWorkReservation(context);

        String firstReservation = ForwardScheduler.reserveUrgentWork(context);
        assertFalse(firstReservation.isEmpty());
        for (int i = 0; i < 500; i++) {
            assertEquals("", ForwardScheduler.reserveUrgentWork(context));
        }
        ForwardScheduler.acknowledgeUrgentWork(context, "another-process-token");
        assertEquals("", ForwardScheduler.reserveUrgentWork(context));
        ForwardScheduler.acknowledgeUrgentWork(context, firstReservation);
        String secondReservation = ForwardScheduler.reserveUrgentWork(context);
        assertFalse(secondReservation.isEmpty());
        assertFalse(firstReservation.equals(secondReservation));
        ForwardScheduler.clearUrgentWorkReservation(context);
    }

    @Test
    public void urgentLaneClaimsSmsBeforeOlderStatusRows() {
        long now = System.currentTimeMillis();
        assertTrue(database.enqueueStatus(QueueItem.KIND_HEARTBEAT, "旧心跳", "旧状态"));
        assertTrue(database.enqueueStatus(QueueItem.KIND_ALERT, "旧提醒", "旧状态"));
        assertEquals(
                QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms("10013", "虚构的紧急短信优先级测试", now + 2L, 0));

        QueueItem claimed = database.claimReady(now + 1_000L, 1, true).get(0);

        assertEquals(QueueItem.KIND_SMS, claimed.kind);
    }

    @Test
    public void clearingQueueInvalidatesAnAlreadyClaimedReadLinkGeneration() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        SmsReadFeature.setEnabled(context, true);
        assertEquals(
                QueueDatabase.EnqueueResult.INSERTED,
                SmsReadFeature.enqueueIncomingSms(
                        context,
                        database,
                        "10014",
                        "隐私模式转换后的正文",
                        "虚构的清空队列在途线索测试",
                        now,
                        now,
                        0));
        QueueItem claimed = database.claimReady(now + 1_000L, 1).get(0);
        assertTrue(SmsReadFeature.hasCurrentReadLinkGeneration(context, claimed));

        database.clear();

        assertFalse(SmsReadFeature.hasCurrentReadLinkGeneration(context, claimed));
        assertEquals(0, database.count());
    }

    @Test
    public void failedEnqueueRecoveryUsesAPrivateExplicitAlarm() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        ForwardScheduler.scheduleEnqueueRecovery(context);
        assertTrue(ForwardScheduler.hasEnqueueRecovery(context));
        ForwardScheduler.cancelEnqueueRecovery(context);
        assertFalse(ForwardScheduler.hasEnqueueRecovery(context));
    }

    @Test
    public void successfulRetryWakeupDoesNotCancelAnExistingRecoveryAlarm() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        assertEquals(
                QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms("10015", "虚构的恢复闹钟竞态测试", now, 0));
        QueueItem item = database.claimReady(now + 1_000L, 1).get(0);
        database.markRetry(item.id, 1, now + 60_000L);
        ForwardScheduler.scheduleEnqueueRecovery(context);

        ForwardScheduler.scheduleUrgentRetryFromQueue(context);
        List<WorkInfo> work = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(ForwardScheduler.RETRY_WAKEUP_WORK_NAME)
                .get(5L, TimeUnit.SECONDS);

        assertFalse(work.isEmpty());
        assertTrue(ForwardScheduler.hasEnqueueRecovery(context));
    }

    @Test
    public void migrationCoalescesAnExistingHeartbeatBacklog() {
        database.clear();
        SQLiteDatabase writable = database.getWritableDatabase();
        insertLegacyHeartbeat(writable, "legacy-heartbeat-1", 1L);
        insertLegacyHeartbeat(writable, "legacy-heartbeat-2", 2L);
        insertLegacyHeartbeat(writable, "legacy-heartbeat-3", 3L);

        assertEquals(2, QueueDatabase.coalescePendingHeartbeats(writable));
        assertEquals(1, database.pendingStats().heartbeat);
    }

    @Test
    public void migrationTrimsLegacyNonSmsBacklogToItsReservedCapacity() {
        database.clear();
        SQLiteDatabase writable = database.getWritableDatabase();
        for (int position = 0; position < 75; position++) {
            insertLegacyNonSms(
                    writable,
                    "legacy-alert-" + position,
                    QueueItem.KIND_ALERT,
                    position);
        }

        assertEquals(25, QueueDatabase.trimPendingNonSms(writable));
        assertEquals(50, database.pendingStats().alert);
        assertEquals(50, database.pendingStats().total);
    }

    @Test
    public void expiredReadReceiptLeavesAnExplicitHistoryOutcome() {
        long now = System.currentTimeMillis();
        assertEquals(QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms("10001", "虚构的已读联动超时测试", now, 0));
        QueueItem item = database.claimReady(System.currentTimeMillis() + 1_000L, 1).get(0);
        database.markSuccess(item.id, 0, "SMTP 服务器已接受邮件");
        database.remove(item.id);
        database.enqueueReadReceipt(item, now - 1L);

        assertEquals(1, database.expireReadReceipts(now));
        assertTrue(database.recentHistory(5).get(0).detail.contains("未标记已读"));
        assertFalse(database.readReceiptRequests(now).iterator().hasNext());
    }

    @Test
    public void canceledReadReceiptDoesNotLeaveHistoryPendingForever() {
        long now = System.currentTimeMillis();
        assertEquals(QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms("10002", "虚构的关闭已读联动测试", now, 0));
        QueueItem item = database.claimReady(System.currentTimeMillis() + 1_000L, 1).get(0);
        database.markSuccess(item.id, 0, "SMTP 服务器已接受邮件 · 正在请求系统短信标记已读");
        database.remove(item.id);
        database.enqueueReadReceipt(item, now + 60_000L);

        database.cancelReadReceipts("SMTP 服务器已接受邮件 · 已读联动已关闭");

        assertTrue(database.recentHistory(5).get(0).detail.contains("已读联动已关闭"));
        assertFalse(database.readReceiptRequests(now).iterator().hasNext());
    }

    @Test
    public void clearingHistoryAlsoPurgesPendingReadReceiptData() {
        long now = System.currentTimeMillis();
        QueueItem item = new QueueItem(
                "history-clear-read-receipt", QueueItem.KIND_SMS, now,
                "10003", "虚构的清空历史测试", 0, 0);
        database.enqueueReadReceipt(item, now + 60_000L);

        database.clearHistory();

        assertFalse(database.readReceiptRequests(now).iterator().hasNext());
        assertTrue(database.recentHistory(5).isEmpty());
    }

    @Test
    public void clearingHistoryUsesTheReadLinkOperationLock() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        Thread clearThread = new Thread(() -> {
            started.countDown();
            database.clearHistory();
            finished.countDown();
        });

        synchronized (SmsReadFeature.operationLock()) {
            clearThread.start();
            assertTrue(started.await(2L, TimeUnit.SECONDS));
            assertFalse(finished.await(200L, TimeUnit.MILLISECONDS));
        }
        assertTrue(finished.await(2L, TimeUnit.SECONDS));
        clearThread.join(2_000L);
    }

    @Test
    public void readReceiptExpirySchedulesPersistentCleanupWork() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        database.enqueueReadReceipt(
                new QueueItem(
                        "durable-cleanup", QueueItem.KIND_SMS, now,
                        "10004", "虚构的持久清理测试", 0, 0),
                now + 60_000L);

        ReadReceiptCleanupWorker.schedule(context);

        List<WorkInfo> work = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(ReadReceiptCleanupWorker.WORK_NAME)
                .get(5L, TimeUnit.SECONDS);
        assertFalse(work.isEmpty());
        assertFalse(work.get(0).getState().isFinished());
        WorkManager.getInstance(context)
                .cancelUniqueWork(ReadReceiptCleanupWorker.WORK_NAME)
                .getResult()
                .get(5L, TimeUnit.SECONDS);
    }

    @Test
    public void failedReadReceiptCleanupSchedulingClearsTemporaryMatchingData() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        assertEquals(QueueDatabase.EnqueueResult.INSERTED,
                database.enqueueSms("10016", "虚构的清理调度失败测试", now, 0));
        QueueItem item = database.claimReady(now + 1_000L, 1).get(0);
        database.markSuccess(item.id, 0, "SMTP 服务器已接受邮件 · 正在请求系统短信标记已读");
        database.remove(item.id);
        database.enqueueReadReceipt(item, now + 60_000L);

        ReadReceiptCleanupWorker.handleSchedulingFailure(context);

        assertFalse(database.hasReadReceipt(item.id));
        assertTrue(database.recentHistory(5).get(0).detail.contains("清理任务调度失败"));
    }

    @Test
    public void notificationListenerIsPrivateAndSystemPermissionProtected() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ComponentName component = SmsReadFeature.listenerComponent(context);
        ServiceInfo info = context.getPackageManager().getServiceInfo(
                component, PackageManager.GET_META_DATA);

        assertFalse(info.exported);
        assertEquals(
                "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                info.permission);
        assertEquals(context.getPackageName(), component.getPackageName());
    }

    private static void insertLegacyHeartbeat(
            SQLiteDatabase database, String id, long createdAt) {
        insertLegacyNonSms(database, id, QueueItem.KIND_HEARTBEAT, createdAt);
    }

    private static void insertLegacyNonSms(
            SQLiteDatabase database, String id, String kind, long createdAt) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("kind", kind);
        values.put("received_at", createdAt);
        values.put("sender_encrypted", "");
        values.put("body_encrypted", "");
        values.put("sim_slot", -1);
        values.put("attempts", 0);
        values.put("next_attempt_at", createdAt);
        values.put("lease_until", 0L);
        values.put("delivered_mask", 0);
        values.put("created_at", createdAt);
        database.insertOrThrow("pending_messages", null, values);
    }
}
