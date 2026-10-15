-- Portiert von db/migration/V901__wartung_vorlagen.sql (MySQL) nach PostgreSQL.
-- Wartung: Startvorlagen (Modulkatalog) fuer Brandschutztueren, Tore und Zusatzmodule.
--
-- Uebernommen aus der HandwerkPro-Wartungs-App (Endzustand nach deren Migrationen 016, 019,
-- 031-033 und 038). Es sind reine Vorlagen, keine Kundendaten.
--
-- Bewusste Abweichungen vom Original:
--  * "Anzahl Tuerfluegel" entfaellt als Datenfeld: Die Bauart (1-fluegelig, 2-fluegelig,
--    Schiebetuer) steht jetzt als eigene Auswahl am Modul.
--  * Standort, Hersteller und Zulassungsnummer stehen nur noch als globale Felder, nicht mehr
--    zusaetzlich in jedem Modul.
--  * Der Pruefpunkt "Maengel festgestellt" entfaellt: Die Maengelliste ergibt sich aus den
--    Punkten mit Ergebnis "nicht in Ordnung".
--  * "Naechste Wartung (Notiz)" entfaellt: Die Faelligkeit steht im Wartungsplan der Tuer.
--  * Tippfehler "Feuerwiederstand" korrigiert, Emoji in Bezeichnungen entfernt.
--
-- Idempotent: Jede Zeile wird nur angelegt, wenn es sie noch nicht gibt. Icons sind Namen aus
-- lucide-react.

-- ── Brandschutztür (m_bst) ──────────────────────────────────────────────
INSERT INTO wartung_modul (modul_key, name, beschreibung, icon, kategorie, system_modul, aktiv, sortierung, standard_intervall_monate)
SELECT 'm_bst', 'Brandschutztür', 'Klassifizierter Feuerschutzabschluss (T30/T60/T90) nach DIN 4102-5 bzw. DIN EN 16034 mit DIBt-Zulassung oder CE-Kennzeichnung.', 'DoorClosed', 'BASIS', TRUE, TRUE, 1, 12
 WHERE NOT EXISTS (SELECT 1 FROM wartung_modul WHERE modul_key = 'm_bst');

-- ── Rauchschutztür (m_rs) ──────────────────────────────────────────────
INSERT INTO wartung_modul (modul_key, name, beschreibung, icon, kategorie, system_modul, aktiv, sortierung, standard_intervall_monate)
SELECT 'm_rs', 'Rauchschutztür', 'Rauchschutztür nach DIN 18095 mit umlaufender Dichtung und Selbstschließvorrichtung.', 'Wind', 'BASIS', TRUE, TRUE, 3, 12
 WHERE NOT EXISTS (SELECT 1 FROM wartung_modul WHERE modul_key = 'm_rs');

-- ── Brandschutztor (m_tor) ──────────────────────────────────────────────
INSERT INTO wartung_modul (modul_key, name, beschreibung, icon, kategorie, system_modul, aktiv, sortierung, standard_intervall_monate)
SELECT 'm_tor', 'Brandschutztor', 'Feuerschutz-Schiebe-, Roll- oder Sektionaltor mit DIBt-Zulassung.', 'Warehouse', 'BASIS', TRUE, TRUE, 2, 12
 WHERE NOT EXISTS (SELECT 1 FROM wartung_modul WHERE modul_key = 'm_tor');

-- ── Dicht-/selbstschließende Tür (m_dicht) ────────────────────────────────
INSERT INTO wartung_modul (modul_key, name, beschreibung, icon, kategorie, system_modul, aktiv, sortierung, standard_intervall_monate)
SELECT 'm_dicht', 'Dicht-/selbstschließende Tür', 'Nicht klassifizierte Tür mit mind. dreiseitig umlaufender Dichtung und ggf. Türschließer. Anforderung ergibt sich aus dem objektbezogenen Brandschutzkonzept, nicht aus einer Produktnorm.', 'Lock', 'BASIS', TRUE, TRUE, 10, 12
 WHERE NOT EXISTS (SELECT 1 FROM wartung_modul WHERE modul_key = 'm_dicht');

-- ── Feststellanlage (m_fsa) ─────────────────────────────────────────────
INSERT INTO wartung_modul (modul_key, name, beschreibung, icon, kategorie, system_modul, aktiv, sortierung, standard_intervall_monate)
SELECT 'm_fsa', 'Feststellanlage', 'Für Türen/Tore mit automatischer Offenhaltung und selbsttätiger Auslösung im Brandfall (Haftmagnet oder Freilaufschließer, z.B. GEZE ISM).', 'Radio', 'ZUSATZ', TRUE, TRUE, 4, 12
 WHERE NOT EXISTS (SELECT 1 FROM wartung_modul WHERE modul_key = 'm_fsa');

