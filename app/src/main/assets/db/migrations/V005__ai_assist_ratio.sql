PRAGMA foreign_keys = ON;

ALTER TABLE time_log ADD COLUMN ai_assist_ratio INTEGER CHECK(ai_assist_ratio BETWEEN 0 AND 100);
ALTER TABLE income ADD COLUMN ai_assist_ratio INTEGER CHECK(ai_assist_ratio BETWEEN 0 AND 100);
ALTER TABLE expense ADD COLUMN ai_assist_ratio INTEGER CHECK(ai_assist_ratio BETWEEN 0 AND 100);
ALTER TABLE learning_record ADD COLUMN ai_assist_ratio INTEGER CHECK(ai_assist_ratio BETWEEN 0 AND 100);

CREATE INDEX IF NOT EXISTS idx_time_log_ai_assist_ratio ON time_log(ai_assist_ratio);
CREATE INDEX IF NOT EXISTS idx_income_ai_assist_ratio ON income(ai_assist_ratio);
CREATE INDEX IF NOT EXISTS idx_expense_ai_assist_ratio ON expense(ai_assist_ratio);
CREATE INDEX IF NOT EXISTS idx_learning_ai_assist_ratio ON learning_record(ai_assist_ratio);
