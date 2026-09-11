# 游戏按钮映射 v6.6 阶段验收

| 门禁 | 状态 | 说明 |
|---|---|---|
| Gate 1：180° 坐标翻转 | PASS / DEVICE NOT RUN | 相反横屏与相反竖屏统一为相同逻辑方向；坐标策略 3 项单元测试通过 |
| Gate 2：编辑器沉浸式全屏 | CODE COMPLETE / DEVICE NOT RUN | 四类区域编辑器均隐藏状态栏和导航栏；nova 5 Pro 未连接、未实测 |
| Gate 3：全屏设置检测与入口 | PASS / DEVICE NOT RUN | 实际窗口覆盖检测 + 通用显示设置入口；华为路径仅在明确识别为华为时显示，荣耀和其他厂商反例测试通过 |
| Gate 4：最近任务隐藏 | CODE COMPLETE / DEVICE NOT RUN | 使用 `ActivityManager.AppTask.setExcludeFromRecents` 即时切换并允许恢复；不保证后台存活 |
| Gate 5：v6.6 构建 | PASS | `testDebugUnitTest`、`lintDebug`、`assembleDebug` 全部成功 |

## 当前版本状态

- 单元测试：10/10 通过，0 failures，0 errors，0 skipped。
- Android Lint：0 errors，63 warnings。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，versionName `6.6`，versionCode `39`。
- APK SHA-256：`B4599F2EAFB0EF393CAB30E5D56EC0381DB117B5E88FB4754469DB95B5E3830F`。
- 未连接 Android 设备；最近任务、沉浸式全屏、窗口检测和 nova 5 Pro 行为均为 `DEVICE NOT RUN`，不能视为真机验收通过。
