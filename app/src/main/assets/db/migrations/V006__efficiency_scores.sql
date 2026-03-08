PRAGMA foreign_keys = ON;

ALTER TABLE time_log ADD COLUMN efficiency_score INTEGER CHECK(efficiency_score BETWEEN 1 AND 10);
ALTER TABLE learning_record ADD COLUMN efficiency_score INTEGER CHECK(efficiency_score BETWEEN 1 AND 10);

CREATE INDEX IF NOT EXISTS idx_time_log_efficiency_score ON time_log(efficiency_score);
CREATE INDEX IF NOT EXISTS idx_learning_efficiency_score ON learning_record(efficiency_score);
