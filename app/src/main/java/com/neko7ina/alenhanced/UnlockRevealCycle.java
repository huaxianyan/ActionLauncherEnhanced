package com.neko7ina.alenhanced;

/** Main-thread dream/AOD unlock state, separate from an explicit Home re-entry. */
final class UnlockRevealCycle {
    private enum Phase { NONE, DREAMING, UNLOCKED }

    private Phase phase = Phase.NONE;

    void onDreamStarted() {
        // Both charging screen savers and AOD send the native dream-start signal.
        phase = Phase.DREAMING;
    }

    void onScreenOff() {
        // DREAMING_STARTED can arrive before the SCREEN_OFF for this same sleep.
        if (phase == Phase.UNLOCKED) {
            reset();
        }
    }

    void reset() {
        phase = Phase.NONE;
    }

    boolean waitingForUnlock() {
        return phase == Phase.DREAMING;
    }

    boolean onUserPresent() {
        if (phase == Phase.NONE) {
            return false;
        }
        phase = Phase.UNLOCKED;
        return true;
    }

    boolean needsHomeReentry() {
        return phase != Phase.NONE;
    }

}
