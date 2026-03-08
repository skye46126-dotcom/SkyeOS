ALTER TABLE tag ADD COLUMN emoji TEXT;
ALTER TABLE tag ADD COLUMN scope TEXT NOT NULL DEFAULT 'global';
ALTER TABLE tag ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS project_tag (
    project_id TEXT NOT NULL,
    tag_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (project_id, tag_id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS income_tag (
    income_id TEXT NOT NULL,
    tag_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (income_id, tag_id),
    FOREIGN KEY (income_id) REFERENCES income(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS expense_tag (
    expense_id TEXT NOT NULL,
    tag_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (expense_id, tag_id),
    FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_tag_scope_active ON tag(scope, is_active);
CREATE INDEX IF NOT EXISTS idx_project_tag_tag_id ON project_tag(tag_id);
CREATE INDEX IF NOT EXISTS idx_income_tag_tag_id ON income_tag(tag_id);
CREATE INDEX IF NOT EXISTS idx_expense_tag_tag_id ON expense_tag(tag_id);
