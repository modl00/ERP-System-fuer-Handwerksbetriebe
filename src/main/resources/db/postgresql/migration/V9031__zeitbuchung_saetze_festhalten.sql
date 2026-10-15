-- Portiert von db/migration/V932__zeitbuchung_saetze_festhalten.sql (MySQL) nach PostgreSQL.
-- Kostenrechnung Stufe 1 (#166): Eine Zeitbuchung haelt ihre Saetze selbst fest. Idempotent.
--
-- verrechnungssatz_wert: der Satz (Euro je Stunde), mit dem die Buchung bewertet wird, zum Zeitpunkt des Buchens festgehalten. Vorher zeigte die Buchung nur auf eine
--   Zeile von arbeitsgang_stundensatz; wurde diese Zeile geaendert, aenderten sich alle Buchungen des Jahres rueckwirkend.
-- kostensatz_wert: der Kostensatz (Selbstkosten je Stunde) der Buchung; bleibt vorerst leer und wird mit den spaeteren Stufen (Personalkostensatz) gefuellt.
-- Die vorhandenen Buchungen bekommen als Verrechnungssatz genau die Zahl, auf die sie heute zeigen: Projektkosten und Gewinn bleiben beim Update unveraendert.

ALTER TABLE zeitbuchung ADD COLUMN IF NOT EXISTS verrechnungssatz_wert DECIMAL(10,2) NULL;

ALTER TABLE zeitbuchung ADD COLUMN IF NOT EXISTS kostensatz_wert DECIMAL(10,2) NULL;

UPDATE zeitbuchung z
SET verrechnungssatz_wert = s.satz
FROM arbeitsgang_stundensatz s
WHERE s.id = z.arbeitsgang_stundensatz_id AND z.verrechnungssatz_wert IS NULL;
