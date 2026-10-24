-- Ministry names are unique regardless of case (also checked in MinistryService)
CREATE UNIQUE INDEX uq_ministry_name ON ministry (lower(name));

-- A schedule always belongs to a ministry, uses java.time.DayOfWeek names and is not repeated
ALTER TABLE service_schedule ALTER COLUMN ministry_id SET NOT NULL;
ALTER TABLE service_schedule ADD CONSTRAINT chk_service_schedule_day_of_week
    CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'));
ALTER TABLE service_schedule ADD CONSTRAINT uq_service_schedule_slot
    UNIQUE (ministry_id, day_of_week, start_time);
