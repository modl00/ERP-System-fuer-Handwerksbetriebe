-- Reservierungs-Felder für Bedarfsliste im Projekt
ALTER TABLE artikel_in_projekt
    ADD COLUMN IF NOT EXISTS reserviert BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reserviert_am TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reserviert_menge NUMERIC(10, 3);
