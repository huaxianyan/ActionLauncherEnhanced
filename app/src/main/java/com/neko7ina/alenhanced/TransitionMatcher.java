package com.neko7ina.alenhanced;

import java.util.List;

final class TransitionMatcher {
    static final int MODE_OPEN = 1;
    static final int MODE_CLOSE = 2;
    static final int MODE_TO_FRONT = 3;
    static final int MODE_TO_BACK = 4;

    static final int FLAG_SHOW_WALLPAPER = 1;
    static final int FLAG_IS_WALLPAPER = 1 << 1;

    static final int ACTIVITY_TYPE_STANDARD = 1;
    static final int ACTIVITY_TYPE_HOME = 2;
    static final int NO_TASK = -1;

    private TransitionMatcher() {
    }

    static boolean shouldKeepWallpaperVisible(int transitionType, List<ChangeFacts> changes) {
        if (transitionType != MODE_OPEN) {
            return false;
        }

        boolean openingWallpaper = false;
        boolean openingHomeWithWallpaper = false;
        boolean closingStandardTask = false;

        for (ChangeFacts change : changes) {
            if (isOpening(change.mode)
                    && hasFlag(change.flags, FLAG_IS_WALLPAPER)) {
                openingWallpaper = true;
            }
            if (isOpening(change.mode)
                    && change.activityType == ACTIVITY_TYPE_HOME
                    && hasFlag(change.flags, FLAG_SHOW_WALLPAPER)) {
                openingHomeWithWallpaper = true;
            }
            if (isClosing(change.mode)
                    && change.activityType == ACTIVITY_TYPE_STANDARD) {
                closingStandardTask = true;
            }
        }

        return openingWallpaper && openingHomeWithWallpaper && closingStandardTask;
    }

    static boolean isOpening(int mode) {
        return mode == MODE_OPEN || mode == MODE_TO_FRONT;
    }

    private static boolean isClosing(int mode) {
        return mode == MODE_CLOSE || mode == MODE_TO_BACK;
    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }

    static final class ChangeFacts {
        final int mode;
        final int flags;
        final int activityType;

        ChangeFacts(int mode, int flags, int activityType) {
            this.mode = mode;
            this.flags = flags;
            this.activityType = activityType;
        }
    }
}
