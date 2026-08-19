# V0.4 Phase 0 环境与权限门禁

## 当前状态

`PENDING`

## 已检查

- 工作目录：`E:\按钮重映射`
- 测试设备：`XCU47H455LJJUODQ / PKT110`
- Android：`16 / API 36`
- 物理显示：`1216×2640`
- ADB：可连接
- 当前设备已安装主 App：`com.example.buttonremapping`
- 当前设备未发现 Shizuku 包；工程本地 Gradle 缓存未发现 Shizuku 依赖

## 门禁条件

1. 只使用 Shizuku 官方组件和公开文档支持的授权路径。
2. 必须先确认 Shizuku 服务运行、授权回调和 UserService 启动结果。
3. 不得以 Root、Accessibility、shell input 或其他未批准路径替代 Shizuku。
4. 在 input-test-app 安装并准备好之前，不启动任何输入注入测试。
5. 不安装或启动任何真实游戏。

## 当前结论

设备侧 Shizuku 环境尚未准备好，因此输入注入链路当前为“未验证”。完成官方组件安装/启动检查后再推进 V0.4-C。
