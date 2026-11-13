package com.neko7ina.alenhanced;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UnlockRevealCycleTest {
    @Test
    public void dreamOrAodUnlockKeepsUnlockTimingUntilUserReturnsHome() {
        // Model both delivery orders; device evidence also has repeated AOD onStart.
        for (boolean userPresentBeforeStart : new boolean[] { false, true }) {
            UnlockRevealCycle cycle = new UnlockRevealCycle();
            cycle.onDreamStarted();
            // Device failure: SCREEN_OFF arrives after DREAMING_STARTED and erased the cycle.
            cycle.onScreenOff();
            if (userPresentBeforeStart) {
                assertTrue(cycle.onUserPresent());
            }

            assertEquals(!userPresentBeforeStart, cycle.waitingForUnlock());
            if (!userPresentBeforeStart) {
                assertTrue(cycle.onUserPresent());
            }
            assertTrue(cycle.needsHomeReentry());
            // A second onStart after USER_PRESENT must still keep unlock timing.
            assertFalse(cycle.waitingForUnlock());
            assertTrue(cycle.onUserPresent());

            // Explicit Home ends the unlock-specific timing; ordinary unlock is unchanged.
            cycle.reset();
            assertFalse(cycle.needsHomeReentry());
            assertFalse(cycle.onUserPresent());

            cycle.onDreamStarted();
            cycle.onUserPresent();
            cycle.onScreenOff();
            assertFalse(cycle.needsHomeReentry());
        }
    }
}
