package com.server.smsforwarder;

final class UiDestination {
    static final int GUARDIAN = 0;
    static final int EMAIL = 1;
    static final int RULES = 2;
    static final int HISTORY = 3;
    static final int SETTINGS = 4;
    static final int SYSTEM_GUARDIAN = 5;
    static final int MAINTENANCE = 6;
    static final int ONBOARDING = 7;

    private UiDestination() {
    }

    static int root(int destination) {
        if (destination == EMAIL || destination == SYSTEM_GUARDIAN
                || destination == MAINTENANCE || destination == ONBOARDING) {
            return SETTINGS;
        }
        return destination;
    }

    static boolean isValid(int destination) {
        return destination >= GUARDIAN && destination <= ONBOARDING;
    }
}
