-- Portiert von db/migration/V941__wartung_protokoll_verlauf_enum.sql (MySQL) nach PostgreSQL.
-- Korrektur zu V9039 (MySQL: Status-Spalten als ENUM). Auf PostgreSQL sind Java-Enums VARCHAR ohne Werteliste: nichts zu tun.
SELECT 1;
