-- Fertigungsunterlagen am Artikelstamm (DXF, STEP, DWG, STP, DXB, PDF)
-- und projektspezifische Anhänge an Bestellpositionen (Issue #510)
CREATE TABLE IF NOT EXISTS artikel_fertigungsunterlage (
    id                    BIGSERIAL    PRIMARY KEY,
    artikel_id            BIGINT       NOT NULL REFERENCES artikel(id) ON DELETE CASCADE,
    original_dateiname    VARCHAR(500) NOT NULL,
    gespeicherter_dateiname VARCHAR(500) NOT NULL,
    erstellt_am           TIMESTAMP    NOT NULL,
    dateigroesse_bytes    BIGINT
);

CREATE TABLE IF NOT EXISTS bestell_position_anhang (
    id                    BIGSERIAL    PRIMARY KEY,
    position_id           BIGINT       NOT NULL REFERENCES bestell_position(id) ON DELETE CASCADE,
    original_dateiname    VARCHAR(500) NOT NULL,
    gespeicherter_dateiname VARCHAR(500) NOT NULL,
    erstellt_am           TIMESTAMP    NOT NULL,
    dateigroesse_bytes    BIGINT,
    -- NULL = direkt hochgeladen (projektspezifisch), NOT NULL = aus Artikelstamm übernommen
    quell_fertigungsunterlage_id BIGINT REFERENCES artikel_fertigungsunterlage(id) ON DELETE SET NULL
);
