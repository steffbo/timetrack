-- Add break_minutes column to time_entries table
-- Default value is 0 (no break)
ALTER TABLE time_entries
ADD COLUMN break_minutes INTEGER NOT NULL DEFAULT 0;

-- Add comment for documentation
COMMENT ON COLUMN time_entries.break_minutes IS 'Break duration in minutes (default: 0)';
