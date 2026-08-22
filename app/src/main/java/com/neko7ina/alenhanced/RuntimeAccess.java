package com.neko7ina.alenhanced;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class RuntimeAccess {
    private final Method getType;
    private final Method getChanges;
    private final Method getMode;
    private final Method getFlags;
    private final Method getTaskInfo;
    private final Method getLeash;
    private final Field topActivityType;
    private final Method show;
    private final Method setAlpha;

    private RuntimeAccess(
            Method getType,
            Method getChanges,
            Method getMode,
            Method getFlags,
            Method getTaskInfo,
            Method getLeash,
            Field topActivityType,
            Method show,
            Method setAlpha) {
        this.getType = getType;
        this.getChanges = getChanges;
        this.getMode = getMode;
        this.getFlags = getFlags;
        this.getTaskInfo = getTaskInfo;
        this.getLeash = getLeash;
        this.topActivityType = topActivityType;
        this.show = show;
        this.setAlpha = setAlpha;
    }

    static RuntimeAccess create(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> transitionInfo = classLoader.loadClass("android.window.TransitionInfo");
        Class<?> change = classLoader.loadClass("android.window.TransitionInfo$Change");
        Class<?> runningTaskInfo = classLoader.loadClass(
                "android.app.ActivityManager$RunningTaskInfo");
        Class<?> surfaceControl = classLoader.loadClass("android.view.SurfaceControl");
        Class<?> transaction = classLoader.loadClass(
                "android.view.SurfaceControl$Transaction");

        return new RuntimeAccess(
                accessible(transitionInfo.getDeclaredMethod("getType")),
                accessible(transitionInfo.getDeclaredMethod("getChanges")),
                accessible(change.getDeclaredMethod("getMode")),
                accessible(change.getDeclaredMethod("getFlags")),
                accessible(change.getDeclaredMethod("getTaskInfo")),
                accessible(change.getDeclaredMethod("getLeash")),
                accessible(runningTaskInfo.getField("topActivityType")),
                accessible(transaction.getDeclaredMethod("show", surfaceControl)),
                accessible(transaction.getDeclaredMethod(
                        "setAlpha", surfaceControl, float.class)));
    }

    TransitionSnapshot inspect(Object transitionInfo) throws ReflectiveOperationException {
        int transitionType = (int) getType.invoke(transitionInfo);
        List<?> rawChanges = (List<?>) getChanges.invoke(transitionInfo);
        List<TransitionMatcher.ChangeFacts> facts = new ArrayList<>(rawChanges.size());
        List<Object> openingWallpaperLeashes = new ArrayList<>(1);

        for (Object change : rawChanges) {
            int mode = (int) getMode.invoke(change);
            int flags = (int) getFlags.invoke(change);
            Object taskInfo = getTaskInfo.invoke(change);
            int activityType = taskInfo == null
                    ? TransitionMatcher.NO_TASK
                    : topActivityType.getInt(taskInfo);

            facts.add(new TransitionMatcher.ChangeFacts(mode, flags, activityType));
            if (TransitionMatcher.isOpening(mode)
                    && (flags & TransitionMatcher.FLAG_IS_WALLPAPER) != 0) {
                openingWallpaperLeashes.add(getLeash.invoke(change));
            }
        }

        return new TransitionSnapshot(transitionType, facts, openingWallpaperLeashes);
    }

    void keepVisible(Object transaction, List<Object> wallpaperLeashes)
            throws ReflectiveOperationException {
        for (Object leash : wallpaperLeashes) {
            show.invoke(transaction, leash);
            setAlpha.invoke(transaction, leash, 1.0f);
        }
    }

    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }

    private static Field accessible(Field field) {
        field.setAccessible(true);
        return field;
    }

    static final class TransitionSnapshot {
        final int transitionType;
        final List<TransitionMatcher.ChangeFacts> changes;
        final List<Object> openingWallpaperLeashes;

        TransitionSnapshot(
                int transitionType,
                List<TransitionMatcher.ChangeFacts> changes,
                List<Object> openingWallpaperLeashes) {
            this.transitionType = transitionType;
            this.changes = changes;
            this.openingWallpaperLeashes = openingWallpaperLeashes;
        }
    }
}
