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

## STATUS-022：Home 打开抽屉修订包已安装，用户复测通过

- 日期：2026-10-23。用户授权覆盖安装及仅重启 Action Launcher 进程。
- 已备份原安装包 352951 字节，核对原包与修订包 APK 签名均有效且证书一致。
  adb install -r 返回 Success；随后停止并重新打开 AL，新进程正常启动。
- 00:54:20 新进程出现 reveal、back Hook 及解锁信号接收器加载记录，
  未见 unavailable、failed、disabled 或异常记录，首次原生显现正常收尾。
- 回退包、签名、安装和加载证据保留在上一现场目录的 deployment/。
  普通进程 maps 未提供模块 APK 路径，不以该项推断加载失败或追加重启。
- 未重启手机或 SystemUI，未变更设置、模块作用域及独立修复模块。
- 用户随后反馈「试了试确实不会了」，确认「桌面锁屏 → 解锁 → Home 打开抽屉」
  不再出现异常淡入，该路径本轮视觉验收通过。准确次数及长期结果未知，
  不将此反馈扩大为从应用返回、面板返回或 AOD 全部回归通过。

## STATUS-021：修正解锁后按 Home 打开抽屉误播桌面显现，待安装验收

- 日期：2026-10-23。用户确认在桌面锁屏，解锁后通过 Home 键打开抽屉，
  看到动画变成淡入。日志在 00:29:48、57 和 00:30:13 保留解锁后的补放，
  每次追加 b(160, onStart)，随后 450 ms 原生桌面显现；未读取屏幕图像。
- 旧实现将所有 onNewIntent 和 AL 自己的返回键都当成回桌面，解锁周期未结束时
  会补播桌面显现。Home 被用户配置为打开抽屉时，该补播与抽屉动画叠加。
- 核对 AL 53.0 原生 onNewIntent：o0 && !FLAG_ACTIVITY_BROUGHT_TO_FRONT
  进入桌面内 Home 操作分支。复用此条件，只对 MAIN/HOME 的真正回桌面入口
  调用 healBeforeReentry；不用窗口焦点、时间窗口或新增 Hook 推断操作来源。
- AL 主 Activity 的返回入口仅转发原生 onBackPressed，不再补放桌面显现。
  同文件移除无调用方的旧构造入口及多余转发方法，保持单一补放调用入口。
- 新增纯逻辑 HomeReentryMatcher 和 1 条回归测试，覆盖桌面内 Home 与真正返回。
  执行 testDebugUnitTest assembleDebug：10 条测试通过，Debug APK 353883 字节。
  初次编译遗漏 libxposed getArg 的显式类型转换，已修正后完成验证。
- APK：app/build/outputs/apk/debug/app-debug.apk。
  SHA-256：dd24f765d8224652528d89261f40e3f3c8622027c952b8a2773a5f505e657366。
- 现场及局部原生反汇编：本地 baseline-restore/drawer-after-unlock-20261023-003159。
  本轮未安装、重启或修改设备设置。抽屉打开、面板返回、解锁后从应用按 Home
  以及 AOD 路径均待新版真机验收，不以逻辑测试代替动画观感。

## STATUS-020：两次无线充电屏保自动退出，独立调查继续

- 用户在 10 月 21 日、22 日分别报告自行亮屏解锁。两次均抓到无线充电后
  系统自动进入屏保，随即以无需认证的原生请求退出，未见当次新的认证成功。
- 前一次是否手动锁屏未知，不将锁屏短暂可见当成安全锁定已完成。
  具体首次 going-away 请求来源仍待查，不把它归为 AOD 动画漏匹配。
- 原始证据和结论维护在独立本地 Android 工作区 STATUS-012～013。
  本仓库及两个修复模块均未修改，无安装、开关或重启变更。

## STATUS-019：独立手机日志工具已构建，待安装验收

- 用户同意制作脱离电脑后的本地诊断工具。实现放在独立本地 Android 工作区的
  `issues/aod-diagnostics/`，入口为该目录 `USAGE.md`，不修改本项目修复代码。
- 工具「解锁现场记录」0.1.0 提供开始、保存、停止、导出及通知、快捷设置入口。
  root 只读指定日志，循环 8 MiB，保留两份现场，含临时文件约 50 MB 内。
- 构建、1 条存储流程测试及最终签名验证通过。用户随后连接设备并授权安装，
  独立工具安装返回 Success。助手未打开应用、授予权限或启动采集，
  无设置、修复模块或重启变更。用户自行配置启动并保存，已只读确认采集进程及
  首份 ZIP 的完整性，窗口、锁屏缓冲、相关模块日志均非空，保存时无工具错误。
- 下一步验证应用内导出及断线后的日常采集，ADB 取出不代替导出验收。
  此次只解决现场留存方式，不代表
  AOD 偶发无动画已解决，或新分支已通过视觉验收。

## STATUS-018：修订后首次疑似不动，后续未复现，不追加猜测修复

- 用户报告「第一次好像还是没动画，后续没有复现成功」。保留不确定措辞，
  不宣布偶发已解决，也不将未复现视为新分支通过。
- 只读保存本地 `baseline-restore/aod-lift-test-20261021-153705/` 的
  modules、transitions、buffers 三份日志。本轮未修改产品代码、构建、安装、
  设置或重启，仅记录结果，未读取图像。
- 15:34:19.775 和 26.429 指纹成功时 deviceInteractive=true、deviceDreaming=false，
  原生均选 MODE_UNLOCK_COLLAPSING，即已经醒来的锁屏解锁。AL 保留解锁计时，
  未调度 Home 显现。它们不是上轮现场的 mode=6/FROM_DREAM，不能继续追加模式来
  强行覆盖。第一次对应的完整窗口日志已被覆盖，无法确认其窗口目标及动画执行。
- 30.128、33.435、36.891、41.570、49.157 有 mode=1/FINGERPRINT/BIOMETRIC、
  fromAod=true 的原生请求 accepted=true。保留的 #32、#36、#44 WAKE 持续约
  312～321 ms 正常完成。45.785 为另一条亮屏指纹原生路径，#40 TO_BACK 已携带
  Home，45.862～46.340 正常完成，模块未接管。
- 本轮没有 mode=6/LIFT 的实际命中证据，STATUS-017 新分支仍待自然触发验证。
  也未发现 AL 解锁误播 Home 或修复异常禁用。日志运行状态不代替逐次视觉判断。
