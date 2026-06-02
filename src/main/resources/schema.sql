CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    account_balance NUMERIC(19, 2) DEFAULT 0
);

CREATE TABLE IF NOT EXISTS finance_operations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation_date TIMESTAMP,
    payment_date DATE,
    card_number VARCHAR(255),
    status VARCHAR(255),
    operation_amount NUMERIC(19, 2),
    operation_currency VARCHAR(32),
    payment_amount NUMERIC(19, 2),
    payment_currency VARCHAR(32),
    cashback NUMERIC(19, 2),
    category VARCHAR(255),
    mcc INTEGER,
    description TEXT,
    exclude_from_analytics BOOLEAN NOT NULL DEFAULT FALSE,
    counterparty VARCHAR(255),
    bonuses NUMERIC(19, 2),
    investment_rounding NUMERIC(19, 2),
    rounded_operation_amount NUMERIC(19, 2),
    source VARCHAR(64),
    operation_key VARCHAR(128)
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS account_balance NUMERIC(19, 2) DEFAULT 0;

ALTER TABLE finance_operations
    ADD COLUMN IF NOT EXISTS exclude_from_analytics BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE finance_operations
    ADD COLUMN IF NOT EXISTS counterparty VARCHAR(255);

ALTER TABLE finance_operations
    ADD COLUMN IF NOT EXISTS operation_key VARCHAR(128);

CREATE TABLE IF NOT EXISTS rules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255),
    original_prompt TEXT,
    conditions_json TEXT NOT NULL,
    actions_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_applied_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE rules
    ADD COLUMN IF NOT EXISTS last_applied_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS rule_applications (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES rules(id) ON DELETE CASCADE,
    operation_id BIGINT NOT NULL REFERENCES finance_operations(id) ON DELETE CASCADE,
    before_category VARCHAR(255),
    before_exclude_from_analytics BOOLEAN NOT NULL DEFAULT FALSE,
    before_counterparty VARCHAR(255),
    before_description TEXT,
    after_category VARCHAR(255),
    after_exclude_from_analytics BOOLEAN NOT NULL DEFAULT FALSE,
    after_counterparty VARCHAR(255),
    after_description TEXT,
    applied_at TIMESTAMP,
    CONSTRAINT uq_rule_applications_rule_operation UNIQUE (rule_id, operation_id)
);

CREATE INDEX IF NOT EXISTS idx_finance_operations_user_date
    ON finance_operations (user_id, operation_date DESC);

CREATE INDEX IF NOT EXISTS idx_finance_operations_user_key
    ON finance_operations (user_id, operation_key);

CREATE INDEX IF NOT EXISTS idx_finance_operations_user_category
    ON finance_operations (user_id, category);

CREATE INDEX IF NOT EXISTS idx_rules_user_created
    ON rules (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rule_applications_rule
    ON rule_applications (rule_id);
