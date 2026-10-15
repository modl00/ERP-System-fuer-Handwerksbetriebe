-- Portiert von db/migration/V925__leistung_verschnitt.sql (MySQL) nach PostgreSQL.
-- Verschnitt bei Material in der Leistungskalkulation (Issue #135). Idempotent.
--
-- leistung_position.verschnitt_prozent: Zuschlag auf die Menge einer Material-Zeile fuer Verschnitt und Ausschuss (zum Beispiel 8 Prozent bei Rohrlaengen).
-- Der Preis rechnet mit Menge x (1 + Verschnitt). Leer = kein Verschnitt, so rechnen alle bisherigen Zeilen unveraendert weiter.

ALTER TABLE leistung_position ADD COLUMN IF NOT EXISTS verschnitt_prozent DECIMAL(6,2) NULL;
