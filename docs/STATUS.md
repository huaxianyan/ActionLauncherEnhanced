# Action Launcher Enhanced 交接

## 当前主线

今天保存的「解锁无动画」现场已只读取回并定位：14:04:50 的指纹解锁没有单独 WAKE 过渡，桌面内容被合并进「锁屏出现」的 TO_FRONT 过渡，由系统 KeyguardTransitionHandler 立即结束，本模块的 Hook 未参与，见 STATUS-023。该路径超出已批准边界，未改代码或安装。

已修正「在桌面锁屏，解锁后按 Home 打开抽屉却出现淡入」：仅在真正返回桌面时补放 reveal，桌面内 Home 操作及面板返回交给 AL 原生处理。10 条单元测试和 Debug 构建通过，已按当次授权覆盖安装并仅重启 AL 进程，Hook 加载正常，用户复测确认按 Home 打开抽屉不再出现异常淡入，本轮该路径通过，见 STATUS-021～022。SystemUI 沿用既有进程，以下 AOD 验证边界继续保留。

当前设备运行 AOD 模式与广播顺序修订包，9 条测试及 Debug 构建通过，按用户授权安装并自行重启，加载已确认。用户多次测试大多从下往上，观感较符合预期，但怀疑一次不动。日志确认多次原生动画请求被接受，仍有一条缺请求的待查样本。随后抓到一次抬起唤醒先于指纹的明确无动画现场，已补齐对应条件，9 条测试及 Debug 构建通过，用户授权覆盖安装并自行重启，日志确认接口及接收器正常加载，复测首次仍疑似不动，后续未复现；本轮未捕获 mode=6/LIFT，新分支尚未验收，暂不追加补丁，详见 STATUS-018。已按用户要求在独立本地工作区制作离线日志工具，并按当次授权安装，用户已配置启动，首次保存内容检查通过，应用内导出及断线观察待验证，见 STATUS-019。

原有常规 Home、面板返回和解锁后立即 Home 的基线，以及充电屏保初步结果见 STATUS-009～012。AOD 扩展授权、首轮失败和修订边界见 STATUS-013～015，不能将旧包结果冒充修订包完整回归。独立屏保修复 0.2.0 继续在另一工作区日常观察，本项目不修改它。

## 接手入口

- [README](../README.md)：功能、适配环境、构建命令和安装步骤。
- [签名说明](../SIGNING.md)：发布签名身份和恢复原则。
- [第三方资源](../THIRD_PARTY_NOTICES.md)：资源来源与许可。
- 机器、设备和本地操作约定见本地 `AGENTS.md`。

## STATUS-033：`Unexpected transit type: 3` 的出处与判定条件

- 日期：2026-11-17。静态分析 SystemUI `classes.dex`（Android 17 `CP3A.261005.005`）。
- 该日志来自 `Lcom/android/systemui/keyguard/KeyguardService$1;->startAnimation(...)`，
  即 **SurfaceBehind 的 RemoteTransitionStub**，不是 `KeyguardTransitionHandler` 本体。
- 判定逻辑（dexdump 反汇编）：
  - `type = info.getType()`，`flags = info.getFlags()`；
  - 接受条件：`type == 7`，或 `(flags & 0x100) != 0`，或 `type == 8`，或 `type == 9`；
  - 其它情况打印 `Unexpected transit type: <type>`，随后按数组与 `topActivityType`
    推导一个整数（22 / 23 / 33）继续走完流程，**不做任何 surface-behind 动画**。
- 现场两条过渡都是 `type = 3（TO_FRONT）`、`flags = 0x800`，两个条件都不满足，
  因此 keyguard 侧直接放弃动画；该过渡 4 毫秒结束，桌面随后直接出现。
- 含义：这不是「动画被谁挡住」，而是这条过渡形状根本不是 keyguard 退场/唤醒，
  而是「锁屏出现」类型；AOD pulsing 上直接指纹解锁时，系统先短暂把锁屏当成出现，
  再切到 gone。要让动画恢复，得查清楚 WM/ATMS 为何在这个路径上发出
  `TO_FRONT + 0x800`，而不是唤醒类过渡。
- 未改动代码或设备设置。

## STATUS-032：解锁补放方向错误，已回退（0.6.2）

