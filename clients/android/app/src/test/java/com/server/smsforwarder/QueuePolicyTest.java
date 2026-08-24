package com.server.smsforwarder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class QueuePolicyTest {
    @Test
    public void acceptsItemsWhileBothCapacityLimitsHaveRoom() {
        assertFalse(QueueDatabase.wouldExceedCapacity(499L, 1024L, 2048L));
    }

    @Test
    public void rejectsAtRowLimit() {
        assertTrue(QueueDatabase.wouldExceedCapacity(500L, 0L, 1L));
    }

    @Test
    public void rejectsCiphertextGrowthPastByteBudgetAndOverflowLikeInputs() {
        assertTrue(QueueDatabase.wouldExceedCapacity(1L, 8L * 1024L * 1024L, 1L));
        assertTrue(QueueDatabase.wouldExceedCapacity(1L, Long.MAX_VALUE, Long.MAX_VALUE));
    }
}
