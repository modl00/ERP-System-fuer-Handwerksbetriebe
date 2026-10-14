ALTER TABLE firmeninformation ADD COLUMN IF NOT EXISTS briefpapier_dateiname    VARCHAR(255)             NULL;
ALTER TABLE firmeninformation ADD COLUMN IF NOT EXISTS briefpapier_enthaelt_kopf BOOLEAN NOT NULL DEFAULT FALSE;
