-- Portiert von db/migration/V952__plantafel_lieferung.sql (MySQL) nach PostgreSQL.
-- Plantafel (#331, Stufe 2/7): Lieferung bei Wartevorgängen. Ein Wartevorgang (Material, Fremdleistung) kann einen Lieferanten haben:
-- Dessen übliche Lieferzeit gilt, solange nichts von Hand eingetragen ist; "bestellt am" startet die Lieferzeit (Entscheidung H);
-- der Liefertermin aus der Auftragsbestätigung des Lieferanten korrigiert sie. Dazu die Fremdleistung mit Menge (kg oder m²) für die Sammelcharge.
-- Idempotent, keine Enum-Spalten.

ALTER TABLE plan_vorgang ADD COLUMN IF NOT EXISTS lieferant_id BIGINT NULL;

ALTER TABLE plan_vorgang ADD COLUMN IF NOT EXISTS fremdleistung_id BIGINT NULL;

ALTER TABLE plan_vorgang ADD COLUMN IF NOT EXISTS menge DECIMAL(12,3) NULL;

ALTER TABLE plan_vorgang ADD COLUMN IF NOT EXISTS mengen_einheit VARCHAR(10) NULL;

ALTER TABLE plan_vorgang ADD COLUMN IF NOT EXISTS bestellt_am DATE NULL;

ALTER TABLE plan_vorgang ADD COLUMN IF NOT EXISTS liefertermin_lieferant DATE NULL;
