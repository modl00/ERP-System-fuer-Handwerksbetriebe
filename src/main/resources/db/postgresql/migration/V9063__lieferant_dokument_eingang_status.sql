-- Eingangskorb: Status-Spalte für Lieferanten-Dokumente aus E-Mail-Import.
-- Neue Dokumente landen zunächst als WARTE_AUF_ZUORDNUNG im Eingangskorb;
-- bestehende Zeilen gelten als bereits zugeordnet (ZUGEORDNET).

ALTER TABLE lieferant_dokument
    ADD COLUMN IF NOT EXISTS eingang_status VARCHAR(40);

UPDATE lieferant_dokument
SET eingang_status = 'ZUGEORDNET'
WHERE eingang_status IS NULL;

ALTER TABLE lieferant_dokument
    ALTER COLUMN eingang_status SET NOT NULL,
    ALTER COLUMN eingang_status SET DEFAULT 'WARTE_AUF_ZUORDNUNG';
