-- Zwei-Stufen-Archiv: archivieren (alle Nutzer), endgültig löschen (nur Admin, nur aus Archiv)
ALTER TABLE artikel
    ADD COLUMN IF NOT EXISTS archiviert BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS archiviert_am TIMESTAMP;

ALTER TABLE lieferanten
    ADD COLUMN IF NOT EXISTS archiviert BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS archiviert_am TIMESTAMP;

ALTER TABLE lagerort
    ADD COLUMN IF NOT EXISTS archiviert BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS archiviert_am TIMESTAMP;
