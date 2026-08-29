package com.server.smsforwarder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SmsReadPolicyTest {
    @Test
    public void androidSixRetainsTheFirstDisabledListenerBinding() {
        assertTrue(SmsNotificationListener.retainDisabledBinding(23));
        assertFalse(SmsNotificationListener.retainDisabledBinding(24));
        assertFalse(SmsNotificationListener.retainDisabledBinding(35));
    }

    @Test
    public void listenerDisconnectDisablesOnlyAfterSystemAccessWasRevoked() {
        assertTrue(SmsNotificationListener.shouldDisableAfterDisconnect(true, false));
        assertFalse(SmsNotificationListener.shouldDisableAfterDisconnect(true, true));
        assertFalse(SmsNotificationListener.shouldDisableAfterDisconnect(false, false));
    }

    @Test
    public void onlyARealLocalSmsReceiptIsEligibleForReadLinkage() {
        QueueItem realReceipt = new QueueItem(
                "real", QueueItem.KIND_SMS, 100L, "10000", "示例", 0, 0, 0, "示例线索", 200L);
        QueueItem manualResend = new QueueItem(
                "resend", QueueItem.KIND_SMS, 100L, "10000", "示例", 0, 0, 0, "", 0L);

        assertTrue(SmsReadFeature.isEligibleForReadLink(realReceipt));
        assertFalse(SmsReadFeature.isEligibleForReadLink(manualResend));
    }

    @Test
    public void finalDispatchRequiresPreferencePermissionAndReceipt() {
        assertTrue(SmsReadFeature.canDispatchReadAction(true, true, true));
        assertFalse(SmsReadFeature.canDispatchReadAction(false, true, true));
        assertFalse(SmsReadFeature.canDispatchReadAction(true, false, true));
        assertFalse(SmsReadFeature.canDispatchReadAction(true, true, false));
    }
}
