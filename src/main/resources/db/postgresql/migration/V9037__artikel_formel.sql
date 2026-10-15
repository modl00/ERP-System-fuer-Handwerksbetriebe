-- Portiert von db/migration/V938__artikel_formel.sql (MySQL) nach PostgreSQL.
-- Zuschnitt-Formeln am Artikel (#228): Eingabefelder (JSON) und je eine Formel für Gewicht (kg) und Außenfläche (m²) je Stück. Alles leer, bestehende Artikel ändern sich nicht. Idempotent.
ALTER TABLE artikel_werkstoffe ADD COLUMN IF NOT EXISTS formel_felder_json VARCHAR(4000) NULL;

ALTER TABLE artikel_werkstoffe ADD COLUMN IF NOT EXISTS formel_gewicht VARCHAR(500) NULL;

ALTER TABLE artikel_werkstoffe ADD COLUMN IF NOT EXISTS formel_flaeche VARCHAR(500) NULL;
