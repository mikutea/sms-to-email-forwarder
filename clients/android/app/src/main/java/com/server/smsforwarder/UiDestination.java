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
    static final int HEARTBEAT = 8;
    static final int PRIVACY = 9;
    static final int CONFIG_TRANSFER = 10;
    static final int PLATFORM_CAPABILITIES = 11;
    static final int OPEN_SOURCE_LICENSES = 12;

    private UiDestination() {
    }

    static int root(int destination) {
        if (destination == EMAIL || destination == SYSTEM_GUARDIAN
                || destination == MAINTENANCE || destination == HEARTBEAT
                || destination == PRIVACY || destination == CONFIG_TRANSFER
                || destination == PLATFORM_CAPABILITIES
                || destination == OPEN_SOURCE_LICENSES) {
            return SETTINGS;
        }
        if (destination == ONBOARDING) {
            return GUARDIAN;
        }
        return destination;
    }

    static boolean isValid(int destination) {
        return destination >= GUARDIAN && destination <= OPEN_SOURCE_LICENSES;
    }
}
