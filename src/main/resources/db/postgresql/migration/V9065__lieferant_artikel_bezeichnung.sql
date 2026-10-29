ALTER TABLE lieferanten_artikel_preise
    ADD COLUMN IF NOT EXISTS lieferanten_bezeichnung VARCHAR(500);
