package com.neko7ina.alenhanced;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Removes the 160 ms fully-visible frame before Action Launcher starts its own reveal. */
final class ActionLauncherRevealFix {
    private static final String TAG = "ActionLauncherEnhanced";
    /** 临时验证日志开关，收尾时移除。 */
    private static final boolean DBG = true;
    /**
     * 混淆符号随 Action Launcher 版本变化，更新后需重新定位：
     * - reveal controller（52.0 为 A0.k）：含 onStart()V 与 b(JLjava/lang/String;)V，
     *   通过 const-string "onStart()" / "onHomeIntent()" 的 invoke 目标定位；
     * - reveal state（52.0 为 A0.f）：controller.C 字段 getValue() 的返回类型，
     *   字段 a 为 root View（构造器第二参数传入）。
     * 其余方法/字段名（onStart、b、C、z、z.a、z.c、z.c.d()、z.c.k(Object)、state.a）
     * 在 52.0 与 53.0 中一致，若失效按上述方式重新核对。
     */
    private static final String REVEAL_CONTROLLER_CLASS = "l13";
    private static final String REVEAL_STATE_CLASS = "k13";
    private static final String ON_START_SOURCE = "onStart()";
    private static final String ON_HOME_INTENT_SOURCE = "onHomeIntent()";
    private static final String ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT";

    /**
     * 解锁路径的处理：
     * - AL 53.0 以 RECEIVER_NOT_EXPORTED 注册的 USER_PRESENT 接收器在此环境
     *   （Android 17）收不到解锁广播，锁屏缓存（z.c）停在「已锁定」；解锁当刻的
     *   onStart 据此跳过延迟 reveal —— 这正好使解锁本身不播 reveal（期望行为）。
     * - 但随后按 Home 的 reveal 也被同一缓存压制。模块自注册 RECEIVER_EXPORTED
     *   接收器补收 USER_PRESENT：复位锁屏缓存，并把 controller 的开始时刻字段
     *   E 倒填为很久以前，让后续 Home 的 onStart 过 2s 的 debounce 门。
     *   普通解锁沿用该行为，但广播与 onStart 的顺序并不固定。
     * - 屏保和 AOD 周期保留锁屏缓存直到 USER_PRESENT，随后保留原生解锁
     *   debounce，不倒填 E。充电屏保缺少 SCREEN_ON 缓存置位，AOD 直解
     *   还可能重复 onStart，两者都不能在解锁时提前解除 debounce。
     *   真正的 Home 回桌面入口再解除 debounce 并按需补放。
     */
    private static final long UNLOCK_HEAL_DELAY_MS = 1000L;
    private static final long REENTRY_BACKDATE_MS = 60_000L;

    private final XposedModule module;
    private final AtomicBoolean delayedOnStartPending = new AtomicBoolean();
    private final AtomicBoolean runtimeErrorLogged = new AtomicBoolean();
    private final AtomicBoolean appliedLogged = new AtomicBoolean();
    private final AtomicBoolean homeTimingLogged = new AtomicBoolean();
    private final AtomicBoolean reverseSyncLogged = new AtomicBoolean();
    private final AtomicBoolean unlockHealLogged = new AtomicBoolean();
    private final AtomicBoolean unlockReceiverAttached = new AtomicBoolean();
    private final AtomicBoolean earlyRestoreLogged = new AtomicBoolean();
    private final AtomicBoolean raceHealLogged = new AtomicBoolean();
    private final UnlockRevealCycle unlockCycle = new UnlockRevealCycle();

    private Method getCachedValue;
    private Method setCachedValue;
    private Field deviceStateField;
    private Method scheduleRevealMethod;
    private Field cachedKeyguardStateField;
    private Field deviceContextField;
    private Field revealStartTimeField;
    private Field revealCounterField;
    private Field revealAnimField;
    /** 主线程内串联 onStart 与内层 b() 的探针。 */
    private ThreadLocal<LogPoster> bCallPosters;
    private java.lang.reflect.Field revealPrefsHolderField;
    private java.lang.reflect.Field prefsInnerField;
    private java.lang.reflect.Field revealDurationGetter;
    private java.lang.reflect.Field revealStyleGetter;
    private java.lang.reflect.Field revealModeGetter;
    private java.lang.reflect.Field rootViewField;
    private java.lang.reflect.Field lazyStateField;
    private java.lang.reflect.Method revealStateGetter;

