# 游戏按钮映射（Button Remapping Overlay）

一个 Android 游戏触控布局辅助工具：在游戏画面上叠加虚拟按钮，把"按下虚拟按钮"映射为"在目标位置注入一次点击"，并拦截玩家对目标位置的原生点击，降低误触、实现改键。

当前版本：**v6.2**（versionCode 35，2026-08-18）

## 三种映射方案

| 方案 | 说明 |
|---|---|
| 低风险 · 屏蔽区域 | 从游戏截图中划定需要屏蔽的触摸区域，运行时用局部透明 Overlay 拦截该区域，降低误触（不注入任何输入） |
| 高风险 · 单按钮映射 | 编辑器划定"目标位置"（要改的键）和"新按钮"；运行时在目标位置创建不可见拦截窗口（玩家直点无效），虚拟按钮把一次 Tap 注入到目标矩形几何中心 |
| 中风险 · 长按触发 | 划定长按区域，按下开始计时（区域外轮廓进度条），长按满设定时间（300–3000ms 可调）注入一次 Tap，提前松开/滑动超容差/多指/系统取消均不触发 |

### 高风险方案要点（v6.2）

- **目标区域单一真源**：编辑器显示、保存配置、运行时拦截窗口全部使用同一个 `OverlayGeometry.toPixelRect(config.targetBlock, ...)` 换算结果，无隐式放大；需要更大热区时在编辑器中调大可见目标矩形。
- **注入点 = 用户目标矩形中心**：Tap 固定命中原始目标矩形中心，屏幕边缘目标也不会偏移。
- **完整矩形语义**：目标编辑框固定为矩形（WindowManager 实际输入命中区包含四角）。
- **注入后端**：Shizuku 优先，无障碍 `dispatchGesture` 自动回退；注入前暂时让目标拦截窗让路（`FLAG_NOT_TOUCHABLE` + 等输入窗口更新），完成后恢复，失败自愈重建。
- **长按资格状态机**：按下后移动超过系统 `touchSlop`、触点离开区域、出现第二指或收到系统取消，本次长按资格永久取消，必须抬起后重新按下。

## 已执行验证（v6.2）

- 纯策略单元测试 4/4 通过（`TouchPoliciesTest`：目标中心含屏幕边缘、静止容差、超距移动、离区/多指取消）。
- `lintDebug`：0 errors；`assembleDebug`：通过。
- 详见 `BUILD_VERIFICATION-v6.2.md`、`PHASE_ACCEPTANCE-v6.2.md`、`MANUAL_DEVICE_CHECKLIST-v6.2.md`。

## 已知遗留问题

- 滑动经过屏蔽区/目标区会被拦截窗吞掉（v6.0 方案 B"滑动放行"已删除回退）。
- 无障碍服务在部分设备（如 OPLUS）会被系统周期性重启。
- 注入时目标 Overlay 需短暂让路，存在极短的人手并发竞争窗口。

## 模块结构

```text
app/                    # 主应用（com.example.buttonremapping）
input-test-app/         # 输入链路测试应用（高风险链路代码基础）
```

主要源码（`app/src/main/java/com/example/buttonremapping/`）：

```text
├── MainActivity.kt              # 方案选择、启动/停止
├── OverlayGeometry.kt           # 坐标系统一换算（编辑器/保存/运行时单一真源）
├── TouchPolicies.kt             # 纯策略（目标中心、长按取消判定，可单测）
├── OverlayService.kt            # 低风险屏蔽 Overlay 与悬浮开关
├── LongPressOverlayService.kt   # 长按触发模式运行态
├── highrisk/                    # 高风险模块
│   ├── HighRiskEditorActivity.kt  # 目标位置 + 新按钮编辑器
│   ├── HighRiskOverlayService.kt  # 目标拦截窗 + 虚拟按钮 + 注入调度
│   ├── ShizukuInputClient.kt      # Shizuku 注入后端
│   └── InputAccessibilityService.kt # 无障碍注入后端
└── profile/ProfileManager.kt    # 多方案管理
```

## 构建

要求：JDK 17、Android SDK（compileSdk 35）、Android Studio / Gradle 8.x。

```powershell
# Windows（首次构建较慢）
.\gradlew.bat --no-daemon lintDebug assembleDebug
# macOS / Linux
./gradlew --no-daemon lintDebug assembleDebug
```

单元测试：

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest
```

Debug APK 位于 `app\build\outputs\apk\debug\app-debug.apk`。

> 注意：项目路径含非 ASCII 字符时，本机 Gradle 测试工作进程可能因类路径问题报 `ClassNotFoundException`（已确认与业务代码无关，lint/assemble 不受影响）；可在 ASCII 路径下构建执行测试。

## 明确不包含

不实现：Root、Magisk、ADB 控制、宏录制、连点、OCR、AI 识别、实时画面分析和游戏数据修改。

## 版本历史

见 [UPDATE.md](UPDATE.md)（v0.3.0 → v6.2 完整变更记录）。
