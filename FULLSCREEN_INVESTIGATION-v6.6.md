# v6.6 全屏显示排查记录

## 华为系统侧结论

华为官方说明：大部分应用会自动适应全屏；未自动适应的应用需要用户进入：

`设置 > 显示和亮度 > 更多显示设置 > 应用全屏显示`

并为具体应用开启开关。官方同时提示，强制开启后可能出现内容拉伸等异常。

参考：

- https://consumer.huawei.com/cn/support/content/zh-cn15821022/
- https://consumer.huawei.com/uk/support/content/en-gb01087355/

## 应用代码侧确定缺陷

v6.5 中：

- `HighRiskEditorActivity` 隐藏 `WindowInsets.Type.systemBars()`，包含状态栏和导航栏。
- `ScreenshotEditorActivity`、`LongPressEditorActivity`、`DoubleTapEditorActivity` 和空白布局 `EditorActivity` 只隐藏状态栏。
- Android 11 以下的兼容分支同样缺少 `SYSTEM_UI_FLAG_HIDE_NAVIGATION`。

因此用户所称“应用其余部分可以全屏、编辑屏蔽区不能全屏”不必先归因于华为系统；应用自身的编辑页实现已经足以造成该现象。

本轮已统一为隐藏完整 `systemBars()`，旧系统分支同时增加隐藏导航栏和布局延伸到导航栏。

## Android 公开 API 边界

- Android 提供 `WindowInsetsController.hide(systemBars())`，应用可控制自己的沉浸式系统栏。
- Android 提供 `Settings.ACTION_DISPLAY_SETTINGS`，只能打开系统“显示”设置页面。
- Android 没有公开 API 用于读取华为 EMUI 的“应用全屏显示”单应用开关。
- Android 也没有公开 Intent 可保证直接定位到华为该应用的全屏开关。
- 硬编码华为 Settings 内部 Activity 名称不稳定、没有官方契约，本轮不采用。

参考：

- https://developer.android.com/develop/ui/views/layout/immersive
- https://developer.android.com/reference/android/provider/Settings

## 当前实现和限制

- 设置页新增“运行必须 > 全屏显示”。
- 使用窗口尺寸与最大显示尺寸检查明显的分屏、兼容模式或 letterbox 情况。
- 检测到未覆盖完整显示区域时显示提示。
- “去显示设置”使用公开 `Settings.ACTION_DISPLAY_SETTINGS`。

该检测验证实际窗口覆盖关系，不声称读取华为厂商开关本身。用户已接受“检测实际窗口覆盖 + 打开通用显示设置”的公开 API 方案。华为操作路径只在 `manufacturer` 或 `brand` 明确为 Huawei/华为时显示；荣耀及其他设备显示通用说明。v6.6 Gate 3 据此解除阻塞。