- 保持当前包，不根据首次模糊现象追加固定延迟、全模式匹配或兜底 Hook。
  若继续定位，应先在电脑端实时留存受限标签的窗口日志，再等自然触发，
  否则事后读取环形缓冲可能再次丢失最初样本。持续采集的时长与文件上限另行明确，
  本轮未启动后台常驻采集，不要求用户继续反复复现。

## STATUS-017：捕获 AOD 抬起后紧随指纹的无动画，补齐待测

- 用户日常观察中明确报告刚刚一次解锁无动画。立即保存原始记录，未要求继续复现。
  本地证据目录 `baseline-restore/aod-no-animation-20261021-151705/` 包含
  `modules.txt`、`transitions.txt`、`buffers.txt`，均采集成功，未读取图像。
- 最近现场为 15:15:50：50.127 PowerManager 因 LIFT 从 Dozing 唤醒，
  50.319 指纹成功时 deviceInteractive=true、deviceDreaming=true，原生选
  MODE_WAKE_AND_UNLOCK_FROM_DREAM。50.338 正常进入 gone，设备已解锁。
- 50.460 模块候选日志 fromAod=true、FINGERPRINT、mode=6、wakeReason=16。
  旧规则只接受 mode=1/2 配 reason=17，因此未发出动画请求。AL 的解锁缓存
  与 E 均正常，未播 Home 显现；无需再改 AL 周期或引入延迟。
- 窗口证据完整：#72 TO_FRONT 为 c=[]，原生 Keyguard 处理后立即完成；
  #73 WAKE 携带 AL Home、显示区与壁纸，50.459 ready，50.460 立即完成，
  由 DefaultTransitionHandler 处理。支持本次无动画是已观察条件未覆盖，
  不是请求 accepted 后动画被中断。
- 仅修改 AodUnlockMatcher 与既有测试，新增严格配对的 mode=6 + LIFT=16。
  继续要求 AOD 周期证据、已解锁、指纹、主屏唯一 AL Home 及交互性变化。
  不泛化到任意 FROM_DREAM，也不接纳 TAP；添加该正例与双击不变的断言。
  系统动画复用方式、Hook、广播周期和认证状态均不改。
- testDebugUnitTest assembleDebug 一次通过，9 条测试无失败。日志为本地
  `baseline-restore/aod-lift-unlock-build.txt`。APK 为
  `app/build/outputs/apk/debug/app-debug.apk`，0.5.0 / 8，352951 字节。
  现用包备份为本地 `baseline-restore/pre-aod-lift-fix.apk`。
- 用户随后明确授权安装，`adb install -r` 返回 Success。未改设置或重启，
  用户已自行重启。15:27:56 确认 AOD 绑定与 SystemUI Hook，15:27:57 确认 AOD
  周期接收器，15:28:06 确认 AL 与返回 Hook 加载，当前无本模块 unavailable/disabled。
  启动日志为本地 `baseline-restore/aod-lift-unlock-boot.txt`，本次 mode=6/LIFT
  条件命中及视觉效果仍待验收。独立屏保 0.2.0 正常加载，未修改。
  此次模式与 STATUS-016 的 14:26:37 样本不同，不能反推旧疑似事件已解释，
  也不能仅凭匹配测试宣布所有偶发均已解决。独立屏保 0.2.0 未修改。

## STATUS-016：多次 AOD 向上动画，疑似一次不动待查

- 用户报告测试数次均从下往上，观感较符合，但「好像存在一次不动」。
  记录为主要路径已改善、疑似偶发待查，不写成完整通过或认定用户记错。
- 本轮保存本地 `baseline-restore/aod-order-first-test-{modules,logcat,buffers}.txt`，
  另保存定向窗口日志 `aod-order-first-test-transitions.txt`。未改产品代码、
  构建、安装、设备设置或重启，仅整理本节。
- 系统缓冲保留 14:26:31、37、43、46、50、54 与 14:27:04、09、14 的
  9 个 AOD 直接指纹事件；其中 8 个有 fromAod=true 和原生请求 accepted=true。
  稳态 1 与 pulsing 2 都实际命中。计数是日志事件，不替代用户的测试次数或成功率。
- 本轮 AL 的目标解锁 onStart 均未再调度 b，USER_PRESENT 均保留 dreamUnlock=true。
  包括广播先于 onStart 和重复 onStart 的样本，旧的解锁误播 Home 路径本轮未见。
- 保留下来的完整窗口样本 #49、#54、#59，携带 AL Home 的 WAKE 从 ready 到
  非 aborted 完成约 313～318 ms，支持原处理器实际执行了有时长的过渡。
  仅凭日志仍不能证明每一帧位移、亮屏时刻或完整视觉一致性。
- 可疑样本 14:26:37.827 为真实 AOD 直接指纹，37.968 解锁广播先到，37.988
  AL 启动未播 Home；但无模块动画请求，也无候选条件或错误日志。
  首次提取的完整 logcat 已从 14:26:56 起，后续定向提取也未找回该次窗口记录。
  因此无法区分原生其他路径接管、匹配前返回或其他过渡形状，更不能与用户
  疑似不动那次一一对应。不能无证据地加延迟、强制动画或放宽目标范围。
- 下一步保留当前包，不要求用户立即继续盲测。若再次明确遇到不动，尽快报告
  当时大致时间并即时保存窗口日志，再定位该次目标、过渡类型和负责处理器。
  若需补诊断，先针对早退条件或原生处理器入口设计最小日志，不增兜底 Hook。

## STATUS-015：首轮 AOD 直解失败，修正模式与周期顺序，待安装

- 用户确认双击唤醒后解锁、解锁后应用按 Home 均与之前一样正常，AOD 直接指纹
  与之前一样，未改善。不能将启动绑定成功或条件测试通过写成实际修复生效。
- 只读保存本地 `baseline-restore/aod-unlock-failed-{modules,logcat,buffers}.txt`。
  14:03:01.669、06.703、11.331、17.122 候选均实际读到 mode=1、FINGERPRINT、
  wakeReason=17、unlocked=true。系统缓冲明确记录 MODE_WAKE_AND_UNLOCK，
  上一版只接受此前样本的 pulsing mode=2，因此未发出原生动画请求。
- 14:03:04.044 AL 收到非交互 Dream 开始，系统 04.409 才分发 SCREEN_OFF，
  随后 06.688 解锁记录 dreamUnlock=false。其他样本也有同样周期丢失，
  11.307 重复 onStart 再次调度 Home 类显现。与代码中 SCREEN_OFF 无条件 reset
  一致，不能继续假定熄屏广播总在 AOD 开始之前。
