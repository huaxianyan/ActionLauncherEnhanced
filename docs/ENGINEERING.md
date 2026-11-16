# 实现细则

`AGENTS.md` 只留边界总纲与操作纪律。**改 Hook、改 Transition 匹配或升级 Action Launcher 之前，先把这份读完。**

## 实现边界：具体 Hook 点

- 模块作用域只允许 `com.android.systemui` 和 `com.actionlauncher.playstore`。
- SystemUI Hook 点限定为 `DefaultTransitionHandler.startAnimation(...)`；失效时记录日志并禁用对应修复。
- Action Launcher 显现 Hook 点限定为 53.0 中已验证的 `l13.b(long, String)`（52.0 为 `A0.k.b`）：来源为 `onStart()` 且存在正延迟时，提前把 reveal state 根视图（53.0 为 `k13.a`，52.0 为 `A0.f.a`）设为透明；紧随其后的即时 `onHomeIntent()` 不得取消这项待执行任务。
- Action Launcher 返回 Hook 点限定为 `Activity.finishAfterTransition()`：主 Activity（53.0 混淆名 `ey2`，持有 Workspace/DragLayer/PageIndicator）被调用时转发其旧 `onBackPressed()`，其余 Activity 原样放行。不要 hook `Activity.finish`，也不要替换 `OnBackInvokedDispatcher` 的 callback——两者实测均无效，结论见 STATUS-003。
- Home 入口的锁屏缓存复位补放（STATUS-005）：onNewIntent 的 hook 目标是 `com.actionlauncher.ActionLauncherActivity`（非混淆；`ey2` 不覆写该方法，内部转发 `hn0.onNewIntent`）。复位后若发现 onStart 的 reveal 调度已被旧缓存跳过，补放 AL 自己的 `l13.b(160, "onStart()")`，不新增动画逻辑；原生路径已自行调度（pending 置位）时不重复。
- 钩子安装后必须核对日志无 unavailable 记录（曾因 `ey2` 无 `getDeclaredMethod("onNewIntent", Intent.class)` 静默哑掉一整轮）。
- 实测（Android 17 真机，2026-10-20）：解锁后按 Home 唤起停着的 AL Activity 时，`l13.onStart` 可能先于 onNewIntent 执行约 1 ms——任何要赶在 onStart 前生效的状态修复不能依赖 onNewIntent 的执行顺序。
- AL 混淆类名随版本变动，升级后用本地 DEX 解析工具重新定位符号，不改 Hook 结构。

## 实现边界：允许与不允许

- 不得 Hook Action Launcher 的通用 `Animator`、`View` 或生命周期方法作为正式实现。例外（2026-11-04 用户批准）：Home 动作期间对桌面窗口调用 `View.setRequestedFrameRate(120)`，600 ms 后清除，用于修正 Home 触发抽屉动画时系统按 60Hz 出帧。实现见 `HomeAnimationFrameRate`，只允许这一个定向调用，不扩展到其他动画或常驻高刷。
- 不得全局 Hook `SurfaceControl.Transaction.setAlpha()`，也不得加入范围更大的兜底 Hook。
- 不伪造 Quickstep Provider，不修改 Launcher、壁纸服务、导航模式或系统数据。
- 原有壁纸修复仅匹配 type 为 `OPEN`，且满足「标准应用关闭或退至后台 + Home 打开或置前且请求壁纸 + Wallpaper Change 打开」的 Transition。
- 用户于 2026-10-21 确认扩大至 AOD 直接指纹解锁的原生动画修复（STATUS-013/014）。仍仅 Hook `DefaultTransitionHandler.startAnimation`：只在已验证系统构建上，对主屏唯一 AL Home 任务的交互性变化 `WAKE`，先通过受保护广播确认来自非交互式 AOD 周期，再核对设备已解锁、指纹来源，以及已验证的模式与唤醒原因组合：稳态/pulsing 配 BIOMETRIC，或 FROM_DREAM 配 LIFT（STATUS-017，AOD 抬起唤醒抢先于指纹的现场）。才在原处理器同步调用期间补齐锁屏退出的动画标记，finally 恢复原 flags。DREAMING_STARTED 可能先于同次 SCREEN_OFF，后者不得清除待解锁/待唤醒的 AOD 周期。原生所有者从 SystemUI 已注册的 Dumpable 读取，不 Hook 认证，不改认证状态或模式，不接管 Animator、回调或合成动画参数。绑定或运行失败仅禁用 AOD 修复。该例外不扩大壁纸匹配，不改变其他 Launcher 或任务的过渡。
- Back 触发的 `CLOSE` 和 `TO_BACK` Transition 保持系统窗口动画；Action Launcher 自己的原生内容动画不替换、不复制。
- 壁纸修复只对匹配到的 Wallpaper leash 向原 `startTransaction` 追加 `show()` 和 `setAlpha(..., 1f)`。
- 不再维护模块自有的 Home 内容动画；动画参数、目标 View 和结束状态均由 Action Launcher 原实现负责。
- Pixel Launcher 的 Quickstep Remote Transition 不能受到影响。

## 边界条件与测试

- Transition 匹配逻辑应与 Android 反射访问分离，并为边界条件添加单元测试。
- 真机验证应覆盖 Action Launcher 的 Home 与 Back 路径、Pixel Launcher、应用打开、最近任务、锁屏/解锁、横屏，以及静态/动态壁纸。

## DEX 解析工具

`tools/dex/` 只用来定位混淆符号，与模块实现无关，**不入库，已在 `.gitignore` 忽略**：

- `dexfind.py`：`refs`/`dump`/`disasm`/`methods` 四模式，纯标准库，无需 jadx。
- `findcallers.py`：找某方法的调用点，依赖 `dexfind` 的解析函数。
- `dexref.py`、`dexstr.py`：按引用/字符串检索。

两个坑：**输入必须是裸 `.dex`**，直接丢 APK 会在读 string 表时越界，先从 APK 里解出 `classes*.dex`；`class_data_item` 的 field/method `*_idx_diff` 在每个列表内独立累计（static/instance、direct/virtual 各自从 0 起），跨列表连续累计必然索引溢出。

需要 Java 级反编译时用 jadx 或 androguard，不为此在仓库里留脚本。

## 反射递归

libxposed intercept 内反射 invoke 被 hook 的方法会无限递归（StackOverflowError）。需要回调原实现时用 ThreadLocal 重放标记，或改选不回调的 Hook 点。

## Home 抽屉掉帧（2026-11-04 新增例外）

- 现象：按 Home 开关抽屉时动画一顿一顿，手势滑动同一条抽屉却顺；偶发，非每次。
- 实测根因：Home 动作期间桌面窗口投票 `0 Hz / Default / Normal`，系统按 60Hz 静止速率出帧；触摸路径带 `HighHint` 才升到 120Hz。卡顿时 `SurfaceFlinger` 给桌面 uid 挂 `60.00 Hz` 覆盖。
- 关键坑：`dumpsys display` 的 `mActiveRenderFrameRate` 才是当前出帧速率，`mActiveModeId` 仍是 120Hz 模式；桌面冷启动常自带 120Hz 投票，测量前必须先确认它已回落到 60Hz，否则会把原生状态当成修复效果。
- 状态：改动已装真机，**验证未完成**（等真实卡顿态复测）。未验证前不合并、不发布。