    private static String prefValue(Object pref) {
        if (pref == null) {
            return "null";
        }
        try {
            java.lang.reflect.Method d = pref.getClass().getMethod("d");
            d.setAccessible(true);
            return String.valueOf(d.invoke(pref));
        } catch (Throwable error) {
            return String.valueOf(pref);
        }
    }
    private Handler healHandler;
    private Runnable healTask;
    private KeyguardManager healKeyguard;
    private Object healKeyguardState;
    /** 我们 hook 到的 controller 实例（弱引用，防泄漏），解锁广播时刷新其 E。 */
    private final List<WeakReference<Object>> controllers = new ArrayList<>();

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
        this.scheduleRevealMethod = scheduleReveal;

        Field lazyState = controllerClass.getDeclaredField("C");
        lazyState.setAccessible(true);
        Method getValue = lazyState.getType().getMethod("getValue");
        getValue.setAccessible(true);
        Field deviceState = controllerClass.getDeclaredField("z");
        deviceState.setAccessible(true);
        Field deviceContext = deviceState.getType().getDeclaredField("a");
        deviceContext.setAccessible(true);
        Field keyguardState = deviceState.getType().getDeclaredField("c");
        keyguardState.setAccessible(true);
        Method cachedValueGetter = keyguardState.getType().getMethod("d");
        cachedValueGetter.setAccessible(true);
        Method cachedValueSetter = keyguardState.getType().getMethod(
                "k", Object.class);
        cachedValueSetter.setAccessible(true);
        this.getCachedValue = cachedValueGetter;
        this.setCachedValue = cachedValueSetter;
        this.deviceStateField = deviceState;
        this.cachedKeyguardStateField = keyguardState;
        this.deviceContextField = deviceContext;
        Field revealStartTime = controllerClass.getDeclaredField("E");
        revealStartTime.setAccessible(true);
        this.revealStartTimeField = revealStartTime;
        Field revealCounter = controllerClass.getDeclaredField("G");
        revealCounter.setAccessible(true);
        this.revealCounterField = revealCounter;
        Field revealAnim = null;
        try {
            revealAnim = stateClass.getDeclaredField("c");
            revealAnim.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
            // 字段名可能不同；探针失败不影响主逻辑。
        }
        this.revealAnimField = revealAnim;
        this.bCallPosters = new ThreadLocal<>();
        try {
            revealPrefsHolderField = controllerClass.getDeclaredField("w");
            revealPrefsHolderField.setAccessible(true);
        } catch (Throwable error) {
            revealPrefsHolderField = null;
            if (DBG) {
                module.log(Log.INFO, TAG, "[dbg] prefs probe holder unavailable: " + error);
            }
        }

        Field rootView = stateClass.getDeclaredField("a");
        rootView.setAccessible(true);
        this.rootViewField = rootView;
        this.lazyStateField = lazyState;
        this.revealStateGetter = getValue;

