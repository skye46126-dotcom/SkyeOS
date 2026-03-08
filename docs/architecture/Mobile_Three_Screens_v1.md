# LifeOS 手机端三屏结构 v1

当前已拆为 3 个独立 Activity：

- `MainActivity`（Dashboard）：今日/近7天核心指标、公共池占比、手动备份入口
- `InputActivity`：正式字段表单录入（项目、时间记录、收入、支出、学习）
- `RecentActivity`：最近记录聚合列表（time/income/expense/learning）

## 设计目的

- 从“技术验证页”升级到“可日常操作结构”。
- 保持简单导航，避免过早引入复杂路由框架。
- 先跑通录入和回看闭环，再逐步替换为正式 UI。
- 时间记录支持日期时间选择器（DatePicker + TimePicker）。
- 分类字段已下拉化（time/income/expense/learning）。
- 项目支持列表点击填充绑定（自动追加 `projectId:1.0`）。
