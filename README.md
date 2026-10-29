# yota+ refresh

为 **YOTA 3+ 墨水背屏**提供跨应用使用的全刷悬浮按钮，帮助清除残影。当前版本 **0.4**，基于此前的 0.3 版更名，刷新逻辑保持一致。

## 下载与使用

从本仓库的 [Releases](../../releases) 下载 `yota-plus-refresh.apk`。

1. 安装并打开 **yota+ refresh**。
2. 允许“显示在其他应用上层”，返回应用，点击“显示悬浮按钮”。
3. 点击背屏上的刷新箭头，请求一次全屏刷新。

拖动图标可调整位置，位置会被记住；长按约 0.9 秒可关闭。也可通过常驻通知或应用内按钮关闭。应用不开机自启。

## 兼容性与权限

- 已在 YOTA 3+、Android 8.1 / Y54 上运行过此前 0.3 版；本次 0.4 仅更名，仍需实际屏幕确认效果。
- 最低 Android 8.0（API 26），依赖系统自带的 `com.yotadevices.sdk` 共享库。
- **不支持原版 Android 7.1**，也不适用于普通 Android 手机；其他 YOTA 固件尚未验证。
- 只申请悬浮窗权限，不申请网络、存储、无障碍或 root 权限。
- 按钮只显示在墨水背屏。不会修改其他应用或系统的刷新配置。
- 全刷可改善残影，不能增加背屏物理分辨率，也不能改变系统字体粗细。

包名保留为 `local.yota.fullrefresh`。从相同签名的旧版升级可保留按钮位置；自行构建的签名不同则不能直接覆盖安装。

## 实现

通过反射加载设备自带的 YOTA SDK，识别 EPD Display，在该显示器的 display context 中创建 `TYPE_APPLICATION_OVERLAY` 悬浮窗。

点击后创建短暂的全屏刷新层（白色，alpha 为 1/255，不获取焦点、不接收触摸），设置 `setViewDithering(view, 1)`，然后调用 `Epd.fullUpdate(view, true)`。约 1.2 秒后移除临时层，期间忽略重复请求。

Y54 的 SDK 对 updateType=4 发送一次性刷新请求，因此调用后回读常驻 updateType 不一定是 4。API 调用成功不等于物理清屏成功；残影改善以实际屏幕观察为准。

## 从源码构建

需要 Python 3、JDK 17、Android SDK Platform 27 与 Build-Tools 35.0.0，无 Gradle/Maven 或第三方运行依赖。

配置 `JAVA_HOME` 和 `ANDROID_HOME` 后，设置以下环境变量：

| 变量 | 用途 |
| --- | --- |
| `YOTA_KEYSTORE` | 本地签名密钥文件路径，放在仓库外 |
| `YOTA_STORE_PASSWORD` | 密钥库密码 |
| `YOTA_KEY_ALIAS` | 密钥别名，默认 `yota` |
| `YOTA_KEY_PASSWORD` | 可选，私钥密码与密钥库密码不同时设置 |

```sh
python build.py
```

也可通过 `--java-home`、`--build-tools`、`--android-jar` 指定工具路径。产物为 `build/yota-plus-refresh.apk`，脚本自动验证 APK 对齐和签名并输出 SHA-256。

请使用自己的签名密钥。仓库不包含签名私钥、密钥密码、手机备份、固件、设备标识、日志或本地工具；生成文件和密钥扩展名已加入 `.gitignore`。发布的 APK 包含 Android 正常安装校验所需的签名公钥证书，不包含签名私钥。
