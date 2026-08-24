# 正式发布流程

正式版本通过 `.github/workflows/release.yml` 从 `vX.Y.Z` 标签构建。标签必须指向 `main` 中的提交，且必须与 Android `versionName` 和 `docs/release-notes/X.Y.Z.md` 一致。

## 签名秘密

GitHub Actions 仅读取以下 Repository Secrets：

- `YANJIAN_RELEASE_KEYSTORE_BASE64`
- `YANJIAN_RELEASE_KEYSTORE_PASSWORD`
- `YANJIAN_RELEASE_KEY_ALIAS`
- `YANJIAN_RELEASE_KEY_PASSWORD`

签名文件只在临时 Runner 中解码，工作流结束时删除。仓库、普通 CI、Issue、PR、日志和构建缓存都不得包含密钥或密码。

Android 要求同一应用后续更新持续使用同一签名证书。创建正式签名密钥前，必须确定至少两个独立、加密、可恢复的离线备份位置，并由仓库所有者完成恢复演练。密钥丢失或泄露会破坏后续更新能力。

## 工作流门槛

1. 标签、版本号、versionCode、发布说明和 `main` 祖先关系一致。
2. JVM 单元测试、Android Lint 和压缩 Release 构建通过。
3. APK 使用只从 Secrets 注入的正式密钥签名。
4. `apksigner` 验证通过，并导出公开证书信息。
5. 生成 APK/证书/SBOM 的 SHA-256、CycloneDX SBOM 和 GitHub 构建来源证明。
6. 资产上传成功后才创建稳定 GitHub Release。

用户可以使用 `gh attestation verify <apk> --repo mikutea/sms-to-email-forwarder` 验证构建来源，并使用 `sha256sum -c SHA256SUMS.txt` 验证下载完整性。
