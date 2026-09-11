# 游戏按钮映射 v6.7 GitHub 自动更新真机检查

当前状态：`NOT RUN`

## 准备

1. 安装 `button-remapping-v6.7-debug.apk`，确认 versionCode 为 40。
2. 使用同一签名证书构建 v6.8，versionCode 必须大于 40。
3. 创建非草稿、非预发布的 GitHub Release `v6.8`。
4. Release 只上传一个 `button-remapping-v6.8-debug.apk` 或 `button-remapping-v6.8-release.apk`。

## 正向验证

1. 在 v6.7 设置页点击“应用更新 > 立即检查”。
2. 确认提示发现 v6.8，并显示 Release 更新说明。
3. 点击“下载更新”，确认系统显示下载通知。
4. 下载完成后确认应用显示“哈希、包名、版本号和签名校验通过”。
5. 未授权安装未知应用时，确认可跳转到本应用专属授权页。
6. 返回后确认系统安装器打开；用户确认后应可覆盖安装且保留原配置。
7. 再次进入设置，确认显示当前版本 v6.8 且检查结果为最新版。

## 失败路径

- 错误文件名、多个符合命名的 APK、非 GitHub 下载地址或缺少 GitHub SHA-256 时必须拒绝 Release。
- SHA-256、包名、versionCode 或签名不符时必须拒绝进入安装器。
- 网络不可用时必须显示检查失败，不能导致应用崩溃或影响既有映射功能。
