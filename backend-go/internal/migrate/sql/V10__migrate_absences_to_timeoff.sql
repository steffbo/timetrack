-- Migrate SICK, PTO, EVENT time entries to time_off table
-- PTO maps to VACATION, EVENT maps to PERSONAL
INSERT INTO time_off (user_id, start_date, end_date, time_off_type, notes, created_at, updated_at)
SELECT
    te.user_id,
    te.entry_date as start_date,
    te.entry_date as end_date,
    CASE te.entry_type
        WHEN 'SICK' THEN 'SICK'
        WHEN 'PTO' THEN 'VACATION'
        WHEN 'EVENT' THEN 'PERSONAL'
    END as time_off_type,
    te.notes,
    te.created_at,
    te.updated_at
FROM time_entries te
WHERE te.entry_type IN ('SICK', 'PTO', 'EVENT');

-- Delete migrated records from time_entries
DELETE FROM time_entries WHERE entry_type IN ('SICK', 'PTO', 'EVENT');

-- Update constraint to only allow WORK
ALTER TABLE time_entries DROP CONSTRAINT IF EXISTS time_entries_entry_type_check;
ALTER TABLE time_entries ADD CONSTRAINT time_entries_entry_type_check
    CHECK (entry_type = 'WORK');
