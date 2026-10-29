ALTER TABLE lager_buchung
    ADD COLUMN IF NOT EXISTS chargennummer VARCHAR(100),
    ADD COLUMN IF NOT EXISTS werkstoffzeugnis_id BIGINT,
    ADD CONSTRAINT IF NOT EXISTS fk_lager_buchung_zeugnis
        FOREIGN KEY (werkstoffzeugnis_id) REFERENCES lieferant_dokument(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_lager_buchung_charge
    ON lager_buchung(artikel_id, chargennummer) WHERE chargennummer IS NOT NULL;
