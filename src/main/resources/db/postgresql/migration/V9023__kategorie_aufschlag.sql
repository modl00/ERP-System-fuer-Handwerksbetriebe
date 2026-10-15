-- Portiert von db/migration/V923__kategorie_aufschlag.sql (MySQL) nach PostgreSQL.
-- Aufschlag je Artikelkategorie (Issue #121). Leer = die Kategorie hat keinen eigenen Aufschlag (dann gilt der der Oberkategorie, sonst der allgemeine).
-- Idempotent.

ALTER TABLE kategorie ADD COLUMN IF NOT EXISTS verkaufsaufschlag_prozent DECIMAL(6,2) NULL;
