PRAGMA foreign_keys = ON;

-- ==========================================
-- 1) Multi-user core tables
-- ==========================================

CREATE TABLE IF NOT EXISTS users (
    id                          TEXT PRIMARY KEY,
    username                    TEXT NOT NULL UNIQUE,
    display_name                TEXT NOT NULL,
    password_hash               TEXT NOT NULL,
    status                      TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','disabled')),
    timezone                    TEXT NOT NULL DEFAULT 'Asia/Shanghai',
    currency_code               TEXT NOT NULL DEFAULT 'CNY',
    ideal_hourly_rate_cents     INTEGER NOT NULL DEFAULT 0,
    created_at                  TEXT NOT NULL,
    updated_at                  TEXT NOT NULL,
    last_login_at               TEXT
);

CREATE TABLE IF NOT EXISTS user_session (
    id                          TEXT PRIMARY KEY,
    user_id                     TEXT NOT NULL,
    token_hash                  TEXT NOT NULL UNIQUE,
    device_id                   TEXT,
    user_agent                  TEXT,
    issued_at                   TEXT NOT NULL,
    expires_at                  TEXT NOT NULL,
    revoked_at                  TEXT,
    last_seen_at                TEXT,
    created_at                  TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_session_user_id ON user_session(user_id);
CREATE INDEX IF NOT EXISTS idx_user_session_expires_at ON user_session(expires_at);

-- ==========================================
-- 2) Ownership columns for data isolation
--    Note: additive and backward compatible
-- ==========================================

ALTER TABLE project ADD COLUMN owner_user_id TEXT;
ALTER TABLE tag ADD COLUMN owner_user_id TEXT;
ALTER TABLE time_log ADD COLUMN owner_user_id TEXT;
ALTER TABLE income ADD COLUMN owner_user_id TEXT;
ALTER TABLE expense ADD COLUMN owner_user_id TEXT;
ALTER TABLE learning_record ADD COLUMN owner_user_id TEXT;
ALTER TABLE daily_review ADD COLUMN owner_user_id TEXT;
ALTER TABLE metric_snapshot ADD COLUMN owner_user_id TEXT;
ALTER TABLE backup_record ADD COLUMN owner_user_id TEXT;
ALTER TABLE restore_record ADD COLUMN owner_user_id TEXT;
ALTER TABLE audit_log ADD COLUMN owner_user_id TEXT;

CREATE INDEX IF NOT EXISTS idx_project_owner_user_id ON project(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_tag_owner_user_id ON tag(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_time_log_owner_user_id ON time_log(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_income_owner_user_id ON income(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_expense_owner_user_id ON expense(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_learning_owner_user_id ON learning_record(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_daily_review_owner_user_id ON daily_review(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_metric_snapshot_owner_user_id ON metric_snapshot(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_backup_record_owner_user_id ON backup_record(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_restore_record_owner_user_id ON restore_record(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_owner_user_id ON audit_log(owner_user_id);

-- ==========================================
-- 3) Lightweight project collaboration
-- ==========================================

CREATE TABLE IF NOT EXISTS project_member (
    project_id                   TEXT NOT NULL,
    user_id                      TEXT NOT NULL,
    role                         TEXT NOT NULL CHECK(role IN ('owner','member')),
    created_at                   TEXT NOT NULL,
    PRIMARY KEY (project_id, user_id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_project_member_user_id ON project_member(user_id);

-- ==========================================
-- 4) Backfill from single-user profile
-- ==========================================

INSERT OR IGNORE INTO users (
    id,
    username,
    display_name,
    password_hash,
    status,
    timezone,
    currency_code,
    ideal_hourly_rate_cents,
    created_at,
    updated_at
)
SELECT
    up.id,
    'owner',
    COALESCE(up.display_name, 'Owner'),
    '__SET_ME__',
    'active',
    COALESCE(up.timezone, 'Asia/Shanghai'),
    COALESCE(up.currency_code, 'CNY'),
    COALESCE(up.ideal_hourly_rate_cents, 0),
    up.created_at,
    up.updated_at
FROM user_profile up
LIMIT 1;

UPDATE project SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';
UPDATE tag SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';
UPDATE time_log SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';
UPDATE income SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';
UPDATE expense SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';
UPDATE learning_record SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';
UPDATE daily_review SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';
UPDATE metric_snapshot SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';
UPDATE backup_record SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';
UPDATE restore_record SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';
UPDATE audit_log SET owner_user_id = (SELECT id FROM users ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL OR owner_user_id = '';

-- Seed project owner membership for existing projects.
INSERT OR IGNORE INTO project_member (project_id, user_id, role, created_at)
SELECT
    p.id,
    p.owner_user_id,
    'owner',
    COALESCE(p.created_at, CURRENT_TIMESTAMP)
FROM project p
WHERE p.owner_user_id IS NOT NULL AND p.owner_user_id <> '';
