<div align="center">
  <img src="docs/images/icon.png" width="120" alt="Action Launcher Enhanced 图标">

  # Action Launcher Enhanced

  [![Build](https://github.com/huaxianyan/ActionLauncherEnhanced/actions/workflows/build.yml/badge.svg)](https://github.com/huaxianyan/ActionLauncherEnhanced/actions/workflows/build.yml)
</div>

面向 Action Launcher 的 LSPosed 增强模块。修复按 Home 返回桌面时壁纸短暂变黑、内容在动画开始前闪烁，以及 Android 16+ 强制启用预测性返回后按返回键无法关闭抽屉与侧边栏的问题。全部改动沿用 Action Launcher 自己的原生动画。

## 特性

- 修复按 Home 返回 Action Launcher 时壁纸短暂变黑的问题。
- 消除 Action Launcher 原生桌面动画开始前的内容闪烁。
- 让 Home 与 Back 返回桌面时使用相同的原生动画起播时机。
- 修复按返回键不关闭抽屉/侧边栏、反而直接播放回桌面动画的问题（转发给 Action Launcher 自身的返回处理）。
- 保留 Action Launcher 自己的淡入、位移、时长和插值曲线。
- 不影响 Pixel Launcher 的 Quickstep Remote Transition。

## 适用环境

开发与真机验证覆盖以下环境：

- Pixel 10 Pro。
- Android 17，SDK 37。
- Action Launcher 53.0。
- LSPosed 2.1.1，modern API 102。
- 三按钮导航。

## 安装

1. 从 [Releases](https://github.com/huaxianyan/ActionLauncherEnhanced/releases) 下载 APK。
2. 安装并在 LSPosed 中启用模块。
3. 确认静态作用域包含 `com.android.systemui` 与 `com.actionlauncher.playstore`。
4. 重启设备。

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.neko7ina.alenhanced%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fhuaxianyan%2FActionLauncherEnhanced%22%2C%22author%22%3A%22huaxianyan%22%2C%22name%22%3A%22Action%20Launcher%20Enhanced%22%7D"><img src="docs/images/obtainium-badge.png" alt="Get it on Obtainium" height="48"></a>

装了 [Obtainium](https://github.com/ImranR98/Obtainium) 的话，点上面的按钮即可把本项目加入更新列表，之后新版本会自动检查。

```text
Application ID: com.neko7ina.alenhanced
min SDK:        35（Android 15）
target SDK:     36
```

模块没有设置界面，不会修改 Action Launcher 数据、壁纸、导航模式或系统设置。

## 设计与实现

模块只做定向 Hook：SystemUI 侧接管桌面过渡的壁纸可见性，Action Launcher 侧保持原生显现动画的起播时机，并把被强制预测性返回接管的主 Activity finish 转发回它自身的返回处理。安全边界：

- 不伪造 Quickstep Provider。
- 不全局 Hook `SurfaceControl.Transaction.setAlpha()`。
- 不修改 Action Launcher APK 或用户数据。
- 目标 Hook 失效时记录错误并停止对应修复，不使用扩大范围的回退方案。

Android 系统或 Action Launcher 更新后，如果目标实现发生变化，可能需要重新适配，当前适配状态见 [交接记录](docs/STATUS.md)。

### 构建

需要 JDK 17 和 Android SDK 36：

```powershell
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
$env:JAVA_HOME = "C:\path\to\jdk-17"
.\gradlew.bat testDebugUnitTest assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

推送 `v*` tag 后，GitHub Actions 会使用仓库的加密签名 secrets 构建 APK、验证签名、核对固定的发布证书、生成 SHA-256 校验文件并发布 Release。签名身份和恢复原则见 [`SIGNING.md`](SIGNING.md)。

## 文档

- [交接记录](docs/STATUS.md)：当前主线、各轮验证边界与下一步。
- [`SIGNING.md`](SIGNING.md)：发布签名身份与恢复原则。
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)：第三方资源来源与许可。

## 项目与资源来源

- 模块使用 [libxposed API](https://github.com/libxposed/api) 102。
- 系统过渡行为的分析基于 Android WindowManager Shell 的运行时行为和 Android framework 资源。
- 应用图标取自 Action Launcher 52.0 的 `com.actionlauncher.playstore` 安装包，仅用于表明本模块的适配对象。
- Action Launcher 名称、图标及相关品牌资产的权利属于其各自权利人。本项目与 Action Launcher 官方没有隶属或授权关系。
