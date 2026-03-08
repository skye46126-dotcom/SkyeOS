# SkyOSBridge 插件接入说明（VCP协议版）

## 1. 目录放置

将以下目录整体放到 VCP 的 `Plugin/` 下：

- `SkyOSBridge/plugin-manifest.json`
- `SkyOSBridge/SkyOSBridge.js`
- `SkyOSBridge/config.env`（由 `config.env.example` 复制）

## 2. 配置

`config.env` 示例：

```env
SKYOS_BASE_URL=https://your-domain.com/skyos
SKYOS_API_KEY=replace_with_strong_service_token
SKYOS_TIMEOUT_MS=15000
```

## 3. 支持命令

- `SkyOSParseInput`
- `SkyOSCommitEntries`
- `SkyOSGetOverview`
- `SkyOSListRecent`
- `SkyOSBackupUpload`
- `SkyOSBackupRestore`

插件支持：
- 单命令（`command`）
- 批量命令（`command1/command2...` + 参数后缀 `arg1/arg2...`）

## 4. 安全说明

- 推荐在 VCP 端使用服务间密钥调用 SkyOS。
- 如插件开启了 `requiresAdmin` 且主控注入了 `DECRYPTED_AUTH_CODE`，`SkyOSBackupRestore` 需提供 `requireAdmin`。

## 5. 返回结构

成功：

```json
{ "status": "success", "result": ... }
```

失败：

```json
{ "status": "error", "error": "..." }
```

