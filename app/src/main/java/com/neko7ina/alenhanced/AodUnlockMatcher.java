package com.neko7ina.alenhanced;

/** Facts from the verified Android 17 AOD fingerprint path, not authentication decisions. */
final class AodUnlockMatcher {
    static final int TRANSIT_WAKE = 11;
    private static final int WAKE_AND_UNLOCK = 1;
    private static final int WAKE_AND_UNLOCK_PULSING = 2;
    private static final int WAKE_AND_UNLOCK_FROM_DREAM = 6;
    private static final int WAKE_REASON_LIFT = 16;
    private static final int WAKE_REASON_BIOMETRIC = 17;

    private AodUnlockMatcher() {
    }

    static boolean shouldUseUnlockAnimation(
            int transitionType, boolean onlyTargetHome, boolean changedInteractive,
            boolean unlocked, int biometricMode, boolean fingerprint, int wakeReason,
            boolean fromAod) {
        boolean directFingerprintWake = wakeReason == WAKE_REASON_BIOMETRIC
                && (biometricMode == WAKE_AND_UNLOCK
                        || biometricMode == WAKE_AND_UNLOCK_PULSING);
        boolean liftThenFingerprint = wakeReason == WAKE_REASON_LIFT
                && biometricMode == WAKE_AND_UNLOCK_FROM_DREAM;
        return fromAod && transitionType == TRANSIT_WAKE
                && onlyTargetHome
                && changedInteractive
                && unlocked
                && fingerprint
                && (directFingerprintWake || liftThenFingerprint);
    }
}
