-- Artikelvarianten: Ein Grundartikel (z. B. "QH Quadratrohr") fasst viele echte Artikel zusammen (Varianten wie "QH 40x40x2").
-- Die Varianten sind normale Artikel mit eigenen Preisen; sie zeigen nur auf ihren Grundartikel.
-- Löscht man einen Grundartikel, bleiben die Varianten als eigene Artikel bestehen (ON DELETE SET NULL).
-- Idempotent.

ALTER TABLE artikel ADD COLUMN IF NOT EXISTS ist_grundartikel BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE artikel ADD COLUMN IF NOT EXISTS kurzbezeichnung VARCHAR(32) NULL;
ALTER TABLE artikel ADD COLUMN IF NOT EXISTS grundartikel_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_artikel_grundartikel') THEN
        ALTER TABLE artikel ADD CONSTRAINT fk_artikel_grundartikel FOREIGN KEY (grundartikel_id) REFERENCES artikel (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_artikel_grundartikel ON artikel (grundartikel_id);
