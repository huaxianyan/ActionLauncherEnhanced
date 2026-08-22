package com.neko7ina.alenhanced;

import static com.neko7ina.alenhanced.TransitionMatcher.ACTIVITY_TYPE_HOME;
import static com.neko7ina.alenhanced.TransitionMatcher.ACTIVITY_TYPE_STANDARD;
import static com.neko7ina.alenhanced.TransitionMatcher.FLAG_IS_WALLPAPER;
import static com.neko7ina.alenhanced.TransitionMatcher.FLAG_SHOW_WALLPAPER;
import static com.neko7ina.alenhanced.TransitionMatcher.MODE_CLOSE;
import static com.neko7ina.alenhanced.TransitionMatcher.MODE_OPEN;
import static com.neko7ina.alenhanced.TransitionMatcher.MODE_TO_BACK;
import static com.neko7ina.alenhanced.TransitionMatcher.MODE_TO_FRONT;
import static com.neko7ina.alenhanced.TransitionMatcher.NO_TASK;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class TransitionMatcherTest {
    @Test
    public void matchesAppClosingToHomeWithOpeningWallpaper() {
        assertTrue(matches(
                change(MODE_TO_BACK, 0, ACTIVITY_TYPE_STANDARD),
                change(MODE_TO_FRONT, FLAG_SHOW_WALLPAPER, ACTIVITY_TYPE_HOME),
                change(MODE_OPEN, FLAG_IS_WALLPAPER, NO_TASK)));
    }

    @Test
    public void acceptsOpenAndCloseModes() {
        assertTrue(matches(
                change(MODE_CLOSE, 0, ACTIVITY_TYPE_STANDARD),
                change(MODE_OPEN, FLAG_SHOW_WALLPAPER, ACTIVITY_TYPE_HOME),
                change(MODE_TO_FRONT, FLAG_IS_WALLPAPER, NO_TASK)));
    }

    @Test
    public void rejectsBackTriggeredTransition() {
        assertFalse(TransitionMatcher.shouldKeepWallpaperVisible(
                MODE_TO_BACK,
                List.of(
                        change(MODE_TO_BACK, 0, ACTIVITY_TYPE_STANDARD),
                        change(MODE_TO_FRONT, FLAG_SHOW_WALLPAPER, ACTIVITY_TYPE_HOME),
                        change(MODE_OPEN, FLAG_IS_WALLPAPER, NO_TASK))));
    }

    @Test
    public void rejectsTransitionWithoutWallpaperChange() {
        assertFalse(matches(
                change(MODE_CLOSE, 0, ACTIVITY_TYPE_STANDARD),
                change(MODE_OPEN, FLAG_SHOW_WALLPAPER, ACTIVITY_TYPE_HOME)));
    }

    @Test
    public void rejectsHomeThatDoesNotRequestWallpaper() {
        assertFalse(matches(
                change(MODE_CLOSE, 0, ACTIVITY_TYPE_STANDARD),
                change(MODE_OPEN, 0, ACTIVITY_TYPE_HOME),
                change(MODE_OPEN, FLAG_IS_WALLPAPER, NO_TASK)));
    }

    @Test
    public void rejectsHomeToAppAndNonStandardTaskTransitions() {
        assertFalse(matches(
                change(MODE_TO_BACK, FLAG_SHOW_WALLPAPER, ACTIVITY_TYPE_HOME),
                change(MODE_TO_FRONT, 0, ACTIVITY_TYPE_STANDARD),
                change(MODE_OPEN, FLAG_IS_WALLPAPER, NO_TASK)));

        assertFalse(matches(
                change(MODE_CLOSE, 0, ACTIVITY_TYPE_HOME),
                change(MODE_OPEN, FLAG_SHOW_WALLPAPER, ACTIVITY_TYPE_HOME),
                change(MODE_OPEN, FLAG_IS_WALLPAPER, NO_TASK)));
    }

    private static boolean matches(TransitionMatcher.ChangeFacts... changes) {
        return TransitionMatcher.shouldKeepWallpaperVisible(MODE_OPEN, List.of(changes));
    }

    private static TransitionMatcher.ChangeFacts change(
            int mode, int flags, int activityType) {
        return new TransitionMatcher.ChangeFacts(mode, flags, activityType);
    }
}