-- ── Glasfüllung (m_glas) ─────────────────────────────────────────────────
INSERT INTO wartung_modul (modul_key, name, beschreibung, icon, kategorie, system_modul, aktiv, sortierung, standard_intervall_monate)
SELECT 'm_glas', 'Glasfüllung', NULL, 'AppWindow', 'ZUSATZ', TRUE, TRUE, 5, 12
 WHERE NOT EXISTS (SELECT 1 FROM wartung_modul WHERE modul_key = 'm_glas');

-- ── Panikfunktion (m_panik) ───────────────────────────────────────────────
INSERT INTO wartung_modul (modul_key, name, beschreibung, icon, kategorie, system_modul, aktiv, sortierung, standard_intervall_monate)
SELECT 'm_panik', 'Panikfunktion', NULL, 'LifeBuoy', 'ZUSATZ', TRUE, TRUE, 6, 12
 WHERE NOT EXISTS (SELECT 1 FROM wartung_modul WHERE modul_key = 'm_panik');

-- ── Bodendichtung (m_boden) ───────────────────────────────────────────────
INSERT INTO wartung_modul (modul_key, name, beschreibung, icon, kategorie, system_modul, aktiv, sortierung, standard_intervall_monate)
SELECT 'm_boden', 'Bodendichtung', NULL, 'ArrowDownToLine', 'ZUSATZ', TRUE, TRUE, 7, 12
 WHERE NOT EXISTS (SELECT 1 FROM wartung_modul WHERE modul_key = 'm_boden');

-- ── Motorantrieb (m_antrieb) ────────────────────────────────────────────────
INSERT INTO wartung_modul (modul_key, name, beschreibung, icon, kategorie, system_modul, aktiv, sortierung, standard_intervall_monate)
SELECT 'm_antrieb', 'Motorantrieb', NULL, 'Cog', 'ZUSATZ', TRUE, TRUE, 8, 12
 WHERE NOT EXISTS (SELECT 1 FROM wartung_modul WHERE modul_key = 'm_antrieb');

-- ── Elektrische Verriegelung (m_eltvtr) ────────────────────────────────────
INSERT INTO wartung_modul (modul_key, name, beschreibung, icon, kategorie, system_modul, aktiv, sortierung, standard_intervall_monate)
SELECT 'm_eltvtr', 'Elektrische Verriegelung', 'Elektrisches Verriegelungssystem für Türen in Rettungswegen mit Nottaste, nach EltVTR bzw. DIN EN 13637.', 'OctagonAlert', 'ZUSATZ', TRUE, TRUE, 11, 12
 WHERE NOT EXISTS (SELECT 1 FROM wartung_modul WHERE modul_key = 'm_eltvtr');

-- ── Andockbarkeit der Zusatzmodule ─────────────────────────────────────────
INSERT INTO wartung_modul_andockbar (modul_id, basis_modul_id)
SELECT z.id, b.id FROM wartung_modul z JOIN wartung_modul b ON b.modul_key IN ('m_bst', 'm_rs', 'm_tor')
WHERE z.modul_key = 'm_fsa'
ON CONFLICT DO NOTHING;

INSERT INTO wartung_modul_andockbar (modul_id, basis_modul_id)
SELECT z.id, b.id FROM wartung_modul z JOIN wartung_modul b ON b.modul_key IN ('m_bst', 'm_rs')
WHERE z.modul_key = 'm_glas'
ON CONFLICT DO NOTHING;

INSERT INTO wartung_modul_andockbar (modul_id, basis_modul_id)
SELECT z.id, b.id FROM wartung_modul z JOIN wartung_modul b ON b.modul_key IN ('m_bst', 'm_rs')
WHERE z.modul_key = 'm_panik'
ON CONFLICT DO NOTHING;

INSERT INTO wartung_modul_andockbar (modul_id, basis_modul_id)
SELECT z.id, b.id FROM wartung_modul z JOIN wartung_modul b ON b.modul_key IN ('m_rs')
WHERE z.modul_key = 'm_boden'
ON CONFLICT DO NOTHING;

INSERT INTO wartung_modul_andockbar (modul_id, basis_modul_id)
SELECT z.id, b.id FROM wartung_modul z JOIN wartung_modul b ON b.modul_key IN ('m_tor')
WHERE z.modul_key = 'm_antrieb'
ON CONFLICT DO NOTHING;

INSERT INTO wartung_modul_andockbar (modul_id, basis_modul_id)
SELECT z.id, b.id FROM wartung_modul z JOIN wartung_modul b ON b.modul_key IN ('m_bst', 'm_rs', 'm_dicht')
WHERE z.modul_key = 'm_eltvtr'
ON CONFLICT DO NOTHING;

