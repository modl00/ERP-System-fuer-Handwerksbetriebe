-- Portiert von db/migration/V920__wartung_feldtyp_jahr.sql (MySQL) nach PostgreSQL.
-- Wartung: neue Art von Datenfeld "Jahreszahl" (JAHR).
-- Auf PostgreSQL sind Java-Enums VARCHAR ohne Werteliste in der Datenbank: ein neuer Wert braucht keine Migration.
SELECT 1;
