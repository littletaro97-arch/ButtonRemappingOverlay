# 游戏按钮映射 V0.4.0 构建与设备记录

日期：2026-08-14

## 构建

- 命令：`gradlew.bat --no-daemon lintDebug assembleDebug :input-test-app:assembleDebug`
- 结果：`BUILD SUCCESSFUL`
- Lint：通过；仅有 Android API 弃用提示，没有错误。
- 主 App 版本：`0.4.0`，versionCode `4`。
- 主 APK：`app/build/outputs/apk/debug/app-debug.apk`
- 主 APK SHA-256：`9F8E07D86F841CD6AF1EF0C772C79617E536DE568862E6878BE1A3DDC57A979B`
- input-test-app APK：`input-test-app/build/outputs/apk/debug/input-test-app-debug.apk`
- input-test-app SHA-256：`1A3ED4C2145520BB190870F2F7B271EBF8E44769E68B17E07DA50A97D0B672A0`

## 设备

- 设备：`XCU47H455LJJUODQ` / `PKT110`
- Android：`16` / API 36
- 物理屏幕：`1216 x 2640`；测试时锁定横屏为 `2640 x 1216`
- 主 App、input-test-app、官方 Shizuku v13.6.0 均已通过 ADB 安装。
- Overlay 权限：已确认 `SYSTEM_ALERT_WINDOW: allow`。

## 结论

代码、编辑器、测试应用和保护逻辑已构建并安装。Shizuku 服务进程可以启动，但设备的 ColorOS 报告 ADB 权限受限，授权流程未进入可授权状态。因此“虚拟按钮 -> Shizuku -> 目标坐标”的系统注入链路仍为“未验证”。

没有安装、启动或操作任何真实游戏。
