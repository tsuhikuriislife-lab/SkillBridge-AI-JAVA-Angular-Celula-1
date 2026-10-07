ALTER TABLE offerings ADD COLUMN provider_id UUID;
ALTER TABLE offerings ADD COLUMN start_time TIME;
ALTER TABLE offerings ADD COLUMN end_time TIME;
ALTER TABLE offerings ADD COLUMN end_day VARCHAR(255);
ALTER TABLE offerings ADD COLUMN photo_url VARCHAR(1000);