- 日期：2026-11-16。用户实测 0.6.1 后反馈：解锁动画变成「从应用返回桌面」的形状
  （自上而下），应为「在桌面解锁」（自下而上）。现场
  `scene-20261116-224911-100`、`scene-20261116-224921-476`。
- 现场证据：`Replayed skipped reveal after unlock` 在约 8 分钟内出现 5 次，
  说明补放并不只在偶发场景触发；每次补放都紧跟一条 `b() delay=160 source=onStart()`
  与随后的 `b() delay=0`，AL 播放的是它自己的显现动画。
- 判断错误的原因：把解锁动画当成 AL 的内容动画。正常解锁的桌面动画是系统的 WAKE
  过渡（桌面窗口自下而上），AL 的 `b()` 是「回到桌面」时的内容显现，形状与方向都不同；
  用它替代会得到方向错误的动画，也与「不替换、不伪造系统过渡」的边界冲突。
- 处置：`git revert` 掉补放（连同 STATUS-031 一并未保留），版本 `0.6.2 / 11`，
  已装机并重启 AL 进程；宁可回到「无动画」，也不保留方向错误的动画。
- 结论：要修只能从系统侧缺失的 WAKE 过渡入手（STATUS-023/030 记录的
  `KeyguardTransitionHandler` 与 `Unexpected transit type: 3`），不再从 AL 侧补动画。

## STATUS-030：解锁后无桌面动画——原生无过渡动画，且 AL 自己的 reveal 被旧缓存跳过

- 日期：2026-11-15 23:05:36。现场包 `scene-20261115-230540-951`（「解锁现场记录」0.1.2）。
  指纹在 AOD pulsing 上直接解锁：`DOZE_REQUEST_PULSE → DOZE_PULSING`，
  `calculateModeForFingerprint … deviceDreaming=true` → `startWakeAndUnlock(MODE_WAKE_AND_UNLOCK_PULSING)`，
  .670 `keyguardDone`、.673 场景 lockscreen→gone。用户观感：解锁后桌面直接出现，没有动画。
- 系统侧（Windows/`WindowManagerShell`）：只有一条过渡 `#1345 t=TO_FRONT f=0x800`
  （锁屏出现类型），由 `KeyguardTransitionHandler` 接管，日志 `Unexpected transit type: 3`，
  .862 开始、.866 结束，4 ms 完成；**全程没有 WAKE 过渡**。与 STATUS-023 的形态一致。
- AL 侧：.811 `onStart cached=true actual=false E=1794754826429 now=1794755136811`
  —— E 比 onStart 早 310 秒，AL 的原生 debounce 不成立，因此**压根没有调用 `b()`**
  （该时段模块日志里没有任何 `b()` 或 reveal 记录）；.857 `USER_PRESENT flip` 才把缓存
  复位并把 E 写成当前时刻，但那时 onStart 已经过去，没人补放。
- 结论：这一现象有两个独立缺口——系统没有 WAKE 过渡（模块未介入，也不该伪造过渡），
  以及解锁路径没有补放 AL 自己的 reveal 调度。现有补放只存在于 Home 回桌面入口
  （`healBeforeReentry`），纯解锁路径没有。
- 可选修复（待用户决定）：在 `onUserPresent` 完成缓存复位后，若 `delayedOnStartPending`
  未置位（原生确实没调度），按 Home 路径同样的保护调用
  `scheduleRevealMethod.invoke(controller, 160L, ON_START_SOURCE)`。
  风险：补放发生在解锁后约 200 ms，观感会是「桌面稍晚淡入」而非与系统过渡同步；
  正常解锁（原生已调度）因 pending 已置位不会重复。

## STATUS-029：发布 0.6.0

- 日期：2026-11-07。自 0.3.2 以来累积的改动一并发布，版本 `0.6.0 / 9`。
- 内容：AOD 直接指纹解锁动画补齐；解锁后 Home 的 reveal 竞态与抽屉重放修复；
  返回键交回原生；Home 动作期间请求 120Hz。
- 发布说明写入 `docs/release-notes/v0.6.0.md`，单独写明适用系统与 Action Launcher 版本，
  并明确标注两处未完成验证：`mode6 + LIFT` 组合未在真机命中、Home 帧率请求未在真实
  掉帧状态下复测。
- 构建走 GitHub Actions（打 tag 触发，使用仓库 secrets 里的发布私钥），本地只做
  单测与 Debug 构建核对。
