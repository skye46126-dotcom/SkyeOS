CREATE TABLE IF NOT EXISTS expense_project (
    expense_id               TEXT NOT NULL,
    project_id               TEXT NOT NULL,
    weight_ratio             REAL NOT NULL DEFAULT 1.0 CHECK(weight_ratio > 0),
    created_at               TEXT NOT NULL,
    PRIMARY KEY (expense_id, project_id),
    FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_expense_project_project_id
    ON expense_project(project_id);
