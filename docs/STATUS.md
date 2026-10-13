# Action Launcher Enhanced 交接

## 当前主线

维护 Android 17 与 Action Launcher 53.0 的返回桌面体验修复：Home 起播时机（壁纸黑屏与内容闪烁）和返回键路径（强制预测性返回导致抽屉不关闭）。适配限于已验证的 Hook，不扩大系统或 Launcher 的修改范围。

## 接手入口

- [README](../README.md)：功能、适配环境、构建命令和安装步骤。
- [签名说明](../SIGNING.md)：发布签名身份和恢复原则。
- [第三方资源](../THIRD_PARTY_NOTICES.md)：资源来源与许可。
- 机器、设备和本地操作约定见本地 `AGENTS.md`。

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
