PRAGMA foreign_keys = ON;

-- High-frequency read path indexes for owner + soft-delete + time-window filters.
CREATE INDEX IF NOT EXISTS idx_time_log_owner_deleted_started_at
    ON time_log(owner_user_id, is_deleted, started_at);
CREATE INDEX IF NOT EXISTS idx_time_log_owner_deleted_category_started_at
    ON time_log(owner_user_id, is_deleted, category, started_at);
CREATE INDEX IF NOT EXISTS idx_time_log_owner_deleted_public_started_at
    ON time_log(owner_user_id, is_deleted, is_public_pool, started_at);

-- Relation-table composite indexes to speed project/tag joins.
CREATE INDEX IF NOT EXISTS idx_time_log_project_project_time
    ON time_log_project(project_id, time_log_id);
CREATE INDEX IF NOT EXISTS idx_time_log_tag_tag_time
    ON time_log_tag(tag_id, time_log_id);

-- Income/expense/learning review windows.
CREATE INDEX IF NOT EXISTS idx_income_owner_deleted_occurred_on
    ON income(owner_user_id, is_deleted, occurred_on);
CREATE INDEX IF NOT EXISTS idx_income_owner_deleted_passive_occurred_on
    ON income(owner_user_id, is_deleted, is_passive, occurred_on);
CREATE INDEX IF NOT EXISTS idx_income_owner_deleted_public_occurred_on
    ON income(owner_user_id, is_deleted, is_public_pool, occurred_on);

CREATE INDEX IF NOT EXISTS idx_expense_owner_deleted_occurred_on
    ON expense(owner_user_id, is_deleted, occurred_on);
CREATE INDEX IF NOT EXISTS idx_expense_owner_deleted_category_occurred_on
    ON expense(owner_user_id, is_deleted, category, occurred_on);

CREATE INDEX IF NOT EXISTS idx_learning_owner_deleted_occurred_on
    ON learning_record(owner_user_id, is_deleted, occurred_on);
CREATE INDEX IF NOT EXISTS idx_learning_owner_deleted_public_occurred_on
    ON learning_record(owner_user_id, is_deleted, is_public_pool, occurred_on);
CREATE INDEX IF NOT EXISTS idx_learning_owner_deleted_started_at
    ON learning_record(owner_user_id, is_deleted, started_at);

CREATE INDEX IF NOT EXISTS idx_metric_snapshot_owner_window_date
    ON metric_snapshot(owner_user_id, window_type, snapshot_date);

-- Query-specific indexes for backup and management listing pages.
CREATE INDEX IF NOT EXISTS idx_backup_record_owner_type_created_at
    ON backup_record(owner_user_id, backup_type, created_at);
CREATE INDEX IF NOT EXISTS idx_recurring_rule_owner_active_start_updated
    ON expense_recurring_rule(owner_user_id, is_active, start_month, updated_at);
CREATE INDEX IF NOT EXISTS idx_capex_owner_active_purchase_updated
    ON expense_capex(owner_user_id, is_active, purchase_date, updated_at);
