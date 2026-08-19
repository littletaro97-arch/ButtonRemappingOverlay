# 游戏按钮映射 V0.4.0 真机检查清单

## 已执行

- [x] 主 App 安装并启动。
- [x] 首页高风险方案警告页显示。
- [x] 高风险状态页显示 Shizuku、权限和输入模块状态。
- [x] 进入编辑器，空白横屏画布显示“目标位置”和“新按钮”。
- [x] 保存布局后，目标位置和新按钮均显示“已设置”。
- [x] input-test-app 安装并启动。
- [x] input-test-app 直接触摸目标区 1 次：记录 1 个 DOWN、1 个 UP，目标计数为 1。
- [x] input-test-app 直接触摸目标区 20 次：在此前 1 次基础上目标计数为 21，目标日志为 21 条，没有重复计数。
- [x] input-test-app 触摸目标区外：记录 DOWN/UP，但没有新增目标计数。
- [x] 清理后没有发现高风险映射服务、UserService 或高风险 Overlay 窗口残留。
- [x] 最近 Logcat 未发现 FATAL EXCEPTION、ANR、SecurityException 或输入超时。

## 未验证

- [ ] Shizuku API 权限授权成功。
- [ ] Shizuku UserService 成功启动。
- [ ] 虚拟按钮点击向 input-test-app 目标坐标注入一次 DOWN/UP。
- [ ] 虚拟按钮连续点击 20 次保持注入 1:1。
- [ ] 高风险 Overlay 与测试应用其他区域触摸的联合验证。
- [ ] 停止高风险映射后再次点击不再产生注入。

## 设备阻塞原因

官方 Shizuku 管理器显示：`ADB 权限受限`。本机为 OPPO/ColorOS；已按官方建议检查开发者选项，但本机开发者选项页面没有出现可切换的 Permission monitoring 项，授权状态仍为未授权。没有使用 Root、Accessibility、shell input、未公开设置写入或其他绕过方式。

测试范围严格排除了真实游戏。