        module.hook(onStart)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    try {
                        trackController(chain.getThisObject());
                        controllerByThread.put(Thread.currentThread(),
                                chain.getThisObject());
                        Object device = deviceState.get(chain.getThisObject());
                        Object state = keyguardState.get(device);
                        Boolean cached = (Boolean) cachedValueGetter.invoke(state);
                        Context context = (Context) deviceContext.get(device);
                        KeyguardManager keyguardManager =
                                context.getSystemService(KeyguardManager.class);
                        attachUnlockReceiver(context, keyguardManager);
                        Boolean actual = keyguardManager.isKeyguardLocked();
                        if (DBG) {
                            module.log(Log.INFO, TAG,
                                    "[dbg] onStart cached=" + cached
                                            + " actual=" + actual
                                            + " E=" + revealStartTimeField.getLong(
                                                    chain.getThisObject())
                                            + " now=" + System.currentTimeMillis());
                        }
                        Object revealState0 = getValue.invoke(lazyState.get(
                                chain.getThisObject()));
                        boolean cachedLocked = Boolean.TRUE.equals(cached);
                        boolean actualLocked = Boolean.TRUE.equals(actual);
                        if (unlockCycle.waitingForUnlock() && !cachedLocked) {
                            cachedValueSetter.invoke(state, Boolean.TRUE);
                            cachedLocked = true;
                            if (DBG) {
                                module.log(Log.INFO, TAG,
                                        "[dbg] preserved dream/AOD unlock cache");
                            }
                        }
                        if (cachedLocked == actualLocked) {
                            // 缓存与实际一致，不干预。
                        } else if (cachedLocked && !actualLocked) {
                            // 解锁当刻的 onStart：AL 依据此缓存压制自己的解锁
                            // reveal。不立即改写，走延迟复位兑底。
                            scheduleUnlockHeal(state, keyguardManager);
                        } else {
                            // 反方向（如锁定后 onStart）立即同步「已锁定」。
                            cachedValueSetter.invoke(state, actual);
                            if (reverseSyncLogged.compareAndSet(false, true)) {
                                module.log(Log.INFO, TAG,
                                        "Synchronized Action Launcher lock state");
                            }
                        }
                        int preG = revealCounterField.getInt(chain.getThisObject());
                        Object preAnim = revealAnimField != null
                                ? revealAnimField.get(revealState0) : null;
                        bCallPosters.set(new LogPoster());
                        Object startResult = chain.proceed();
                        int postG = revealCounterField.getInt(chain.getThisObject());
                        Object postAnim = revealAnimField != null
                                ? revealAnimField.get(revealState0) : null;
                        String bCalls = "";
                        LogPoster poster = bCallPosters.get();
                        if (poster != null) {
                            bCalls = poster.calls;
                        }
                        if (DBG) {
                            module.log(Log.INFO, TAG,
                                    "[dbg] onStart-post G=" + postG + " (pre " + preG
                                            + ") anim=" + describeAnim(postAnim)
                                            + " (pre " + describeAnim(preAnim) + ")"
                                            + bCalls);
                        }
                        return startResult;
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

                    if (DBG) {
                        module.log(Log.INFO, TAG,
                                "[dbg] b() delay=" + delay + " source=" + source);
                        controllerByThread.put(Thread.currentThread(),
                                chain.getThisObject());
                        LogPoster poster = bCallPosters.get();
                        if (poster != null) {
                            poster.calls = poster.calls + " [b=" + delay + " " + source
                                    + "]";
                        }
                    }
                    if (delay > 0L && ON_START_SOURCE.equals(source)) {
                        try {
                            Object lazy = lazyState.get(chain.getThisObject());
                            Object revealState = getValue.invoke(lazy);
                            Object root = rootView.get(revealState);
                            if (!(root instanceof View view)) {
                                throw new IllegalStateException(
                                        "Action Launcher reveal root is not a View");
                            }
                            view.setAlpha(0.0f);
                            delayedOnStartPending.set(true);
                            if (DBG) {
                                module.log(Log.INFO, TAG,
                                        "[dbg] reveal scheduled " + revealPrefsInfo());
                            }
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
                        Object revealResult = chain.proceed();
                        if (DBG) {
                            module.log(Log.INFO, TAG,
                                    "[dbg] reveal run-post " + revealPrefsInfo());
                        }
                        final Object ctrlFinal = chain.getThisObject();
                        if (healHandler == null) {
                            healHandler = new Handler(Looper.getMainLooper());
                        }
                        healHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Object dev = deviceStateField.get(ctrlFinal);
                                    if (dev == null) {
                                        return;
                                    }
                                    Object st = cachedKeyguardStateField.get(dev);
                                    Object lazy = lazyStateField.get(ctrlFinal);
                                    Object rs = revealStateGetter.invoke(lazy);
                                    Object anim = revealAnimField != null
                                            ? revealAnimField.get(rs) : null;
                                    float alpha = rootViewField.get(rs)
                                            instanceof View v ? v.getAlpha() : -1f;
                                    module.log(Log.INFO, TAG,
                                            "[dbg] reveal settle alpha=" + alpha
                                                    + " anim=" + describeAnim(anim));
                                } catch (Throwable error) {
                                    logRuntimeErrorOnce(error);
                                }
                            }
                        }, 700L);
                        return revealResult;
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

        if (DBG) {
            module.log(Log.INFO, TAG,
                    "[dbg] reveal hook installed; ordered dream/AOD unlock timing enabled");
        } else {
            module.log(Log.INFO, TAG, "Action Launcher reveal hook installed");
        }
    }

    private static String describeAnim(Object anim) {
        if (anim == null) {
            return "null";
        }
        try {
            java.lang.reflect.Method running = anim.getClass().getMethod("isRunning");
            running.setAccessible(true);
            return anim.getClass().getSimpleName() + "(running=" + running.invoke(anim)
                    + ")";
        } catch (Throwable error) {
            return anim.getClass().getName();
        }
    }

    /** 主线程内串联 onStart 与内层 b() 调用的探针容器。 */
    private static final class LogPoster {
        String calls = "";
    }

    /**
     * 探针：读取 b() 内使用的偏好链 w.a.[o|n|m].d()（时长 / 风格 n13 / 模式 m13）
     * 及当前 root 透明度与动画状态，用于判断 reveal 是真动画还是瞬时显示。
     */
    private String revealPrefsInfo() {
        if (revealPrefsHolderField == null) {
            return "prefs:none";
        }
        try {
            Object holder = revealPrefsHolderField.get(currentController());
            if (holder != null && prefsInnerField == null) {
                prefsInnerField = holder.getClass().getDeclaredField("a");
                prefsInnerField.setAccessible(true);
            }
            Object inner = prefsInnerField != null ? prefsInnerField.get(holder) : null;
            if (inner != null && revealDurationGetter == null) {
                java.lang.reflect.Field dur = inner.getClass().getDeclaredField("o");
                dur.setAccessible(true);
                revealDurationGetter = dur;
                java.lang.reflect.Field style = inner.getClass().getDeclaredField("n");
                style.setAccessible(true);
                revealStyleGetter = style;
                java.lang.reflect.Field mode = inner.getClass().getDeclaredField("m");
                mode.setAccessible(true);
                revealModeGetter = mode;
            }
            StringBuilder info = new StringBuilder();
            if (inner != null) {
                Object durPref = ((java.lang.reflect.Field) revealDurationGetter).get(inner);
                info.append("dur=").append(prefValue(durPref));
                Object stylePref = ((java.lang.reflect.Field) revealStyleGetter).get(inner);
                info.append(" style=").append(stylePref);
                Object modePref = ((java.lang.reflect.Field) revealModeGetter).get(inner);
                info.append(" mode=").append(prefValue(modePref));
            }
            Object controller = currentController();
            Object lazy = lazyStateField.get(controller);
            Object revealState = revealStateGetter.invoke(lazy);
            info.append(" alpha=")
                    .append(rootViewField.get(revealState) instanceof View view
                            ? view.getAlpha() : "?");
            info.append(" anim=").append(
                    describeAnim(revealAnimField != null
                            ? revealAnimField.get(revealState) : null));
            return info.toString();
        } catch (Throwable error) {
            return "prefs:err" + error;
        }
    }

    private final java.util.Map<Thread, Object> controllerByThread = new java.util.concurrent.ConcurrentHashMap<>();

    private Object currentController() {
        return controllerByThread.get(Thread.currentThread());
    }

    private void trackController(Object controller) {
        for (Iterator<WeakReference<Object>> it = controllers.iterator(); it.hasNext();) {
            Object tracked = it.next().get();
            if (tracked == null) {
                it.remove();
            } else if (tracked == controller) {
                return;
            }
        }
        controllers.add(new WeakReference<>(controller));
    }

    /**
     * 解锁信号命中（主线程于 onReceive）：每个已知 controller 对应的锁屏缓存
     * 复位为「未锁定」。屏保与 AOD 解锁保留当前时刻作为原生 debounce 起点，
     * 其他解锁沿用原有 Home 时序修复。显式 Home 入口负责解除解锁 debounce。
     */
    private void onUserPresent(KeyguardManager keyguardManager) {
        if (keyguardManager.isKeyguardLocked()) {
            if (DBG) {
                module.log(Log.INFO, TAG, "[dbg] USER_PRESENT ignored; still locked");
            }
            return;
        }
        long now = System.currentTimeMillis();
        boolean dreamUnlock = unlockCycle.onUserPresent();
        long revealStart = dreamUnlock ? now : now - REENTRY_BACKDATE_MS;
        for (Iterator<WeakReference<Object>> it = controllers.iterator(); it.hasNext();) {
            Object controller = it.next().get();
            if (controller == null) {
                it.remove();
                continue;
            }
            try {
                Object device = deviceStateField.get(controller);
                Object state = cachedKeyguardStateField.get(device);
                boolean cachedLocked = Boolean.TRUE.equals(getCachedValue.invoke(state));
                if (cachedLocked) {
                    setCachedValue.invoke(state, Boolean.FALSE);
                }
                if (cachedLocked || dreamUnlock) {
                    // 缓存观察者可能刷新 E，因此最后写入本次入口需要的原生时刻。
                    revealStartTimeField.setLong(controller, revealStart);
                }
            } catch (Throwable error) {
                logRuntimeErrorOnce(error);
            }
        }
        if (earlyRestoreLogged.compareAndSet(false, true)) {
            module.log(Log.INFO, TAG, "Restored Action Launcher unlock state early");
        }
        if (DBG) {
            module.log(Log.INFO, TAG, "[dbg] USER_PRESENT flip done time=" + now
                    + " E=" + revealStart
                    + " dreamUnlock=" + dreamUnlock);
        }
    }

    /** 接收原生屏保周期与解锁信号，不改系统认证状态。 */
    private void attachUnlockReceiver(
            Context context, KeyguardManager keyguardManager) {
        if (!unlockReceiverAttached.compareAndSet(false, true)) {
            return;
        }
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context unused, Intent data) {
                    try {
                        String action = data.getAction();
                        if (Intent.ACTION_DREAMING_STARTED.equals(action)) {
                            PowerManager power = context.getSystemService(PowerManager.class);
                            boolean interactive = power.isInteractive();
                            unlockCycle.onDreamStarted();
                            if (DBG) {
                                module.log(Log.INFO, TAG,
                                        "[dbg] dream started interactive=" + interactive);
                            }
                        } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                            unlockCycle.onScreenOff();
                            if (DBG) {
                                module.log(Log.INFO, TAG, "[dbg] SCREEN_OFF waitingForUnlock="
                                        + unlockCycle.waitingForUnlock());
                            }
                        } else if (ACTION_USER_PRESENT.equals(action)) {
                            onUserPresent(keyguardManager);
                        }
                    } catch (Throwable error) {
                        logRuntimeErrorOnce(error);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_DREAMING_STARTED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            module.log(Log.INFO, TAG, "Listening for Action Launcher unlock signal");
        } catch (Throwable error) {
            unlockReceiverAttached.set(false);
            module.log(Log.ERROR, TAG,
                    "Unlock signal listener unavailable; fallback timing applies",
                    error);
        }
    }

    private String shortName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    /** 兜底：模块接收器未生效时，在解锁当刻 onStart 之后延迟复位锁屏缓存。 */
    private void scheduleUnlockHeal(Object state, KeyguardManager keyguardManager) {
        if (healHandler == null) {
            healHandler = new Handler(Looper.getMainLooper());
        }
        if (healTask != null) {
            healHandler.removeCallbacks(healTask);
        }
        healKeyguard = keyguardManager;
        healKeyguardState = state;
        Runnable task = healTask;
        if (task == null) {
            task = new Runnable() {
                @Override
                public void run() {
                    healTask = null;
                    try {
                        if (unlockCycle.waitingForUnlock() || healKeyguard.isKeyguardLocked()) {
                            return;
                        }
                        Boolean cached =
                                (Boolean) getCachedValue.invoke(healKeyguardState);
                        if (!Boolean.TRUE.equals(cached)) {
                            return;
                        }
                        setCachedValue.invoke(healKeyguardState, Boolean.FALSE);
                        if (unlockHealLogged.compareAndSet(false, true)) {
                            module.log(Log.INFO, TAG,
                                    "Restored Action Launcher unlock state");
                        }
                    } catch (Throwable error) {
                        logRuntimeErrorOnce(error);
                    }
                }
            };
            healTask = task;
        }
        healHandler.postDelayed(task, UNLOCK_HEAL_DELAY_MS);
    }

    /**
     * 真正的 Home 回桌面入口复位锁屏缓存并倒填 E。若屏保解锁已复位缓存，
     * 仍需在这里解除为解锁保留的原生 debounce。onStart 已跳过调度时沿用
     * 原有补放路径，已调度时通过 pending 保留同一次原生动画。
     */
    public void healBeforeReentry() {
        Object healedController = null;
        try {
            Object[] snapshot = controllers.toArray();
            long now = System.currentTimeMillis();
            for (Object ref0 : snapshot) {
                @SuppressWarnings("unchecked")
                WeakReference<Object> ref = (WeakReference<Object>) ref0;
                Object controller = ref.get();
                if (controller == null) {
                    continue;
                }
                Object device = deviceStateField.get(controller);
                if (device == null) {
                    continue;
                }
                Object state = cachedKeyguardStateField.get(device);
                Boolean cached = (Boolean) getCachedValue.invoke(state);
                if (!Boolean.TRUE.equals(cached) && !unlockCycle.needsHomeReentry()) {
                    continue;
                }
                Context context = (Context) deviceContextField.get(device);
                KeyguardManager keyguardManager =
                        context.getSystemService(KeyguardManager.class);
                if (keyguardManager.isKeyguardLocked()) {
                    return;
                }
                if (Boolean.TRUE.equals(cached)) {
                    setCachedValue.invoke(state, Boolean.FALSE);
                }
                revealStartTimeField.setLong(controller, now - REENTRY_BACKDATE_MS);
                healedController = controller;
                if (raceHealLogged.compareAndSet(false, true)) {
                    module.log(Log.INFO, TAG,
                            "Healed lock cache before Home re-entry");
                }
                if (DBG) {
                    module.log(Log.INFO, TAG,
                            "[dbg] reentry heal done time=" + now);
                }
            }
        } catch (Throwable error) {
            logRuntimeErrorOnce(error);
            return;
        }
        if (healedController == null || scheduleRevealMethod == null) {
            return;
        }
        unlockCycle.reset();
        // 实测 Android 17 上该路径的 onStart 先于 onNewIntent 执行，AL 的锁屏
        // 缓存门已把 reveal 调度跳过。这里补放 AL 自己的那次调度（postDelayed
        // 之后调用的正是同一个方法），b() 钩子的 alpha0/pending 机制照常接管。
        // 缓存早已复位（onStart 已正常调度）时 pending 已置位，不再重复。
        if (!delayedOnStartPending.compareAndSet(false, true)) {
            if (DBG) {
                module.log(Log.INFO, TAG,
                        "[dbg] reentry heal skip; scheduling already pending");
            }
            return;
        }
        try {
            scheduleRevealMethod.invoke(healedController, 160L, ON_START_SOURCE);
            if (DBG) {
                module.log(Log.INFO, TAG,
                        "[dbg] replayed skipped reveal scheduling");
            }
        } catch (Throwable error) {
            delayedOnStartPending.set(false);
            if (DBG) {
                module.log(Log.INFO, TAG,
                        "[dbg] reveal replay failed: " + error);
            }
        }
    }

    private void logRuntimeErrorOnce(Throwable error) {
        if (runtimeErrorLogged.compareAndSet(false, true)) {
            module.log(Log.ERROR, TAG,
                    "Action Launcher reveal adjustment failed; leaving it unchanged",
                    error);
        }
    }
}
