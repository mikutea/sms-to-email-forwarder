# 开发与验证

## Android 工具链

- JDK 17
- Gradle Wrapper 8.9
- Android SDK Platform 35
- Android Build Tools 35.0.0

构建命令：

```powershell
cd clients/android
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Linux CI 使用对应的 `./gradlew` 命令。

## 服务器共享工作区

正式源码位于服务器共享目录。Gradle 在 SMB 上执行 `clean` 时可能把中间目录错误地表现为文件，因此发布验证应把同一份源码复制到本机 NTFS 临时构建目录，再执行干净构建。复制前后需要比较所有非生成源码的 SHA-256，确保验证的正是服务器工作区版本。

构建输出、`local.properties`、签名文件和 SMTP 配置均不得提交。

## 发布边界

- Debug APK 仅用于目标手机联调。
- Release APK 必须使用项目专用发布密钥签名；密钥和密码不能进入 GitHub。
- 没有完成目标手机的锁屏、断网恢复和真实短信测试前，不标记稳定版本。
- SMTP 测试成功只证明邮件出口可用，不证明系统会在后台持续交付短信广播。

## 版本状态

`v0.2.0-beta.1` 已通过本地 JVM 单元测试、Android Lint、Debug 和 Release APK 构建。合并前 GitHub Actions 必须在 Pull Request 的准确 HEAD 上重复执行同一组检查。目标手机验收完成前不创建稳定版标签或公开签名 Release。
