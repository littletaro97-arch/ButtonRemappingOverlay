# V0.5.1 Overlay 启动兼容性修复文件边界

- `app/src/main/java/com/example/buttonremapping/OverlayService.kt`：本轮唯一业务代码写入文件，负责低风险 Overlay 创建、恢复和异常保护。
- `app/src/main/java/com/example/buttonremapping/OverlayViews.kt`：只读检查；除非编译或触摸行为证明必须修改，否则不改动。
- `BUILD_VERIFICATION-v0.5.1-overlay-startup-fix.md`：构建与验证记录。

不修改高风险输入注入、Profile 数据结构、测试 App 和真实游戏相关代码。
