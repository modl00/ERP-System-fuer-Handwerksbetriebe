-- Portiert von db/migration/V936__arbeitsgang_formel.sql (MySQL) nach PostgreSQL.
-- Arbeitsgang mit Formel (#216): Eingabefelder (JSON) und Formeln für Bearbeitungs- und Rüstzeit; an der Position der Leistung die eingegebenen Werte. Idempotent.
ALTER TABLE arbeitsgang ADD COLUMN IF NOT EXISTS formel_felder_json VARCHAR(4000) NULL;

ALTER TABLE arbeitsgang ADD COLUMN IF NOT EXISTS formel_bearbeitung VARCHAR(500) NULL;

ALTER TABLE arbeitsgang ADD COLUMN IF NOT EXISTS formel_ruest VARCHAR(500) NULL;

ALTER TABLE leistung_position ADD COLUMN IF NOT EXISTS variablen_json VARCHAR(2000) NULL;
