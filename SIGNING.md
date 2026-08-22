# Release signing

正式 Release 必须使用与 [`signing/release-certificate.pem`](signing/release-certificate.pem) 匹配的私钥签名。该证书是公开的签名身份基准，不包含私钥。

可使用以下命令计算证书 SHA-256：

```bash
openssl x509 -in signing/release-certificate.pem -outform DER \
  | sha256sum
```

GitHub Actions 会在发布前比较 APK 签名证书和仓库内的基准证书。两者不一致时立即终止，避免发布无法覆盖安装旧版本的 APK。

私钥库、alias 和密码不进入 Git：

- 私钥库及恢复信息保存在项目外的受控本地目录；
- 完整恢复副本保存在独立网络存储；
- GitHub Actions 仅通过仓库加密 secrets 获取签名材料。

需要恢复发布能力时，必须同时取得 PKCS#12 私钥库和 `signing-credentials.txt`。只有公开 PEM 证书无法签发 APK。
