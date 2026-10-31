-- Add state field to users table for public holiday calculation
ALTER TABLE users ADD COLUMN state VARCHAR(50);

-- Update existing users to have a default state (Berlin)
UPDATE users SET state = 'BERLIN' WHERE state IS NULL;

-- Make state NOT NULL after setting defaults
ALTER TABLE users ALTER COLUMN state SET NOT NULL;

-- Create vacation_balance table
CREATE TABLE vacation_balance (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,

    -- Year this balance applies to
    year INTEGER NOT NULL,

    -- Allowances in days (not hours)
    annual_allowance_days DECIMAL(5,1) NOT NULL DEFAULT 30.0,
    carried_over_days DECIMAL(5,1) DEFAULT 0.0,
    adjustment_days DECIMAL(5,1) DEFAULT 0.0,

    -- Calculated fields (updated when vacation is added/removed)
    used_days DECIMAL(5,1) DEFAULT 0.0,
    remaining_days DECIMAL(5,1),

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_vacation_balance_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_year UNIQUE (user_id, year),
    CONSTRAINT chk_annual_allowance CHECK (annual_allowance_days >= 0),
    CONSTRAINT chk_carried_over CHECK (carried_over_days >= 0)
);

-- Create indexes for performance
CREATE INDEX idx_vacation_balance_user_year ON vacation_balance(user_id, year);

-- Initialize vacation balance for existing users for current year (2025)
INSERT INTO vacation_balance (user_id, year, annual_allowance_days, carried_over_days, adjustment_days, used_days, remaining_days)
SELECT
    id,
    2025,
    30.0,
    0.0,
    0.0,
    0.0,
    30.0
FROM users;
