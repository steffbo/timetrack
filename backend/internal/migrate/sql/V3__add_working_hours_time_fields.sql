-- Add optional start_time and end_time columns to working_hours table
-- When these are set, they can be used to automatically calculate hours
ALTER TABLE working_hours
    ADD COLUMN start_time TIME,
    ADD COLUMN end_time TIME;

-- Add check constraint to ensure if one time is set, both must be set
ALTER TABLE working_hours
    ADD CONSTRAINT chk_working_hours_times
    CHECK (
        (start_time IS NULL AND end_time IS NULL) OR
        (start_time IS NOT NULL AND end_time IS NOT NULL)
    );

-- Add check constraint to ensure end_time is after start_time when both are set
ALTER TABLE working_hours
    ADD CONSTRAINT chk_working_hours_time_order
    CHECK (
        (start_time IS NULL AND end_time IS NULL) OR
        (end_time > start_time)
    );