-- ── Bauarten ────────────────────────────────────────────────────────────────
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'einflg', '1-flügelig', 0 FROM wartung_modul WHERE modul_key = 'm_bst'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'zweiflg', '2-flügelig', 1 FROM wartung_modul WHERE modul_key = 'm_bst'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'schiebe', 'Schiebetür', 2 FROM wartung_modul WHERE modul_key = 'm_bst'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'einflg', '1-flügelig', 0 FROM wartung_modul WHERE modul_key = 'm_rs'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'zweiflg', '2-flügelig', 1 FROM wartung_modul WHERE modul_key = 'm_rs'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'schiebe', 'Schiebetür', 2 FROM wartung_modul WHERE modul_key = 'm_rs'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'einflg', '1-flügelig', 0 FROM wartung_modul WHERE modul_key = 'm_tor'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'zweiflg', '2-flügelig', 1 FROM wartung_modul WHERE modul_key = 'm_tor'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'schiebe', 'Schiebetür', 2 FROM wartung_modul WHERE modul_key = 'm_tor'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'einflg', '1-flügelig', 0 FROM wartung_modul WHERE modul_key = 'm_dicht'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'zweiflg', '2-flügelig', 1 FROM wartung_modul WHERE modul_key = 'm_dicht'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'schiebe', 'Schiebetür', 2 FROM wartung_modul WHERE modul_key = 'm_dicht'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'magnet', 'Haftmagnet', 0 FROM wartung_modul WHERE modul_key = 'm_fsa'
ON CONFLICT DO NOTHING;
INSERT INTO wartung_modul_untertyp (modul_id, schluessel, label, sortierung)
SELECT id, 'freilauf', 'Freilauf (z.B. GEZE ISM)', 1 FROM wartung_modul WHERE modul_key = 'm_fsa'
ON CONFLICT DO NOTHING;

-- ── Abschnitte ──────────────────────────────────────────────────────────────
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte Brandschutztür', 0 FROM wartung_modul mo
WHERE mo.modul_key = 'm_bst' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Brandschutztür');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte 2-flügeliger Abschluss', 99 FROM wartung_modul mo
WHERE mo.modul_key = 'm_bst' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte 2-flügeliger Abschluss');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte Rauchschutztür', 0 FROM wartung_modul mo
WHERE mo.modul_key = 'm_rs' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Rauchschutztür');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte 2-flügeliger Abschluss', 99 FROM wartung_modul mo
WHERE mo.modul_key = 'm_rs' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte 2-flügeliger Abschluss');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte Brandschutztor', 0 FROM wartung_modul mo
WHERE mo.modul_key = 'm_tor' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Brandschutztor');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte 2-flügeliger Abschluss', 99 FROM wartung_modul mo
WHERE mo.modul_key = 'm_tor' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte 2-flügeliger Abschluss');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte Dicht-/selbstschließende Tür', 0 FROM wartung_modul mo
WHERE mo.modul_key = 'm_dicht' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Dicht-/selbstschließende Tür');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte 2-flügeliger Abschluss', 99 FROM wartung_modul mo
WHERE mo.modul_key = 'm_dicht' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte 2-flügeliger Abschluss');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte Feststellanlage', 0 FROM wartung_modul mo
WHERE mo.modul_key = 'm_fsa' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Feststellanlage');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte Glasfüllung', 0 FROM wartung_modul mo
WHERE mo.modul_key = 'm_glas' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Glasfüllung');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte Panikfunktion', 0 FROM wartung_modul mo
WHERE mo.modul_key = 'm_panik' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Panikfunktion');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte Bodendichtung', 0 FROM wartung_modul mo
WHERE mo.modul_key = 'm_boden' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Bodendichtung');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte Motorantrieb', 0 FROM wartung_modul mo
WHERE mo.modul_key = 'm_antrieb' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Motorantrieb');
INSERT INTO wartung_modul_abschnitt (modul_id, bereich, label, sortierung)
SELECT mo.id, 'PRUEFPUNKTE', 'Prüfpunkte Elektrische Verriegelung', 0 FROM wartung_modul mo
WHERE mo.modul_key = 'm_eltvtr' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_abschnitt ab WHERE ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Elektrische Verriegelung');

