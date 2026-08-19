# V0.4 Phase 1 接受记录

## 实现范围

- 单个高风险虚拟按钮。
- 单个目标坐标。
- 短按一次最多产生一个映射请求。
- Shizuku UserService 与系统 InputManager 注入实现已写入代码。
- 长按、拖动、连点、宏、多按钮和自动操作未加入。
- 低风险 Overlay 逻辑未依赖高风险注入模块。
- 新增 `input-test-app`，不包含游戏代码。

## 门禁结果

| 门禁 | 结果 | 说明 |
|---|---|---|
| 编译与 Lint | PASS | 主 App 和 input-test-app 均通过 |
| 编辑器保存 | PASS | 目标位置、新按钮位置保存后可恢复 |
| 测试应用直接触摸 | PASS | 1 次和连续 20 次计数正确 |
| Shizuku 服务启动 | PASS | 官方 v13.6.0 服务进程可启动 |
| Shizuku 权限授权 | BLOCKED | OPPO/ColorOS 报告 ADB 权限受限 |
| 系统输入注入 1:1 | UNVERIFIED | 依赖上一个门禁，未执行 |
| 真实游戏测试 | NOT RUN | 硬性禁止 |

## 阶段结论

V0.4 的代码和测试夹具已交付，但不能宣称完整验收通过。当前设备无法完成 Shizuku 授权，所以系统级输入注入、虚拟按钮 Overlay 联合触摸和 1:1 映射仍必须由具备可用 Shizuku 授权的受控测试设备继续验证。
