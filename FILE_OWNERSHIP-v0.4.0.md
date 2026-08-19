# V0.4 文件归属与修改边界

| 文件/目录 | 允许内容 | 禁止内容 |
|---|---|---|
| `app/src/main/java/com/example/buttonremapping/MainActivity.kt` | 方案入口、高风险警告、低/高风险页面切换 | 注入实现、低风险服务依赖高风险模块 |
| `app/src/main/java/com/example/buttonremapping/highrisk/` | 高风险配置、编辑器、状态、Overlay、Shizuku 适配 | 多按钮、宏、长按/滑动、真实游戏逻辑 |
| `app/src/main/java/com/example/buttonremapping/lowrisk/` | 如需新增，仅保留低风险入口适配 | 输入注入、Shizuku 调用 |
| `app/src/main/java/com/example/buttonremapping/LayoutPrefs.kt` | 兼容现有配置；高风险配置使用独立键空间 | 覆盖低风险已有键值 |
| `input-test-app/` | 独立测试应用、触摸计数、事件日志和目标区域 | 游戏包名、游戏资源、自动操作 |
| `shizuku-user-service/` 或 `app/.../shizuku/` | 最小化 UserService Binder 与一次性 Tap | Root、Accessibility、Hook、反作弊 |
| `PROJECT_PLAN-v0.4.0.md`、`PHASE_*_ACCEPTANCE-v0.4.0.md` | 计划、门禁和实测证据 | 把未执行测试写成通过 |
| `outputs/` | V0.4 APK、构建报告、设备报告 | 中间缓存、真实游戏 APK |