-- ── Pruefpunkte ─────────────────────────────────────────────────────────────
INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Brandschutztür'
CROSS JOIN (
    SELECT 'Zulassungsschild / Kennzeichnung lesbar' AS label, 1 AS sort, NULL AS nur
    UNION ALL SELECT 'Kennzeichnung des Schließbereichs' AS label, 2 AS sort, NULL AS nur
    UNION ALL SELECT 'Zarge auf Beschädigungen (Sichtkontrolle)' AS label, 3 AS sort, NULL AS nur
    UNION ALL SELECT 'Tür-/Torblatt auf Beschädigungen (Sichtkontrolle)' AS label, 4 AS sort, NULL AS nur
    UNION ALL SELECT 'Bänder auf festen Sitz prüfen' AS label, 5 AS sort, NULL AS nur
    UNION ALL SELECT 'Türschloß / Drücker auf Funktion prüfen' AS label, 6 AS sort, NULL AS nur
    UNION ALL SELECT 'Zylinder auf festen Sitz prüfen' AS label, 7 AS sort, NULL AS nur
    UNION ALL SELECT 'Funktionsprüfung Falle / Schließblech' AS label, 8 AS sort, NULL AS nur
    UNION ALL SELECT 'Dichtungen / Quellband auf Beschädigungen' AS label, 9 AS sort, NULL AS nur
    UNION ALL SELECT 'Spaltmaße prüfen' AS label, 10 AS sort, NULL AS nur
    UNION ALL SELECT 'Schließfunktionskontrolle durchführen' AS label, 11 AS sort, NULL AS nur
    UNION ALL SELECT 'Schließfolgeregler prüfen' AS label, 12 AS sort, NULL AS nur
    UNION ALL SELECT 'Türschließer gemäß Zulassung' AS label, 13 AS sort, NULL AS nur
    UNION ALL SELECT 'Türschließer auf festen Sitz und Funktion' AS label, 14 AS sort, NULL AS nur
    UNION ALL SELECT 'Schließgeschwindigkeit prüfen' AS label, 15 AS sort, NULL AS nur
) x
WHERE mo.modul_key = 'm_bst' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte 2-flügeliger Abschluss'
CROSS JOIN (
    SELECT 'Schließfolgesteuerung prüfen (Gang- vor Standflügel)' AS label, 1 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Schließfolgeregler auf Funktion und Einstellung prüfen' AS label, 2 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Beide Flügel schließen vollständig' AS label, 3 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Koordinator auf Funktion prüfen' AS label, 4 AS sort, 'zweiflg' AS nur
) x
WHERE mo.modul_key = 'm_bst' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Rauchschutztür'
CROSS JOIN (
    SELECT 'Zulassungsschild / Kennzeichnung lesbar' AS label, 1 AS sort, NULL AS nur
    UNION ALL SELECT 'Kennzeichnung des Schließbereichs' AS label, 2 AS sort, NULL AS nur
    UNION ALL SELECT 'Türblatt auf Beschädigungen (Sichtkontrolle)' AS label, 3 AS sort, NULL AS nur
    UNION ALL SELECT 'Zarge auf Beschädigungen (Sichtkontrolle)' AS label, 4 AS sort, NULL AS nur
    UNION ALL SELECT 'Dichtungen / Bürstendichtung auf Beschädigungen' AS label, 5 AS sort, NULL AS nur
    UNION ALL SELECT 'Bänder auf festen Sitz und Funktion prüfen' AS label, 6 AS sort, NULL AS nur
    UNION ALL SELECT 'Türschloß / Drücker auf Funktion prüfen' AS label, 7 AS sort, NULL AS nur
    UNION ALL SELECT 'Spaltmaße prüfen (Rauchschutzanforderung)' AS label, 8 AS sort, NULL AS nur
    UNION ALL SELECT 'Schließfunktionskontrolle durchführen' AS label, 9 AS sort, NULL AS nur
    UNION ALL SELECT 'Türschließer auf Funktion prüfen' AS label, 10 AS sort, NULL AS nur
) x
WHERE mo.modul_key = 'm_rs' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte 2-flügeliger Abschluss'
CROSS JOIN (
    SELECT 'Schließfolgesteuerung prüfen (Gang- vor Standflügel)' AS label, 1 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Schließfolgeregler auf Funktion und Einstellung prüfen' AS label, 2 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Beide Flügel schließen vollständig' AS label, 3 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Koordinator auf Funktion prüfen' AS label, 4 AS sort, 'zweiflg' AS nur
) x
WHERE mo.modul_key = 'm_rs' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Brandschutztor'
CROSS JOIN (
    SELECT 'Zulassungsschild / Kennzeichnung lesbar' AS label, 1 AS sort, NULL AS nur
    UNION ALL SELECT 'Tor auf sichtbare Beschädigungen prüfen' AS label, 2 AS sort, NULL AS nur
    UNION ALL SELECT 'Führungsschienen auf Beschädigungen prüfen' AS label, 3 AS sort, NULL AS nur
    UNION ALL SELECT 'Laufrollen / Scharniere auf festen Sitz prüfen' AS label, 4 AS sort, NULL AS nur
    UNION ALL SELECT 'Dichtungen auf Beschädigungen prüfen' AS label, 5 AS sort, NULL AS nur
    UNION ALL SELECT 'Schließfunktion prüfen' AS label, 6 AS sort, NULL AS nur
    UNION ALL SELECT 'Selbstschließung funktionsfähig' AS label, 7 AS sort, NULL AS nur
    UNION ALL SELECT 'Notentriegelung prüfen' AS label, 8 AS sort, NULL AS nur
    UNION ALL SELECT 'Schließgewichte / Federn prüfen' AS label, 9 AS sort, NULL AS nur
) x
WHERE mo.modul_key = 'm_tor' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte 2-flügeliger Abschluss'
CROSS JOIN (
    SELECT 'Schließfolgesteuerung prüfen (Gang- vor Standflügel)' AS label, 1 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Schließfolgeregler auf Funktion und Einstellung prüfen' AS label, 2 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Beide Flügel schließen vollständig' AS label, 3 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Koordinator auf Funktion prüfen' AS label, 4 AS sort, 'zweiflg' AS nur
) x
WHERE mo.modul_key = 'm_tor' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Dicht-/selbstschließende Tür'
CROSS JOIN (
    SELECT 'Mindestens dreiseitig umlaufende Dichtung vorhanden und intakt' AS label, 1 AS sort, NULL AS nur
    UNION ALL SELECT 'Türblatt, Zarge, Beschläge: Zustand und Gängigkeit' AS label, 2 AS sort, NULL AS nur
    UNION ALL SELECT 'Türschließer vorhanden (falls selbstschließend gefordert)' AS label, 3 AS sort, NULL AS nur
    UNION ALL SELECT 'Tür schließt vollständig und selbsttätig (falls gefordert)' AS label, 4 AS sort, NULL AS nur
    UNION ALL SELECT 'Keine unzulässigen Feststellvorrichtungen (Keile o.ä.)' AS label, 5 AS sort, NULL AS nur
) x
WHERE mo.modul_key = 'm_dicht' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte 2-flügeliger Abschluss'
CROSS JOIN (
    SELECT 'Schließfolgesteuerung prüfen (Gang- vor Standflügel)' AS label, 1 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Schließfolgeregler auf Funktion und Einstellung prüfen' AS label, 2 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Beide Flügel schließen vollständig' AS label, 3 AS sort, 'zweiflg' AS nur
    UNION ALL SELECT 'Koordinator auf Funktion prüfen' AS label, 4 AS sort, 'zweiflg' AS nur
) x
WHERE mo.modul_key = 'm_dicht' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Feststellanlage'
CROSS JOIN (
    SELECT 'Übereinstimmung Anlage mit Zulassung/Kennzeichnung' AS label, 1 AS sort, NULL AS nur
    UNION ALL SELECT 'Alle Befestigungsschrauben fester Sitz' AS label, 2 AS sort, NULL AS nur
    UNION ALL SELECT 'Anlage, Leitungen, Anschlüsse unbeschädigt' AS label, 3 AS sort, NULL AS nur
    UNION ALL SELECT 'Umgebung frei, keine Verschmutzung/Behinderung' AS label, 4 AS sort, NULL AS nur
    UNION ALL SELECT 'Rauchmelder sauber und zulassungskonform positioniert' AS label, 5 AS sort, NULL AS nur
    UNION ALL SELECT 'Haftmagnet hält Tür/Tor zuverlässig in Offenposition' AS label, 6 AS sort, 'magnet' AS nur
    UNION ALL SELECT 'Magnet-Befestigung und Gegenplatte (Ankerplatte) in Ordnung' AS label, 7 AS sort, 'magnet' AS nur
    UNION ALL SELECT 'Freilauffunktion aktiviert sich nach erstmaligem Öffnen (ca. 90°)' AS label, 6 AS sort, 'freilauf' AS nur
    UNION ALL SELECT 'Tür in beide Richtungen leichtgängig frei beweglich (Freilauf)' AS label, 7 AS sort, 'freilauf' AS nur
    UNION ALL SELECT 'Elektrische Vorspannung des Schließers vorhanden' AS label, 8 AS sort, 'freilauf' AS nur
    UNION ALL SELECT 'Zuverlässiges, vollständiges Schließen AUS dem Freilauf bei Auslösung' AS label, 9 AS sort, 'freilauf' AS nur
    UNION ALL SELECT 'Schließfunktion: Tür/Tor schließt aus jedem Öffnungswinkel selbsttätig und vollständig' AS label, 10 AS sort, NULL AS nur
    UNION ALL SELECT 'Schließfolgeregler funktioniert (bei mehrflügelig)' AS label, 11 AS sort, NULL AS nur
    UNION ALL SELECT 'Feststellfunktion hält Tür/Tor sicher in Offenlage' AS label, 12 AS sort, NULL AS nur
    UNION ALL SELECT 'Handauslösetaster frei zugänglich, Beschriftung lesbar, Funktion geprüft' AS label, 13 AS sort, NULL AS nur
    UNION ALL SELECT 'Auslösung über Rauchmelder (Prüfaerosol/Simulation) → Feststellung wird aufgehoben' AS label, 14 AS sort, NULL AS nur
    UNION ALL SELECT 'Auslösung über Handauslösetaster → Feststellung wird aufgehoben' AS label, 15 AS sort, NULL AS nur
    UNION ALL SELECT 'Auslösung bei Energieausfall bzw. Umschalten auf Notstromversorgung' AS label, 16 AS sort, NULL AS nur
    UNION ALL SELECT 'Rückstellung/Wiederscharfschaltung funktioniert (Statusanzeige grün)' AS label, 17 AS sort, NULL AS nur
    UNION ALL SELECT 'Meldertausch-Frist eingehalten (5 bzw. 8 Jahre je nach Bauart, siehe Datenfeld)' AS label, 18 AS sort, NULL AS nur
) x
WHERE mo.modul_key = 'm_fsa' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Glasfüllung'
CROSS JOIN (
    SELECT 'Verglasung auf Beschädigung prüfen (Risse, Sprünge)' AS label, 1 AS sort, NULL AS nur
    UNION ALL SELECT 'Kennzeichnung der Verglasung lesbar (Brandschutzglas)' AS label, 2 AS sort, NULL AS nur
    UNION ALL SELECT 'Glasleisten / Verglasungsdichtungen auf Sitz prüfen' AS label, 3 AS sort, NULL AS nur
    UNION ALL SELECT 'Verglasungsrahmen auf Beschädigungen prüfen' AS label, 4 AS sort, NULL AS nur
) x
WHERE mo.modul_key = 'm_glas' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Panikfunktion'
CROSS JOIN (
    SELECT 'Panikbeschlag vorhanden und gekennzeichnet' AS label, 1 AS sort, NULL AS nur
    UNION ALL SELECT 'Panikstange auf Funktion prüfen' AS label, 2 AS sort, NULL AS nur
    UNION ALL SELECT 'Öffnung durch Druck auf Panikstange (Vollpanikfunktion)' AS label, 3 AS sort, NULL AS nur
    UNION ALL SELECT 'Mitnehmerklappe auf Funktion prüfen' AS label, 4 AS sort, NULL AS nur
    UNION ALL SELECT 'Schließfolge bei 2-flügeliger Tür prüfen' AS label, 5 AS sort, NULL AS nur
) x
WHERE mo.modul_key = 'm_panik' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Bodendichtung'
CROSS JOIN (
    SELECT 'Bodendichtung auf Beschädigungen prüfen' AS label, 1 AS sort, NULL AS nur
    UNION ALL SELECT 'Bodendichtung schließt vollständig (Absenkdichtung)' AS label, 2 AS sort, NULL AS nur
    UNION ALL SELECT 'Auslösemechanismus Bodendichtung prüfen' AS label, 3 AS sort, NULL AS nur
    UNION ALL SELECT 'Dichtfläche / Bodenschwelle auf Beschädigungen prüfen' AS label, 4 AS sort, NULL AS nur
) x
WHERE mo.modul_key = 'm_boden' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Motorantrieb'
CROSS JOIN (
    SELECT 'Antrieb auf sichtbare Beschädigungen prüfen' AS label, 1 AS sort, NULL AS nur
    UNION ALL SELECT 'Antriebssteuerung auf Funktion prüfen' AS label, 2 AS sort, NULL AS nur
    UNION ALL SELECT 'Sicherheitssensoren / Lichtschranke prüfen' AS label, 3 AS sort, NULL AS nur
    UNION ALL SELECT 'NOT-STOP Funktion prüfen' AS label, 4 AS sort, NULL AS nur
    UNION ALL SELECT 'Notentriegelung / Handbetrieb prüfen' AS label, 5 AS sort, NULL AS nur
    UNION ALL SELECT 'Elektrische Anschlüsse und Leitungen prüfen' AS label, 6 AS sort, NULL AS nur
) x
WHERE mo.modul_key = 'm_antrieb' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

