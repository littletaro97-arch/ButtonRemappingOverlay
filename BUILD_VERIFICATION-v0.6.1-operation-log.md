# V0.6.1 构建验证记录

## 构建命令

```text
./gradlew.bat --no-daemon lintDebug assembleDebug testDebugUnitTest :input-test-app:testDebugUnitTest
```

## 结果

- `BUILD SUCCESSFUL`
- `lintDebug`：通过；仅有已有 Android API 弃用提示和冗余初始化提示
- `testDebugUnitTest`：`NO-SOURCE`
- `:input-test-app:testDebugUnitTest`：`NO-SOURCE`
- 未执行 ADB 安装、真机操作、Logcat 行为验证

## APK

- 路径：`C:\Users\LittleTaro\Documents\Codex\2026-08-14\android-app-overlay-shizuku-root-magisk\outputs\button-remapping-v0.6.1-operation-log-debug.apk`
- applicationId：`com.example.buttonremapping`
- versionCode：`8`
- versionName：`0.6.1`
- minSdk：`26`
- targetSdk：`35`
- SHA-256：`655CA1B05512B661BDA262034063782B5201D59092DFA4D89B33D444EAA11F80`

## 日志验证边界

本次只进行了静态编译产物验证。日志文件的实际创建、写入、复制和清空流程需要在设备上由用户操作确认。
