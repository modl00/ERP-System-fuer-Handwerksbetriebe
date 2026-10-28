ALTER TABLE artikel ADD COLUMN IF NOT EXISTS handelseinheit_bezeichnung VARCHAR(50);
ALTER TABLE artikel ADD COLUMN IF NOT EXISTS handelseinheit_meter NUMERIC(10,3);