INSERT INTO wartung_modul_pruefpunkt (modul_id, abschnitt_id, label, nur_bei_untertyp, sortierung)
SELECT mo.id, ab.id, x.label, x.nur, x.sort
FROM wartung_modul mo
JOIN wartung_modul_abschnitt ab ON ab.modul_id = mo.id AND ab.bereich = 'PRUEFPUNKTE' AND ab.label = 'Prüfpunkte Elektrische Verriegelung'
CROSS JOIN (
    SELECT 'Nottaste: rot, pilzförmig, beleuchtet, Beschriftung lesbar' AS label, 1 AS sort, NULL AS nur
    UNION ALL SELECT 'Nottaste löst Verriegelung sofort und vollständig aus' AS label, 2 AS sort, NULL AS nur
    UNION ALL SELECT 'Ruhestromprinzip: Verriegelung öffnet bei Stromausfall unverzüglich' AS label, 3 AS sort, NULL AS nur
    UNION ALL SELECT 'Signalgeber/LED zeigt Status korrekt an (rot=verriegelt, grün=frei)' AS label, 4 AS sort, NULL AS nur
    UNION ALL SELECT 'Wiederverriegelung nur von Hand an der Tür möglich' AS label, 5 AS sort, NULL AS nur
    UNION ALL SELECT 'Notstromversorgung/Akku funktionsfähig (Mindest-Überbrückung 15 Min.)' AS label, 6 AS sort, NULL AS nur
    UNION ALL SELECT 'Tür lässt sich in voller Breite mit einem Griff öffnen (auch unter Vorlast)' AS label, 7 AS sort, NULL AS nur
    UNION ALL SELECT 'Verkabelung und Verriegelungselement unbeschädigt' AS label, 8 AS sort, NULL AS nur
) x
WHERE mo.modul_key = 'm_eltvtr' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_pruefpunkt p
    WHERE p.modul_id = mo.id AND p.label = x.label AND (p.nur_bei_untertyp = x.nur OR (p.nur_bei_untertyp IS NULL AND x.nur IS NULL)));

