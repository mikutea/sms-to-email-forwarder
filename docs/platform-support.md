# 平台能力说明

## Android 与鸿蒙 2/3/4

Android 提供 `SMS_RECEIVED_ACTION`，持有 `RECEIVE_SMS` 权限的应用可以收到新短信广播。保留 Android APK 兼容能力的鸿蒙设备需要逐台完成真机验证。

参考：

- <https://developer.android.com/reference/android/provider/Telephony.Sms.Intents>
- <https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions>

## HarmonyOS NEXT

华为公开 Telephony Kit 的短信能力面向创建、发送短信、短信服务中心与设备能力查询；普通第三方应用没有可用于读取系统新短信正文的公开能力。电话能力同样区分系统应用与三方应用。

因此，本项目不会通过通知监听、无障碍自动点击或私有 API 冒充 NEXT 短信接收支持。这些方案既不能保证拿到完整正文，也不具备稳定的系统升级兼容性。

参考：

- <https://developer.huawei.com/consumer/cn/doc/harmonyos-guides-V13/telephony-overview-V13>
- <https://developer.huawei.com/consumer/cn/doc/doccenter-capabilities/declare-permissions-in-acl>

## NEXT 的可持续替代架构

如果产品必须在 NEXT 上完整运行，短信接收应移出手机应用：

1. 运营商或企业短信网关直接把短信事件送入中继服务；或
2. 独立 4G/5G 短信模块接收 SIM 短信并调用同一中继协议；或
3. 一台保留 Android/鸿蒙 4.x 的受控设备作为短信入口。

此时 NEXT HAP、Android APK 和网页端都只访问同一配置与状态服务，不再依赖 NEXT 读取系统短信。
