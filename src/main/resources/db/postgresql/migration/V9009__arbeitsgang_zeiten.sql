-- Portiert von db/migration/V909__arbeitsgang_zeiten.sql (MySQL) nach PostgreSQL.
-- Arbeitsgaenge kalkulieren: Ruestzeit und Bearbeitungszeit (Normzeiten). Idempotent.
--
-- arbeitsgang.ruestzeit_minuten:       einmalige Zeit je Los (Maschine einrichten, Material holen), in Minuten. Leer = keine Angabe.
-- arbeitsgang.bearbeitungszeit_minuten: Zeit je Einheit (je Stueck, je lfm ...), in Minuten. Leer = keine Angabe.
-- leistung.losgroesse:                 Auf wie viele Einheiten der Leistung sich die Ruestzeit verteilt (Standard 1: volle Ruestzeit je Einheit).
-- leistung_position.ruestzeit_minuten / bearbeitungszeit_minuten: Zeiten einer Arbeits-Zeile der Kalkulation. Beide leer = die Zeile
--   rechnet wie bisher mit "menge" in Stunden. Sonst ist "menge" nur die daraus errechnete Stundenzahl je Einheit der Leistung.

ALTER TABLE arbeitsgang ADD COLUMN IF NOT EXISTS ruestzeit_minuten DECIMAL(8,2) NULL;

ALTER TABLE arbeitsgang ADD COLUMN IF NOT EXISTS bearbeitungszeit_minuten DECIMAL(8,2) NULL;

ALTER TABLE leistung ADD COLUMN IF NOT EXISTS losgroesse DECIMAL(12,3) NOT NULL DEFAULT 1;

ALTER TABLE leistung_position ADD COLUMN IF NOT EXISTS ruestzeit_minuten DECIMAL(8,2) NULL;

ALTER TABLE leistung_position ADD COLUMN IF NOT EXISTS bearbeitungszeit_minuten DECIMAL(8,2) NULL;