-- ── Datenfelder der Module ──────────────────────────────────────────────────
-- Die Datenfelder werden nur beim ersten Lauf angelegt: spaetere Migrationen (V9021) benennen Felder um, ein zweiter Lauf wuerde sie sonst neu anlegen.
DO $$
BEGIN
IF NOT EXISTS (SELECT 1 FROM wartung_modul_datenfeld) AND NOT EXISTS (SELECT 1 FROM wartung_globales_datenfeld) THEN
INSERT INTO wartung_modul_datenfeld (modul_id, label, feldtyp, sortierung)
SELECT mo.id, x.label, x.typ, x.sort
FROM wartung_modul mo
CROSS JOIN (
    SELECT 'Feuerwiderstand / Rauchschutzklasse' AS label, 'TEXT' AS typ, 1 AS sort
    UNION ALL SELECT 'Gebrauchs- u. Wartungsanweisung liegt vor' AS label, 'JA_NEIN' AS typ, 2 AS sort
) x
WHERE mo.modul_key = 'm_bst' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_datenfeld d WHERE d.modul_id = mo.id AND d.label = x.label);

INSERT INTO wartung_modul_datenfeld (modul_id, label, feldtyp, sortierung)
SELECT mo.id, x.label, x.typ, x.sort
FROM wartung_modul mo
CROSS JOIN (
    SELECT 'Rauchschutzklasse' AS label, 'TEXT' AS typ, 1 AS sort
) x
WHERE mo.modul_key = 'm_rs' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_datenfeld d WHERE d.modul_id = mo.id AND d.label = x.label);