- AL：SCREEN_OFF 只清已解锁的旧周期，保留已开始且尚待解锁的 Dream/AOD 周期。
  USER_PRESENT 和显式 Home 的现有状态转移不变，新增等待状态日志与 ordered 标识。
- SystemUI：模式接受实际观察到的稳态 1 和 pulsing 2，但不只放宽数值。
  新增 AodWakeCycle，通过现有 Hook 首次运行时注册的受保护 Dream/屏幕广播，
  单独证明这次 WAKE 来自非交互式 AOD。周期支持 Dream 在 SCREEN_OFF 前到达，
  SCREEN_ON 后的下一次熄屏结束未消费的旧唤醒，WAKE 消费后不留到下一次。
  该状态跨主线程广播和 Shell 线程访问，状态方法同步，不用固定时间窗。
- 继续核对主屏、唯一 AL Home、交互性变化、设备与锁屏已解锁、指纹和生物识别
  唤醒原因；没有 AOD 周期的普通熄屏指纹不纳入。仍只复用原处理器动画分支，
  无新 Hook，不改认证状态或模式。新增接收器出错时仅停用该修复。
- 修改既有模式与解锁周期测试，新增一条 AOD 唤醒周期测试。测试覆盖此次真实的
  后到 SCREEN_OFF，以及 AOD 证据只供所属唤醒使用的范围边界。
  testDebugUnitTest assembleDebug 一次通过，9 条测试无失败；构建日志为本地
  `baseline-restore/aod-unlock-order-build.txt`。原生动画请求和视觉效果仍未验收。
- 待测 APK：`app/build/outputs/apk/debug/app-debug.apk`，0.5.0 / 8，352775 字节。
  首轮 AOD 包保留为本地 `baseline-restore/aod-first-attempt.apk`，屏保初步通过包
  `pre-aod-fix.apk` 保留。用户随后授权覆盖安装，`adb install -r` 返回 Success。
  用户自行重启后，ADB 一度未列出设备；按用户当次授权重启主机 ADB 服务后恢复连接，
  未重启手机或改设置。14:20:40 日志确认 AOD 周期接收器已附加，14:20:51 确认 AL
  ordered 新标识及返回 Hook，当前本模块未见 unavailable/disabled。日志保存在本地
  `baseline-restore/aod-unlock-order-boot.txt`。独立屏保 0.2.0 正常加载，未修改。
  加载成功尚不等于 AOD 路径命中或视觉验收。
- 下一步优先验 AOD 直解：fromAod=true、模式 1/2、原生请求 accepted 和实际方向。
  若未命中，按对应日志查原因；若已请求但视觉仍无效，再查原处理器实际动画目标，
  不叠加延迟或反转参数。用户已通过的 2、3 是首轮包结果，不冒充修订包回归结果。

## STATUS-014：实现 AOD 直解扩展，待安装验收

- 用户明确确认 STATUS-013 的系统过渡边界扩展。已同步本地 AGENTS 与私有归档，
  仍只使用既有 DefaultTransitionHandler.startAnimation Hook，不增加认证 Hook。
- AL：UnlockRevealCycle 将交互式充电屏保和非交互式 AOD 都作为解锁周期。
  解锁前补齐缓存，USER_PRESENT 保留原生 E 当前时刻，防止重复 onStart 误跑
  Home 显现。显式 Home/back 仍解除 debounce 并按 pending 状态补放。
  普通非 Dream 解锁沿用原行为，未把所有解锁一律倒填或一律抑制。
- SystemUI：新增 AodUnlockFix 与纯条件 AodUnlockMatcher。仅支持已核对的
  CP3A.261005.005；只匹配单主屏、唯一任务为 AL Home 且置前、带交互性变化的 WAKE。
  还要求 KeyguardManager 确认设备及锁屏已解锁、原生 BiometricUnlockController
  mode=2 且 source=FINGERPRINT、WakefulnessLifecycle 原因为 BIOMETRIC=17。
- 所有者从 Dependency 中的 DumpManagerImpl.getDumpables 读取已注册实例。
  原处理器同步调用期间，为这条迟到的 WAKE 补齐动画用 KEYGUARD_GOING_AWAY
  标记，finally 恢复原 flags。认证状态、认证模式、过渡 type 与回调均未改写。
  动画选择、目标处理、时长、系统动画缩放、合并与结束由原处理器负责。
- 复用的是原处理器的 TransitionAnimation.loadKeyguardExitAnimation 分支，
  不新增模块 Animator，不复制动画参数。它与普通路径的 surfaceBehindEntryAnimator
  是否视觉一致，尚无真机证据，不能提前宣布向上动画已恢复。
- 原 OPEN 壁纸匹配不变。非目标任务、多任务/多屏、其他 Launcher、仍锁定或
  非直解条件均保持原路径；已带锁屏退出标记的过渡也不干预。
  AOD 绑定或运行异常单独禁用，不停用已有壁纸修复。
- 接口依据：现有设备 SystemUI DEX，以及只读拉取的 framework.jar。
  后者保存在本地 `baseline-restore/aod-framework.jar`，确认动画标记属于
  WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY，而不是 TransitionInfo 常量。
  系统 dumpable 列表确认两个所有者均已注册。实际运行读取仍待加载新包后确认。
- 首次编译发现 SDK 未公开 TaskInfo.displayId。改用 AOD 反射绑定中的单一
  TransitionInfo Root 主屏检查，避免把隐藏字段依赖加到原壁纸绑定上。
  随后 testDebugUnitTest assembleDebug 成功，8 条测试均通过：旧匹配 6 条、
  解锁周期 1 条、AOD 条件 1 条。条件与状态测试不代替 Hook 入口的真机验收。
  构建日志为本地 `aod-unlock-build-final.txt`，没有重跑已通过的构建。
- 待测 APK：`app/build/outputs/apk/debug/app-debug.apk`，0.5.0 / 8，349507 字节。
  上一轮屏保初步通过的 APK 保留为本地 `baseline-restore/pre-aod-fix.apk`。
  用户随后授权覆盖安装，`adb install -r` 返回 Success，并自行重启。
  13:59:55 启动日志确认 AOD native unlock animation bindings ready 及 SystemUI Hook；
  14:00:06 确认 AL dream/AOD 新标识和返回 Hook，当前未见本模块 unavailable/disabled。
  日志保存在本地 `baseline-restore/aod-unlock-boot.txt`。独立屏保 0.2.0 正常加载，未修改。
  这些仅证明加载与反射绑定，尚未验证 AOD 条件命中或动画效果。
