-- Fremdleistung (#430): eigene Lieferzeit je Leistung. Leer = Standard des Lieferanten gilt.
-- Idempotent.
ALTER TABLE fremdleistung ADD COLUMN IF NOT EXISTS lieferzeit_tage INT NULL;
