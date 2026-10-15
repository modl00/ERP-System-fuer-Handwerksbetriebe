-- Portiert von db/migration/V906__firma_briefpapier.sql (MySQL) nach PostgreSQL.
-- Briefpapier der Firma: eine Bild- oder PDF-Datei, die in jedem Formular (Angebot, Rechnung, Mahnung ...) als Hintergrund der ganzen A4-Seite liegt. Idempotent.
--
-- briefpapier_dateiname: Basename der gespeicherten Datei (briefpapier.png|jpg|pdf), die Datei liegt unter uploads/firma/briefpapier.
-- briefpapier_enthaelt_kopf: Enthaelt das Briefpapier schon Briefkopf und Fusszeile? Dann lassen die Formulare ihre eigenen Firmenangaben
--   (Name, Adresse, Bank ..., Logo) weg, damit nichts doppelt steht.

ALTER TABLE firmeninformation ADD COLUMN IF NOT EXISTS briefpapier_dateiname VARCHAR(255) NULL;

ALTER TABLE firmeninformation ADD COLUMN IF NOT EXISTS briefpapier_enthaelt_kopf BOOLEAN NOT NULL DEFAULT FALSE;