- 注意：设备上现装的是 Debug 签名包，正式发布包签名不同，直接覆盖安装会失败；
  是否切到正式包另行决定，不在本次发布里处理。

## STATUS-028：Home 动作期间请求 120Hz（已实现，验证未完成）

- 日期：2026-11-04。经用户授权，新增 `HomeAnimationFrameRate`：在 Home 动作放行前
  对桌面窗口调用 `View.setRequestedFrameRate(120)`，600 ms 后清除请求。不改动画参数，
  不动系统设置，只在 Home 动作路径生效。
- 首次测量（安装后、桌面冷启动）：动画期间投票 `120 Hz / ExactOrMultiple / HighHint`，
  显示 120.00001，抽屉 55～56 帧、8.33 ms。但同一时刻冷启动的桌面**本身**就投 120Hz，
  因此这次测量不能证明是本模块抬上去的。
- 未能复现卡顿态的阻碍：安装后桌面持续投票 120Hz（`ExactOrMultiple`），
  等待约 4 分钟仍未回落到 60Hz（用户可感知的「卡」需要显示先掉到 60）。
- 恢复语义确认：`setRequestedFrameRate` 对应 `Default` 兼容级别，而现场残留的
  `ExactOrMultiple` 投票来自 Action Launcher 自身，故 600 ms 后的清除不是残留原因；
  清除期仍加了一次 `invalidate()`。
- 待办：在真实卡顿态下复测（需显示先回落到 60Hz），确认动画升到 120Hz 且随后恢复正常；
  未通过验证前不合并、不发布。

## STATUS-027：Home 键切换抽屉为 60Hz 提交，非本模块所致

- 日期：2026-11-04。用户反馈连续按 Home 展开/收起抽屉时掉帧，确认是抽屉滑动本身不连贯
  （不是桌面淡入重放）。
- 实测（`dumpsys gfxinfo com.actionlauncher.playstore framestats`，1080x2410 面板、
  120Hz 活动模式）：
  - Home 键展开抽屉：28 帧，帧间隔固定 16.67 ms（60Hz）。
  - Home 键收起抽屉：28 帧，帧间隔固定 16.67 ms（60Hz）。
  - 手势上滑展开抽屉：50 帧，间隔 8.34 ms（120Hz）。
  - 手势下滑收起抽屉：72 帧，间隔 8.34 ms（120Hz）。
  - 掉帧率仅 3.2%（9/277），GPU 90 分位 2 ms，即不是渲染负载问题，而是提交节奏为 60Hz。
- 模块无关的证据：连续 10 次 Home 键期间 LSPosed 日志中 `ActionLauncherEnhanced`
  条目零增长（onStart、锁屏缓存、补放均未触发）；SystemUI 侧
  `Kept wallpaper visible for third-party Home transition` 也未出现，说明壁纸保持
  未被调用；模块源码不含任何帧率相关调用。
- 现场附带发现：壁纸图层（`Wallpaper BBQ wrapper`，uid 10264
  `com.google.android.apps.magicportrait`，pid 3403）投票 30Hz，桌面窗口投票
  120Hz（`Gte Normal`），切换期间 `frameRateOverride` 曾把桌面 uid 压到 60Hz。
- 复测（同日稍后、同样的 Home 键触发）：两次均为 54 帧、8.34 ms（120Hz）。
  同期 `dumpsys SurfaceFlinger` 中唯一非零投票是桌面窗口的 120Hz（Gte Normal），
  壁纸仍为 30Hz，没有任何 60Hz 投票。
- 因此这不是固定的 60Hz 提交，而是显示处于 60Hz 静止状态时，Home 键触发的动画
  不会把它抬回 120Hz；触摸（手势滑动）会立刻抬回。属于系统自适应刷新率的决策，
  且本机存在 60Hz 的 ARR 模式（modeId 3/4）。
- 系统投票面：用户设置允许到 120Hz（peak=120、min=0）；本机无逐应用刷新率覆盖，
  `mAlwaysRespectAppRequest=false`。
- 用户补充：不是每次按 Home 都掉帧；遇到时只要呼出一次侧边栏（该动画流畅），
  之后 Home 开关抽屉就恢复正常。即掉帧与「显示当时是否停在 60Hz」相关，
  而不是 Home 动画本身固定 60Hz。
