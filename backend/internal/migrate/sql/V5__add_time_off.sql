-- Create time_off table for vacations, sick days, and other time off
CREATE TABLE time_off (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,

    -- Date range
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,

    -- Type: VACATION, SICK, PERSONAL, PUBLIC_HOLIDAY
    time_off_type VARCHAR(20) NOT NULL CHECK (time_off_type IN ('VACATION', 'SICK', 'PERSONAL', 'PUBLIC_HOLIDAY')),

    -- Override hours per day (NULL = use expected hours from working_hours)
    hours_per_day DECIMAL(4,2),

    -- Optional notes
    notes TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_time_off_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_end_after_start CHECK (end_date >= start_date),
    CONSTRAINT chk_hours_per_day CHECK (hours_per_day IS NULL OR (hours_per_day >= 0 AND hours_per_day <= 24))
);

-- Create indexes for performance
CREATE INDEX idx_time_off_user_id ON time_off(user_id);
CREATE INDEX idx_time_off_dates ON time_off(user_id, start_date, end_date);
CREATE INDEX idx_time_off_type ON time_off(user_id, time_off_type);
CREATE INDEX idx_time_off_start_date ON time_off(start_date);
