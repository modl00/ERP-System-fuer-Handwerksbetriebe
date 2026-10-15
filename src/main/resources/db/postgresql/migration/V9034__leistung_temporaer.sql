-- Portiert von db/migration/V935__leistung_temporaer.sql (MySQL) nach PostgreSQL.
-- Temporäre Leistung (#55, #205): Eine einmalige Position im Angebot lässt sich kalkulieren. Dafür entsteht im Hintergrund eine Leistung, die nicht im Katalog erscheint
-- (temporaer = 1), bis sie in den Leistungsstamm übernommen wird. Idempotent.
ALTER TABLE leistung ADD COLUMN IF NOT EXISTS temporaer BOOLEAN NOT NULL DEFAULT FALSE;
