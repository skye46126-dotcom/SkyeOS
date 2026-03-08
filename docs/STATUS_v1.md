# LifeOS 项目状态 v1（2026-03-07）

## 当前完成

- Android 本地端：
- 三页面结构（Dashboard / Input / Recent）
- SQLite 长期 schema + migration
- 核心写入链路（项目/时间/收入/支出/学习）
- 指标快照（日/周）
- 本地备份/恢复
- 录入体验优化（时间选择器、分类下拉、项目绑定辅助）

- 国内服务器端（自用）：
- FastAPI 备份服务
- Docker Compose 部署（API + Nginx）
- 基础鉴权（API Key）
- 上传/列表/下载/删除接口

## 进行中

- Android 与服务端同步打通（上传最新备份到私有服务器）

## 下一步（建议顺序）

1. Android 增加“云备份设置页”（域名、API Key、设备名）
2. 接入上传/拉取列表/下载恢复
3. 增加 HTTPS 与证书配置
4. 增加自动化测试（迁移、指标、备份恢复）
5. 发布自用稳定版（tag v0.1）