- 下一步用户测试 AOD 直接指纹解锁及对照路径。
  先验证 AOD 直指纹的候选事实、原生请求 accepted 日志及视觉方向，再确认双击
  唤醒后解锁、充电屏保解锁、随后 Home 和面板返回。未命中先看原因，不补固定延迟。

## STATUS-013：扩大到 AOD 直接指纹解锁，系统动画边界待确认

- 用户目标：非充电 AOD 直接指纹解锁，也呈现正常的向上解锁效果。
  当前有桌面不动和 Home 类向下显现两种现象。双击唤醒后再解锁正常。
  用户明确说明这是扩大修复范围，不是在指责此前屏保修复引入问题，并要求开始。
- 本地保存 `baseline-restore/aod-fingerprint-{modules,logcat,buffers}.txt`，
  以及 `aod-native-animation-{disasm,branches}.txt`。未读截图或录屏。
- 13:12:47 和 13:12:59 均为指纹成功、设备非交互、WAKE_AND_DISMISS_PULSING。
  AOD 开始日志 interactive=false，USER_PRESENT 为 screenSaver=false，
  未进入新增充电屏保分支，不能据此声称该分支导致 AOD 故障。
- 47.376 的 AL onStart 缓存为 true，没有调度 Home 显现。
  59.728 首次 onStart 也未调度，但 59.762 模块解锁接收器复位缓存并倒填 E，
  59.846 第二次 onStart 调度 b(160,onStart)，13:13:00.014 原生显现开始。
  原有解锁早复位逻辑参与了重复启动后的错误显现，需纳入修复。
- 系统侧 47.310 的过渡 #59（TO_FRONT）和 59.703 的 #66（TO_BACK）均 c=[]。
  Home 窗口与壁纸在后续 #60/#67 WAKE 过渡才出现。普通向上解锁对照样本
  12:35:10.312 的 #136 则在锁屏退出过渡中直接携带 Home 窗口。
- 解析当前设备实际 SystemUI DEX：DefaultTransitionHandler.startAnimation
  在 type=11（WAKE）且非 keyguardGoingAway 时 apply 并完成，不播放窗口动画。
  KeyguardUnlockAnimationController.notifyStartSurfaceBehindRemoteAnimation
  的 canned 分支遇到 isWakeAndUnlock=true 时，直接置完成状态并结束；
  其他路径才启动 surfaceBehindEntryAnimator。后者是原生代码分支证据，
  不是声称上述空目标样本实际调用了该控制器或把它当作唯一原因。
- 两部分需分开：AL 解锁周期抑制错误 Home 显现；系统侧恢复 AOD 直解进入效果。
  只做前者不能交付为完整修复，也不反转 AL 动画、复制动画或伪造认证模式。
- 当前约定将 SystemUI 限于 OPEN 过渡和壁纸 show/alpha。恢复此处窗口进入动画
  超出该边界，需要先确认是否允许针对已解锁且目标为 AL 的 AOD WAKE 过渡
  研究并实现系统原生动画复用。不得顺势加入全局 Hook 或影响 Pixel Launcher。
- 本轮只调查、保存证据和更新本节，未改产品代码、测试、构建、安装、设置或重启。
  下一步先确认上述边界，再确定具体实现与改动规模，不把两次样本直接写成视觉验收。

## STATUS-012：屏保解锁动画本轮初步通过

- 用户报告测试两三次「好像都正常」，保留这一限定，不写为完整回归或长期验收。
- 只读保存本地 `baseline-restore/screen-saver-unlock-first-test.txt`。
  本轮未修改代码、重新构建、安装、设置或重启。
- 13:08:56.939、13:09:03.773、13:09:10.300 收到交互式屏保开始信号。
  相应解锁时 13:09:00.428、07.769、12.909 均补齐原生锁屏缓存，
  onStart 没有调度 b，随后 USER_PRESENT 保留 E 为当前时刻且 screenSaver=true。
  这些是日志事件，不据此替换用户报告的测试次数。
- 后续 13:09:18.865 显式 Home 重入发现已有调度 pending，沿用该次动画，
  19.026 原生显现运行，19.729 收尾 alpha=1。24.649 另一条 Home 路径也正常
  调度并收尾，26.729 返回键转发命中。没有把日志运行状态当作全部视觉验收。
- 当前证据支持屏保解锁不再多跑 Home 类显现，并保留随后 Home 的动画。
  实测命中的是 onStart 先于解锁广播的顺序；反向顺序当前仅有状态单元测试。
- 后续先日常观察，不追加盲测或动画闸门。修复后 AOD、横屏、Pixel Launcher、
  壁纸等完整回归未逐项验收，不能沿用修复前结果补写为已通过。

## STATUS-011：实现屏保解锁的 AL 原生计时修复，待真机验收

- 用户明确要求开始修复。仅改 RevealFix，新增 UnlockRevealCycle 和一条状态测试。
  SystemUI、BackFix、MainModule 与独立屏保模块均未修改。
- 沿用现有接收器，新增 DREAMING_STARTED 与 SCREEN_OFF 的周期信号。
  屏保开始时读取 PowerManager.isInteractive，非交互式周期不启用本修复。
- 解锁信号尚未到达时，在现有 onStart Hook 中补齐 AL 的原生锁屏缓存，
  由 AL 自己跳过 Home 类显现。解锁信号先到时，把原生 E 保留为解锁当前时刻，
  不再倒填到过去，交由原有 debounce 抑制紧随其后的显现。
- 显式 Home/back 入口继续解除缓存与 debounce，按原有 pending 机制补放。
  普通解锁不启用屏保分支，SCREEN_OFF 和显式重入结束周期。
- 未新增时间窗口、焦点闸门或动画参数，未复制动画。删除只写不读的 unlockFlipTime，
  两处重入倒填间隔统一定义。日志增加屏保识别、解锁时刻选择和新 Hook 标识。
- 验证：testDebugUnitTest assembleDebug 成功，原有 6 条测试与新增 1 条状态测试通过。
  新测试覆盖 USER_PRESENT 在 onStart 前后两种状态顺序，不代替真实 Hook 验收。
  构建记录为本地 `baseline-restore/screen-saver-unlock-build.txt`。
