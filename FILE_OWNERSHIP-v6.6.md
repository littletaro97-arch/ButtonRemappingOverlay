# 游戏按钮映射 v6.6 文件边界

## 本轮允许修改

- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/buttonremapping/OverlayGeometry.kt`
- `app/src/main/java/com/example/buttonremapping/*EditorActivity.kt`
- `app/src/main/java/com/example/buttonremapping/highrisk/HighRiskEditorActivity.kt`
- `app/src/main/java/com/example/buttonremapping/MainActivity.kt`
- `app/src/main/java/com/example/buttonremapping/RuntimeProtection.kt`
- `app/src/main/java/com/example/buttonremapping/RuntimeProtectionActivity.kt`
- v6.6 新增的运行策略与静态测试文件
- `PROJECT_PLAN-v6.6.md`、`FILE_OWNERSHIP-v6.6.md`
- `UPDATE.md`、`PHASE_ACCEPTANCE-v6.6.md`、`FULLSCREEN_INVESTIGATION-v6.6.md`
- `BUILD_VERIFICATION-v6.6.md`、`MANUAL_DEVICE_CHECKLIST-v6.6.md`

## 本轮禁止修改

- `input-test-app/` 当前未提交实验代码
- `touch-interaction-probe/`
- v6.5 以前的验收和构建记录
- 在 Gate 5 通过前修改 `versionName`、`versionCode` 或生成 v6.6 APK
