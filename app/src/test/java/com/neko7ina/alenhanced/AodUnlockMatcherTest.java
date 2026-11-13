package com.neko7ina.alenhanced;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AodUnlockMatcherTest {
    @Test
    public void directAodFingerprintUnlockAnimatesOnlyTheUnlockedActionLauncherWake() {
        // Captured AOD samples use both steady (1) and pulsing (2) fingerprint modes.
        assertTrue(AodUnlockMatcher.shouldUseUnlockAnimation(
                11, true, true, true, 1, true, 17, true));
        assertTrue(AodUnlockMatcher.shouldUseUnlockAnimation(
                11, true, true, true, 2, true, 17, true));
        // Captured no-animation case: AOD lift wake, then successful fingerprint (mode 6).
        assertTrue(AodUnlockMatcher.shouldUseUnlockAnimation(
                11, true, true, true, 6, true, 16, true));
        // Double-tap wake remains on the user's already-working native path.
        assertFalse(AodUnlockMatcher.shouldUseUnlockAnimation(
                11, true, true, true, 6, true, 15, true));
        // The same mode without an observed AOD cycle is outside the approved scope.
        assertFalse(AodUnlockMatcher.shouldUseUnlockAnimation(
                11, true, true, true, 1, true, 17, false));

        // Approved scope excludes other launchers/tasks and an authenticated-but-locked device.
        assertFalse(AodUnlockMatcher.shouldUseUnlockAnimation(
                11, false, true, true, 2, true, 17, true));
        assertFalse(AodUnlockMatcher.shouldUseUnlockAnimation(
                11, true, true, false, 2, true, 17, true));

        // The user's working double-tap/face path and ordinary Home are separate inputs.
        assertFalse(AodUnlockMatcher.shouldUseUnlockAnimation(
                11, true, true, true, 2, true, 15, true));
        assertFalse(AodUnlockMatcher.shouldUseUnlockAnimation(
                11, true, true, true, 7, false, 15, true));
        assertFalse(AodUnlockMatcher.shouldUseUnlockAnimation(
                1, true, true, true, 2, true, 17, true));
    }
}
