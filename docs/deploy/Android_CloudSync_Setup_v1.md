# Android 云备份对接说明 v1

## 已实现

手机端新增 `CloudSyncActivity`：
- 保存云配置：`serverBaseUrl` / `apiKey` / `deviceId`
- 上传最新本地手动备份到服务器
- 拉取服务端备份列表
- 按文件名下载并触发本地恢复

入口：
- Dashboard 页按钮 `Go To Cloud Sync`

## 使用步骤

1. 先完成服务器部署（见 `China_Server_SelfHost_v1.md`）
2. 在手机端进入 `Cloud Sync` 页面
3. 填写：
- Server Base URL：例如 `http://your-server-ip` 或 `https://your-domain`
- API Key：与服务器 `.env` 中一致
- Device ID：自定义，如 `android-self`
4. 先点 `Save Cloud Config`
5. 点 `Upload Latest Manual Backup`
6. 点 `List Remote Backups` 验证上传结果
7. 将列表中的文件名粘贴到 `Filename to download and restore`
8. 点 `Download & Restore By Filename`

## 注意

- 当前上传的是“本地手动备份文件”（若不存在会先创建一个手动备份）
- 目前是手动触发，不是自动后台定时任务
- 生产建议使用 HTTPS
- 当前清单已允许 `http` 明文请求用于自用联调，生产建议切回 HTTPS-only
