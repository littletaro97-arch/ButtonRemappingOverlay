# 游戏按钮映射 V6.2 构建与验证记录

日期：2026-08-18

## 结论

V6.2 已完成代码级修复、纯策略单元测试、lint 和 APK 构建。未使用 ADB，真实游戏/设备行为仍待用户手工验收。

## 版本与 APK

- applicationId：`com.example.buttonremapping`
- versionName：`6.2`
- versionCode：`35`
- minSdk：`26`
- targetSdk / compileSdk：`35 / 35`
- APK：`E:\Deepseek output\游戏按钮映射\button-remapping-v6.2-debug.apk`
- 大小：`1,222,157` bytes
- SHA-256：`5192275A5FF83FD0F8BACD6B4CCE3FF94A5A852437CD47A7AD3EBB322C110DE1`
- APK 签名：Android Debug，APK Signature Scheme v2 验证通过

## 已执行检查

| 检查 | 结果 | 证据 |
|---|---|---|
| 目标区域不再隐式扩张 | PASS | 源码中不存在 `targetInterceptRect` / `TARGET_INTERCEPT_*`；运行时直接使用 `toPixelRect(targetBlock)` |
| 注入点取用户目标矩形中心 | PASS | `TargetAreaPolicy.center(left, right/top, bottom)`；边缘矩形策略测试覆盖 |
| 长按滑动资格取消 | PASS | 移动超 `touchSlop`、离区、多指、系统取消均进入不可逆取消路径 |
| 单元测试 | PASS | 4 tests，0 failures，0 errors |
| lintDebug | PASS | 0 errors，48 个既有 warning |
| assembleDebug | PASS | canonical 中文路径构建成功 |
| APK 源/交付哈希一致 | PASS | 两者 SHA-256 均为上述值 |

原始测试结果：`E:\Deepseek output\游戏按钮映射\UNIT_TEST_RESULTS-v6.2.xml`，SHA-256 `645362CE48BCFA56B984CBEBF5B839D110E7622F4583B7F3CC71F25D08DAA135`。

## 构建命令与路径限制

Canonical 工作区执行：

```text
gradlew.bat --no-daemon lintDebug assembleDebug
```

项目在中文路径下执行 Gradle JVM 单元测试时，测试类已编译但测试工作进程报 `ClassNotFoundException`。将同一组源文件按 SHA-256 确认一致后复制到 ASCII 临时构建目录，执行：

```text
gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

结果为 `BUILD SUCCESSFUL`。这说明失败来自本机 Gradle 测试工作进程的中文路径类路径处理，不是测试断言或业务源码失败。

本机没有 `apkanalyzer`；使用 SDK `aapt` 和 `apksigner` 作为替代。`aapt` 不能直接读取中文 APK 路径，因此在 ASCII 临时路径读取元数据；它对基线已有的 `@android:drawable/sym_def_app_icon` 报资源解析警告，但仍正确读出 package/version。`apksigner verify` 通过。

## 未执行

- 未使用 ADB。
- 未安装 APK，未启动应用。
- 未在真实游戏验证 Overlay 输入命中、坐标中心或长按滑出行为。
- 未做 iQOO Neo9 / OriginOS 专项验证。
