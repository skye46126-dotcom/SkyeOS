# LifeOS 指标口径 v1（手机端）

## 时间窗口定义

- `day`: 锚点日期当天
- `week`: 锚点日期向前 6 天 + 当天（滚动 7 天）
- `month`: 锚点日期所在自然月（1号到月末）
- `year`: 锚点日期所在自然年（1月1日到12月31日）

## 汇总口径

- `total_income_cents`: 窗口内 `income.amount_cents` 求和（`is_deleted=0`）
- `total_expense_cents`: 窗口内 `expense.amount_cents` 求和（`is_deleted=0`）
- `passive_income_cents`: 窗口内 `income.is_passive=1` 求和
- `necessary_expense_cents`: 窗口内 `expense.category='necessary'` 求和
- `total_work_minutes`: 窗口内 `time_log.category='work'` 时长求和

## 指标公式

- `hourly_rate_cents = total_income_cents * 60 / total_work_minutes`
- `time_debt_cents = ideal_hourly_rate_cents - hourly_rate_cents`
- `passive_cover_ratio = passive_income_cents / necessary_expense_cents`
- `freedom_cents = passive_income_cents - necessary_expense_cents`

## 空值策略

- 当 `total_work_minutes = 0`，`hourly_rate_cents` 与 `time_debt_cents` 写 `null`
- 当 `necessary_expense_cents = 0`，`passive_cover_ratio` 写 `null`

## 数据来源优先级

- 指标展示默认读 `metric_snapshot`
- 若无快照，先触发一次 `recomputeSnapshot` 再读取

