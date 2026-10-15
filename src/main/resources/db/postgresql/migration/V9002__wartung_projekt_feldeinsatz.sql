-- Portiert von db/migration/V902__wartung_projekt_feldeinsatz.sql (MySQL) nach PostgreSQL.
-- Wartung: Projekt an der Wartung (Tuer), Folgepruefung aus einer Vorlage, Bericht im Projekt und
-- gespeicherte Unterschrift je Mitarbeiter. Idempotent.
--
-- wartung_tuer.projekt_id: Die Wartung (Anlage) gehoert zu einem Projekt, neue Feldeinsaetze erben es.
--   ON DELETE SET NULL: Wird ein Projekt geloescht, bleibt die Wartung mit ihrer Historie erhalten.
-- wartung_protokoll.vorlage_protokoll_id: Aus welcher frueheren Pruefung dieser Feldeinsatz entstand (nur Anzeige).
-- wartung_protokoll.bericht_*: Der als PDF im Projekt abgelegte Bericht (ohne Fremdschluessel auf das
--   Projekt-Dokument, das durch eine Vererbungstabelle laeuft; die Anwendung haelt die Verknuepfung).
-- wartung_mitarbeiter_unterschrift: Das Bild wird beim Anlegen eines Feldeinsatzes ins Protokoll KOPIERT.
--   Eine spaetere Aenderung wirkt deshalb nie auf bestehende oder abgeschlossene Protokolle zurueck.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'wartung_tuer' AND column_name = 'projekt_id') THEN
        ALTER TABLE wartung_tuer ADD COLUMN projekt_id BIGINT NULL;
        -- Bestehende Wartungen: das Projekt der neuesten Pruefung uebernehmen (nur beim ersten Lauf, also wenn die Spalte neu ist).
        UPDATE wartung_tuer t SET projekt_id = (SELECT p.projekt_id FROM wartung_protokoll p WHERE p.tuer_id = t.id AND p.projekt_id IS NOT NULL ORDER BY p.datum DESC, p.id DESC LIMIT 1);
    END IF;
END $$;
ALTER TABLE wartung_tuer DROP CONSTRAINT IF EXISTS fk_wartung_tuer_projekt;
ALTER TABLE wartung_tuer ADD CONSTRAINT fk_wartung_tuer_projekt FOREIGN KEY (projekt_id) REFERENCES projekt(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_wartung_tuer_projekt ON wartung_tuer (projekt_id);

ALTER TABLE wartung_protokoll ADD COLUMN IF NOT EXISTS vorlage_protokoll_id BIGINT NULL;
ALTER TABLE wartung_protokoll DROP CONSTRAINT IF EXISTS fk_wartung_protokoll_vorlage;
ALTER TABLE wartung_protokoll ADD CONSTRAINT fk_wartung_protokoll_vorlage FOREIGN KEY (vorlage_protokoll_id) REFERENCES wartung_protokoll(id) ON DELETE SET NULL;

ALTER TABLE wartung_protokoll ADD COLUMN IF NOT EXISTS bericht_dokument_id BIGINT NULL, ADD COLUMN IF NOT EXISTS bericht_erstellt_am TIMESTAMP(6) NULL;

CREATE TABLE IF NOT EXISTS wartung_mitarbeiter_unterschrift (
    mitarbeiter_id BIGINT NOT NULL PRIMARY KEY,
    bild TEXT NOT NULL,
    name VARCHAR(200) NULL,
    geaendert_am TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_wartung_ma_unterschrift_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id) ON DELETE CASCADE
);
