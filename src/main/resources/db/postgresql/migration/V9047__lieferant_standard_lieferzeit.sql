-- Portiert von db/migration/V948__lieferant_standard_lieferzeit.sql (MySQL) nach PostgreSQL.
-- Standard-Lieferzeit am Lieferanten (Arbeitstage ab Bestellung), Plantafel #331.
-- Leer = nicht angegeben. Die Plantafel nutzt den Wert spaeter, wenn zu einem
-- Material noch keine Auftragsbestaetigung des Lieferanten vorliegt.
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

ALTER TABLE lieferanten ADD COLUMN IF NOT EXISTS standard_lieferzeit_tage INT NULL;
