# LifeOS AI 快速录入 v1

## 说明

新增 `AIQuickInputActivity`，实现：
- 解析模式选择：`auto / vcp / rule`
- 自然语言输入
- 解析预览（草稿）
- 确认提交（复用现有 Create*UseCase 入库）

## 保持不变

- 手动录入页 `InputActivity` 保留且继续可用
- AI 快速录入是并行入口，不替代手动流程

## 当前行为

- `auto`：先 VCP，失败自动降级 Rule
- `vcp`：当前尚未接通，会回退由 orchestrator 处理错误
- `rule`：可直接工作，支持时间/收入/支出/学习基础解析

