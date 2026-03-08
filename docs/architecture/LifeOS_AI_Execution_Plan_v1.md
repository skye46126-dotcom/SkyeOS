# LifeOS AI 方向执行计划 v1

## 1. 目标

在保持“每日 5-10 分钟记录”的前提下，把 SkyOS 的录入能力升级为：

- `AI增强`：通过 VCP 中间层做自然语言结构化、补全、建议。
- `保底可用`：当 VCP 或外部模型不可用时，自动降级到本地规则解析与脚本导入，保证不中断。

硬约束：
- 保留手动填写功能，不做替换，只做增强。
- 解析结果必须“先预览后提交”。

## 2. 架构落点

新增 AI 解析编排层：

1. `ParserEngine` 抽象
- `VcpParserEngine`：调用 SkyOS Backend -> VCP -> LLM。
- `RuleParserEngine`：本地规则解析（保底）。

2. `AiParseOrchestrator`
- 根据模式路由：`auto | vcp | rule`
- `auto` 模式优先 VCP，失败自动降级 rule。

3. `ParseDraft` 草稿结构
- 解析结果统一进入草稿，不直接入库。
- 用户确认后调用 `commit_entries` 批量写入。

## 3. 模式定义

- `auto`：默认。优先 VCP，失败降级 Rule。
- `vcp`：仅 VCP，适合稳定联网环境。
- `rule`：仅规则，离线/降本场景。

## 4. 数据与接口补全

## 4.1 Backend 接口

- `POST /api/v1/ai/parse`
  - 入参：`{ raw_text, context_date, parser_hint }`
  - 出参：`{ request_id, draft_items[], confidence, warnings[] }`

- `POST /api/v1/entries/commit`
  - 入参：`{ request_id, entries[] }`
  - 要求：幂等提交、事务写入。

- `GET /api/v1/ai/jobs/{request_id}`（可选）
  - 用于异步解析状态查询。

## 4.2 App 侧草稿结构

统一草稿字段：
- `kind`: time_log | income | expense | learning | project
- `payload`: 各类型结构化字段
- `confidence`: 0~1
- `source`: vcp | rule
- `warnings`: 解析告警

## 5. UI 流程

新增“AI快速录入”流程：

1. 粘贴自然语言日记/输入模板
2. 点击“AI解析”
3. 展示草稿列表（可编辑）
4. 一键确认入库
5. 展示成功条目和失败条目

并行保留手动录入页作为基础路径。

## 6. 失败与降级策略

- VCP 超时（>8s）或 5xx：自动切 Rule。
- Rule 解析失败条目进入 `unknown_items`，用户手动补。
- 所有失败写 `audit_log`，并给出可读错误。

## 7. 安全策略

- App 不直连 VCP，仅连 SkyOS API。
- SkyOS API 与 VCP 用服务间密钥通信。
- 生产环境 HTTPS-only，关闭 cleartext。
- 敏感配置全部放服务端环境变量。

## 8. 测试计划

必须覆盖：

1. 解析正确性
- 时间/收入/支出/学习常见句式。

2. 幂等提交
- 相同 `request_id` 重试不重复写入。

3. 降级路径
- 模拟 VCP 失败，自动切 Rule 并可提交成功。

4. 回归
- 手动录入流程不受影响。

## 9. 执行里程碑

### M1（本轮开始）
- 定义 `ParserEngine` 抽象、模式枚举、草稿数据结构、Orchestrator。

### M2
- 接入 `RuleParserEngine`（本地保底解析可用）。

### M3
- 接入 `VcpParserEngine`（通过 SkyOS Backend 网关）。

### M4
- 新增 AI 快速录入页面（预览/编辑/确认）。

### M5
- 增加自动化测试与生产安全收口（HTTPS-only）。

