# Action Launcher Enhanced

[![Build](https://github.com/huaxianyan/ActionLauncherEnhanced/actions/workflows/build.yml/badge.svg)](https://github.com/huaxianyan/ActionLauncherEnhanced/actions/workflows/build.yml)

Action Launcher Enhanced 是一个面向 Action Launcher 的 LSPosed 增强模块。目前用于改善 Android 16 上从应用返回桌面时的壁纸与内容动画。

## 功能

- 修复按 Home 返回 Action Launcher 时壁纸短暂变黑的问题；
- 消除 Action Launcher 原生桌面动画开始前的内容闪烁；
- 让 Home 与 Back 返回桌面时使用相同的原生动画起播时机；
- 保留 Action Launcher 自己的淡入、位移、时长和插值曲线；
- 不影响 Pixel Launcher 的 Quickstep Remote Transition。

## 适用环境

当前版本针对以下环境开发并完成真机验证：

- Pixel 10 Pro；
- Android 16，SDK 36；
- Action Launcher 52.0；
- LSPosed 2.1.1，modern API 102；
- 三按钮导航。

模块使用定向的系统和 Action Launcher 内部 Hook。Android 系统或 Action Launcher 更新后，如果目标实现发生变化，可能需要重新适配。

## 安装

1. 从 [Releases](https://github.com/huaxianyan/ActionLauncherEnhanced/releases) 下载 APK；
2. 安装并在 LSPosed 中启用模块；
3. 确认静态作用域包含：
   - `com.android.systemui`
   - `com.actionlauncher.playstore`
4. 重启设备。

模块没有设置界面，不会修改 Action Launcher 数据、壁纸、导航模式或系统设置。

## 构建

需要 JDK 17 和 Android SDK 36：

```powershell
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
$env:JAVA_HOME = "C:\path\to\jdk-17"
.\gradlew.bat testDebugUnitTest assembleDebug
```

Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

推送 `v*` tag 后，GitHub Actions 会使用仓库的加密签名 secrets 构建 APK、验证签名、核对固定的发布证书、生成 SHA-256 校验文件并发布 Release。签名身份和恢复原则见 [`SIGNING.md`](SIGNING.md)。

## 项目与资源来源

- 模块使用 [libxposed API](https://github.com/libxposed/api) 102；
- 系统过渡行为的分析基于 Android WindowManager Shell 的运行时行为和 Android framework 资源；
- 应用图标取自 Action Launcher 52.0 的 `com.actionlauncher.playstore` 安装包，仅用于表明本模块的适配对象；
- Action Launcher 名称、图标及相关品牌资产的权利属于其各自权利人。本项目与 Action Launcher 官方没有隶属或授权关系；
- 更完整的资源说明见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

## 安全边界

- 不伪造 Quickstep Provider；
- 不全局 Hook `SurfaceControl.Transaction.setAlpha()`；
- 不修改 Action Launcher APK 或用户数据；
- 目标 Hook 失效时记录错误并停止对应修复，不使用扩大范围的回退方案。