- 待测 APK 仍为 `app/build/outputs/apk/debug/app-debug.apk`，版本 0.5.0 / 8。
  用户随后授权覆盖安装，`adb install -r` 返回 Success，并自行重启。
  13:05:12 启动日志确认新标识 `screen-saver unlock timing enabled`、返回 Hook
  与 SystemUI Hook 安装，当前日志未见本模块 unavailable。独立屏保 0.2.0
  也正常加载并捕获原生所有者。日志为本地 `screen-saver-unlock-boot.txt`。
  尚未发生屏保路径的命中验证。广播实际送达与 interactive 分类、
  缓存及时补齐、原生动画方向、普通解锁/AOD 和已通过的 Home 路径仍需真机确认。
- 下一步请用户测桌面 → 充电时钟屏保 → 双击退出 → 立即解锁，
  对比普通桌面锁定解锁的动画，再验证随后从应用按 Home，读取屏保周期命中日志。
  若广播未到或顺序不符，记录失败现场，不叠加固定延迟或更广 Hook。

## STATUS-010：三组基线通过，定位充电屏保解锁的显现差异

- 用户确认常规 Home、抽屉/侧边栏返回、解锁后立即 Home 均正常。
  多次测试未再发现锁屏露出桌面内容或设备再次锁定，次数未统计。
- 剩余现象：桌面锁定后普通解锁，元素从下往上；充电时钟屏保双击退出后立即
  解锁，元素像应用按 Home 返回一样从上往下。用户对照表明不开 AOD 也会触发，
  拔掉充电使用 AOD 的操作未触发，不能再将此路径称为 AOD 故障。
- 只读保存 `baseline-restore/animation-direction-{modules,logcat,buffers}.txt`。
  12:36:33.830 启动 EverydayClockDreamService（canDoze=false），35.912 转入
  锁屏，36.253 人脸通过，36.931 用户从进入图标进入 gone。
- 36.957 AL onStart 读到 cached=false、actual=false，立即调度 b(160,onStart)，
  37.118 执行 b(0,onStart)，ObjectAnimator running=true，37.820 收尾 alpha=1。
  这一例没有 Home 竞态补放日志，显现由 AL 原生 onStart 自行调度。
- 普通解锁样本 12:35:10.292 的缓存为 true，onStart 没有调度 b。系统日志同时
  区分了 DozeService（canDoze=true）与充电时钟屏保，支持用户的触发范围观察。
- 已核对 AL 原生 f81：SCREEN_ON 将缓存 c 置 true，SCREEN_OFF 将其置 false。
  当前证据支持屏保唤醒未恢复同一缓存门，导致 AL 多执行 Home 类原生显现。
  不能仅凭日志确定每段屏幕位移的来源，未读取截图或录屏。
- 下一步确认原生屏保结束信号在 AL 内的可用性及顺序，再确定最小缓存修复。
  必须考虑模块 USER_PRESENT 早期复位可能早于 AL onStart，不新增固定时间窗口、
  焦点闸门、全局动画 Hook 或复制动画。不能只凭 Dream 广播名称就假定投递顺序。
- 本轮只取材和更新文档，未改代码、构建、安装、设置或重启。屏保修复 0.2.0 保持原样。

## STATUS-009：恢复昨日下午基线，准备重新验收

- 用户同意先恢复到昨日下午暂时认为正常的版本，再逐项测试。
  对应提交 `cae406f`，时间为 2026-10-20 15:48:18，历史边界见 STATUS-005。
- 已先将全部未提交修改保存到本地
  `.workbuddy/baseline-restore/pre-restore.patch`，保留此前调查文档。
- 仅将 RevealFix、BackFix 和 MainModule 恢复为该提交的原始内容，已核对无差异。
  恢复 AL Hook 安装、原有锁屏缓存修复与 Home 补放；撤销后续 SCREEN_ON、
  焦点和 homeIntentSeen 试错逻辑。未增加其他功能或清理基线中的探针。
- `testDebugUnitTest assembleDebug` 成功。现有 TransitionMatcher 的 6 条测试通过，
  这不代表 AL Hook 或真机行为已通过。构建记录在本地 `baseline-restore/build.txt`。
- 待测 APK：`app/build/outputs/apk/debug/app-debug.apk`，0.5.0 / 8，340427 字节。
  用户随后明确授权安装，`adb install -r` 返回 Success。已覆盖安装该基线包，
  未改模块开关或重启，尚未确认 AL / SystemUI 进程加载了新代码。
- 独立屏保模块 0.2.0 的构建、加载及本轮复测结果沿用其工作区记录，长期效果未知。
  不重新测试或改动屏保模块，也不把其验收视为本模块验收。
- 用户已完成重启。12:25 启动日志确认 SystemUI Hook、AL reveal 和返回 Hook
  安装成功，当前日志未见本模块 unavailable 或临时停用记录。原生 reveal 已执行并
  收尾为 alpha=1.0；独立屏保 0.2.0 也加载并捕获原生所有者，不代表行为均已验收。
  日志保存在本地 `baseline-restore/boot-modules.txt`。
- 下一步请用户测常规 Home、抽屉/侧边栏返回、解锁后立即 Home。其他回归项随后按现象定位，
  不在首次基线复测前叠加新修复。完整验收仍含 Pixel Launcher、最近任务、横屏和壁纸。

## STATUS-008：定位屏保宽限许可残留与错误隐藏锁屏的分支

- 后续归属：用户于 2026-10-21 指定独立 Android 本地工作区。全模块停用现场、
  系统包及 `sysui-` 相关取材已移交，当前实际位置见跨项目索引与新工作区约定。
  下文原取材路径为历史位置，本项目保留调查结论，不继续承载系统锁屏补丁。
- 仅分析已保存的全模块停用现场和真机系统包，未操作设备或修改产品代码。
  新提取的相关实现为本地 `sysui-wake-disasm.txt`。
- 隐藏指令的直接来源已确认：01:22:51.493，`WindowManagerLsVis` 以
  `can wake directly to gone, LS must never show` 为由调用
  `ATMS#setLockScreenShown(false, false)`，并非 AL 动画或旧动画收尾导致。
- `KeyguardWakeDirectlyToGoneInteractor` 在计时 RUNNING 时置
  `canIgnoreAuthAndReturnToGone=true`，ELAPSED 时置 false。
  清理该许可的场景过渡过滤器明确排除 from/to 为 Lockscreen 的过渡，
  因而本次 Dream → Lockscreen 不走该清理分支。
- `canWakeDirectlyToGone` 的组合条件要求设备已解锁，再判断锁屏关闭、抑制锁屏、
  唤醒并退出模式或上述宽限许可。人脸成功使「已解锁」成立，残留许可使结果为 true。
  窗口可见性分支以该结果优先强制锁屏不可见，覆盖场景仍在锁屏的结果。