INSERT INTO wartung_modul_datenfeld (modul_id, label, feldtyp, sortierung)
SELECT mo.id, x.label, x.typ, x.sort
FROM wartung_modul mo
CROSS JOIN (
    SELECT 'Tortyp / Bauart' AS label, 'TEXT' AS typ, 1 AS sort
    UNION ALL SELECT 'Antrieb vorhanden' AS label, 'JA_NEIN' AS typ, 2 AS sort
    UNION ALL SELECT 'Antriebstyp / Steuerung' AS label, 'TEXT' AS typ, 3 AS sort
) x
WHERE mo.modul_key = 'm_tor' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_datenfeld d WHERE d.modul_id = mo.id AND d.label = x.label);

INSERT INTO wartung_modul_datenfeld (modul_id, label, feldtyp, sortierung)
SELECT mo.id, x.label, x.typ, x.sort
FROM wartung_modul mo
CROSS JOIN (
    SELECT 'Anforderung lt. Brandschutzkonzept' AS label, 'TEXT' AS typ, 1 AS sort
    UNION ALL SELECT 'Selbstschließend gefordert' AS label, 'JA_NEIN' AS typ, 2 AS sort
) x
WHERE mo.modul_key = 'm_dicht' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_datenfeld d WHERE d.modul_id = mo.id AND d.label = x.label);

