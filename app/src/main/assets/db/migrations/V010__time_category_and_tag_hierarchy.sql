PRAGMA foreign_keys = OFF;

-- 1) Allow custom time categories by removing enum CHECK constraint on time_log.category.
ALTER TABLE time_log RENAME TO time_log_legacy_v010;

CREATE TABLE IF NOT EXISTS time_log (
    id                      TEXT PRIMARY KEY,
    started_at              TEXT NOT NULL,
    ended_at                TEXT NOT NULL,
    duration_minutes        INTEGER NOT NULL CHECK(duration_minutes > 0),
    category                TEXT NOT NULL,
    value_score             INTEGER CHECK(value_score BETWEEN 1 AND 10),
    state_score             INTEGER CHECK(state_score BETWEEN 1 AND 10),
    note                    TEXT,
    source                  TEXT NOT NULL DEFAULT 'manual' CHECK(source IN ('manual','external','import','system')),
    parse_confidence        REAL CHECK(parse_confidence >= 0.0 AND parse_confidence <= 1.0),
    is_public_pool          INTEGER NOT NULL DEFAULT 0 CHECK(is_public_pool IN (0,1)),
    is_deleted              INTEGER NOT NULL DEFAULT 0 CHECK(is_deleted IN (0,1)),
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL,
    owner_user_id           TEXT,
    ai_assist_ratio         INTEGER CHECK(ai_assist_ratio BETWEEN 0 AND 100),
    efficiency_score        INTEGER CHECK(efficiency_score BETWEEN 1 AND 10),
    CHECK(ended_at > started_at)
);

INSERT INTO time_log (
    id, started_at, ended_at, duration_minutes, category, value_score, state_score, note,
    source, parse_confidence, is_public_pool, is_deleted, created_at, updated_at,
    owner_user_id, ai_assist_ratio, efficiency_score
)
SELECT
    id, started_at, ended_at, duration_minutes, category, value_score, state_score, note,
    source, parse_confidence, is_public_pool, is_deleted, created_at, updated_at,
    owner_user_id, ai_assist_ratio, efficiency_score
FROM time_log_legacy_v010;

DROP TABLE time_log_legacy_v010;

-- Recreate time_log indexes lost after table rebuild.
CREATE INDEX IF NOT EXISTS idx_time_log_started_at ON time_log(started_at);
CREATE INDEX IF NOT EXISTS idx_time_log_ended_at ON time_log(ended_at);
CREATE INDEX IF NOT EXISTS idx_time_log_category ON time_log(category);
CREATE INDEX IF NOT EXISTS idx_time_log_public_pool ON time_log(is_public_pool);
CREATE INDEX IF NOT EXISTS idx_time_log_deleted ON time_log(is_deleted);
CREATE INDEX IF NOT EXISTS idx_time_log_owner_user_id ON time_log(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_time_log_ai_assist_ratio ON time_log(ai_assist_ratio);
CREATE INDEX IF NOT EXISTS idx_time_log_efficiency_score ON time_log(efficiency_score);
CREATE INDEX IF NOT EXISTS idx_time_log_owner_deleted_started_at ON time_log(owner_user_id, is_deleted, started_at);
CREATE INDEX IF NOT EXISTS idx_time_log_owner_deleted_category_started_at ON time_log(owner_user_id, is_deleted, category, started_at);
CREATE INDEX IF NOT EXISTS idx_time_log_owner_deleted_public_started_at ON time_log(owner_user_id, is_deleted, is_public_pool, started_at);

-- 2) Tag hierarchy foundation: parent + 2-level depth marker.
ALTER TABLE tag ADD COLUMN parent_tag_id TEXT;
ALTER TABLE tag ADD COLUMN level INTEGER NOT NULL DEFAULT 1;
UPDATE tag SET level = 1 WHERE level IS NULL OR level < 1;
CREATE INDEX IF NOT EXISTS idx_tag_scope_parent_level ON tag(scope, parent_tag_id, level, is_active, sort_order);

-- 3) Legacy learning records: backfill started_at / ended_at for editability.
UPDATE learning_record
SET started_at = occurred_on || 'T00:00:00Z'
WHERE started_at IS NULL OR started_at = '';

UPDATE learning_record
SET ended_at = strftime(
        '%Y-%m-%dT%H:%M:%SZ',
        datetime(
            started_at,
            '+' || CASE WHEN duration_minutes > 0 THEN duration_minutes ELSE 1 END || ' minutes'
        )
    )
WHERE ended_at IS NULL OR ended_at = '';

PRAGMA foreign_keys = ON;
