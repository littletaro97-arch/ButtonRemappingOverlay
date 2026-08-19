# V0.5 Phase 0 门禁

## 通过条件

- 旧 `layout_config` 和 `single_mapping_config` 不被删除。
- 首次访问时自动生成同模式的“默认方案”。
- 低风险和高风险 Profile 使用独立的 mode 集合，但由同一个 ProfileManager 管理。
- 切换、复制和删除操作不跨模式覆盖数据。
- 当前 Profile 不允许直接删除。

## 当前状态

`PASS（代码与构建门禁）`

说明：旧配置未删除；默认 Profile 会在应用启动时为 low/high 两种模式初始化。实际设备上的迁移结果、方案切换、复制和删除交互未由本轮执行，留待用户自行验证。
