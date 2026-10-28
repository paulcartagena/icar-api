-- An event can't end before it starts (also checked in EventService)
ALTER TABLE event ADD CONSTRAINT chk_event_period CHECK (end_at >= start_at);

-- Photos are always shown in a defined order; rows loaded before the API existed go first
UPDATE event_photo SET display_order = 1 WHERE display_order IS NULL;
ALTER TABLE event_photo ALTER COLUMN display_order SET NOT NULL;
