-- Portiert von db/migration/V939__arbeitsgang_archiviert.sql (MySQL) nach PostgreSQL.
-- Arbeitsgang ausblenden statt löschen (#235): Ein benutzter Arbeitsgang kann archiviert werden. Er erscheint nicht mehr in der Auswahl, bestehende Leistungen und Buchungen bleiben unverändert. Idempotent.
ALTER TABLE arbeitsgang ADD COLUMN IF NOT EXISTS archiviert BOOLEAN NOT NULL DEFAULT FALSE;
