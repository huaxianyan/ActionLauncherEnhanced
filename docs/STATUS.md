# Action Launcher Enhanced 交接

## 当前主线

维护 Android 16 与 Action Launcher 52.0 的返回桌面动画修复。适配限于已验证的 Hook，不扩大系统或 Launcher 的修改范围。

## 接手入口

- [README](../README.md)：功能、适配环境、构建命令和安装步骤。
- [签名说明](../SIGNING.md)：发布签名身份和恢复原则。
- [第三方资源](../THIRD_PARTY_NOTICES.md)：资源来源与许可。
- 机器、设备和本地操作约定见本地 `AGENTS.md`。

## STATUS-001：建立公开交接入口

- 日期：2026-10-13。
- 仓库：`huaxianyan/ActionLauncherEnhanced`，分支 `main`，整理前基线 `d4c5bfc`。
- 当前实现：修复 Home 路径的壁纸黑屏和桌面内容闪烁，沿用 Action Launcher 原生动画。基线最后一次修复针对解锁后动画持续消失。
- 历史验证：README 记载 Pixel、Android 16、Action Launcher 52.0、LSPosed modern API 102 和三按钮导航下的真机验收。本轮没有重新验证设备或新系统版本。
- 本轮完成：补交接入口，将已跟踪的 `AGENTS.md` 移出公开索引，本地文件保留。
- 本轮核对：仓库分支、远端关系、README 和构建工作流。没有运行 Gradle、连接设备、安装 APK 或推送发布标签。
- CI 与发布：`main` 推送按现有工作流触发 Debug 测试和构建，结果未查询。签名发布依赖标签或手动操作，本轮未发版。
- 下一步：若系统或 Action Launcher 更新后出现回归，先核对目标 Hook 与匹配条件，再在实际故障环境验收。更广适配范围尚无验证依据。