- 试图按「静止 20 秒 → Home」与「侧边栏 → Home」做 A/B，两组均为 120Hz，
  但这是在**未复现掉帧的时段**做的，不能作为「侧边栏能抬回 120Hz」的证据，
  只能说明流畅态下两条路径都是 120Hz。A/B 结论作废。
- 抓到的决定性证据（2026-11-04 深夜，卡顿态现场）：
  - 卡顿时：`mActiveRenderFrameRate=60.000004`；桌面窗口投票
    `{0.00 Hz, Default, Normal}`（不要求帧率、也不带高刷提示）；
    `SurfaceFlinger` 的 setFrameRate 表里挂着 `(uid=10339, 60.00 Hz)` 覆盖。
  - 呼出侧边栏（触摸边缘内滑）：投票变成 `{0.00 Hz, Default, HighHint}`，
    显示立刻升到 120.00001，10339 的 60Hz 覆盖消失。
  - 再按 Home：投票回落到 `{0.00 Hz, Default, Normal}`，显示随即掉回
    60.000004，抽屉动画 35 帧、间隔 16.67 ms。
  - 同一晚另一次流畅态：窗口投票 `{120.00 Hz, Gte, Normal}`，抽屉 54 帧、8.34 ms。
- 结论：抽屉动画本身没有问题，问题在于 Home 动作期间桌面窗口**不要求高刷新率**
  （Normal 类别、0Hz 无偏好），系统就按 60Hz 静止速率出帧；触摸路径带 HighHint
  才会升到 120Hz。模块全程未参与（日志零增长、壁纸保持未触发、源码无帧率调用）。
- 可选修复方向（超出当前边界，待用户决定）：在 Home 动作（现有 onNewIntent 钩子点）
  期间为桌面窗口请求 120Hz（`Gte`）或高刷提示，动画结束恢复原值。
  属新增的窗口帧率请求，不是通用 View/Animator 钩子，但需要明确授权。
- 未改动任何代码或设备设置。

## STATUS-026：双声现场定位为锁屏退出完成被触发三次

- 日期：2026-10-26。只读取回 09:04:21 保存的现场，本地目录
  baseline-restore/double-sound-20261026-113330，ZIP 完整、无不完整标记。
- 原生代码确认：只有 `KeyguardViewMediator.onKeyguardExitFinished` 会播放解锁音
  （`mUnlockSoundId`），并在同一次调用里发一次 USER_PRESENT。
- 本次解锁（抬起唤醒后指纹，17.354 MODE_UNLOCK_COLLAPSING）该结束例程跑了三次：
  音效 17.366/17.408/17.852，USER_PRESENT 17.408/17.452/17.856，一一对应。
  前两次仅隔 42 ms，被原生 `stop()` 截断，所以实际听到两声。
- 三次来源：17.356 与 17.362 各有一条 dismiss 请求进入 keyguardDone（KeyguardDismiss
  两次记录），随后 17.458 的锁屏退出过渡在 17.843 再次走结束路径（wasShowing=false）。
- 与模块无关：本模块只接收 USER_PRESENT，不参与锁屏退出与音效播放。上一次 12:29
  双声样本为两次，机制相同。
- 修复需在 SystemUI 的锁屏退出/音效路径加 Hook，属新边界；本次未改代码、未安装、
  未变更设置。是否继续查重复 dismiss 的来源待定。
- 用户于同日决定：先继续积累现场，再做深入调查；即使最终要修，也放在系统侧的
  模块里，不进入本项目。本项目只保留现象与证据索引。

## STATUS-025：锁屏音效双播已定位到 SystemUI 音效池，工具 0.1.2 已安装

- 日期：2026-10-25。本次只读查看设备的 `dumpsys audio`（不需重现偶发）。
- 早期现场里 `KeyguardViewMediator` 持有 `mLockSounds` SoundPool，用途为
  USAGE_ASSISTANCE_SONIFICATION；因此“音效池启动两次”就是锁屏音效被播了两遍。
- 具体样本：12:29:40.064 与 40.492 各启动一次，相隔 428 ms，与 40.096、40.496
  两次 USER_PRESENT 对应；本次锁屏退出发生在 40.06～40.49。
- 因此双声来自系统 UI 的锁屏音效，不来自本模块；但“第二声是锁屏声还是解锁声”
  以及为何触发两次仍未知。
- 工具已准备 0.1.2 / 3：新增 KeyguardViewMediator 常驻标签与 audio.txt（保存时
  读取 dumpsys audio，含播放器表与播发历史，约 120 KB）。构建通过，命令已在
  设备上只读核对。APK 49607 字节，SHA-256 见工具 USAGE。
