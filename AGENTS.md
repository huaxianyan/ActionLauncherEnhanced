# AGENTS.md

## 项目目标

修复 Android 16 与 Action Launcher 52.0 返回桌面时的两个过渡连续性问题：

- WM Shell 默认过渡把 Wallpaper leash 保持为 `alpha = 0`；
- Action Launcher 延迟启动原生显现动画，导致完整桌面先出现再回到动画初始状态。

## 实现边界

- 模块作用域只允许 `com.android.systemui` 和 `com.actionlauncher.playstore`。
- SystemUI Hook 点限定为 `DefaultTransitionHandler.startAnimation(...)`；失效时记录日志并禁用对应修复。
- Action Launcher Hook 点限定为 52.0 中已验证的 `A0.k.b(long, String)`：来源为 `onStart()` 且存在正延迟时，提前把 `A0.f.a` 根视图设为透明；紧随其后的即时 `onHomeIntent()` 不得取消这项待执行任务。
- 不得 Hook Action Launcher 的通用 `Animator`、`View` 或生命周期方法作为正式实现。
- 不得全局 Hook `SurfaceControl.Transaction.setAlpha()`，也不得加入范围更大的兜底 Hook。
- 不伪造 Quickstep Provider，不修改 Launcher、壁纸服务、导航模式或系统数据。
- SystemUI 仅匹配 type 为 `OPEN`，且满足「标准应用关闭或退至后台 + Home 打开或置前且请求壁纸 + Wallpaper Change 打开」的 Transition。
- Back 触发的 `CLOSE` 和 `TO_BACK` Transition 保持系统窗口动画；Action Launcher 自己的原生内容动画不替换、不复制。
- 只对匹配到的 Wallpaper leash 向原 `startTransaction` 追加 `show()` 和 `setAlpha(..., 1f)`。
- 不再维护模块自有的 Home 内容动画；动画参数、目标 View 和结束状态均由 Action Launcher 原实现负责。
- Pixel Launcher 的 Quickstep Remote Transition 不能受到影响。

## 工程约定

- 使用 modern libxposed API 102，静态作用域写入 `META-INF/xposed/scope.list`。
- 保持模块无设置界面，除非后续出现明确且可验证的配置需求。
- Transition 匹配逻辑应与 Android 反射访问分离，并为边界条件添加单元测试。
- 每次修改后至少运行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

- 真机验证应覆盖 Action Launcher 的 Home 与 Back 路径、Pixel Launcher、应用打开、最近任务、锁屏/解锁、横屏，以及静态/动态壁纸。
