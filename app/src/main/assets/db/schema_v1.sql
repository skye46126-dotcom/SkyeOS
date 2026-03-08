PRAGMA foreign_keys = ON;

-- =========================
-- Core metadata
-- =========================

CREATE TABLE IF NOT EXISTS migration (
    version         INTEGER PRIMARY KEY,
    name            TEXT NOT NULL,
    checksum        TEXT NOT NULL,
    applied_at      TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS user_profile (
    id                      TEXT PRIMARY KEY,
    display_name            TEXT NOT NULL,
    timezone                TEXT NOT NULL DEFAULT 'Asia/Shanghai',
    currency_code           TEXT NOT NULL DEFAULT 'CNY',
    ideal_hourly_rate_cents INTEGER NOT NULL DEFAULT 0,
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL
);

-- =========================
-- Project and tags
-- =========================

CREATE TABLE IF NOT EXISTS project (
    id                      TEXT PRIMARY KEY,
    name                    TEXT NOT NULL UNIQUE,
    status                  TEXT NOT NULL CHECK(status IN ('active', 'paused', 'done')),
    started_on              TEXT NOT NULL,
    ended_on                TEXT,
    ai_enable_ratio         INTEGER NOT NULL DEFAULT 0 CHECK(ai_enable_ratio BETWEEN 0 AND 100),
    score                   INTEGER CHECK(score BETWEEN 1 AND 10),
    note                    TEXT,
    is_deleted              INTEGER NOT NULL DEFAULT 0 CHECK(is_deleted IN (0,1)),
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tag (
    id                      TEXT PRIMARY KEY,
    name                    TEXT NOT NULL UNIQUE,
    tag_group               TEXT NOT NULL DEFAULT 'custom',
    is_system               INTEGER NOT NULL DEFAULT 0 CHECK(is_system IN (0,1)),
    is_active               INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1)),
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL
);

-- =========================
-- Time logs
-- =========================

CREATE TABLE IF NOT EXISTS time_log (
    id                      TEXT PRIMARY KEY,
    started_at              TEXT NOT NULL,
    ended_at                TEXT NOT NULL,
    duration_minutes        INTEGER NOT NULL CHECK(duration_minutes > 0),
    category                TEXT NOT NULL CHECK(category IN ('work','learning','life','entertainment','rest','social')),
    value_score             INTEGER CHECK(value_score BETWEEN 1 AND 10),
    state_score             INTEGER CHECK(state_score BETWEEN 1 AND 10),
    note                    TEXT,
    source                  TEXT NOT NULL DEFAULT 'manual' CHECK(source IN ('manual','external','import','system')),
    parse_confidence        REAL CHECK(parse_confidence >= 0.0 AND parse_confidence <= 1.0),
    is_public_pool          INTEGER NOT NULL DEFAULT 0 CHECK(is_public_pool IN (0,1)),
    is_deleted              INTEGER NOT NULL DEFAULT 0 CHECK(is_deleted IN (0,1)),
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL,
    CHECK(ended_at > started_at)
);

