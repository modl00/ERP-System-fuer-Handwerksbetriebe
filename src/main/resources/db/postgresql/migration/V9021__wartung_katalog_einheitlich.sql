-- Portiert von db/migration/V921__wartung_katalog_einheitlich.sql (MySQL) nach PostgreSQL.
-- Wartung: einheitlicher Katalog der Datenfelder (Konzept docs/WARTUNG_DATENUEBERNAHME_KONZEPT.md). Idempotent, nur fuer Katalog-Vorlagen.
--
-- Ein Feld, eine Bedeutung: Angaben mit wenigen festen Werten werden Auswahllisten statt Freitext (Hersteller, Anschlag, Werkstoff, Tuerart, Feuerwiderstand,
-- Glasart, Melder- und Feststellanlagen-Typ), Jahre werden Jahreszahlen, "RS" wird ein eigenes Ja/Nein-Feld. Jede Liste beginnt mit "keine Angabe"
-- (wird im Protokoll nicht ausgedruckt) und endet mit "Sonstiges". Geaendert wird nur, was noch im Auslieferungszustand ist (Art Text, keine Optionen):
-- Wer ein Feld selbst angepasst hat, behaelt seine Fassung. Bestehende Protokolle bleiben unberuehrt (sie tragen eine Kopie der Felder).

UPDATE wartung_globales_datenfeld SET feldtyp = 'AUSWAHL', optionen = 'keine Angabe
Schüco
Hörmann
Hoba
Geze
Dorma
Brandis
Wires Bauelemente
Riexinger
Sonstiges'
WHERE label = 'Hersteller' AND feldtyp = 'TEXT' AND optionen IS NULL;

INSERT INTO wartung_globales_datenfeld (label, feldtyp, optionen, sortierung)
SELECT 'Baujahr', 'JAHR', NULL, 4
FROM (SELECT 1 AS dummy) x
WHERE NOT EXISTS (SELECT 1 FROM wartung_globales_datenfeld g WHERE g.label = 'Baujahr');

INSERT INTO wartung_globales_datenfeld (label, feldtyp, optionen, sortierung)
SELECT 'Anschlag', 'AUSWAHL', 'keine Angabe
DIN links
DIN rechts
Schiebetor
Sonstiges', 5
FROM (SELECT 1 AS dummy) x
WHERE NOT EXISTS (SELECT 1 FROM wartung_globales_datenfeld g WHERE g.label = 'Anschlag');

INSERT INTO wartung_globales_datenfeld (label, feldtyp, optionen, sortierung)
SELECT 'Werkstoff', 'AUSWAHL', 'keine Angabe
Stahl
Aluminium
Holz
Holz / Glas
Sonstiges', 6
FROM (SELECT 1 AS dummy) x
WHERE NOT EXISTS (SELECT 1 FROM wartung_globales_datenfeld g WHERE g.label = 'Werkstoff');

INSERT INTO wartung_globales_datenfeld (label, feldtyp, optionen, sortierung)
SELECT 'Tür- / Torart', 'AUSWAHL', 'keine Angabe
Drehtür
Schiebetür
Schiebetor
Brandschutztor
Sonstiges', 7
FROM (SELECT 1 AS dummy) x
WHERE NOT EXISTS (SELECT 1 FROM wartung_globales_datenfeld g WHERE g.label = 'Tür- / Torart');

INSERT INTO wartung_globales_datenfeld (label, feldtyp, optionen, sortierung)
SELECT 'Typ / Modell', 'TEXT', NULL, 8
FROM (SELECT 1 AS dummy) x
WHERE NOT EXISTS (SELECT 1 FROM wartung_globales_datenfeld g WHERE g.label = 'Typ / Modell');

UPDATE wartung_modul_datenfeld SET label = 'Feuerwiderstand', feldtyp = 'AUSWAHL', optionen = 'keine Angabe
T30
T60
T90
F30
F90
Sonstiges'
WHERE label = 'Feuerwiderstand / Rauchschutzklasse' AND feldtyp = 'TEXT' AND optionen IS NULL AND modul_id IN (SELECT id FROM wartung_modul WHERE modul_key = 'm_bst');

INSERT INTO wartung_modul_datenfeld (modul_id, label, feldtyp, optionen, sortierung)
SELECT mo.id, 'Rauchschutz (RS)', 'JA_NEIN', NULL, 3
FROM wartung_modul mo
WHERE mo.modul_key = 'm_bst' AND NOT EXISTS (SELECT 1 FROM wartung_modul_datenfeld d WHERE d.modul_id = mo.id AND d.label = 'Rauchschutz (RS)');

UPDATE wartung_modul_datenfeld SET label = 'Feststellanlage Hersteller / Typ', feldtyp = 'AUSWAHL', optionen = 'keine Angabe
GEZE ISM
GEZE RS5
Dorma TS 93
Dorma RMZ
D+H
FSZ Basis
Sonstiges'
WHERE label = 'Feststellvorrichtung Hersteller/Typ' AND feldtyp = 'TEXT' AND optionen IS NULL AND modul_id IN (SELECT id FROM wartung_modul WHERE modul_key = 'm_fsa');

UPDATE wartung_modul_datenfeld SET label = 'Rauchmelder Typ', feldtyp = 'AUSWAHL', optionen = 'keine Angabe
GEZE GC151
GEZE GC171
GEZE GC172
ORS 142
Dorma RM-N
Deckenmelder
Sonstiges'
WHERE label = 'Rauchmelder Hersteller/Typ' AND feldtyp = 'TEXT' AND optionen IS NULL AND modul_id IN (SELECT id FROM wartung_modul WHERE modul_key = 'm_fsa');

UPDATE wartung_modul_datenfeld SET label = 'Rauchmelder Einbaujahr', feldtyp = 'JAHR', optionen = NULL
WHERE label = 'Rauchmelder Einbaudatum' AND feldtyp = 'TEXT' AND optionen IS NULL AND modul_id IN (SELECT id FROM wartung_modul WHERE modul_key = 'm_fsa');

DELETE FROM wartung_modul_datenfeld
WHERE label = 'Hersteller / Fabrikat' AND feldtyp = 'TEXT' AND optionen IS NULL AND modul_id IN (SELECT id FROM wartung_modul WHERE modul_key = 'm_fsa');

UPDATE wartung_modul_datenfeld SET label = 'Glasart', feldtyp = 'AUSWAHL', optionen = 'keine Angabe
Einscheibensicherheitsglas (ESG)
Pilkington Pyrostop
Mehrfachverglasung
Sonstiges'
WHERE label = 'Verglasung / Glasart' AND feldtyp = 'TEXT' AND optionen IS NULL AND modul_id IN (SELECT id FROM wartung_modul WHERE modul_key = 'm_glas');
