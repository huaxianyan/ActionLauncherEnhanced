package com.neko7ina.alenhanced;

/** Identifies the next wake from an observed non-interactive dream (AOD). */
final class AodWakeCycle {
    private enum Phase { NONE, AOD, WAKING_FROM_AOD }

    private Phase phase = Phase.NONE;

    synchronized void onDreamStarted(boolean interactive) {
        phase = interactive ? Phase.NONE : Phase.AOD;
    }

    synchronized void onScreenOff() {
        // Keep a new AOD start even if its SCREEN_OFF is delivered later.
        if (phase == Phase.WAKING_FROM_AOD) {
            phase = Phase.NONE;
        }
    }

    synchronized void onScreenOn() {
        if (phase == Phase.AOD) {
            phase = Phase.WAKING_FROM_AOD;
        }
    }

    synchronized boolean consumeWake() {
        boolean fromAod = phase != Phase.NONE;
        phase = Phase.NONE;
        return fromAod;
    }
}
