package com.neko7ina.alenhanced;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Action Launcher 53.0 (targetSdk 37) 的抽屉/侧边栏返回键处理仍在旧
 * onBackPressed/onKeyDown 路径里，但 Android 16+ 对 targetSdk>=36 强制预测性返回：
 * 返回键被系统 back 导航接管后，系统向主 Activity post 一个 RequestFinishCallback，
 * 经 Handler 异步调用 Activity.finishAfterTransition() 把 home task 关掉，
 * system_server 随即重新启动桌面——表现为"按返回不关抽屉而是播放统一的回桌面动画"。
 * 因为 finish 发生在异步消息里，调用栈不含 back 特征，只能在 finishAfterTransition
 * 这个入口拦截。
 *
 * 修复：hook Activity.finishAfterTransition()。主 Activity 上被调用时改走它自己的
 * 旧 onBackPressed()——该方法只关闭 shortcuts/folder/抽屉/侧边栏等 UI，桌面干净时
 * 无操作（与 AL 52.0 的三按钮返回行为一致），不会误退出。其余 Activity 原样放行。
 */
final class ActionLauncherBackFix {
    private static final String TAG = "ActionLauncherEnhanced";
    /**
     * Launcher 主 Activity 基类（混淆名随 AL 版本变化，52.0 为 A0.k 所在代、53.0 为 ey2）：
     * com.actionlauncher.ActionLauncherActivity 的祖先类，持有 Workspace / DragLayer /
     * PageIndicator 字段，覆写 onBackPressed/onKeyDown/onKeyUp。
     * 定位方法：dex 解析工具找 ActionLauncherActivity 父类链中带上述 launcher3 视图
     * 字段的类。
     */
    private static final String LAUNCHER_ACTIVITY_CLASS = "ey2";

    private final XposedModule module;
    private final AtomicBoolean handledLogged = new AtomicBoolean();
    private final AtomicBoolean errorLogged = new AtomicBoolean();
    private final AtomicBoolean homeErrorLogged = new AtomicBoolean();

    private Class<?> launcherActivityClass;
    private Method launcherOnBackPressed;
    /** 仅在真正的 Home 回桌面入口复位锁屏缓存并按需补放（RevealFix 提供）。 */
    private final Runnable beforeReentry;
    private final HomeAnimationFrameRate homeFrameRate;

    ActionLauncherBackFix(XposedModule module, Runnable beforeReentry,
            HomeAnimationFrameRate homeFrameRate) {
        this.module = module;
        this.beforeReentry = beforeReentry;
        this.homeFrameRate = homeFrameRate;
    }

    void install(ClassLoader classLoader) throws ReflectiveOperationException {
        launcherActivityClass = classLoader.loadClass(LAUNCHER_ACTIVITY_CLASS);
        launcherOnBackPressed = launcherActivityClass.getDeclaredMethod("onBackPressed");
        launcherOnBackPressed.setAccessible(true);

        Class<?> activityClass = classLoader.loadClass("android.app.Activity");
        Method finishAfterTransition = activityClass.getDeclaredMethod(
                "finishAfterTransition");
        finishAfterTransition.setAccessible(true);

        module.hook(finishAfterTransition)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object self = chain.getThisObject();
                    if (!launcherActivityClass.isInstance(self)) {
                        return chain.proceed();
                    }
                    try {
                        launcherOnBackPressed.invoke(self);
                        if (handledLogged.compareAndSet(false, true)) {
                            module.log(Log.INFO, TAG,
                                    "Rerouted system back to Action Launcher "
                                            + "back handler");
                        }
                        return null;
                    } catch (Throwable error) {
                        if (errorLogged.compareAndSet(false, true)) {
                            module.log(Log.ERROR, TAG,
                                    "Back reroute failed; falling back to finish",
                                    error);
                        }
                        return chain.proceed();
                    }
                });

        try {
            // ey2 不覆写 onNewIntent（ek2 链上无声明）；实际实现在
            // com.actionlauncher.ActionLauncherActivity（非混淆，内部转发 hn0）。
            // AL 53.0 onNewIntent 的原生条件：o0 && !FLAG_ACTIVITY_BROUGHT_TO_FRONT
            // 表示已在桌面上的 Home 操作，可执行用户配置的打开抽屉等动作。
            Field alreadyOnHome = launcherActivityClass.getDeclaredField("o0");
            alreadyOnHome.setAccessible(true);
            Method onNewIntent = classLoader.loadClass(
                    "com.actionlauncher.ActionLauncherActivity")
                    .getDeclaredMethod("onNewIntent", Intent.class);
            onNewIntent.setAccessible(true);
            module.hook(onNewIntent)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Intent intent = (Intent) chain.getArg(0);
                            boolean homeIntent = intent != null
                                    && Intent.ACTION_MAIN.equals(intent.getAction())
                                    && intent.hasCategory(Intent.CATEGORY_HOME);
                            boolean broughtToFront = intent != null
                                    && (intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0;
                            if (HomeReentryMatcher.shouldRepair(homeIntent,
                                    alreadyOnHome.getBoolean(chain.getThisObject()), broughtToFront)) {
                                beforeReentry.run();
                            }
                            if (homeIntent) {
                                // 抽屉动画在本次 onNewIntent 内启动，先请求高刷再放行。
                                homeFrameRate.requestForHomeAnimation(
                                        (Activity) chain.getThisObject());
                            }
                        } catch (Throwable error) {
                            if (homeErrorLogged.compareAndSet(false, true)) {
                                module.log(Log.ERROR, TAG,
                                        "Home re-entry check failed; leaving native Home action unchanged",
                                        error);
                            }
                        }
                        return chain.proceed();
                    });
        } catch (Throwable error) {
            module.log(Log.INFO, TAG,
                    "onNewIntent hook unavailable; race heal inactive: " + error);
        }

        module.log(Log.INFO, TAG, "Action Launcher back hook installed");
    }

}
