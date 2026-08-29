package com.server.smsforwarder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class QueuePolicyTest {
    @Test
    public void acceptsItemsWhileBothCapacityLimitsHaveRoom() {
        assertFalse(QueueDatabase.wouldExceedCapacity(549L, 1024L, 2048L));
    }

    @Test
    public void rejectsAtRowLimit() {
        assertTrue(QueueDatabase.wouldExceedCapacity(550L, 0L, 1L));
    }

    @Test
    public void rejectsCiphertextGrowthPastByteBudgetAndOverflowLikeInputs() {
        assertTrue(QueueDatabase.wouldExceedCapacity(1L, 8L * 1024L * 1024L, 1L));
        assertTrue(QueueDatabase.wouldExceedCapacity(1L, Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    public void reservesQueueCapacityForRealSms() {
        assertTrue(QueueDatabase.wouldExceedKindCapacity(
                QueueItem.KIND_HEARTBEAT, 0L, 50L));
        assertTrue(QueueDatabase.wouldExceedKindCapacity(
                QueueItem.KIND_ALERT, 500L, 50L));
        assertFalse(QueueDatabase.wouldExceedKindCapacity(
                QueueItem.KIND_SMS, 499L, 50L));
        assertTrue(QueueDatabase.wouldExceedKindCapacity(
                QueueItem.KIND_SMS, 500L, 0L));
    }

    @Test
    public void historyCapacityCoversTheEntirePendingQueue() {
        assertTrue(QueueDatabase.historyRowCapacity()
                >= QueueDatabase.pendingRowCapacity());
    }

    @Test
    public void pendingStatsSeparatesSmsFromStatusTraffic() {
        PendingStats stats = new PendingStats(7, 2, 1, 3);

        assertEquals(13, stats.total);
        assertTrue(stats.compactLabel().contains("短信 7"));
        assertTrue(stats.compactLabel().contains("心跳 2"));
        assertTrue(stats.compactLabel().contains("提醒 1"));
        assertTrue(stats.compactLabel().contains("其他 3"));
    }

    @Test
    public void onlyDuplicateSmsEnqueueResultsReconcileExistingQueueWork() {
        assertTrue(ForwardScheduler.shouldReconcileAfterDuplicate(
                QueueDatabase.EnqueueResult.DUPLICATE));
        assertFalse(ForwardScheduler.shouldReconcileAfterDuplicate(
                QueueDatabase.EnqueueResult.INSERTED));
        assertFalse(ForwardScheduler.shouldReconcileAfterDuplicate(
                QueueDatabase.EnqueueResult.CAPACITY_REACHED));
    }

    @Test
    public void aStaleRetryWaitRowCannotBecomeAnExplicitHistoricalResend() {
        assertTrue(ForwardScheduler.isRetryOnlyHistoryStatus("RETRY_WAIT"));
        assertFalse(ForwardScheduler.isRetryOnlyHistoryStatus("SUCCESS"));
        assertFalse(ForwardScheduler.isRetryOnlyHistoryStatus("FAILED"));
    }
}
