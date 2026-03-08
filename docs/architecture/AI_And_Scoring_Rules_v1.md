# LifeOS AI与评分准则 v1

## 1. 目标边界

- AI职责：提取客观信息、结构化输入、补全可推断字段。
- 人工职责：填写主观评分（效率分/价值分/状态分）。
- 默认原则：`不确定不填`，不允许编造。

## 2. 一键填写字段协议

### 2.1 time_log

- 必填候选：`category`、时间范围（`start_hour/end_hour`或`duration_hours`）
- 可选：`description`、`ai_ratio(0-100)`、`efficiency_score(1-10)`、`value_score(1-10)`、`state_score(1-10)`
- 评分字段仅当原文明确出现“X分”时提取。

### 2.2 income

- 必填候选：`source`、`amount`
- 可选：`type`、`ai_ratio(0-100)`

### 2.3 expense

- 必填候选：`amount`
- 可选：`category`、`note`、`ai_ratio(0-100)`

### 2.4 learning

- 必填候选：`content`、`duration_minutes`
- 可选：`application_level`、`ai_ratio(0-100)`、`efficiency_score(1-10)`
- 学习效率分同样只在原文明确给分时提取。

## 3. 校验规则

- 时间：`ended_at > started_at`，跨天允许。
- 金额：`amount_cents >= 0`。
- AI率：`0-100`，越界置空并记录 warning。
- 评分：`1-10`，越界置空并记录 warning。
- 枚举值：不在白名单时回退默认值（如 `other/necessary/input`）。

## 4. 评分录入准则（人工）

- 工作效率分：针对该时间块“执行质量和专注度”打分（1-10）。
- 价值分：针对该时间块“产出价值”打分（1-10）。
- 状态分：针对该时间块“身心状态”打分（1-10）。
- 学习效率分：针对该学习块“吸收与应用效率”打分（1-10）。

## 5. 统计口径（加权平均）

### 5.1 工作效率均分

```
work_eff_avg = Σ(duration_minutes * efficiency_score) / Σ(duration_minutes)
```

仅统计 `time_log.category='work'` 且 `efficiency_score` 非空。

### 5.2 学习效率均分

```
learn_eff_avg = Σ(duration_minutes * efficiency_score) / Σ(duration_minutes)
```

仅统计 `learning_record.efficiency_score` 非空。

## 6. 与AI率协同

- AI率用于衡量“该记录中由AI辅助完成的比例”，范围 0-100。
- 复盘中 AI辅助率按时长加权：

```
ai_assist_rate = Σ(duration_minutes * ai_assist_ratio/100) / Σ(duration_minutes)
```

- AI率与评分独立：AI率高不代表效率高，需结合评分和结果共同判断。
