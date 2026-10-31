-- Table: recurring_off_day_conflict_warnings
-- Tracks when work entries occur on recurring off-days
-- Users must acknowledge these warnings, but they persist for calendar highlighting

CREATE TABLE IF NOT EXISTS recurring_off_day_conflict_warnings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conflict_date DATE NOT NULL,
    time_entry_id BIGINT REFERENCES time_entries(id) ON DELETE CASCADE,
    recurring_off_day_id BIGINT REFERENCES recurring_off_days(id) ON DELETE CASCADE,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_conflict_per_date UNIQUE (user_id, conflict_date)
);

CREATE INDEX idx_conflict_warnings_user_id ON recurring_off_day_conflict_warnings(user_id);
CREATE INDEX idx_conflict_warnings_date ON recurring_off_day_conflict_warnings(conflict_date);
CREATE INDEX idx_conflict_warnings_acknowledged ON recurring_off_day_conflict_warnings(acknowledged);
