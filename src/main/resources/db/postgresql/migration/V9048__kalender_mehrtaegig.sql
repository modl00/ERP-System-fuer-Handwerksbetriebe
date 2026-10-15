-- Portiert von db/migration/V949__kalender_mehrtaegig.sql (MySQL) nach PostgreSQL.
-- Mehrtaegige Kalendertermine (Plantafel): letzter Tag des Termins.
-- Leer = der Termin dauert nur am Tag "datum" (wie bisher). Idempotent: Mehrfach-Ausfuehrung unschaedlich.

ALTER TABLE kalender_eintrag ADD COLUMN IF NOT EXISTS datum_bis DATE NULL;
