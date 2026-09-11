# 游戏按钮映射 v6.6 构建验证

## 构建身份

- versionName：`6.6`
- versionCode：`39`
- applicationId：`com.example.buttonremapping`
- 变体：`debug`
- minSdk：`26`

## 自动验证

- `:app:testDebugUnitTest`：PASS，10 tests，0 failures，0 errors，0 skipped。
- `:app:lintDebug`：PASS，0 errors，63 warnings。
- `:app:assembleDebug`：PASS。

为规避 Windows/JDK 对中文工作区路径生成测试参数文件时的编码问题，验证期间把同一工作区临时映射为 ASCII 盘符 `V:`；构建完成后已移除该映射，没有复制第二份源码。

## APK

- 工作区路径：`E:\按钮重映射\app\build\outputs\apk\debug\app-debug.apk`
- 交付路径：`E:\Deepseek output\游戏按钮映射\button-remapping-v6.6-debug.apk`
- 大小：`2352035` bytes
- SHA-256：`B4599F2EAFB0EF393CAB30E5D56EC0381DB117B5E88FB4754469DB95B5E3830F`

## 未执行

- 未连接 Android 真机或模拟器。
- 未验证最近任务卡片隐藏/恢复。
- 未验证沉浸式系统栏和窗口覆盖检测。
- 未在华为 nova 5 Pro 上验证 EMUI 全屏开关及编辑器行为。
