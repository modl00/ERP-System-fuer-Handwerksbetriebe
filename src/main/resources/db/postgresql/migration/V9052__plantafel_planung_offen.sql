-- Portiert von db/migration/V953__plantafel_planung_offen.sql (MySQL) nach PostgreSQL.
-- Plantafel (#331, Stufe 5): Fenster bei der Auftragsbestätigung. "Später" legt die Standardkette mit geschätzten Werten an und merkt den Auftrag
-- als "neu, noch nicht eingeplant" (planung_offen), bis der Planer das Fenster ausfüllt. Idempotent, keine Enum-Spalten.
ALTER TABLE plan_projekt ADD COLUMN IF NOT EXISTS planung_offen BOOLEAN NOT NULL DEFAULT FALSE;
