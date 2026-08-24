package com.neko7ina.alenhanced;

import android.app.KeyguardManager;
import android.content.Context;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Removes the 160 ms fully-visible frame before Action Launcher starts its own reveal. */
final class ActionLauncherRevealFix {
    private static final String TAG = "ActionLauncherEnhanced";
    private static final String REVEAL_CONTROLLER_CLASS = "A0.k";
    private static final String REVEAL_STATE_CLASS = "A0.f";
    private static final String ON_START_SOURCE = "onStart()";
    private static final String ON_HOME_INTENT_SOURCE = "onHomeIntent()";

    private final XposedModule module;
    private final AtomicBoolean delayedOnStartPending = new AtomicBoolean();
    private final AtomicBoolean runtimeErrorLogged = new AtomicBoolean();
    private final AtomicBoolean appliedLogged = new AtomicBoolean();
    private final AtomicBoolean homeTimingLogged = new AtomicBoolean();
    private final AtomicBoolean keyguardStateLogged = new AtomicBoolean();

    ActionLauncherRevealFix(XposedModule module) {
        this.module = module;
    }

    void install(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> controllerClass = classLoader.loadClass(REVEAL_CONTROLLER_CLASS);
        Class<?> stateClass = classLoader.loadClass(REVEAL_STATE_CLASS);

        Method onStart = controllerClass.getDeclaredMethod("onStart");
        onStart.setAccessible(true);
        Method scheduleReveal = controllerClass.getDeclaredMethod(
                "b", long.class, String.class);
        scheduleReveal.setAccessible(true);

        Field lazyState = controllerClass.getDeclaredField("C");
        lazyState.setAccessible(true);
        Method getValue = lazyState.getType().getMethod("getValue");
        getValue.setAccessible(true);
        Field deviceState = controllerClass.getDeclaredField("z");
        deviceState.setAccessible(true);
        Field deviceContext = deviceState.getType().getDeclaredField("a");
        deviceContext.setAccessible(true);
        Field cachedKeyguardState = deviceState.getType().getDeclaredField("c");
        cachedKeyguardState.setAccessible(true);
        Method getCachedValue = cachedKeyguardState.getType().getMethod("d");
        getCachedValue.setAccessible(true);
        Method setCachedValue = cachedKeyguardState.getType().getMethod(
                "k", Object.class);
        setCachedValue.setAccessible(true);

        Field rootView = stateClass.getDeclaredField("a");
        rootView.setAccessible(true);

        module.hook(onStart)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    try {
                        Object device = deviceState.get(chain.getThisObject());
                        Object keyguardState = cachedKeyguardState.get(device);
                        Object cachedState = getCachedValue.invoke(keyguardState);
                        Context context = (Context) deviceContext.get(device);
                        KeyguardManager keyguardManager =
                                context.getSystemService(KeyguardManager.class);
                        Boolean actualState = keyguardManager.isKeyguardLocked();
                        if (!actualState.equals(cachedState)) {
                            setCachedValue.invoke(keyguardState, actualState);
                            if (keyguardStateLogged.compareAndSet(false, true)) {
                                module.log(Log.INFO, TAG,
                                        "Synchronized Action Launcher lock state");
                            }
                        }
                    } catch (Throwable error) {
                        logRuntimeErrorOnce(error);
                    }
                    return chain.proceed();
                });

        module.hook(scheduleReveal)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    long delay = (long) chain.getArg(0);
                    Object source = chain.getArg(1);

                    if (delay > 0L && ON_START_SOURCE.equals(source)) {
                        try {
                            Object lazy = lazyState.get(chain.getThisObject());
                            Object state = getValue.invoke(lazy);
                            Object root = rootView.get(state);
                            if (!(root instanceof View view)) {
                                throw new IllegalStateException(
                                        "Action Launcher reveal root is not a View");
                            }
                            view.setAlpha(0.0f);
                            delayedOnStartPending.set(true);
                            if (appliedLogged.compareAndSet(false, true)) {
                                module.log(Log.INFO, TAG,
                                        "Prepared Action Launcher native reveal");
                            }
                        } catch (Throwable error) {
                            delayedOnStartPending.set(false);
                            logRuntimeErrorOnce(error);
                        }
                    } else if (delay == 0L && ON_START_SOURCE.equals(source)) {
                        delayedOnStartPending.set(false);
                    } else if (delay == 0L
                            && ON_HOME_INTENT_SOURCE.equals(source)
                            && delayedOnStartPending.get()) {
                        if (homeTimingLogged.compareAndSet(false, true)) {
                            module.log(Log.INFO, TAG,
                                    "Kept Action Launcher native reveal timing for Home");
                        }
                        return null;
                    }
                    return chain.proceed();
                });

        module.log(Log.INFO, TAG, "Action Launcher reveal hook installed");
    }

    private void logRuntimeErrorOnce(Throwable error) {
        if (runtimeErrorLogged.compareAndSet(false, true)) {
            module.log(Log.ERROR, TAG,
                    "Action Launcher reveal adjustment failed; leaving it unchanged",
                    error);
        }
    }
}
