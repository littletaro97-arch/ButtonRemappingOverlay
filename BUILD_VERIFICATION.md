# V0.3 本地构建与设备验证记录

## 构建

- 工作目录：`E:\按钮重映射`
- 包名：`com.example.buttonremapping`
- 版本：`versionCode 3 / versionName 0.3.0`
- 目标 SDK：35
- 命令：

```powershell
$env:ANDROID_HOME='C:\Users\LittleTaro\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
.\gradlew.bat --no-daemon lintDebug assembleDebug
```

结果：`lintDebug` 和 `assembleDebug` 通过，无编译错误。Gradle 输出仅包含 Android 平台弃用 API 警告，没有阻断问题。

## 最终 APK

- 路径：`E:\按钮重映射\app\build\outputs\apk\debug\app-debug.apk`
- 大小：`900,656 bytes`
- SHA-256：`F533E2892067C1576ADEE1BEFC24E523CDE4A7D7CF3600D559DC824F03997475`
- `adb install -r`：成功

## ADB 真机证据

设备：`XCU47H455LJJUODQ`，型号 `PKT110`，物理尺寸 `1216×2640`。

- 首页显示“游戏按钮映射”，低风险入口可用，高风险入口禁用；重新安装后的系统应用详情显示版本 `0.3.0`。
- 通过系统文件选择器选择设备截图，读取尺寸 `2640×1216`；截图编辑器将其铺满 `[0,0][2640,1216]` 画布。
- 单矩形拖动后为 `[1298,553][1747,711]`，缩放后为 `[1298,553][1953,814]`；保存后进程重启再打开仍保持该位置和大小。
- 形状滑块从“形状 圆形”切换到“形状 正方形”并持久化；运行态使用圆形时生成 32 个内部透明拦截条。
- 进入截图编辑器时没有“原按钮屏蔽区域”或“屏蔽开关”窗口，服务记录为空。
- 横屏运行态屏蔽矩形为 `(1298,553)(655×261)`，悬浮开关自动调整为 `(1996,553)(172×147)`，两者无交集。
- 将悬浮开关长按拖向屏蔽区后，最终仍调整到 `(1996,553)(172×147)`，位置被保存。
- 计算器测试中：屏蔽 ON 时中心“8”无响应、屏蔽区外“5”可输入；圆形模式下圆角外“清除”点击可穿透，中心“8”仍被拦截。
- 点击悬浮开关后屏蔽窗口数量从 `32` 变为 `0`，再次点击恢复为 `32`；最后强制停止服务后无残留 Overlay 窗口和服务记录。
- 最后已停止测试服务；没有实现任何输入注入或自动操作。

## 静态范围检查

源代码未发现 Shizuku、Magisk、AccessibilityService、InputManager、`injectInputEvent`、MotionEvent 注入、shell input 或 ADB 控制实现引用。高风险方案仍只有 UI 入口和 `HighRiskManager` 占位。
