package com.neko7ina.alenhanced;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AodWakeCycleTest {
    @Test
    public void aodStartBeforeScreenOffStillIdentifiesItsWake() {
        AodWakeCycle cycle = new AodWakeCycle();
        cycle.onDreamStarted(false);
        cycle.onScreenOff();
        cycle.onScreenOn();
        assertTrue(cycle.consumeWake());
        assertFalse(cycle.consumeWake());

        // A non-target wake may be handled outside DefaultTransitionHandler.
        // Its next sleep still ends the old AOD evidence.
        cycle.onDreamStarted(false);
        cycle.onScreenOn();
        cycle.onScreenOff();
        assertFalse(cycle.consumeWake());

        cycle.onDreamStarted(true);
        assertFalse(cycle.consumeWake());
    }
}
