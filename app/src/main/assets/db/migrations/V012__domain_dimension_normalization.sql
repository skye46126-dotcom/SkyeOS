PRAGMA foreign_keys = ON;

-- 1) Dimension tables: centralize domain enums and stop scattering literals.
CREATE TABLE IF NOT EXISTS dim_project_status (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1)),
    is_system   INTEGER NOT NULL DEFAULT 1 CHECK(is_system IN (0,1)),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dim_time_category (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1)),
    is_system   INTEGER NOT NULL DEFAULT 1 CHECK(is_system IN (0,1)),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dim_income_type (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1)),
    is_system   INTEGER NOT NULL DEFAULT 1 CHECK(is_system IN (0,1)),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dim_expense_category (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1)),
    is_system   INTEGER NOT NULL DEFAULT 1 CHECK(is_system IN (0,1)),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dim_learning_level (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1)),
    is_system   INTEGER NOT NULL DEFAULT 1 CHECK(is_system IN (0,1)),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

-- 2) Seed canonical dimensions (idempotent).
INSERT OR IGNORE INTO dim_project_status(code, display_name, sort_order, is_active, is_system, created_at, updated_at)
VALUES
    ('active', 'Active', 10, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('paused', 'Paused', 20, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('done', 'Done', 30, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT OR IGNORE INTO dim_time_category(code, display_name, sort_order, is_active, is_system, created_at, updated_at)
VALUES
    ('work', 'Work', 10, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('learning', 'Learning', 20, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('life', 'Life', 30, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('entertainment', 'Entertainment', 40, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('rest', 'Rest', 50, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('social', 'Social', 60, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT OR IGNORE INTO dim_income_type(code, display_name, sort_order, is_active, is_system, created_at, updated_at)
VALUES
    ('salary', 'Salary', 10, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('project', 'Project', 20, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('investment', 'Investment', 30, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('system', 'System', 40, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('other', 'Other', 50, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT OR IGNORE INTO dim_expense_category(code, display_name, sort_order, is_active, is_system, created_at, updated_at)
VALUES
    ('necessary', 'Necessary', 10, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('experience', 'Experience', 20, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription', 'Subscription', 30, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('investment', 'Investment', 40, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT OR IGNORE INTO dim_learning_level(code, display_name, sort_order, is_active, is_system, created_at, updated_at)
VALUES
    ('input', 'Input', 10, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('applied', 'Applied', 20, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('result', 'Result', 30, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Keep existing custom time categories by promoting data values into the dimension.
INSERT OR IGNORE INTO dim_time_category(code, display_name, sort_order, is_active, is_system, created_at, updated_at)
SELECT DISTINCT
    LOWER(TRIM(category)) AS code,
    TRIM(category) AS display_name,
    1000 AS sort_order,
    1 AS is_active,
    0 AS is_system,
    CURRENT_TIMESTAMP AS created_at,
    CURRENT_TIMESTAMP AS updated_at
FROM time_log
WHERE category IS NOT NULL
  AND TRIM(category) <> '';

-- 3) Add FK key columns to facts (compatible: keep old text columns for now).
ALTER TABLE project ADD COLUMN status_id INTEGER REFERENCES dim_project_status(id);
ALTER TABLE time_log ADD COLUMN category_id INTEGER REFERENCES dim_time_category(id);
ALTER TABLE income ADD COLUMN type_id INTEGER REFERENCES dim_income_type(id);
ALTER TABLE expense ADD COLUMN category_id INTEGER REFERENCES dim_expense_category(id);
ALTER TABLE learning_record ADD COLUMN application_level_id INTEGER REFERENCES dim_learning_level(id);

-- 4) Backfill keys from legacy text columns.
UPDATE project
SET status = LOWER(TRIM(status))
WHERE status IS NOT NULL;

UPDATE time_log
SET category = LOWER(TRIM(category))
WHERE category IS NOT NULL;

UPDATE income
SET type = LOWER(TRIM(type))
WHERE type IS NOT NULL;

UPDATE expense
SET category = LOWER(TRIM(category))
WHERE category IS NOT NULL;

UPDATE learning_record
SET application_level = LOWER(TRIM(application_level))
WHERE application_level IS NOT NULL;

UPDATE project
SET status_id = (
    SELECT d.id FROM dim_project_status d WHERE d.code = project.status LIMIT 1
)
WHERE status_id IS NULL;

UPDATE time_log
SET category_id = (
    SELECT d.id FROM dim_time_category d WHERE d.code = time_log.category LIMIT 1
)
WHERE category_id IS NULL;

UPDATE income
SET type_id = (
    SELECT d.id FROM dim_income_type d WHERE d.code = income.type LIMIT 1
)
WHERE type_id IS NULL;

UPDATE expense
SET category_id = (
    SELECT d.id FROM dim_expense_category d WHERE d.code = expense.category LIMIT 1
)
WHERE category_id IS NULL;

UPDATE learning_record
SET application_level_id = (
    SELECT d.id FROM dim_learning_level d WHERE d.code = learning_record.application_level LIMIT 1
)
WHERE application_level_id IS NULL;

-- 5) Read/write path indexes for normalized joins.
CREATE INDEX IF NOT EXISTS idx_project_status_id ON project(status_id);
CREATE INDEX IF NOT EXISTS idx_time_log_category_id ON time_log(category_id);
CREATE INDEX IF NOT EXISTS idx_income_type_id ON income(type_id);
CREATE INDEX IF NOT EXISTS idx_expense_category_id ON expense(category_id);
CREATE INDEX IF NOT EXISTS idx_learning_level_id ON learning_record(application_level_id);

CREATE INDEX IF NOT EXISTS idx_dim_project_status_code_active ON dim_project_status(code, is_active);
CREATE INDEX IF NOT EXISTS idx_dim_time_category_code_active ON dim_time_category(code, is_active);
CREATE INDEX IF NOT EXISTS idx_dim_income_type_code_active ON dim_income_type(code, is_active);
CREATE INDEX IF NOT EXISTS idx_dim_expense_category_code_active ON dim_expense_category(code, is_active);
CREATE INDEX IF NOT EXISTS idx_dim_learning_level_code_active ON dim_learning_level(code, is_active);
