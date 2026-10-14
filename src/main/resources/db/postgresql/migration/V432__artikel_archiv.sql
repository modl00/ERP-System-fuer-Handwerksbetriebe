-- Artikel-Archiv: Ein archivierter Artikel verschwindet aus Suche und Auswahl, bleibt aber erhalten und lässt sich wiederherstellen.
-- "Endgültig gelöscht" heißt: Wird der Artikel in keinem Projekt verwendet, entfällt er ganz. Steckt er in Projekten, bleibt er als unsichtbarer
-- Platzhalter (geloescht_am gesetzt) bestehen, damit die Projekte weiter seinen Namen und Preis zeigen.
-- Idempotent.

ALTER TABLE artikel ADD COLUMN IF NOT EXISTS archiviert_am TIMESTAMP NULL;
ALTER TABLE artikel ADD COLUMN IF NOT EXISTS archiviert_von VARCHAR(100) NULL;
ALTER TABLE artikel ADD COLUMN IF NOT EXISTS geloescht_am TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_artikel_archiviert ON artikel (archiviert_am);
