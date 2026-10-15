-- Portiert von db/migration/V944__arbeitsgang_satzquelle.sql (MySQL) nach PostgreSQL.
-- Kalkulationssatz am Arbeitsgang: von Hand eintragen (wie bisher) oder dem Verrechnungssatz der Kostenrechnung folgen, für den ganzen Betrieb oder eine Qualifikation.
-- Beide Spalten sind Text (kein ENUM), Vorgabe EIGEN = alles bleibt wie bisher. Idempotent.
ALTER TABLE arbeitsgang ADD COLUMN IF NOT EXISTS satz_quelle VARCHAR(20) NOT NULL DEFAULT 'EIGEN';
ALTER TABLE arbeitsgang ADD COLUMN IF NOT EXISTS satz_gruppe VARCHAR(30) NULL;
