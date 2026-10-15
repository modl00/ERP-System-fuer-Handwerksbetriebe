-- Portiert von db/migration/V937__artikel_gewicht_flaeche.sql (MySQL) nach PostgreSQL.
-- Gewicht und Fläche am Artikel eindeutig (#228): Fläche je m² Blech (beide Seiten = 2), Gewicht und Fläche je Stück, Herkunft des Gewichts und die Dichte, mit der es berechnet wurde.
-- Alles optional und ohne Vorbelegung: Bestehende Artikel und Leistungen ändern sich nicht. Idempotent.
ALTER TABLE artikel_werkstoffe ADD COLUMN IF NOT EXISTS flaeche_je_qm DECIMAL(10,4) NULL;

ALTER TABLE artikel_werkstoffe ADD COLUMN IF NOT EXISTS kg_je_stueck DECIMAL(12,4) NULL;

ALTER TABLE artikel_werkstoffe ADD COLUMN IF NOT EXISTS m2_je_stueck DECIMAL(12,4) NULL;

ALTER TABLE artikel_werkstoffe ADD COLUMN IF NOT EXISTS gewicht_herkunft VARCHAR(12) NULL;

ALTER TABLE artikel_werkstoffe ADD COLUMN IF NOT EXISTS gewicht_dichte DECIMAL(8,4) NULL;
