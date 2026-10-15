-- Portiert von db/migration/V931__fremdleistung_menge_aus.sql (MySQL) nach PostgreSQL.
-- Fremdleistung: Menge aus dem berechneten Gewicht oder der Oberflaeche der Leistung (Stufe 4 von #135). Idempotent.
-- leistung_position.menge_aus: HAND (leer) = die Menge steht in der Zeile; GEWICHT = Gesamtgewicht (kg) und OBERFLAECHE = Gesamtoberflaeche (m2) der uebrigen Zeilen
--   der Leistung bzw. Baugruppe, berechnet aus Artikel (kg je m, Mantelflaeche je m) und Menge.
ALTER TABLE leistung_position ADD COLUMN IF NOT EXISTS menge_aus VARCHAR(20) NULL;
