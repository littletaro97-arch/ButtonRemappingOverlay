# 游戏按钮映射 v6.7 阶段验收

| 门禁 | 状态 | 说明 |
|---|---|---|
| Gate 1：版本与 Release 策略 | PASS | 版本比较、唯一 APK、GitHub 域名和 SHA-256 格式测试通过 |
| Gate 2：下载前确认 | CODE COMPLETE / DEVICE NOT RUN | 发现更高版本后必须由用户点击“下载更新” |
| Gate 3：下载后校验 | CODE COMPLETE / DEVICE NOT RUN | 哈希、包名、versionCode、签名任一不符均拒绝安装 |
| Gate 4：Android 安装交接 | CODE COMPLETE / DEVICE NOT RUN | 无静默安装；必要时引导“安装未知应用”，最终由系统安装器确认 |
| Gate 5：v6.7 构建 | PASS | 单元测试、Lint、debug APK 构建全部成功 |
| Gate 6：GitHub 未来版本闭环 | NOT RUN | 尚未发布 v6.8，未执行 v6.7 → v6.8 真机覆盖更新 |

## 自动验证结果

- 单元测试：13/13 通过，0 failures，0 errors，0 skipped。
- Android Lint：0 errors，64 warnings。
- APK：versionName `6.7`，versionCode `40`，applicationId `com.example.buttonremapping`。
- APK SHA-256：`8567D54719F784419A05476BDD0F7DF48EF708314771B610FBC6E93C9F2ED1BC`。
- 签名证书 SHA-256：`bea44b037cace6a94e52549a5633382020c0128bf0c588d3f95a82f5f1344e2f`。

本文件不把本地自动验证描述为 GitHub 自动更新端到端通过。