- 正常对照就在同一份缓冲中：01:22:43.579 同样是 FaceWithoutBypass、
  NONE_UNLOCKED，但锁屏仍显示；45.364 用户点击进入图标后才转入 gone。
  故障时 51.492 同样的认证发生于 Dream → Lockscreen，51.493 窗口被隐藏，
  52.235 场景却稳定在 lockscreen。认证本身没有请求直接进入桌面。
- 计时从 49.615 持续到 54.637 才撤销宽限许可，54.638 随即重新锁定。
  由此串起窗口提前隐藏、场景未进入设备、计时继续、旧认证缓存与新锁定状态分裂。
  空动画 leash 的错误同时存在，但不是这次隐藏指令的来源证据。
- 待验证修复方向：结束屏保宽限、转入正常锁屏时，同步撤销直接返回桌面的许可，
  并使这一轮屏保遗留闹钟失效；保留当前人脸认证不自动跳过锁屏的设置语义。
  只改可见性会留下到期重锁，只停计时则可能留下宽限许可，均不是完整方案。
- 这属于系统锁屏修复，不在本项目已批准的 Hook 边界内。尚未确定最小落点、
  未实现或部署，不能宣称已修复。实现前需另行确认范围，验收须覆盖正常屏保
  延迟锁定、睡眠唤醒、主动锁定与人脸需滑动进入等路径，不能放宽认证条件。

## STATUS-007：全部 LSPosed 模块停用后仍复现

- 用户授权临时关闭其余模块并重启一次。已通过管理界面逐个关闭原先启用的
  Hachidori、Pixel MiPush、SyncClipboard 系统扩展、Thanox Pro、不要竖屏、
  剪贴板白名单、隐藏应用列表、FingerPay；Action Launcher Enhanced 和 Scene
  保持原有关闭状态，作用域选择保留。
- 已重启，`sys.boot_completed=1`；框架报告 System server connected、
  `10 installed, 0 enabled user states`。本轮未卸载、未修改框架数据库。
- 原启用清单留在本地 `isolation-module-state.json`，界面证据为
  `isolation-before-*.xml`、`isolation-after-*.xml`；设备侧临时 XML 已删除。
- 用户随后报告复现，本轮需要尝试很多次。未统计次数和条件，不能据此认定关闭
  模块降低概率，或指认某个模块放大问题。
- 现场已保存：`all-off-held-logcat.txt`、`all-off-held-auth.txt`、
  `all-off-held-buffers.txt`、`all-off-held-state.txt`、`all-off-held-monitor.txt`。
  取证时框架仍报告 0 个启用状态。
- 时序：01:22:49.615 调度 5000 ms 锁定闹钟 seq=11；51.161 场景从 dream
  转为 lockscreen；51.492 人脸成功（FaceWithoutBypass），51.493 旧锁屏报告隐藏；
  54.638 以 `lock screen timeout elapsed` 原因再次锁定。
- 54.642 指纹监听为 false、`canSkipBouncer=true`，无强认证要求或指纹锁定。
  保留现场显示旧生物识别认证仍有效、TrustManager `deviceLocked=0`，
  `strongAuthRequired=0x0`、`primaryAuthFlags=0`，而新设备状态已再次锁定。
- 结论：在没有 LSPosed 模块加载时同一故障链仍成立，停止以模块动画逻辑解释它。
  此对照不排除模块已写入的持久设置、其他非 LSPosed 修改，尚不等于未修改系统
  的原生复现。下一步针对场景未进入 gone 而旧锁屏已隐藏的衔接定位，不再重复
  模块开关对照或无边界要求用户复现。
- 已告知用户现场保存完毕，可输入 PIN 恢复使用。全部模块保持关闭，恢复模块
  或再次重启须经用户确认。

## STATUS-006：重新核对屏保退出与认证状态，暂停推测性修复

- 日期：2026-10-21。基线提交 `cae406f`，工作区含前轮未提交的 Hook 改动。
- 用户持续复现：双击唤醒后桌面播放动画，稍后内容消失、设备再次锁定，
  此时指纹无法进入，只能输入 PIN。前轮多次安装均未获得用户验收。
- 取证：`logcat` 全缓冲、SystemUI 的 KeyguardLog、DeviceEntryLog、
  KeyguardUpdateMonitorLog、DeviceEntryFaceAuthRepositoryLog 与 `dumpsys dreams`。
- 实测相关流程使用 `EverydayClockDreamService`（`canDoze=false`）充电屏保，
  不是前轮认定的 AOD。不能据此认定设备所有唤醒流程都不是 AOD。
- 同一案例：00:16:04.661 进入屏保，07.732 退出，08.433 人脸认证成功，
  08.438 锁屏报告隐藏，09.671 设备状态又变为未解锁，13.190 输入 PIN 后恢复。
- 09.679 指纹监听仍为 false，`canSkipBouncer=true`，
  `strongerAuthRequired=false`、`fingerprintLockedOut=false`。这否定了前轮
  「久未解锁要求安全 PIN」的解释，显示认证状态与设备解锁状态不一致。
- 重新锁定距屏保开始约 5 秒；旧现场还记录了 `lock screen timeout elapsed`。
  延迟锁定是调查方向，尚未证明完整因果关系，更未排除模块影响。
- 当前源码有 SCREEN_ON 写缓存、Home 补放、焦点判断及 alpha 覆写等多套干预。
  STATUS-005 的验收边界不涵盖本问题，其「解锁路径不受影响」不能沿用。
- 临时诊断改动：`MainModule` 在 AL 包入口只记录停用日志并返回，
  RevealFix、BackFix 均不安装；SystemUI 代码不变。此为临时功能停用，非修复。
- 验证：`testDebugUnitTest assembleDebug` 成功，同签名覆盖安装成功；新 AL
  进程记录 `DIAGNOSTIC: all Action Launcher hooks disabled; SystemUI unchanged`。
  本轮未卸载、未改框架数据库、未重启，未改 SystemUI 进程状态。
- 用户对照结果：停用 AL 全部 Hook 后，桌面动画及随后只能输入 PIN 均复现。
  新现场：00:42:47.340 调度 5000 ms 锁定闹钟，49.176 人脸成功，
  52.368 以 `lock screen timeout elapsed` 原因重新锁定。
