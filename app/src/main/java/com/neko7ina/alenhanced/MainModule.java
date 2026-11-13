package com.neko7ina.alenhanced;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class MainModule extends XposedModule {
    private static final String TAG = "ActionLauncherEnhanced";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    static final String ACTION_LAUNCHER_PACKAGE = "com.actionlauncher.playstore";
    private static final String HANDLER_CLASS =
            "com.android.wm.shell.transition.DefaultTransitionHandler";
    private static final AtomicBoolean SYSTEM_UI_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean ACTION_LAUNCHER_INSTALLED = new AtomicBoolean();

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage()) {
            return;
        }

        if (ACTION_LAUNCHER_PACKAGE.equals(param.getPackageName())
                && ACTION_LAUNCHER_INSTALLED.compareAndSet(false, true)) {
            try {
                ActionLauncherRevealFix revealFix = new ActionLauncherRevealFix(this);
                revealFix.install(param.getClassLoader());
                new ActionLauncherBackFix(this, revealFix::healBeforeReentry,
                        new HomeAnimationFrameRate(this))
                        .install(param.getClassLoader());
            } catch (Throwable error) {
                ACTION_LAUNCHER_INSTALLED.set(false);
                log(Log.ERROR, TAG,
                        "Action Launcher hooks unavailable", error);
            }
            return;
        }

        if (!SYSTEM_UI_PACKAGE.equals(param.getPackageName())
                || !SYSTEM_UI_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        try {
            installHook(param.getClassLoader());
            log(Log.INFO, TAG, "Hook installed; build=" + Build.FINGERPRINT);
        } catch (Throwable error) {
            SYSTEM_UI_INSTALLED.set(false);
            log(Log.ERROR, TAG,
                    "Hook unavailable; no fallback will be installed", error);
        }
    }

    private void installHook(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> handler = classLoader.loadClass(HANDLER_CLASS);
        Method startAnimation = Arrays.stream(handler.getDeclaredMethods())
                .filter(MainModule::isExpectedStartAnimation)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(
                        HANDLER_CLASS + ".startAnimation(expected signature)"));
        startAnimation.setAccessible(true);

        RuntimeAccess access = RuntimeAccess.create(classLoader);
        AtomicBoolean runtimeErrorLogged = new AtomicBoolean();
        AodUnlockFix aodFix = createAodFix(classLoader, handler);
        AtomicBoolean aodDisabled = new AtomicBoolean();

        hook(startAnimation)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    boolean adjusted = false;
                    Integer originalFlags = null;
                    try {
                        RuntimeAccess.TransitionSnapshot snapshot =
                                access.inspect(chain.getArg(1));
                        adjusted = TransitionMatcher.shouldKeepWallpaperVisible(
                                snapshot.transitionType,
                                snapshot.changes);
                        if (adjusted) {
                            access.keepVisible(
                                    chain.getArg(2),
                                    snapshot.openingWallpaperLeashes);
                        }
                        if (aodFix != null && !aodDisabled.get()) {
                            try {
                                originalFlags = aodFix.prepare(
                                        chain.getThisObject(), chain.getArg(1), snapshot);
                            } catch (Throwable error) {
                                aodDisabled.set(true);
                                log(Log.ERROR, TAG,
                                        "AOD animation adjustment disabled", error);
                            }
                        }
                    } catch (Throwable error) {
                        logRuntimeErrorOnce(runtimeErrorLogged, error);
                    }

                    Object result;
                    try {
                        result = chain.proceed();
                    } finally {
                        if (originalFlags != null) {
                            try {
                                aodFix.restore(chain.getArg(1), originalFlags);
                            } catch (Throwable error) {
                                aodDisabled.set(true);
                                log(Log.ERROR, TAG,
                                        "AOD transition flags could not be restored; adjustment disabled",
                                        error);
                            }
                        }
                    }
                    if (originalFlags != null) {
                        log(Log.INFO, TAG,
                                "Requested native unlock animation for Action Launcher AOD wake"
                                        + "; accepted=" + result);
                    }
                    if (adjusted && Boolean.TRUE.equals(result)) {
                        log(Log.INFO, TAG,
                                "Kept wallpaper visible for third-party Home transition");
                    }
                    return result;
                });
    }

    private AodUnlockFix createAodFix(ClassLoader loader, Class<?> handler) {
        try {
            // Owner fields and native animation routing were verified on this build only.
            if (!"CP3A.261005.005".equals(Build.ID)) {
                log(Log.INFO, TAG, "AOD unlock adjustment disabled for unverified build");
                return null;
            }
            AodUnlockFix fix = new AodUnlockFix(loader, handler,
                    message -> log(Log.INFO, TAG, message));
            log(Log.INFO, TAG, "AOD native unlock animation bindings ready");
            return fix;
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "AOD unlock adjustment unavailable; disabled", error);
            return null;
        }
    }

    private void logRuntimeErrorOnce(AtomicBoolean logged, Throwable error) {
        if (logged.compareAndSet(false, true)) {
            log(Log.ERROR, TAG,
                    "Transition adjustment failed; leaving it unchanged", error);
        }
    }

    private static boolean isExpectedStartAnimation(Method method) {
        if (!"startAnimation".equals(method.getName())
                || method.getReturnType() != boolean.class) {
            return false;
        }

        Class<?>[] types = method.getParameterTypes();
        return types.length == 5
                && "android.os.IBinder".equals(types[0].getName())
                && "android.window.TransitionInfo".equals(types[1].getName())
                && "android.view.SurfaceControl$Transaction".equals(types[2].getName())
                && "android.view.SurfaceControl$Transaction".equals(types[3].getName())
                && "com.android.wm.shell.transition.Transitions$TransitionFinishCallback"
                        .equals(types[4].getName());
    }
}
