# 游戏按钮映射 v6.7 GitHub 自动更新验证计划

## 目标

1. 以 v6.6 为功能基线，版本升级为 v6.7 / versionCode 40。
2. 应用启动时限频检查 GitHub Latest Release。
3. 设置页提供不受限频影响的“立即检查更新”。
4. 发现更高版本后，经用户确认下载 APK；校验完成后调起 Android 系统安装器。

## 发布契约

- 仓库：`littletaro97-arch/ButtonRemappingOverlay`，必须保持公开可读。
- Release 必须是非草稿、非预发布的 Latest Release。
- tag 使用 `v<versionName>`，例如 `v6.8`。
- 每个 Release 只放一个 APK 资产，文件名为 `button-remapping-v<versionName>-debug.apk` 或 `button-remapping-v<versionName>-release.apk`。
- APK 的 `applicationId` 必须是 `com.example.buttonremapping`。
- APK 的 `versionCode` 必须高于已安装版本。
- APK 必须继续使用证书 SHA-256 `bea44b037cace6a94e52549a5633382020c0128bf0c588d3f95a82f5f1344e2f` 签名。
- GitHub Release API 返回的资产 SHA-256 必须与下载文件一致。

## 边界与风险

- Android 不允许普通应用静默安装更新；最终安装必须由用户在系统安装器确认。
- 用户还需要允许本应用“安装未知应用”。
- 当前链路使用 Android Debug 证书。若调换电脑、删除 debug keystore 或由 GitHub Actions 使用另一把 key 构建，覆盖更新会失败。
- 自动检查最长每 6 小时一次，避免频繁消耗 GitHub 未认证 API 限额；手动检查不受此限制。
- 本轮不发布 GitHub Release，不修改 v6.6 既有功能。

## 门禁

1. 纯策略测试覆盖版本比较、资产唯一性和 SHA-256 解析。
2. 下载包必须依次通过哈希、包名、版本号、签名校验。
3. `testDebugUnitTest`、`lintDebug`、`assembleDebug` 全部通过后才交付 v6.7 APK。
4. GitHub 端到端验证需先安装 v6.7，再发布同签名且 versionCode 更高的 v6.8；未执行前标记为 `NOT RUN`。