- 保留现场读取：TrustManager 的 `deviceLocked=0`、`strongAuthRequired=0x0`，
  KeyguardUpdateMonitor 的 `getUserUnlockedWithBiometric()=true`、
  `strongAuthFlags=0`、指纹 `listening: actual=0 expected=0`，而 DeviceEntry
  已为未解锁。认证状态分裂与指纹停听均有直接证据，非生物识别锁定策略。
- 本次屏保退出过渡由 `KeyguardTransitionHandler` 处理，未见模块壁纸调整日志。
  这仍不能替代完全停用 SystemUI Hook 的对照。
- 用户授权后，通过 LSPosed 管理界面关闭整个模块（模块开关 checked=false，
  静态作用域勾选保留），框架启用状态数量从 9 降为 8。随后执行一次重启，
  `sys.boot_completed=1`，LSPosed 连接正常，启动日志未见本模块加载记录。
  未卸载、未改数据库、未改其他模块。界面取材的设备侧 XML 已精确删除。
- 完全停用对照：用户完成重启后首次解锁，再次复现桌面动画和稍后只能 PIN。
  00:55:10.919 调度 5000 ms 闹钟，12.815 人脸认证成功，15.928 以
  `lock screen timeout elapsed` 重新锁定。框架仍为 8 个启用状态，本模块已关闭。
- 结论：该故障不依赖本模块当前 Hook 加载。定位到系统屏保退出、延迟锁定与
  认证状态衔接异常；其他模块仍启用，尚不能认定为无第三方影响的 Android 缺陷。
  前轮为此故障叠加的焦点、广播、alpha 闸门未修复问题，不作为交付方案保留。
- 后续只读定位：从真机 `CP3A.261005.005` 提取 `SystemUIGoogle.apk`，
  用本机已有 DEX 工具读取相关实现，未改设备设置或安装新 Hook。
  相关反汇编留在本地 `.workbuddy/tmp-rehook/sysui-lock-disasm.txt` 和
  `sysui-entered-disasm.txt`，原始对照日志为 `disabled-control-logcat.txt`、
  `disabled-control-auth.txt`。
- 实现证据：`LockAfterDelayInteractor.handleDreamingWhileEntered` 只收集
  `isDeviceEntered`。true 时停止计时，false 且场景进入 Dream 时启动计时；
  场景和认证状态在该收集器中只是读取值，不是独立触发源。
  `handleWakefulness` 按 `isAsleep()` 去重，收到清醒状态时才调用
  `ensureTimerStopped("awake")`。这说明屏保结束本身并没有一条独立的取消路径，
  人脸认证成功也不等于该收集器收到「已进入设备」。
- 对照时序进一步细化：00:55:12.476 场景从 dream 转到 lockscreen；
  12.815 记录 `FaceWithoutBypass(userId=0)`、设备已解锁，旧锁屏监视器同时
  报告隐藏；之后到 15.928 超时前，没有场景转入 gone 或停止计时的记录。
  因此不能把旧锁屏报告隐藏直接当作新场景已完成进入桌面。
- 指纹停听的代码对应：`KeyguardUpdateMonitor.shouldListenForFingerprint`
  对屏下指纹要求 `canSkipBouncer=false`。本次 12.812 人脸通过后该值变为 true，
  15.932 再锁定时仍为 true，且监听为 false。结合未要求强认证、未指纹锁定，
  支持「新设备状态已锁定，旧认证缓存仍有效」的状态衔接故障，非指纹传感器失效。
- 当前定位边界：已找到计时继续运行及指纹停听的代码条件，但未证明场景与旧认证
  路径分裂的首因。其余模块仍启用；日志出现 Thanox 的 Activity 拦截记录，
  只能证明它在场，不能据此归因。下一项有区分力的验证是经用户授权，关闭其余
  模块并重启后做同路径对照，不再重复本模块单独停用实验。
- 修复方向尚未验收：若排除其他模块仍复现，应修正系统屏保退出、场景进入与
  延迟锁定的衔接，并保证真正重新锁定时认证缓存同步失效。不能简单在任意人脸
  成功时取消锁定，也不能强制放行认证或只覆写指纹监听条件。
- 下一步：本模块保持关闭。清理试错与临时诊断、恢复原有能力是独立的未完成事项。
  不把未提交源码整体认作稳定基线，任何重新启用、其他模块变更或重启均须与用户确认。
  若需临时避开触发路径，可经用户同意停用充电时钟屏保后验证；尚未执行或证明有效。

## STATUS-005：修复解锁后 Home 无 reveal 的竞态与补放

- 日期：2026-10-20。
- 现象：开应用 → 锁屏 → 解锁 → 立刻按 Home，偶发（概率随打开来源：抽屉/
  侧边栏开应用比桌面图标更易触发）桌面无 reveal 动画。
- 排障：链路探针（l13.onStart 锁屏缓存/计时门、l13.b(delay,source) 调用链、
  reveal 偏好链 w.a.[o|n|m]、root 透明度与动画状态、动画收尾 settle 探针）
  + 失败案例屏幕录制逐帧比对，确认两种失败模式：
  1. 竞态跳过（主因）：AL 53.0 在 Android 17 收不到自己注册的
     NOT_EXPORTED USER_PRESENT，锁屏缓存停在「已锁定」；解锁后按 Home 时
     模块接收器把缓存复位并倒填 E，但实测 Android 17 上该路径的
     onStart 可能先于 onNewIntent 执行，AL 的 onStart 门先读到旧缓存、
     直接跳过 reveal 调度，复位再快也无效。
     注：`ey2` 不声明 onNewIntent（曾误钩报 NoSuchMethodException），
     真实实现在 `com.actionlauncher.ActionLauncherActivity`（非混淆，
     内部转发 hn0.onNewIntent）。
  2. 视觉丟失（录屏曾见）：reveal 链路引擎侧完整（b(160) 调度、
     ObjectAnimator running、settle alpha=1.0）但屏幕上工作区图标未出现。
     未定位到稳定根因，复测中未再出现；保留 settle 探针可立即鉴别。
- 修复（RevealFix + BackFix 联动）：
  - Home 入口（`ActionLauncherActivity.onNewIntent`）与返回入口
    （`Activity.finishAfterTransition`）都先调 `healBeforeReentry()`：
    发现「缓存=已锁定、KeyguardManager 实报未锁定」时当场复位缓存并
    倒填 E。
  - 若该次 onStart 的调度已被旧缓存跳过，在 onNewIntent 里补放 AL 自己的
    调度（invoke 已验证的 `l13.b(160, "onStart()")`，与其 postDelayed 后
    调用的是同一方法），b() 钩子的 alpha0/pending 机制照常接管；不新增
    动画逻辑。onStart 已正常调度（pending 已置位）时不重复。
  - 探针开关 `DBG`：当前保持 true（含 b()/onStart/settle 全链路日志），
    稳定期后应关闭。
