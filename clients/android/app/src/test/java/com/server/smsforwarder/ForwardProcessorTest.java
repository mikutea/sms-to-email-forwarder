package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ForwardProcessorTest {
    @Test
    public void retryDelayUsesExponentialBackoffWithCap() {
        assertEquals(30_000L, ForwardProcessor.retryDelay(1, false));
        assertEquals(60_000L, ForwardProcessor.retryDelay(2, false));
        assertEquals(6L * 60L * 60L * 1000L, ForwardProcessor.retryDelay(100, false));
    }

    @Test
    public void authenticationFailureWaitsSixHours() {
        assertEquals(6L * 60L * 60L * 1000L, ForwardProcessor.retryDelay(1, true));
    }
}
