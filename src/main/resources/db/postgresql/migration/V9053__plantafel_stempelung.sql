-- Portiert von db/migration/V954__plantafel_stempelung.sql (MySQL) nach PostgreSQL.
-- Plantafel (#331, Stufe 6): Stempelung als Fortschritt. Die gestempelten Stunden eines Auftrags zählen vom Aufwand der Vorgänge ab.
-- Die Zuordnung läuft über die Abteilung des Arbeitsgangs (Arbeitszeit aus der Vorgangsart). Teilen sich mehrere Vorgangsarten eine Abteilung,
-- legt diese Tabelle fest, zu welcher Vorgangsart ein Arbeitsgang zählt. Idempotent, keine Enum-Spalten.
CREATE TABLE IF NOT EXISTS plan_arbeitsgang_art (
    arbeitsgang_id BIGINT NOT NULL PRIMARY KEY,
    typ_id BIGINT NOT NULL,
    CONSTRAINT fk_plan_aga_typ FOREIGN KEY (typ_id) REFERENCES plan_vorgangstyp (id) ON DELETE CASCADE
);
