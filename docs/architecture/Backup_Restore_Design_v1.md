# LifeOS 本地备份恢复设计 v1（手机端）

## 目标

- 本地优先，不依赖云端。
- 备份可追溯：每次备份/恢复都有数据库记录。
- 恢复可执行：可以用 `backup_record.id` 直接恢复。

## 类型

- `daily_incremental`
- `weekly_full`
- `monthly_archive`
- `manual`

## 存储位置

- 根目录：`<app_files>/lifeos_backups/`
- 子目录：
- `daily/`
- `weekly/`
- `monthly/`
- `manual/`

备份文件命名：
- `lifeos_<backup_type>_yyyyMMdd_HHmmss.db`（UTC 时间）

## 执行流程

创建备份：
1. `PRAGMA wal_checkpoint(FULL)`
2. 关闭数据库连接
3. 复制 `lifeos.db` 到备份目录
4. 计算 SHA-256
5. 重新打开数据库并写 `backup_record`

恢复备份：
1. 按 `backup_record.id` 读取备份文件路径
2. 关闭数据库连接
3. 用备份文件覆盖 `lifeos.db`
4. 重新打开数据库
5. 写 `restore_record`

## 关键实现

- 仓储实现：
`SQLiteLifeOsBackupRepository`
- 用例：
`CreateBackupUseCase`
`RestoreBackupUseCase`
`GetLatestBackupUseCase`

## 约束

- 当前为单机本地模式，不做冲突合并。
- 恢复后若业务需要，可触发指标快照重算任务。

