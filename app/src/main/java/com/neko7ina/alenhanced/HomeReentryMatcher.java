package com.neko7ina.alenhanced;

/** Mirrors AL 53.0's distinction between returning Home and an in-place Home action. */
final class HomeReentryMatcher {
    static boolean shouldRepair(boolean homeIntent, boolean alreadyOnHome, boolean broughtToFront) {
        return homeIntent && (!alreadyOnHome || broughtToFront);
    }
}
