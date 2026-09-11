# 游戏按钮映射 v6.7 构建验证

## 构建身份

- versionName：`6.7`
- versionCode：`40`
- applicationId：`com.example.buttonremapping`
- 变体：`debug`
- minSdk：`26`

## 自动验证

- `:app:testDebugUnitTest`：PASS，13 tests，0 failures，0 errors，0 skipped。
- `:app:lintDebug`：PASS，0 errors，64 warnings。
- `:app:assembleDebug`：PASS。
- APK Manifest 已确认包含 `INTERNET` 和 `REQUEST_INSTALL_PACKAGES`。

## APK

- 工作区路径：`E:\按钮重映射\app\build\outputs\apk\debug\app-debug.apk`
- 交付路径：`E:\Deepseek output\游戏按钮映射\button-remapping-v6.7-debug.apk`
- 大小：`2387244` bytes
- SHA-256：`8567D54719F784419A05476BDD0F7DF48EF708314771B610FBC6E93C9F2ED1BC`
- 签名证书 SHA-256：`bea44b037cace6a94e52549a5633382020c0128bf0c588d3f95a82f5f1344e2f`

## GitHub 现状

- 2026-09-11 查询到的 Latest Release 是 v6.2，而不是 v6.7 或更高版本。
- v6.2 Release APK 的签名证书与本 APK 一致。
- 本轮未创建 tag、未推送提交、未发布 GitHub Release。
- 因不存在 versionCode 大于 40 的正式 Release，端到端自动更新验证未执行。
