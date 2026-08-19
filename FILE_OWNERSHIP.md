# 文件归属与修改边界

本项目当前由单一执行者实现，不启用并行写入。该文件保留明确边界，避免后续扩展把第二阶段能力混入第一阶段。

| 文件/目录 | 当前归属 | 允许内容 | 禁止内容 |
|---|---|---|---|
| `app/src/main/java/com/example/buttonremapping/MainActivity.kt` | 主界面 | 应用名称、截图选择、低风险编辑/服务入口 | 输入注入、自动控制 |
| `app/src/main/java/com/example/buttonremapping/EditorActivity.kt` | 旧版屏幕编辑器 | 无截图时的单区域拖动、缩放、透明度、保存 | 多按钮、宏、游戏控制 |
| `app/src/main/java/com/example/buttonremapping/ScreenshotEditorActivity.kt` | 截图编辑器 | 单矩形截图预览、拖动、缩放、比例保存 | OCR、AI 识别、自动找按钮 |
| `app/src/main/java/com/example/buttonremapping/EditorViews.kt` | 编辑组件 | 编辑态标签、拖动、缩放手柄、截图画布 | 运行态输入注入 |
| `app/src/main/java/com/example/buttonremapping/LayoutPrefs.kt` | 配置 | 截图 URI/尺寸、单区域比例坐标与悬浮开关 | 原始像素作为唯一坐标 |
| `app/src/main/java/com/example/buttonremapping/OverlayGeometry.kt` | 几何 | Insets、安全区、截图比例映射、宽高比提示、边界夹紧 | 全屏吞触摸的区域计算 |
| `app/src/main/java/com/example/buttonremapping/OverlayService.kt` | 运行态 | 前台服务、两个局部 Overlay 窗口、清理 | Shizuku/Root/Accessibility/注入 |
| `app/src/main/java/com/example/buttonremapping/OverlayViews.kt` | Overlay 视图 | 局部拦截、点击反馈、动画、震动 | 发送游戏触摸事件 |
| `app/src/main/res/` | 资源 | 主题、字符串、图标元数据 | 大型视觉效果、无关页面 |
| `PROJECT_PLAN.md`、`PHASE_*_ACCEPTANCE.md` | 治理/验收 | 范围、证据、未执行项 | 把未运行的设备测试写成通过 |
| `outputs/` | 用户交付物 | 构建产物与报告 | 中间缓存、设备备份 |

任何第二阶段功能必须新增计划与门禁，不得借“原型按钮”名义提前加入注入或自动化能力。
