# 正式发布流程

正式版和 Beta 都通过 `.github/workflows/release.yml` 构建。标签分别使用 `vX.Y.Z` 或 `vX.Y.Z-beta.N`，必须指向 `main` 中的提交，并与 Android `versionName` 和对应发布说明一致。

## 签名秘密

GitHub Actions 仅读取以下 Repository Secrets：

- `YANJIAN_RELEASE_KEYSTORE_BASE64`
- `YANJIAN_RELEASE_KEYSTORE_PASSWORD`
- `YANJIAN_RELEASE_KEY_ALIAS`
- `YANJIAN_RELEASE_KEY_PASSWORD`

签名文件只在临时 Runner 中解码，工作流结束时删除。仓库、普通 CI、Issue、PR、日志和构建缓存都不得包含密钥或密码。

Android 要求同一应用后续更新持续使用同一签名证书。创建正式签名密钥前，必须确定至少两个独立、加密、可恢复的备份位置，并由仓库所有者完成恢复演练。密钥丢失或泄露会破坏后续更新能力。

仓库中的 `release/signing-certificate.sha256` 是公开证书指纹信任锚。流水线不仅检查 APK“存在有效签名”，还会要求实际签名证书与该指纹完全一致；稳定版和 Beta 不得使用不同密钥。

## 工作流门槛

1. 标签、版本号、versionCode、发布说明和 `main` 祖先关系一致。
2. JVM 单元测试、Android Lint 和压缩 Release 构建通过。
3. APK 使用只从 Secrets 注入的正式密钥签名。
4. `apksigner` 验证通过，并导出公开证书信息。
5. 生成 APK/证书/SBOM 的 SHA-256、CycloneDX SBOM 和 GitHub 构建来源证明。
6. 资产上传成功后才创建 GitHub Release；带预发布标识的版本自动标记为 Beta。

应用内更新依赖 GitHub Releases API 为 APK 资产返回 `size`、`digest` 和 `browser_download_url`。APK 文件名必须为 `yanjian-v<version>.apk`，标签、Android `versionName`、资产名和发布下载路径必须完全一致，否则客户端只提供发布页回退而不会自动安装。

用户可以使用 `gh attestation verify <apk> --repo mikutea/sms-to-email-forwarder` 验证构建来源，并使用 `sha256sum -c SHA256SUMS.txt` 验证下载完整性。
