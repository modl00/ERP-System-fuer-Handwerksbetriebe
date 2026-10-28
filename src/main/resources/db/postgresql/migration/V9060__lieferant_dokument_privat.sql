-- Lieferanten-Dokumente als "Privat" kennzeichnen (nicht zur Firma gehörend)
ALTER TABLE lieferant_dokument
    ADD COLUMN IF NOT EXISTS privat BOOLEAN NOT NULL DEFAULT FALSE;
