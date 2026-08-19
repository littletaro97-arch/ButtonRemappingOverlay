# V0.5 Profile 文件归属

| 文件/目录 | 负责内容 |
|---|---|
| `app/.../profile/ProfileManager.kt` | Profile 元数据、当前方案、创建、复制、删除、切换、迁移和统一持久化 |
| `app/.../LayoutPrefs.kt` | 低风险旧 SharedPreferences 读取兼容；正常读写转发到 ProfileManager |
| `app/.../highrisk/MappingPrefs.kt` | 高风险旧 SharedPreferences 读取兼容；正常读写转发到 ProfileManager |
| `app/.../ProfileViews.kt` | 两种模式共用的方案列表和管理操作 UI |
| `MainActivity.kt` | 低风险 Profile 面板接入及运行状态刷新 |
| `highrisk/HighRiskActivity.kt` | 高风险 Profile 面板接入及运行状态刷新 |
| `OverlayService.kt`、`HighRiskOverlayService.kt` | 当前 Profile 名称进入运行通知；配置仍只读当前 Profile |

禁止在低风险或高风险页面各自新增第二套 Profile 数据库。
