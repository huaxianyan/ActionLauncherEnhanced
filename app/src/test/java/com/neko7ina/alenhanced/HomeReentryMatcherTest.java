package com.neko7ina.alenhanced;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HomeReentryMatcherTest {
    @Test
    public void homeOpensDrawerInPlaceAndReturningFromAppKeepsReveal() {
        // Regression: after unlock, Home on an already resumed launcher opens its drawer.
        // AL 53.0 uses o0 && !FLAG_ACTIVITY_BROUGHT_TO_FRONT for that native action.
        assertFalse(HomeReentryMatcher.shouldRepair(true, true, false));
        assertTrue(HomeReentryMatcher.shouldRepair(true, false, false));
        assertTrue(HomeReentryMatcher.shouldRepair(true, true, true));
    }
}
