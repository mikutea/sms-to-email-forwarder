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
        assertEquals(UiDestination.SETTINGS, UiDestination.root(UiDestination.ABOUT));
    }

    @Test
    public void onboardingKeepsGuardianSelected() {
        assertEquals(UiDestination.GUARDIAN, UiDestination.root(UiDestination.ONBOARDING));
        assertEquals(UiDestination.GUARDIAN, UiDestination.root(UiDestination.LOCKSCREEN_TEST));
    }

    @Test
    public void primaryDestinationsRemainSelected() {
        assertEquals(UiDestination.GUARDIAN, UiDestination.root(UiDestination.GUARDIAN));
        assertEquals(UiDestination.RULES, UiDestination.root(UiDestination.RULES));
        assertEquals(UiDestination.HISTORY, UiDestination.root(UiDestination.HISTORY));
    }

    @Test
    public void validatesDestinationBounds() {
        assertTrue(UiDestination.isValid(UiDestination.ABOUT));
        assertFalse(UiDestination.isValid(-1));
        assertFalse(UiDestination.isValid(UiDestination.ABOUT + 1));
    }

    @Test
    public void nestedPagesReturnToTheirVisibleParent() {
        assertEquals(UiDestination.SETTINGS, UiDestination.parent(UiDestination.EMAIL));
        assertEquals(UiDestination.SETTINGS, UiDestination.parent(UiDestination.ABOUT));
        assertEquals(UiDestination.ABOUT, UiDestination.parent(UiDestination.PLATFORM_CAPABILITIES));
        assertEquals(UiDestination.ABOUT, UiDestination.parent(UiDestination.OPEN_SOURCE_LICENSES));
        assertEquals(UiDestination.GUARDIAN, UiDestination.parent(UiDestination.LOCKSCREEN_TEST));
        assertEquals(-1, UiDestination.parent(UiDestination.SETTINGS));
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
                UiDestination.OPEN_SOURCE_LICENSES,
                UiDestination.LOCKSCREEN_TEST,
                UiDestination.ABOUT
        };
        for (int left = 0; left < destinations.length; left++) {
            for (int right = left + 1; right < destinations.length; right++) {
                assertFalse("settings routes must remain distinct",
                        destinations[left] == destinations[right]);
            }
        }
    }
}
