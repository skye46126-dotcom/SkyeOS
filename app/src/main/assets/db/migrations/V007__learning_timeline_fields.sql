ALTER TABLE learning_record ADD COLUMN started_at TEXT;
ALTER TABLE learning_record ADD COLUMN ended_at TEXT;

CREATE INDEX IF NOT EXISTS idx_learning_started_at ON learning_record(started_at);
CREATE INDEX IF NOT EXISTS idx_learning_ended_at ON learning_record(ended_at);