- 日志验证：`Healed lock cache before Home re-entry` +
  `replayed skipped reveal scheduling` 在用户压测中真实触发 4 次，每次
  reveal settle alpha=1.0，动画均可见。
- 真机验证：抽屉/侧边栏/桌面图标三种来源 × 解锁后立刻 Home 均正常；
  解锁本身不播 reveal 的压制行为未受影响；常规 Home/返回路径无回归。
- 注意：模块接收器复位/倒填仍是基础路径；补放只在竞态发生时生效。

## STATUS-004：README 按 readme-standard 重整

- 日期：2026-10-14。
- 结构改为固定骨架：图标与标题居中（模块自身 `mipmap-xxxhdpi` 图标入
  `docs/images/icon.png`）、定位语、特性、适用环境、安装、设计与实现
  （含安全边界与构建）、文档索引、项目与资源来源。
- 定位语去掉「Android 16 上」过时限定；原「开发交接」节并入文档索引。
- 新增 Obtainium 导入按钮：Application ID `com.neko7ina.alenhanced`，徽章图片
  `docs/images/obtainium-badge.png` 与链接格式沿用输入法项目的既有形态；
  安装节补 min SDK 35 / target SDK 36 摘要，与构建配置核对一致。
- chinese-tech-writing 三个扫描脚本（typography/clauses/style）全部通过。

## STATUS-003：修复 AL 53.0 返回键触发"回桌面动画"

- 日期：2026-10-14。
- 现象：AL 53.0 下开抽屉/侧边栏按返回不关闭，直接播放系统"回桌面"过渡。
  Home 键正常。
- 根因：AL 53.0 targetSdk 37 触发 Android 16+ 强制预测性返回。返回键被系统
  back 导航接管，框架向主 Activity post `RequestFinishCallback`，经 Handler
  异步调用 `Activity.finishAfterTransition()` 关掉 home task，system_server
  随即 relaunch 桌面（Transition type=CLOSE + uid 0 START cat=HOME）。
  AL 的抽屉返回逻辑仍在旧 `onBackPressed`/`onKeyDown` 路径，从不执行。
  app 进程内伪装 targetSdk 无效：系统侧判断用安装时记录，且系统对 home task
  的返回不调用窗口注册的 callback（实测验证）。
- 修复：`ActionLauncherBackFix` hook `Activity.finishAfterTransition()`——
  finish 调用栈全是 Handler/Looper 异步帧（无 back 特征，只能在此入口拦截），
  主 Activity 被调用时改走其旧 `onBackPressed()`（混淆类 `ey2`，只关
  shortcuts/folder/抽屉/侧边栏，无 UI 时无操作，与 52.0 行为一致），
  其余 Activity 原样放行。版本 0.4.0→0.5.0（versionCode 8）。
- 排障记录：曾尝试 hook `Activity.finish`（栈检测失败：异步无 back 帧）、
  替换 `WindowOnBackInvokedDispatcher.setTopOnBackInvokedCallback` 的 callback
  （注册成功但系统不调用；且反射 invoke 被 hook 方法需 ThreadLocal 防递归），
  均无效后改为当前方案。
- 真机验证：抽屉按返回正常收起（无 relaunch/CLOSE）、干净桌面连按返回 3 次
  0 relaunch；Home 路径回归正常（reveal timing + 壁纸 hook 日志齐全）。
- 用户验收：2026-10-14 确认抽屉、侧边栏与日常使用中返回键行为均正常，
  0.5.0 作为日常版本使用。
- 外部反馈：已准备英文 BUG 报告发 Action Launcher 官方（仅描述现象、复现
  步骤与强制预测性返回的怀疑方向，不涉及本模块与修复）。

## STATUS-002：适配 Action Launcher 53.0

- 日期：2026-10-13。
- 背景：Action Launcher 更新 53.0（versionCode 53000001，targetSdk 37）后重新混淆，
  模块在 AL 进程内的 Hook 因 `A0.k` 类名失效而停用（`reveal hook unavailable`）。
- 重定位方法：自写 DEX 解析（`.workbuddy/tmp-rehook/dexfind.py`），以
  const-string `"onStart()"` / `"onHomeIntent()"` 的 invoke 目标定位 reveal controller，
  沿 `C` 字段 getValue() 返回类型定位 reveal state。符号映射：
  `A0.k`→`l13`、`A0.f`→`k13`；方法/字段名（onStart、b、C、z、a、c、d、k）未变。
- 代码改动：`ActionLauncherRevealFix.java` 两个类名常量 + 维护注释。
  版本 0.3.2→0.4.0；README 适用环境更新为 Android 17 / AL 53.0。
- 已验收：真机 Hook 安装日志齐全（reveal + 壁纸路径）；用户日常使用确认
  Home 返回桌面的壁纸黑屏与内容闪烁不再出现。
- 注意：52.0 期间确认的「按 Home 掉帧」与模块无关，是 AL 53.0 之前版本
  在 resume 路径同步广播给未安装的 ActionDash 所致，53.0 实测已消失。

## STATUS-001：建立公开交接入口

- 日期：2026-10-13。
- 仓库：`huaxianyan/ActionLauncherEnhanced`，分支 `main`，整理前基线 `d4c5bfc`。
- 当前实现：修复 Home 路径的壁纸黑屏和桌面内容闪烁，沿用 Action Launcher 原生动画。基线最后一次修复针对解锁后动画持续消失。
- 历史验证：README 记载 Pixel、Android 16、Action Launcher 52.0、LSPosed modern API 102 和三按钮导航下的真机验收。本轮没有重新验证设备或新系统版本。
- 本轮完成：补交接入口，将已跟踪的 `AGENTS.md` 移出公开索引，本地文件保留。
- 本轮核对：仓库分支、远端关系、README 和构建工作流。没有运行 Gradle、连接设备、安装 APK 或推送发布标签。
- CI 与发布：`main` 推送按现有工作流触发 Debug 测试和构建，结果未查询。签名发布依赖标签或手动操作，本轮未发版。
- 下一步：若系统或 Action Launcher 更新后出现回归，先核对目标 Hook 与匹配条件，再在实际故障环境验收。更广适配范围尚无验证依据。