CREATE TABLE IF NOT EXISTS time_log_project (
    time_log_id             TEXT NOT NULL,
    project_id              TEXT NOT NULL,
    weight_ratio            REAL NOT NULL DEFAULT 1.0 CHECK(weight_ratio > 0),
    created_at              TEXT NOT NULL,
    PRIMARY KEY (time_log_id, project_id),
    FOREIGN KEY (time_log_id) REFERENCES time_log(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS time_log_tag (
    time_log_id             TEXT NOT NULL,
    tag_id                  TEXT NOT NULL,
    created_at              TEXT NOT NULL,
    PRIMARY KEY (time_log_id, tag_id),
    FOREIGN KEY (time_log_id) REFERENCES time_log(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE RESTRICT
);

-- =========================
-- Income and expense
-- =========================

CREATE TABLE IF NOT EXISTS income (
    id                      TEXT PRIMARY KEY,
    occurred_on             TEXT NOT NULL,
    source_name             TEXT NOT NULL,
    type                    TEXT NOT NULL CHECK(type IN ('salary','project','investment','system','other')),
    amount_cents            INTEGER NOT NULL CHECK(amount_cents >= 0),
    is_passive              INTEGER NOT NULL DEFAULT 0 CHECK(is_passive IN (0,1)),
    note                    TEXT,
    source                  TEXT NOT NULL DEFAULT 'manual' CHECK(source IN ('manual','external','import','system')),
    parse_confidence        REAL CHECK(parse_confidence >= 0.0 AND parse_confidence <= 1.0),
    is_public_pool          INTEGER NOT NULL DEFAULT 0 CHECK(is_public_pool IN (0,1)),
    is_deleted              INTEGER NOT NULL DEFAULT 0 CHECK(is_deleted IN (0,1)),
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS income_project (
    income_id               TEXT NOT NULL,
    project_id              TEXT NOT NULL,
    weight_ratio            REAL NOT NULL DEFAULT 1.0 CHECK(weight_ratio > 0),
    created_at              TEXT NOT NULL,
    PRIMARY KEY (income_id, project_id),
    FOREIGN KEY (income_id) REFERENCES income(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS expense (
    id                      TEXT PRIMARY KEY,
    occurred_on             TEXT NOT NULL,
    category                TEXT NOT NULL CHECK(category IN ('necessary','experience','subscription','investment')),
    amount_cents            INTEGER NOT NULL CHECK(amount_cents >= 0),
    note                    TEXT,
    source                  TEXT NOT NULL DEFAULT 'manual' CHECK(source IN ('manual','external','import','system')),
    parse_confidence        REAL CHECK(parse_confidence >= 0.0 AND parse_confidence <= 1.0),
    is_deleted              INTEGER NOT NULL DEFAULT 0 CHECK(is_deleted IN (0,1)),
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL
);

-- =========================
-- Learning
-- =========================

CREATE TABLE IF NOT EXISTS learning_record (
    id                      TEXT PRIMARY KEY,
    occurred_on             TEXT NOT NULL,
    content                 TEXT NOT NULL,
    duration_minutes        INTEGER NOT NULL CHECK(duration_minutes >= 0),
    application_level       TEXT NOT NULL CHECK(application_level IN ('input','applied','result')),
    note                    TEXT,
    source                  TEXT NOT NULL DEFAULT 'manual' CHECK(source IN ('manual','external','import','system')),
    parse_confidence        REAL CHECK(parse_confidence >= 0.0 AND parse_confidence <= 1.0),
    is_public_pool          INTEGER NOT NULL DEFAULT 0 CHECK(is_public_pool IN (0,1)),
    is_deleted              INTEGER NOT NULL DEFAULT 0 CHECK(is_deleted IN (0,1)),
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS learning_project (
    learning_id             TEXT NOT NULL,
    project_id              TEXT NOT NULL,
    weight_ratio            REAL NOT NULL DEFAULT 1.0 CHECK(weight_ratio > 0),
    created_at              TEXT NOT NULL,
    PRIMARY KEY (learning_id, project_id),
    FOREIGN KEY (learning_id) REFERENCES learning_record(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS learning_tag (
    learning_id             TEXT NOT NULL,
    tag_id                  TEXT NOT NULL,
    created_at              TEXT NOT NULL,
    PRIMARY KEY (learning_id, tag_id),
    FOREIGN KEY (learning_id) REFERENCES learning_record(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE RESTRICT
);

-- =========================
-- Daily review and metrics
-- =========================

CREATE TABLE IF NOT EXISTS daily_review (
    id                      TEXT PRIMARY KEY,
    review_date             TEXT NOT NULL UNIQUE,
    most_important_thing    TEXT,
    most_valuable_time      TEXT,
    biggest_waste           TEXT,
    state_score             INTEGER CHECK(state_score BETWEEN 1 AND 10),
    note                    TEXT,
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS metric_snapshot (
    id                      TEXT PRIMARY KEY,
    snapshot_date           TEXT NOT NULL,
    window_type             TEXT NOT NULL CHECK(window_type IN ('day','week','month','year')),
    hourly_rate_cents       INTEGER,
    time_debt_cents         INTEGER,
    passive_cover_ratio     REAL,
    freedom_cents           INTEGER,
    total_income_cents      INTEGER,
    total_expense_cents     INTEGER,
    total_work_minutes      INTEGER,
    generated_at            TEXT NOT NULL,
    UNIQUE(snapshot_date, window_type)
);

CREATE TABLE IF NOT EXISTS metric_snapshot_project (
    metric_snapshot_id      TEXT NOT NULL,
    project_id              TEXT NOT NULL,
    roi_cents_per_hour      INTEGER,
    income_cents            INTEGER,
    invested_minutes        INTEGER,
    break_even_cents        INTEGER,
    created_at              TEXT NOT NULL,
    PRIMARY KEY (metric_snapshot_id, project_id),
    FOREIGN KEY (metric_snapshot_id) REFERENCES metric_snapshot(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT
);

-- =========================
-- Backup, restore, audit
-- =========================

CREATE TABLE IF NOT EXISTS backup_record (
    id                      TEXT PRIMARY KEY,
    backup_type             TEXT NOT NULL CHECK(backup_type IN ('daily_incremental','weekly_full','monthly_archive','manual')),
    file_path               TEXT NOT NULL,
    file_size_bytes         INTEGER,
    checksum                TEXT,
    status                  TEXT NOT NULL CHECK(status IN ('success','failed')),
    error_message           TEXT,
    created_at              TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS restore_record (
    id                      TEXT PRIMARY KEY,
    backup_record_id        TEXT,
    status                  TEXT NOT NULL CHECK(status IN ('success','failed')),
    error_message           TEXT,
    restored_at             TEXT NOT NULL,
    FOREIGN KEY (backup_record_id) REFERENCES backup_record(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS audit_log (
    id                      TEXT PRIMARY KEY,
    entity_type             TEXT NOT NULL,
    entity_id               TEXT NOT NULL,
    action                  TEXT NOT NULL CHECK(action IN ('insert','update','delete','restore')),
    source                  TEXT NOT NULL CHECK(source IN ('manual','external','system','import')),
    before_json             TEXT,
    after_json              TEXT,
    created_at              TEXT NOT NULL
);

-- =========================
-- Indexes
-- =========================

CREATE INDEX IF NOT EXISTS idx_project_status ON project(status);
CREATE INDEX IF NOT EXISTS idx_project_started_on ON project(started_on);

CREATE INDEX IF NOT EXISTS idx_time_log_started_at ON time_log(started_at);
CREATE INDEX IF NOT EXISTS idx_time_log_ended_at ON time_log(ended_at);
CREATE INDEX IF NOT EXISTS idx_time_log_category ON time_log(category);
CREATE INDEX IF NOT EXISTS idx_time_log_public_pool ON time_log(is_public_pool);
CREATE INDEX IF NOT EXISTS idx_time_log_deleted ON time_log(is_deleted);

CREATE INDEX IF NOT EXISTS idx_time_log_project_project_id ON time_log_project(project_id);
CREATE INDEX IF NOT EXISTS idx_time_log_tag_tag_id ON time_log_tag(tag_id);

CREATE INDEX IF NOT EXISTS idx_income_occurred_on ON income(occurred_on);
CREATE INDEX IF NOT EXISTS idx_income_type ON income(type);
CREATE INDEX IF NOT EXISTS idx_income_passive ON income(is_passive);
CREATE INDEX IF NOT EXISTS idx_income_public_pool ON income(is_public_pool);
CREATE INDEX IF NOT EXISTS idx_income_deleted ON income(is_deleted);

CREATE INDEX IF NOT EXISTS idx_income_project_project_id ON income_project(project_id);

CREATE INDEX IF NOT EXISTS idx_expense_occurred_on ON expense(occurred_on);
CREATE INDEX IF NOT EXISTS idx_expense_category ON expense(category);
CREATE INDEX IF NOT EXISTS idx_expense_deleted ON expense(is_deleted);

CREATE INDEX IF NOT EXISTS idx_learning_occurred_on ON learning_record(occurred_on);
CREATE INDEX IF NOT EXISTS idx_learning_level ON learning_record(application_level);
CREATE INDEX IF NOT EXISTS idx_learning_public_pool ON learning_record(is_public_pool);
CREATE INDEX IF NOT EXISTS idx_learning_deleted ON learning_record(is_deleted);

CREATE INDEX IF NOT EXISTS idx_learning_project_project_id ON learning_project(project_id);
CREATE INDEX IF NOT EXISTS idx_learning_tag_tag_id ON learning_tag(tag_id);

CREATE INDEX IF NOT EXISTS idx_metric_snapshot_date_window ON metric_snapshot(snapshot_date, window_type);
CREATE INDEX IF NOT EXISTS idx_metric_snapshot_project_project_id ON metric_snapshot_project(project_id);

CREATE INDEX IF NOT EXISTS idx_backup_record_created_at ON backup_record(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at);
