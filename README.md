# 雁笺（Yanjian）

雁笺是一款面向自有 Android / 鸿蒙 2–4 设备的短信转邮箱应用。短信在手机本地进入加密队列，再直连用户自己的 SMTP 服务；不需要第三方中继服务器、Root、无障碍或通知读取权限。

> 当前版本：`v0.3.0-beta.3`（视觉与交互校正版）。八个页面已在 Android 15 虚拟设备上逐屏对照设计基线，自动化测试、Android Lint、Debug/Release 构建及短信回归已经通过；厂商后台策略与长时运行仍需在受支持的真实设备上验收，因此暂不标记稳定版。

本仓库公开提供源代码与仅含合成数据的测试。请勿在 Issue、Pull Request、日志或构建产物中提交 SMTP 授权码、真实短信、手机号、设备标识或签名材料。

## 功能

- 接收新短信后调度秒级发送，不读取历史短信；支持多段长短信合并和双卡卡槽规则。
- AES-GCM 加密本机待发队列与历史记录；稳定消息 ID、并发租约、指数退避、容量上限和断网恢复。
- 主/备用 SMTP、多个收件人，以及主通道、失败切换、双通道三种投递策略。
- 仅允许 SMTP over TLS 或强制 STARTTLS，并保持服务器证书主机名校验。
- 发送方白名单/黑名单、关键词、线性时间正则、验证码、时段、星期和正文脱敏规则。
- 旅行守护：真实短信闭环门槛、华为后台授权引导、6/12/24 小时心跳、低电量与断电提醒。
- 加密历史、失败重发、队列清理、规则预览、无密码配置导入导出和脱敏诊断报告。
- 每天最多一次检查项目官方 GitHub 稳定版本，也可手动检查；下载和安装始终由用户确认。
- “雁笺”月白青玉视觉：统一折纸飞雁图标、轻量软拟态内容层、连续玻璃四项一级导航，以及遵循系统“减少动画”设置的流畅交互。

## 平台支持边界

| 平台 | 自动读取新短信 | 客户端形态 | 当前状态 |
| --- | --- | --- | --- |
| Android 6.0（API 23）及以上 | 支持 | APK | `v0.3.0-beta.3`，待扩展真机矩阵 |
| 保留 Android APK 兼容能力的 HarmonyOS 设备 | 支持 | 与 Android 共用 APK | 需要按设备和系统版本完成真机验证 |
| HarmonyOS NEXT | 普通三方应用无公开短信读取能力 | 原生 HAP 只能承担配置、状态等伴随功能 | 不宣称支持自动短信转发 |

HarmonyOS NEXT 若要完整接入，需要把短信入口迁移到运营商网关、独立蜂窝短信设备，或继续由 Android/鸿蒙 4.x 设备接收。项目不会使用无障碍模拟点击、通知偷读或私有 API 冒充 NEXT 支持。

## 工作原理

```text
SIM 新短信
  -> Android / HarmonyOS 2–4 系统短信广播
  -> 雁笺本机 AES-GCM 加密队列
  -> WorkManager 受约束发送与退避重试
  -> 用户自己的 TLS SMTP 服务
  -> 一个或多个收件邮箱
```

直连 SMTP 能去掉第三方服务器，但不能保证严格 exactly-once：若服务器已经接收邮件、手机却在收到成功响应前断网，重试可能产生重复邮件。强制停止、关机、重启后尚未首次解锁、SIM 无服务和邮件服务商延迟也超出普通 App 的控制范围。

## 快速开始

1. 安装 Debug/正式签名 APK，填写 SMTP 主机、端口、用户名、授权码、发件与收件邮箱。
2. 发送 SMTP 测试邮件；不要把网页登录密码当作授权码。
3. 确认短信外发隐私风险，启用自动转发并授予“接收短信”权限。
4. 在华为“应用启动管理”中改为手动管理，允许自启动、关联启动和后台活动，并关闭该应用的电池优化限制。
5. 用一条真实短信完成闭环，再分别验证亮屏、锁屏、Wi-Fi、移动网络和断网补发。
6. 所有离家条件均通过后，再开启“旅行守护”。

详见 [华为 HarmonyOS 4.x 真机设置](docs/harmonyos-4-install.md)、[旅行守护验收](docs/travel-mode.md) 和 [SMTP 配置与安全](docs/smtp-configuration.md)。

## 开发与构建

Android 客户端位于 `clients/android/`，使用 JDK 17、Gradle Wrapper 8.9、Android SDK Platform 35：

```powershell
cd clients/android
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease
```

GitHub Flow：Issue / Milestone 明确范围，从 `main` 创建功能分支，本地验证后提交 Pull Request，由 GitHub Actions 在同一提交上复验，再进行审查和合并。正式 Release APK 需要项目专用签名密钥；密钥和密码不得进入仓库。

## 仓库结构

- `clients/android/`：Android 与 HarmonyOS 2/3/4 兼容客户端。
- `docs/`：平台边界、SMTP、旅行守护、隐私安全与开发说明。
- `.github/workflows/`：单元测试、Lint 和 APK 构建。

版本变化见 [CHANGELOG.md](CHANGELOG.md)，正式签名与验证见 [发布流程](docs/release-process.md)，安全边界与报告方式见 [SECURITY.md](SECURITY.md)。本项目仅用于设备所有者明确授权的短信；部署者负责遵守适用的隐私、通信和数据保护要求。
