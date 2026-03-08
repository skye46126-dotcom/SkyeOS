PRAGMA foreign_keys = ON;

-- Monthly baseline cost plan (e.g. basic living + fixed subscriptions baseline).
CREATE TABLE IF NOT EXISTS expense_baseline_month (
    owner_user_id                    TEXT NOT NULL,
    month                            TEXT NOT NULL, -- YYYY-MM
    basic_living_cents               INTEGER NOT NULL DEFAULT 0 CHECK(basic_living_cents >= 0),
    fixed_subscription_cents         INTEGER NOT NULL DEFAULT 0 CHECK(fixed_subscription_cents >= 0),
    note                             TEXT,
    created_at                       TEXT NOT NULL,
    updated_at                       TEXT NOT NULL,
    PRIMARY KEY (owner_user_id, month),
    FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Recurring monthly rules (subscriptions, insurance, etc).
CREATE TABLE IF NOT EXISTS expense_recurring_rule (
    id                               TEXT PRIMARY KEY,
    owner_user_id                    TEXT NOT NULL,
    name                             TEXT NOT NULL,
    category                         TEXT NOT NULL CHECK(category IN ('necessary','experience','subscription','investment')),
    monthly_amount_cents             INTEGER NOT NULL CHECK(monthly_amount_cents >= 0),
    is_necessary                     INTEGER NOT NULL DEFAULT 1 CHECK(is_necessary IN (0,1)),
    start_month                      TEXT NOT NULL, -- YYYY-MM
    end_month                        TEXT,          -- YYYY-MM nullable
    is_active                        INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1)),
    note                             TEXT,
    created_at                       TEXT NOT NULL,
    updated_at                       TEXT NOT NULL,
    FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Capex/amortization entries for large purchases (phone, laptop, course, etc).
CREATE TABLE IF NOT EXISTS expense_capex (
    id                               TEXT PRIMARY KEY,
    owner_user_id                    TEXT NOT NULL,
    name                             TEXT NOT NULL,
    purchase_date                    TEXT NOT NULL, -- YYYY-MM-DD
    purchase_amount_cents            INTEGER NOT NULL CHECK(purchase_amount_cents >= 0),
    residual_rate_bps                INTEGER NOT NULL DEFAULT 0 CHECK(residual_rate_bps >= 0 AND residual_rate_bps <= 10000),
    useful_months                    INTEGER NOT NULL CHECK(useful_months > 0),
    monthly_amortized_cents          INTEGER NOT NULL CHECK(monthly_amortized_cents >= 0),
    amortization_start_month         TEXT NOT NULL, -- YYYY-MM
    amortization_end_month           TEXT NOT NULL, -- YYYY-MM
    is_active                        INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1)),
    note                             TEXT,
    created_at                       TEXT NOT NULL,
    updated_at                       TEXT NOT NULL,
    FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_expense_baseline_owner_month
    ON expense_baseline_month(owner_user_id, month);
CREATE INDEX IF NOT EXISTS idx_expense_recurring_owner_active
    ON expense_recurring_rule(owner_user_id, is_active, start_month, end_month);
CREATE INDEX IF NOT EXISTS idx_expense_capex_owner_active
    ON expense_capex(owner_user_id, is_active, amortization_start_month, amortization_end_month);
