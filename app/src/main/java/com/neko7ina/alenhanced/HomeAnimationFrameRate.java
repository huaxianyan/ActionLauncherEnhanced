package com.neko7ina.alenhanced;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * Home 动作触发的抽屉动画期间为桌面窗口请求高刷新率。
 *
 * <p>实测（Android 17 / AL 53.0）：Home 动作期间桌面窗口的帧率投票是
 * 0 Hz / Default / Normal，系统因此按 60Hz 静止速率出帧，抽屉动画每帧间隔
 * 16.67 ms；触摸路径（手势、侧边栏）带 HighHint，显示才升到 120Hz。
 * 这里只在 Home 动作放行前请求一次 120Hz，动画结束后清除请求，
 * 不常驻高刷，也不改动画参数。
 */
final class HomeAnimationFrameRate {
    private static final String TAG = "ActionLauncherEnhanced";
    /** 请求的帧率；0 表示清除请求，交回系统决定。 */
    private static final float TARGET_RATE = 120f;
    private static final float NO_REQUEST = 0f;
    /** 抽屉动画约 450 ms，留出余量后清除。 */
    private static final long HOLD_MS = 600L;

    private final XposedModule module;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean unavailableLogged = new AtomicBoolean();
    private final AtomicBoolean restoreFailedLogged = new AtomicBoolean();

    private View target;
    private boolean pending;

    HomeAnimationFrameRate(XposedModule module) {
        this.module = module;
    }

    void requestForHomeAnimation(Activity activity) {
        View decor;
        try {
            decor = activity.getWindow().getDecorView();
            if (decor == null) {
                return;
            }
            if (!pending) {
                target = decor;
            }
            decor.setRequestedFrameRate(TARGET_RATE);
            pending = true;
            handler.removeCallbacks(restoreTask);
            handler.postDelayed(restoreTask, HOLD_MS);
        } catch (Throwable error) {
            if (unavailableLogged.compareAndSet(false, true)) {
                module.log(Log.INFO, TAG,
                        "Home animation frame rate request unavailable: " + error);
            }
        }
    }

    private final Runnable restoreTask = new Runnable() {
        @Override
        public void run() {
            pending = false;
            View restored = target;
            target = null;
            if (restored == null) {
                return;
            }
            try {
                restored.setRequestedFrameRate(NO_REQUEST);
                // 帧率请求要等下一帧才提交给 SurfaceFlinger；桌面静止时不会自己刷新。
                restored.invalidate();
            } catch (Throwable error) {
                if (restoreFailedLogged.compareAndSet(false, true)) {
                    module.log(Log.ERROR, TAG,
                            "Frame rate request clear failed; next Home action re-requests it",
                            error);
                }
            }
        }
    };
}
