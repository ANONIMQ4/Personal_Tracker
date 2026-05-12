CREATE TABLE IF NOT EXISTS broker_holdings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    holding_type VARCHAR(100),
    name VARCHAR(255),
    ticker VARCHAR(64),
    price_text VARCHAR(120),
    value_text VARCHAR(120),
    quantity_text VARCHAR(80),
    profit_text VARCHAR(120),
    profit_amount NUMERIC(19, 2),
    profit_percent NUMERIC(10, 4)
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS account_balance NUMERIC(19, 2) DEFAULT 0;
