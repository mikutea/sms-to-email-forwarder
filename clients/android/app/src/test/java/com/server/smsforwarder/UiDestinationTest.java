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
        assertEquals(UiDestination.SETTINGS, UiDestination.root(UiDestination.HEARTBEAT));
        assertEquals(UiDestination.SETTINGS, UiDestination.root(UiDestination.PRIVACY));
        assertEquals(UiDestination.SETTINGS, UiDestination.root(UiDestination.CONFIG_TRANSFER));
        assertEquals(UiDestination.SETTINGS, UiDestination.root(UiDestination.PLATFORM_CAPABILITIES));
        assertEquals(UiDestination.SETTINGS, UiDestination.root(UiDestination.OPEN_SOURCE_LICENSES));
    }

    @Test
    public void onboardingKeepsGuardianSelected() {
        assertEquals(UiDestination.GUARDIAN, UiDestination.root(UiDestination.ONBOARDING));
    }

    @Test
    public void primaryDestinationsRemainSelected() {
        assertEquals(UiDestination.GUARDIAN, UiDestination.root(UiDestination.GUARDIAN));
        assertEquals(UiDestination.RULES, UiDestination.root(UiDestination.RULES));
        assertEquals(UiDestination.HISTORY, UiDestination.root(UiDestination.HISTORY));
    }

    @Test
    public void validatesDestinationBounds() {
        assertTrue(UiDestination.isValid(UiDestination.OPEN_SOURCE_LICENSES));
        assertFalse(UiDestination.isValid(-1));
        assertFalse(UiDestination.isValid(UiDestination.OPEN_SOURCE_LICENSES + 1));
    }

    @Test
    public void settingsActionsUseDistinctDestinations() {
        int[] destinations = {
                UiDestination.EMAIL,
                UiDestination.RULES,
                UiDestination.SYSTEM_GUARDIAN,
                UiDestination.HEARTBEAT,
                UiDestination.PRIVACY,
                UiDestination.CONFIG_TRANSFER,
                UiDestination.MAINTENANCE,
                UiDestination.PLATFORM_CAPABILITIES,
                UiDestination.OPEN_SOURCE_LICENSES
        };
        for (int left = 0; left < destinations.length; left++) {
            for (int right = left + 1; right < destinations.length; right++) {
                assertFalse("settings routes must remain distinct",
                        destinations[left] == destinations[right]);
            }
        }
    }
}
