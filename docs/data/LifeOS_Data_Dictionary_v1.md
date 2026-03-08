# LifeOS 数据字典 v1（手机端）

本文件用于冻结数据口径，避免后续功能开发时字段语义漂移。

## 全局约定

- 时间：ISO8601 字符串（建议 UTC 存储，展示时按用户时区转换）。
- 金额：`*_cents` 全部使用整数（分）。
- 布尔：`0/1` 整数。
- 删除：统一软删除 `is_deleted`。
- 来源：`source` 统一允许 `manual/external/import/system`。

## 核心表

## user_profile
- `id`: 用户主键（单用户也保留，便于未来扩展）
- `display_name`: 昵称
- `timezone`: 时区，默认 `Asia/Shanghai`
- `currency_code`: 货币代码，默认 `CNY`
- `ideal_hourly_rate_cents`: 理想时薪（分/小时）

## project
- `status`: `active/paused/done`
- `ai_enable_ratio`: AI赋能比例（0-100，仅做数据位）
- `score`: 主观评分（1-10）

## tag
- `tag_group`: 标签组（如 `value`, `life`, `custom`）
- `is_system`: 是否系统预置标签
- `is_active`: 是否可用

## time_log
- `started_at`, `ended_at`: 时间段起止
- `duration_minutes`: 时长（分钟）
- `category`: `work/learning/life/entertainment/rest/social`
- `value_score`: 价值评分（1-10）
- `state_score`: 状态评分（1-10）
- `parse_confidence`: 外部解析置信度（0-1，可空）
- `is_public_pool`: 未归属项目时为 1

跨天规则：
- 只要 `ended_at > started_at` 即合法，天然支持跨天。

## income
- `type`: `salary/project/investment/system/other`
- `amount_cents`: 金额（分）
- `is_passive`: 是否被动收入
- `is_public_pool`: 未绑定项目收入

## expense
- `category`: `necessary/experience/subscription/investment`
- `amount_cents`: 金额（分）

## learning_record
- `duration_minutes`: 学习时长（分钟）
- `application_level`: `input/applied/result`
- `is_public_pool`: 未绑定项目学习

## daily_review
- `review_date`: 自然日（唯一）
- `state_score`: 日状态评分（1-10）

## metric_snapshot
- `window_type`: `day/week/month/year`
- `hourly_rate_cents`: 实际时薪（分/小时）
- `time_debt_cents`: 时间负债（分/小时）
- `passive_cover_ratio`: 被动收入覆盖率
- `freedom_cents`: 自由度（分）

## 关系表（多对多）

- `time_log_project`: 时间可绑定多个项目
- `income_project`: 收入可绑定多个项目
- `learning_project`: 学习可绑定多个项目
- `time_log_tag`, `learning_tag`: 标签绑定

## 分摊口径（当前默认）

- `weight_ratio` 默认 `1.0`。
- 多项目场景先由上层写入明确比例（总和建议为 1.0）。
- 若后续需要“自动平均分摊”，在用例层实现，不写死在数据库。

## 审计与恢复

- `audit_log`: 核心实体增删改审计
- `backup_record`: 备份记录
- `restore_record`: 恢复记录

