package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class UiDestinationTest {
    @Test
    public void settingsChildrenKeepSettingsSelected() {
        assertEquals(UiDestination.SETTINGS, UiDestination.root(UiDestination.EMAIL));
        assertEquals(UiDestination.SETTINGS, UiDestination.root(UiDestination.SYSTEM_GUARDIAN));
        assertEquals(UiDestination.SETTINGS, UiDestination.root(UiDestination.MAINTENANCE));
        assertEquals(UiDestination.SETTINGS, UiDestination.root(UiDestination.ONBOARDING));
    }

    @Test
    public void primaryDestinationsRemainSelected() {
        assertEquals(UiDestination.GUARDIAN, UiDestination.root(UiDestination.GUARDIAN));
        assertEquals(UiDestination.RULES, UiDestination.root(UiDestination.RULES));
        assertEquals(UiDestination.HISTORY, UiDestination.root(UiDestination.HISTORY));
    }

    @Test
    public void validatesDestinationBounds() {
        assertTrue(UiDestination.isValid(UiDestination.ONBOARDING));
        assertFalse(UiDestination.isValid(-1));
        assertFalse(UiDestination.isValid(UiDestination.ONBOARDING + 1));
    }
}
