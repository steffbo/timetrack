-- Add EDUCATION to time_off_type constraint
ALTER TABLE time_off DROP CONSTRAINT time_off_time_off_type_check;
ALTER TABLE time_off ADD CONSTRAINT time_off_time_off_type_check
    CHECK (time_off_type IN ('VACATION', 'SICK', 'CHILD_SICK', 'PERSONAL', 'PUBLIC_HOLIDAY', 'EDUCATION'));
