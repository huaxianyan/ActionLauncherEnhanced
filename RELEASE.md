# 构建与发布

## 版本与产物

- 版本号在模块的 `build.gradle.kts` 里（`versionName` / `versionCode`，`versionCode` 严格递增）。
- 产物 `ActionLauncherEnhanced-<版本>.apk`，Debug 与发布包都用正式身份签名（2026-11-13 用户要求）。

## 签名

- 签名材料从 `~/.android/signing/ActionLauncherEnhanced/signing.properties` 读取，不入库。
- CI 用仓库 secrets 里的正式私钥签名。
- 设备上若还装着旧的 debug 证书包，换签名需要一次性卸载重装——属单独决定，先问用户。

## 流水线

- 推 `v*` tag 触发 GitHub Actions，自动 `gh release create --generate-notes`。
- 随后本地用 `gh release edit --notes-file docs/release-notes/v<版本>.md` 换成正式说明（含适用系统与构建信息表）。
- 仓库 `settings.gradle.kts` 里的阿里云镜像只在 `CI` 环境变量为空时启用；镜像 5xx 会让整个依赖解析失败（2026-11-13 就是这样挂过一次），CI 必须走官方源。

## 已发布

- 0.6.0（2026-11-13）是首个带正式说明的 Release，内容含 AOD 解锁动画、Home 抽屉与高刷修复。
- 其中 `mode6 + LIFT` 组合与 Home 帧率请求的真实掉帧复测未完成，发布说明如实写明。
