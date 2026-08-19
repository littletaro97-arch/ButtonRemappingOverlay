# V0.6.0 权限与厂商兼容性文件边界

- `app/src/main/java/com/example/buttonremapping/RuntimeProtection.kt`：权限状态、设置跳转、厂商识别和诊断数据。
- `app/src/main/java/com/example/buttonremapping/RuntimeProtectionActivity.kt`：权限与运行保障页面、首次设置引导。
- `app/src/main/java/com/example/buttonremapping/MainActivity.kt`：首页入口和低风险启动前检查。
- `app/src/main/java/com/example/buttonremapping/highrisk/HighRiskActivity.kt`：高风险启动前检查。
- `app/src/main/java/com/example/buttonremapping/OverlayService.kt`、`HighRiskOverlayService.kt`：生命周期日志与前台状态标记。
- `app/src/main/java/com/example/buttonremapping/LowRiskManager.kt`、`highrisk/HighRiskManager.kt`：停止事件诊断记录。
- `app/src/main/AndroidManifest.xml`、`app/build.gradle.kts`：权限与版本声明。

不修改 Profile、编辑器、输入注入算法和 input-test-app 核心代码。
