PRAGMA foreign_keys = OFF;

-- Consolidate scattered relation tables into unified link tables.
CREATE TABLE IF NOT EXISTS record_project_link (
    record_type     TEXT NOT NULL CHECK(record_type IN ('time_log','income','expense','learning')),
    record_id       TEXT NOT NULL,
    project_id      TEXT NOT NULL,
    weight_ratio    REAL NOT NULL DEFAULT 1.0 CHECK(weight_ratio > 0),
    owner_user_id   TEXT,
    created_at      TEXT NOT NULL,
    PRIMARY KEY (record_type, record_id, project_id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT,
    FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS record_tag_link (
    record_type     TEXT NOT NULL CHECK(record_type IN ('project','time_log','income','expense','learning')),
    record_id       TEXT NOT NULL,
    tag_id          TEXT NOT NULL,
    owner_user_id   TEXT,
    created_at      TEXT NOT NULL,
    PRIMARY KEY (record_type, record_id, tag_id),
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE RESTRICT,
    FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Backfill project allocations.
INSERT OR IGNORE INTO record_project_link (record_type, record_id, project_id, weight_ratio, owner_user_id, created_at)
SELECT
    'time_log',
    tp.time_log_id,
    tp.project_id,
    COALESCE(tp.weight_ratio, 1.0),
    tl.owner_user_id,
    COALESCE(tp.created_at, tl.created_at, CURRENT_TIMESTAMP)
FROM time_log_project tp
LEFT JOIN time_log tl ON tl.id = tp.time_log_id;

INSERT OR IGNORE INTO record_project_link (record_type, record_id, project_id, weight_ratio, owner_user_id, created_at)
SELECT
    'income',
    ip.income_id,
    ip.project_id,
    COALESCE(ip.weight_ratio, 1.0),
    i.owner_user_id,
    COALESCE(ip.created_at, i.created_at, CURRENT_TIMESTAMP)
FROM income_project ip
LEFT JOIN income i ON i.id = ip.income_id;

INSERT OR IGNORE INTO record_project_link (record_type, record_id, project_id, weight_ratio, owner_user_id, created_at)
SELECT
    'expense',
    ep.expense_id,
    ep.project_id,
    COALESCE(ep.weight_ratio, 1.0),
    e.owner_user_id,
    COALESCE(ep.created_at, e.created_at, CURRENT_TIMESTAMP)
FROM expense_project ep
LEFT JOIN expense e ON e.id = ep.expense_id;

INSERT OR IGNORE INTO record_project_link (record_type, record_id, project_id, weight_ratio, owner_user_id, created_at)
SELECT
    'learning',
    lp.learning_id,
    lp.project_id,
    COALESCE(lp.weight_ratio, 1.0),
    lr.owner_user_id,
    COALESCE(lp.created_at, lr.created_at, CURRENT_TIMESTAMP)
FROM learning_project lp
LEFT JOIN learning_record lr ON lr.id = lp.learning_id;

-- Backfill tag links.
INSERT OR IGNORE INTO record_tag_link (record_type, record_id, tag_id, owner_user_id, created_at)
SELECT
    'project',
    pt.project_id,
    pt.tag_id,
    p.owner_user_id,
    COALESCE(pt.created_at, p.created_at, CURRENT_TIMESTAMP)
FROM project_tag pt
LEFT JOIN project p ON p.id = pt.project_id;

INSERT OR IGNORE INTO record_tag_link (record_type, record_id, tag_id, owner_user_id, created_at)
SELECT
    'time_log',
    tlt.time_log_id,
    tlt.tag_id,
    tl.owner_user_id,
    COALESCE(tlt.created_at, tl.created_at, CURRENT_TIMESTAMP)
FROM time_log_tag tlt
LEFT JOIN time_log tl ON tl.id = tlt.time_log_id;

INSERT OR IGNORE INTO record_tag_link (record_type, record_id, tag_id, owner_user_id, created_at)
SELECT
    'income',
    it.income_id,
    it.tag_id,
    i.owner_user_id,
    COALESCE(it.created_at, i.created_at, CURRENT_TIMESTAMP)
FROM income_tag it
LEFT JOIN income i ON i.id = it.income_id;

INSERT OR IGNORE INTO record_tag_link (record_type, record_id, tag_id, owner_user_id, created_at)
SELECT
    'expense',
    et.expense_id,
    et.tag_id,
    e.owner_user_id,
    COALESCE(et.created_at, e.created_at, CURRENT_TIMESTAMP)
FROM expense_tag et
LEFT JOIN expense e ON e.id = et.expense_id;

INSERT OR IGNORE INTO record_tag_link (record_type, record_id, tag_id, owner_user_id, created_at)
SELECT
    'learning',
    lt.learning_id,
    lt.tag_id,
    lr.owner_user_id,
    COALESCE(lt.created_at, lr.created_at, CURRENT_TIMESTAMP)
FROM learning_tag lt
LEFT JOIN learning_record lr ON lr.id = lt.learning_id;

CREATE INDEX IF NOT EXISTS idx_record_project_link_project
    ON record_project_link(project_id, record_type, record_id);
CREATE INDEX IF NOT EXISTS idx_record_project_link_record
    ON record_project_link(record_type, record_id);
CREATE INDEX IF NOT EXISTS idx_record_project_link_owner
    ON record_project_link(owner_user_id, record_type, created_at);

CREATE INDEX IF NOT EXISTS idx_record_tag_link_tag
    ON record_tag_link(tag_id, record_type, record_id);
CREATE INDEX IF NOT EXISTS idx_record_tag_link_record
    ON record_tag_link(record_type, record_id);
CREATE INDEX IF NOT EXISTS idx_record_tag_link_owner
    ON record_tag_link(owner_user_id, record_type, created_at);

-- Remove legacy scattered relation tables.
DROP TABLE IF EXISTS time_log_project;
DROP TABLE IF EXISTS income_project;
DROP TABLE IF EXISTS expense_project;
DROP TABLE IF EXISTS learning_project;

DROP TABLE IF EXISTS project_tag;
DROP TABLE IF EXISTS time_log_tag;
DROP TABLE IF EXISTS income_tag;
DROP TABLE IF EXISTS expense_tag;
DROP TABLE IF EXISTS learning_tag;

PRAGMA foreign_keys = ON;
