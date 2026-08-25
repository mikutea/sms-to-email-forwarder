# 1.0.0 权限、SMTP 与更新体验审计

## 审计步骤

1. 在隔离的 Android API 35 虚拟设备中安装原预发布 APK，撤销短信权限并移除电池优化豁免。
2. 逐项复现后台授权、应用设置回退、电池优化入口、SMTP 端口/加密错配和旅行守护阻塞提示。
3. 将短信与电池优化改为系统单应用确认；对厂商设置使用可解析入口和应用详情安全回退。
4. 把后台守护清单改成四个可解释状态，取消一个人工确认等同三个系统权限的错误映射。
5. 加入 SMTP 端口联动、飞书预设、安全错误分类、稳定版/Beta 更新通道和统一玻璃态确认框。
6. 在相同视口复测所有路径，并成对检查修复前后截图、文字层级、点击目标、状态刷新和异常回退。

## 结果

整体健康状态：通过。权限入口不再落入空页面；短信和电池优化均能显示系统确认；返回后真实状态自动刷新。SMTP 的 STARTTLS 与 587 保持一致；旅行守护不会在存在阻塞项时误开启，并以统一玻璃卡片给出首个可执行动作。Beta 通道在维护页可选择并持久化。

厂商自启动、关联启动和后台活动没有 Android 标准运行时权限接口，不能由 App 直接勾选或验证。雁笺会打开可用系统入口；用户在系统页完成后进行一次明确确认，真实可靠性仍由锁屏短信闭环验证。

## 截图证据

- `current/01-system-guardian-current.png`：原 1/6 状态将一个人工确认拆成三个伪权限。
- `fixed/01-system-guardian-fixed.png`：四项真实/可解释状态与逐项入口。
- `fixed/02-sms-permission-dialog-fixed.png`：短信运行时权限系统确认。
- `current/04-battery-settings-current.png` 与 `fixed/03-battery-permission-dialog-fixed.png`：从通用列表改为本应用直接确认。
- `current/03-app-settings-result-current.png` 与 `fixed/04-app-settings-fallback-fixed.png`：不可执行入口改为可解析的应用详情回退。
- `current/05-smtp-port-mismatch-current.png` 与 `fixed/05-smtp-port-sync-fixed.png`：465/STARTTLS 错配改为 587/STARTTLS 联动。
- `current/06-travel-dialog-current.png` 与 `fixed/06-travel-glass-dialog-fixed.png`：平台默认弹窗改为可执行的玻璃态阻塞清单。
- `fixed/08-beta-selected-fixed.png`：Beta 更新通道。

验证环境只使用合成数据，不包含真实短信、邮箱地址、SMTP 授权码或签名材料。
