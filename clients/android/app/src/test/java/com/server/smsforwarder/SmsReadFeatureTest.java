package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class SmsReadFeatureTest {
    @Test
    public void receiptExpiryIsAnchoredToForwardSuccessTime() {
        long forwardingSucceededAt = 1_000_000L;

        assertEquals(
                forwardingSucceededAt + 10L * 60L * 1000L,
                SmsReadFeature.requestExpiry(forwardingSucceededAt));
    }

    @Test
    public void queueClearCannotContinueAfterGenerationCommitFailure() {
        assertThrows(
                IllegalStateException.class,
                () -> SmsReadFeature.requireGenerationInvalidationPersisted(false));
    }
}
