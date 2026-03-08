# LifeOS 国内服务器自用部署指南 v1

## 1. 当前部署形态

已提供：
- `server/`：FastAPI 备份服务（上传/列表/下载/删除）
- `deploy/docker-compose.yml`：API + Nginx
- `deploy/nginx/nginx.conf`：反向代理
- `deploy/.env.example`：环境变量模板

## 2. 适用场景

- 你个人自用
- 国内云服务器（阿里云 ECS / 腾讯云 CVM）
- Android 客户端把本地备份文件上传到你的私有服务器

## 3. 首次部署步骤（服务器）

1. 准备服务器
- 系统建议 Ubuntu 22.04 LTS
- 开放端口：`80`（若要 HTTPS，再开放 `443`）

2. 安装 Docker 与 Compose
- 安装 `docker`、`docker compose`

3. 上传项目代码
- 把当前仓库上传到服务器，例如 `/opt/lifeos`

4. 配置环境变量
- 进入 `deploy/`
- 复制 `.env.example` 为 `.env`
- 修改 `LIFEOS_API_KEY` 为强随机字符串

5. 启动服务
- 在 `deploy/` 执行：
```bash
docker compose up -d --build
```

6. 验证
- `GET http://<服务器IP>/health`
- 返回 `status: ok` 即成功

## 4. API 说明（当前可用）

鉴权：
- 请求头 `x-api-key: <LIFEOS_API_KEY>`

接口：
- `GET /health`
- `POST /api/v1/backups/upload?device_id=android&backup_type=manual`（multipart file）
- `GET /api/v1/backups/list?limit=30`
- `GET /api/v1/backups/download/{filename}`
- `DELETE /api/v1/backups/delete/{filename}`

## 5. 国内上线建议

1. 域名与备案
- 绑定域名用于长期使用
- 如用中国大陆节点并提供公网服务，按要求完成备案

2. HTTPS
- 推荐在 Nginx 前增加 HTTPS（证书可用 Let's Encrypt 或云厂商证书）
- 生产必须走 HTTPS，避免备份数据明文传输

3. 安全基线
- 使用强随机 API Key，定期轮换
- 服务器开启防火墙，仅开放必须端口
- 做系统自动安全更新
- 可加 IP 白名单（仅你常用网络）

4. 备份目录
- 服务端备份存储：`deploy/data/backups`
- 建议额外做服务器级定时备份（快照/对象存储）

## 6. 与 Android 端的下一步对接

当前 Android 已有本地备份能力，建议下一步新增：
- 上传任务：把本地最新备份文件上传到 `/api/v1/backups/upload`
- 拉取列表：展示服务端备份历史
- 一键恢复：下载后触发本地恢复流程

这样可以形成：
本地备份 + 私有云备份 双保险。

