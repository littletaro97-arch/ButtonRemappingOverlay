# 游戏按钮映射 V6.2 文件治理

日期：2026-08-18

本轮为单执行者修复，不启用并行写入。历史目录全部只读。

## 允许修改

- `E:\按钮重映射\app\build.gradle.kts`
- `E:\按钮重映射\app\src\main\java\com\example\buttonremapping\highrisk\HighRiskOverlayService.kt`
- `E:\按钮重映射\app\src\main\java\com\example\buttonremapping\highrisk\HighRiskEditorActivity.kt`
- `E:\按钮重映射\app\src\main\java\com\example\buttonremapping\LongPressOverlayService.kt`
- 为 V6.2 新增的纯策略源码与单元测试
- `E:\按钮重映射\UPDATE.md`
- `E:\Deepseek output\游戏按钮映射\` 下新增的 V6.2 计划、验证、验收、清单和 APK

## 只读对照

- `C:\Users\LittleTaro\Documents\Codex\2026-08-14\android-app-overlay-shizuku-root-magisk\outputs`
- `E:\Deepseek output\游戏按钮映射\codex fix`
- V6.1 及以前全部交付物

## 禁止事项

- 不使用 ADB。
- 不覆盖或删除历史版本。
- 不把编译、静态测试等同于真实游戏/设备验收。
