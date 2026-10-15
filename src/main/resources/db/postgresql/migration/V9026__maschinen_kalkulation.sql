-- Portiert von db/migration/V927__maschinen_kalkulation.sql (MySQL) nach PostgreSQL.
-- Maschinenkosten in der Leistungskalkulation (Issue #63, Stufe 3 von #135). Idempotent.
--
-- arbeitsgang.maschinen_stundensatz:   Kosten der Maschine je Stunde in Euro (Abschreibung, Wartung, Energie, Raum); leer = der Arbeitsgang nutzt keine Maschine.
-- arbeitsgang.maschinen_bezeichnung:   Name der Maschine (zum Beispiel "Kappsaege"); nur zur Anzeige.
-- leistung_position.maschinenanteil_prozent: Maschinenzeit in Prozent der Arbeitszeit der Zeile; leer = 100 (die Maschine laeuft die ganze Zeit).
--   Mit 0 rechnet die Zeile ohne Maschine, ueber 100 laeuft die Maschine laenger als der Mitarbeiter dabei ist.

ALTER TABLE arbeitsgang ADD COLUMN IF NOT EXISTS maschinen_stundensatz DECIMAL(10,2) NULL;

ALTER TABLE arbeitsgang ADD COLUMN IF NOT EXISTS maschinen_bezeichnung VARCHAR(100) NULL;

ALTER TABLE leistung_position ADD COLUMN IF NOT EXISTS maschinenanteil_prozent DECIMAL(7,2) NULL;