INSERT INTO wartung_modul_datenfeld (modul_id, label, feldtyp, sortierung)
SELECT mo.id, x.label, x.typ, x.sort
FROM wartung_modul mo
CROSS JOIN (
    SELECT 'Feststellanlagen-Typ (1/3 autark, 2/4 über BMA)' AS label, 'TEXT' AS typ, 1 AS sort
    UNION ALL SELECT 'Hersteller / Fabrikat' AS label, 'TEXT' AS typ, 2 AS sort
    UNION ALL SELECT 'Feststellvorrichtung Hersteller/Typ' AS label, 'TEXT' AS typ, 3 AS sort
    UNION ALL SELECT 'Rauchmelder Hersteller/Typ' AS label, 'TEXT' AS typ, 4 AS sort
    UNION ALL SELECT 'Rauchmelder Einbaudatum' AS label, 'TEXT' AS typ, 5 AS sort
    UNION ALL SELECT 'Melder mit Verschmutzungskompensation (8 Jahre) statt ohne (5 Jahre)' AS label, 'JA_NEIN' AS typ, 6 AS sort
    UNION ALL SELECT 'Handauslösetaster vorhanden' AS label, 'JA_NEIN' AS typ, 7 AS sort
) x
WHERE mo.modul_key = 'm_fsa' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_datenfeld d WHERE d.modul_id = mo.id AND d.label = x.label);

INSERT INTO wartung_modul_datenfeld (modul_id, label, feldtyp, sortierung)
SELECT mo.id, x.label, x.typ, x.sort
FROM wartung_modul mo
CROSS JOIN (
    SELECT 'Verglasung / Glasart' AS label, 'TEXT' AS typ, 1 AS sort
    UNION ALL SELECT 'Brandschutzklassifikation Glas' AS label, 'TEXT' AS typ, 2 AS sort
) x
WHERE mo.modul_key = 'm_glas' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_datenfeld d WHERE d.modul_id = mo.id AND d.label = x.label);

INSERT INTO wartung_modul_datenfeld (modul_id, label, feldtyp, sortierung)
SELECT mo.id, x.label, x.typ, x.sort
FROM wartung_modul mo
CROSS JOIN (
    SELECT 'Panikbeschlag / Typ' AS label, 'TEXT' AS typ, 1 AS sort
    UNION ALL SELECT 'EN-Norm Panikfunktion' AS label, 'TEXT' AS typ, 2 AS sort
) x
WHERE mo.modul_key = 'm_panik' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_datenfeld d WHERE d.modul_id = mo.id AND d.label = x.label);

INSERT INTO wartung_modul_datenfeld (modul_id, label, feldtyp, sortierung)
SELECT mo.id, x.label, x.typ, x.sort
FROM wartung_modul mo
CROSS JOIN (
    SELECT 'Antriebshersteller' AS label, 'TEXT' AS typ, 1 AS sort
    UNION ALL SELECT 'Antriebstyp / Modell' AS label, 'TEXT' AS typ, 2 AS sort
    UNION ALL SELECT 'Steuerungseinheit' AS label, 'TEXT' AS typ, 3 AS sort
) x
WHERE mo.modul_key = 'm_antrieb' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_datenfeld d WHERE d.modul_id = mo.id AND d.label = x.label);

INSERT INTO wartung_modul_datenfeld (modul_id, label, feldtyp, sortierung)
SELECT mo.id, x.label, x.typ, x.sort
FROM wartung_modul mo
CROSS JOIN (
    SELECT 'Hersteller / Typ Verriegelungssystem' AS label, 'TEXT' AS typ, 1 AS sort
    UNION ALL SELECT 'Notstromversorgung / Akku vorhanden' AS label, 'JA_NEIN' AS typ, 2 AS sort
    UNION ALL SELECT 'Letzter Akkutausch' AS label, 'TEXT' AS typ, 3 AS sort
) x
WHERE mo.modul_key = 'm_eltvtr' AND NOT EXISTS (
    SELECT 1 FROM wartung_modul_datenfeld d WHERE d.modul_id = mo.id AND d.label = x.label);

-- ── Globale Datenfelder (in jedem Protokoll) ────────────────────────────────
INSERT INTO wartung_globales_datenfeld (label, feldtyp, sortierung)
SELECT x.label, x.typ, x.sort
FROM (
    SELECT 'Standort / Lage' AS label, 'TEXT' AS typ, 1 AS sort
    UNION ALL SELECT 'Hersteller' AS label, 'TEXT' AS typ, 2 AS sort
    UNION ALL SELECT 'Zulassungsnummer' AS label, 'TEXT' AS typ, 3 AS sort
) x
WHERE NOT EXISTS (SELECT 1 FROM wartung_globales_datenfeld g WHERE g.label = x.label);
END IF;
END $$;
