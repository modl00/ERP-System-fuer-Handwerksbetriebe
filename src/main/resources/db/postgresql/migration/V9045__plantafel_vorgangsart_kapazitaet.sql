-- Portiert von db/migration/V946__plantafel_vorgangsart_kapazitaet.sql (MySQL) nach PostgreSQL.
-- Plantafel Stufe 3 (#184): Je Vorgangsart einstellbar, welche Abteilung ihre Arbeitszeit liefert und wie viele Stunden dort pro Arbeitstag geleistet werden
-- (zum Beispiel Fertigung = Werkstatt, 24 Stunden = 3 Mitarbeiter mal 8). Daraus schlägt das Programm die Dauer aus der Arbeitszeit des Auftrags vor.
-- Beide Spalten sind leer (kein Vorschlag), bis jemand sie einträgt. Idempotent, keine Enum-Spalten.
ALTER TABLE plan_vorgangstyp ADD COLUMN IF NOT EXISTS abteilung_id BIGINT NULL;
ALTER TABLE plan_vorgangstyp ADD COLUMN IF NOT EXISTS stunden_pro_tag DECIMAL(7,2) NULL;
