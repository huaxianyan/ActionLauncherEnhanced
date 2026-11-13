package com.neko7ina.alenhanced;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Consumer;

/** Reuses DefaultTransitionHandler's own keyguard animation on the late AOD Home WAKE. */
final class AodUnlockFix {
    private final Field context;
    private final Method getFlags;
    private final Method setFlags;
    private final Method getRootCount;
    private final Method getRoot;
    private final Method getDisplayId;
    private final int goingAwayFlag;
    private final int changedInteractiveFlag;
    private final Field dependency;
    private final Field dumpManager;
    private final Method getDumpables;
    private final Field dumpable;
    private final Class<?> biometricClass;
    private final Class<?> wakefulnessClass;
    private final Field biometricMode;
    private final Field biometricSource;
    private final Field wakeReason;
    private final Consumer<String> log;
    private final AodWakeCycle wakeCycle = new AodWakeCycle();
    private boolean cycleReceiverAttached;
    private volatile RuntimeException receiverFailure;
    private Object biometricOwner;
    private Object wakefulnessOwner;

    AodUnlockFix(ClassLoader loader, Class<?> handler, Consumer<String> log)
            throws ReflectiveOperationException {
        this.log = log;
        context = field(handler, "mContext");
        Class<?> info = loader.loadClass("android.window.TransitionInfo");
        getFlags = info.getDeclaredMethod("getFlags");
        setFlags = info.getDeclaredMethod("setFlags", int.class);
        getRootCount = info.getDeclaredMethod("getRootCount");
        getRoot = info.getDeclaredMethod("getRoot", int.class);
        getDisplayId = loader.loadClass("android.window.TransitionInfo$Root")
                .getDeclaredMethod("getDisplayId");
        goingAwayFlag = field(loader.loadClass("android.view.WindowManager"),
                "TRANSIT_FLAG_KEYGUARD_GOING_AWAY").getInt(null);
        changedInteractiveFlag = field(info, "FLAG_CHANGED_INTERACTIVE").getInt(null);
        Class<?> dependencies = loader.loadClass("com.android.systemui.Dependency");
        dependency = field(dependencies, "sDependency");
        dumpManager = field(dependencies, "mDumpManager");
        getDumpables = loader.loadClass("com.android.systemui.dump.DumpManagerImpl")
                .getDeclaredMethod("getDumpables");
        dumpable = field(loader.loadClass(
                "com.android.systemui.dump.DumpsysEntry$DumpableEntry"), "dumpable");
        biometricClass = loader.loadClass(
                "com.android.systemui.statusbar.phone.BiometricUnlockController");
        wakefulnessClass = loader.loadClass(
                "com.android.systemui.keyguard.WakefulnessLifecycle");
        biometricMode = field(biometricClass, "mMode");
        biometricSource = field(biometricClass, "mBiometricType");
        wakeReason = field(wakefulnessClass, "mLastWakeReason");
    }

    Integer prepare(Object handler, Object info, RuntimeAccess.TransitionSnapshot snapshot)
            throws ReflectiveOperationException {
        if (receiverFailure != null) {
            throw receiverFailure;
        }
        Context ctx = (Context) context.get(handler);
        attachCycleReceiver(ctx);
        if (snapshot.transitionType != AodUnlockMatcher.TRANSIT_WAKE) {
            return null;
        }
        boolean fromAod = wakeCycle.consumeWake();
        if (!snapshot.onlyActionLauncherHome
                || (snapshot.homeFlags & changedInteractiveFlag) == 0) {
            return null;
        }
        if (!fromAod) {
            log.accept("[dbg] AOD wake left unchanged; no observed AOD cycle");
            return null;
        }
        if ((int) getRootCount.invoke(info) != 1
                || (int) getDisplayId.invoke(getRoot.invoke(info, 0)) != 0) {
            return null;
        }
        int flags = (int) getFlags.invoke(info);
        if ((flags & goingAwayFlag) != 0) {
            // Already a native unlock transition; keep its original animation selection.
            return null;
        }
        KeyguardManager keyguard = ctx.getSystemService(KeyguardManager.class);
        if (keyguard.isKeyguardLocked() || keyguard.isDeviceLocked()) {
            log.accept("[dbg] AOD wake left unchanged; device still locked");
            return null;
        }
        if (biometricOwner == null || wakefulnessOwner == null) {
            Object dependencies = dependency.get(null);
            if (dependencies == null) {
                return null;
            }
            Object manager = dumpManager.get(dependencies);
            Collection<?> entries = (Collection<?>) getDumpables.invoke(manager);
            for (Object entry : entries) {
                Object owner = dumpable.get(entry);
                if (biometricClass.isInstance(owner)) {
                    biometricOwner = owner;
                } else if (wakefulnessClass.isInstance(owner)) {
                    wakefulnessOwner = owner;
                }
            }
            if (biometricOwner == null || wakefulnessOwner == null) {
                throw new IllegalStateException("Native AOD animation owners unavailable");
            }
        }
        Object source = biometricSource.get(biometricOwner);
        boolean fingerprint = source instanceof Enum<?>
                && "FINGERPRINT".equals(((Enum<?>) source).name());
        int mode = biometricMode.getInt(biometricOwner);
        int reason = wakeReason.getInt(wakefulnessOwner);
        log.accept("[dbg] AOD wake mode=" + mode + " source=" + source
                + " wakeReason=" + reason + " unlocked=true fromAod=" + fromAod);
        if (!AodUnlockMatcher.shouldUseUnlockAnimation(
                snapshot.transitionType, snapshot.onlyActionLauncherHome, true, true,
                mode, fingerprint, reason, fromAod)) {
            return null;
        }
        // This is animation metadata for an already completed authentication, not an
        // authentication mode/state write. The original handler owns all animation work.
        setFlags.invoke(info, flags | goingAwayFlag);
        return flags;
    }

    private void attachCycleReceiver(Context context) {
        if (cycleReceiverAttached) {
            return;
        }
        Context app = context.getApplicationContext();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context unused, Intent intent) {
                if (receiverFailure != null) {
                    return;
                }
                try {
                    String action = intent.getAction();
                    if (Intent.ACTION_DREAMING_STARTED.equals(action)) {
                        boolean interactive = app.getSystemService(PowerManager.class)
                                .isInteractive();
                        wakeCycle.onDreamStarted(interactive);
                        log.accept("[dbg] SystemUI dream started interactive=" + interactive);
                    } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        wakeCycle.onScreenOff();
                    } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                        wakeCycle.onScreenOn();
                    }
                } catch (RuntimeException error) {
                    receiverFailure = error;
                    log.accept("AOD wake cycle listener failed; adjustment disabled: " + error);
                }
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        cycleReceiverAttached = true;
        log.accept("AOD wake cycle listener attached");
    }

    void restore(Object info, int flags) throws ReflectiveOperationException {
        setFlags.invoke(info, flags);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
