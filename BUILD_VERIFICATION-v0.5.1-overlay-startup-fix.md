# 游戏按钮映射 V0.5.1 Overlay 启动修复记录

## 修复结论

问题根因定位为低风险服务在创建多个圆角屏蔽窗口时，部分设备的 `WindowManager.addView()` 异常没有被 `setBlockedState()` 捕获。异常向上退出服务后，开关和屏蔽区都会被移除。

本次修复：

- 圆角屏蔽窗口数量从 32 个降为 8 个。
- 屏蔽区创建失败时清理已创建窗口，并尝试单窗口矩形兜底。
- 启动、配置变化、拖动和移除窗口增加异常保护与 Logcat 日志。
- 兜底也失败时不伪装成“已屏蔽”，开关保持可见并显示允许点击状态。
- 未修改高风险输入注入、Profile 管理和测试 App。

## 构建结果

执行：

```text
gradlew.bat --no-daemon lintDebug assembleDebug
```

结果：`BUILD SUCCESSFUL`

## 未验证项

- 未在其他 Android 手机安装或运行本 APK。
- 未执行 ADB 和 Logcat 设备验证。
- 未启动真实游戏。

因此“其他设备不再消失”目前是代码修复结论，尚未标记为真机验证通过。
