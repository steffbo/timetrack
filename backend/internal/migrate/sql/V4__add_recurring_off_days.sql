-- Create recurring_off_days table for recurring schedule exceptions
CREATE TABLE recurring_off_days (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,

    -- Pattern type: 'EVERY_NTH_WEEK' or 'NTH_WEEKDAY_OF_MONTH'
    recurrence_pattern VARCHAR(50) NOT NULL,

    -- Weekday (1=Monday, 7=Sunday)
    weekday SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),

    -- For EVERY_NTH_WEEK pattern
    week_interval INTEGER,
    reference_date DATE,

    -- For NTH_WEEKDAY_OF_MONTH pattern (1=first, 2=second, 3=third, 4=fourth, 5=last)
    week_of_month SMALLINT CHECK (week_of_month BETWEEN 1 AND 5),

    -- Common fields
    start_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN DEFAULT true NOT NULL,
    description TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_recurring_off_days_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    -- Ensure correct fields are set for each pattern type
    CONSTRAINT chk_pattern_fields CHECK (
        (recurrence_pattern = 'EVERY_NTH_WEEK' AND week_interval IS NOT NULL AND reference_date IS NOT NULL AND week_of_month IS NULL) OR
        (recurrence_pattern = 'NTH_WEEKDAY_OF_MONTH' AND week_of_month IS NOT NULL AND week_interval IS NULL AND reference_date IS NULL)
    )
);

-- Create indexes for performance
CREATE INDEX idx_recurring_off_days_user_id ON recurring_off_days(user_id);
CREATE INDEX idx_recurring_off_days_active ON recurring_off_days(user_id, is_active);
CREATE INDEX idx_recurring_off_days_pattern ON recurring_off_days(recurrence_pattern);
