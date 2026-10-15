-- Portiert von db/migration/V924__leistung_baugruppen.sql (MySQL) nach PostgreSQL.
-- Baugruppen in Leistungen (Issue #135, Stufe 1). Idempotent.
--
-- Eine Baugruppe ist eine Leistung, die als Baustein in anderen Leistungen benutzt werden darf (Pfosten, Feld, Handlauf ...). Sie hat wie jede Leistung
-- Material und Arbeit und kann selbst Baugruppen enthalten (mehrere Ebenen; Kreise und mehr als 8 Ebenen prueft der Server beim Speichern).
-- leistung.ist_baugruppe:                     darf als Baugruppe in anderen Leistungen gewaehlt werden
-- leistung_position.baugruppe_id:              die benutzte Baugruppe (Zeile vom Typ BAUGRUPPE); "menge" ist die Menge je Einheit der Leistung.
--                                              Kein ON DELETE CASCADE: eine benutzte Baugruppe laesst sich nicht loeschen (der Server meldet, wo sie benutzt wird).
-- leistung_position.anzahl_vorgaenge:          bei Arbeit mit Zeiten: wie oft der Vorgang je Einheit vorkommt (4 Bohrungen je Stab); die Bearbeitungszeit
--                                              gilt je Vorgang. Leer = 1, so rechnen alle bisherigen Zeilen unveraendert weiter.

ALTER TABLE leistung ADD COLUMN IF NOT EXISTS ist_baugruppe BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE leistung_position ADD COLUMN IF NOT EXISTS baugruppe_id BIGINT NULL;

ALTER TABLE leistung_position DROP CONSTRAINT IF EXISTS fk_leistung_position_baugruppe;
ALTER TABLE leistung_position ADD CONSTRAINT fk_leistung_position_baugruppe FOREIGN KEY (baugruppe_id) REFERENCES leistung(id);

ALTER TABLE leistung_position ADD COLUMN IF NOT EXISTS anzahl_vorgaenge DECIMAL(12,3) NULL;
