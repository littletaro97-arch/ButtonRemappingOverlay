# 游戏按钮映射 V0.5 构建记录

## 构建结果

执行：

```text
gradlew.bat --no-daemon lintDebug assembleDebug :input-test-app:assembleDebug
gradlew.bat --no-daemon testDebugUnitTest :input-test-app:testDebugUnitTest
```

结果：

- `BUILD SUCCESSFUL`
- `lintDebug` 通过，仅保留既有 Android API 弃用警告。
- 两个 `assembleDebug` 通过。
- 两个 `testDebugUnitTest` 为 `NO-SOURCE`，项目当前没有 JVM 单元测试。

## 运行边界

- 本轮未使用 ADB 安装或启动 APK。
- 本轮未查看设备 Logcat。
- 本轮未验证设备上的 Profile 迁移、创建、复制、切换、删除保护和 Overlay 重启行为。
- 未启动真实游戏。