- 已按当次授权覆盖安装（versionCode 3），安装前备份两份旧现场，更新后恢复记录。
- 真实保存生成 12:54:03 验证现场：audio.txt 119657 字节，含播放器表与播放记录
  （锁屏音效 6 次开始记录），常驻日志已出现 KeyguardViewMediator，四份辅助文件
  未截断、无不完整标记，ZIP 完整性通过。手机上保留 12:29 双声现场与验证现场。
- 尚未捕获双声时刻的完整音频记录；未改动画代码、未变更设备设置、未重启。

## STATUS-024：双解锁音效现场已取回，本次保存不含音频证据

- 日期：2026-10-25。只读取回 12:29:47 保存的现场，证据在本地
  baseline-restore/double-unlock-sound-20261025-123051，ZIP 完整、无辅助不完整标记。
- 当时是抬起唤醒（12:29:39.339 LIFT），锁屏可见后触发人脸，39.795 人脸认证成功，
  39.798 设备进入已解锁（来源 FaceWithoutBypass）；期间另有指纹操作，
  39.934 被取消（KIC 报已取消指纹操作）。
- USER_PRESENT 在 40.096 与 40.496 各发一次，间隔约 400 ms。但留存日志里
  179 组解锁中 124 组都是双发（多为 400 ms 间隔），属常态，不足以单独解释偶发双音。
- 工具常驻过滤与保存内容均不含音频相关标签，系统主日志该时段也查不到
  音效播放记录，因此本次保存不能证明声音来源，也不指向模块。
- 下次排查需补音频链路证据：临时只读抓取音频、媒体与锁屏相关标签，或给工具
  增加对应标签。两者均需当次授权，未在本次自行执行。

## STATUS-023：今日无动画样本定位为锁屏出现过渡合并，非模块门控

- 日期：2026-10-24。仅经 ADB 只读取回用户已保存的 14:04:57 现场，新证据在本地
  baseline-restore/no-animation-scene-20261024-235627，ZIP 完整，无辅助不完整标记。
- 14:04:49.872 AOD 指纹按下触发 doze pulse，亮度先亮；50.008 认证成功，
  BiometricUnlockLogger 为 MODE_WAKE_AND_UNLOCK_PULSING，DeviceEntry 记录
  指纹解锁、isUnlocked=true。人脸在 50.013 因门控取消，无第二次认证。
- AL 在 50.152 onStart cached=true，按既有设计不播放自有显现；这不是本次异常。
- 全程只有一条过渡 #2394：类型 TO_FRONT、f=0x800（锁屏出现），内容包含
  AL 桌面任务、显示区域、壁纸且带 FLAG_CHANGED_INTERACTIVE。KeyguardTransitionHandler
  记录 Unexpected transit type: 3 后立即结束，采集耗时约 163 ms，无可播放动画。
- 本模块未产生 AOD 相关日志：修复只接 DefaultTransitionHandler 的 WAKE 过渡，
  本次没有 WAKE。对比 14:01:46 同类指纹解锁：先出现 c=[] 的锁屏出现过渡，
  再接独立 WAKE #2376，模块 accepted=true，动画正常。
- 保留的窗口日志内共 12 次 AOD 指纹唤醒，其中 11 次带独立 WAKE 过渡，仅本次
  被合并。推断与 49.897 显示信息更新中止紧邻过渡、拉长采集窗口有关，
  但只有单一新样本，不宣布根因或普遍规律。
- 结论：这不是解锁状态或 cache 门控错误，也不在现有 Hook 点位内；要补动画需
  新增 KeyguardTransitionHandler 一类 Hook，属新边界，未获授权前不做。
- 用户决定继续记录现象、再观察几次，暂不评估 KeyguardTransitionHandler 新 Hook。
  再遇无动画时先保存现场，以同类样本数量判断是否为偶发合并，不预先改代码。
- 双解锁音效仅与 pulse 加解锁两个阶段相邻，未验证两者是否同名或重复播放，
  保留为线索。本轮未改代码、未构建、未安装、未重启，未变更设备设置。

## 归档索引

- 2026-10-13 ～ 2026-11-13（STATUS-001～022）：`docs/archive/STATUS-2026-10-13_2026-11-13.md`
