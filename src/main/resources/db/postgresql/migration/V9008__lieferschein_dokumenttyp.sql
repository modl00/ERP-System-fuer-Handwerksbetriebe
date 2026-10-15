-- Portiert von db/migration/V908__lieferschein_dokumenttyp.sql (MySQL) nach PostgreSQL.
-- Lieferschein: neuer Dokumenttyp-Wert LIEFERSCHEIN. Auf PostgreSQL sind Java-Enums VARCHAR ohne Werteliste und ohne CHECK-Regel:
-- ein neuer Wert braucht keine Migration (die MySQL-Fassung entfernte alte CHECKs und ENUM-Spalten).
SELECT 1;
