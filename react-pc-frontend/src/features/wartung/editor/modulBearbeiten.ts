import type { GlobalesDatenfeld, ModulAbschnitt, ModulDatenfeld, ModulDetail, WartungBereich } from '../types';

let schluesselZaehler = 0;
const frischerSchluessel = () => `k${++schluesselZaehler}`;

/** Verschiebt einen Eintrag um eine Stelle. Am Rand bleibt die Liste unverändert. */
export function verschiebe<T>(liste: T[], index: number, richtung: -1 | 1): T[] {
    const ziel = index + richtung;
    if (index < 0 || index >= liste.length || ziel < 0 || ziel >= liste.length) return liste;
    const kopie = [...liste];
    [kopie[index], kopie[ziel]] = [kopie[ziel], kopie[index]];
    return kopie;
}

export function entferne<T>(liste: T[], index: number): T[] {
    return liste.filter((_, i) => i !== index);
}

export function ersetze<T>(liste: T[], index: number, neu: T): T[] {
    return liste.map((eintrag, i) => (i === index ? neu : eintrag));
}

/** Eine Option je Zeile; leere Zeilen und Leerraum am Rand fallen weg. */
export function optionenAusText(text: string): string[] {
    return text.split('\n').map((zeile) => zeile.trim()).filter((zeile) => zeile !== '');
}

export function leeresModul(): ModulDetail {
    return {
        id: null,
        version: null,
        modulKey: null,
        name: '',
        beschreibung: null,
        icon: null,
        kategorie: 'BASIS',
        systemModul: false,
        aktiv: true,
        standardIntervallMonate: 12,
        andockbarAn: [],
        untertypen: [],
        abschnitte: [],
        anzahlTueren: 0,
        anzahlProtokolle: 0,
    };
}

export function leererAbschnitt(bereich: WartungBereich): ModulAbschnitt {
    return {
        id: null,
        bereich,
        label: '',
        pruefpunkte: bereich === 'PRUEFPUNKTE' ? [{ id: null, label: '', nurBeiUntertyp: null }] : [],
        datenfelder: bereich === 'DATENFELDER' ? [leeresDatenfeld()] : [],
    };
}

export function leeresDatenfeld(): ModulDatenfeld {
    return { id: null, label: '', feldtyp: 'TEXT', optionen: [] };
}

/** Eine Kopie als neues Modul: ohne Nummern, damit der Server alles neu anlegt. Bauart-Schlüssel bleiben, die Prüfpunkte verweisen darauf. */
export function kopieAlsNeu(m: ModulDetail): ModulDetail {
    return {
        ...m,
        id: null,
        version: null,
        modulKey: null,
        name: `${m.name} (Kopie)`,
        systemModul: false,
        anzahlTueren: 0,
        anzahlProtokolle: 0,
        untertypen: m.untertypen.map((u) => ({ ...u, id: null })),
        abschnitte: m.abschnitte.map((a) => ({
            ...a,
            id: null,
            pruefpunkte: a.pruefpunkte.map((p) => ({ ...p, id: null })),
            datenfelder: a.datenfelder.map((d) => ({ ...d, id: null, optionen: [...d.optionen] })),
        })),
    };
}

/** Was sich seit dem Laden geändert hat. Verglichen wird der Inhalt, nicht die Identität. */
export function istGeaendert(vorher: ModulDetail | null, jetzt: ModulDetail | null): boolean {
    return JSON.stringify(vorher) !== JSON.stringify(jetzt);
}

function datenfeldFehler(feld: Pick<ModulDatenfeld | GlobalesDatenfeld, 'label' | 'feldtyp' | 'optionen'>): string | null {
    if (feld.label.trim() === '') return 'Bitte jedes Datenfeld benennen.';
    if (feld.feldtyp === 'AUSWAHL') {
        const optionen = feld.optionen.map((o) => o.trim()).filter((o) => o !== '');
        if (optionen.length === 0) return `Beim Datenfeld „${feld.label.trim()}" fehlen die Auswahlmöglichkeiten.`;
        if (new Set(optionen).size !== optionen.length) return `Beim Datenfeld „${feld.label.trim()}" kommt eine Auswahlmöglichkeit doppelt vor.`;
    }
    return null;
}

/** Erste Unstimmigkeit in den Datenfeldern (für die gemeinsamen Felder) oder {@code null}. */
export function fehlerInDatenfeldern(felder: GlobalesDatenfeld[]): string | null {
    for (const feld of felder) {
        const fehler = datenfeldFehler(feld);
        if (fehler) return fehler;
    }
    const namen = felder.map((f) => f.label.trim().toLowerCase());
    return new Set(namen).size !== namen.length ? 'Zwei Datenfelder heißen gleich.' : null;
}

/** Erste Unstimmigkeit im Modul oder {@code null}. Der Server prüft alles noch einmal; das hier spart nur den Umweg. */
export function fehlerImModul(m: ModulDetail): string | null {
    if (m.name.trim() === '') return 'Bitte einen Namen eintragen.';
    if (m.untertypen.some((u) => u.label.trim() === '')) return 'Bitte jede Bauart benennen.';
    const schluessel = new Set(m.untertypen.map((u) => u.schluessel).filter((k): k is string => k !== null));
    const bauarten = m.untertypen.map((u) => u.label.trim().toLowerCase());
    if (new Set(bauarten).size !== bauarten.length) return 'Zwei Bauarten heißen gleich.';
    for (const abschnitt of m.abschnitte) {
        if (abschnitt.label.trim() === '') return 'Bitte jeden Abschnitt benennen.';
        if (abschnitt.pruefpunkte.some((p) => p.label.trim() === '')) return 'Bitte jeden Prüfpunkt beschreiben.';
        const verwaist = abschnitt.pruefpunkte.find((p) => p.nurBeiUntertyp !== null && !schluessel.has(p.nurBeiUntertyp));
        if (verwaist) return `Der Prüfpunkt „${verwaist.label.trim()}" gilt für eine Bauart, die es nicht mehr gibt.`;
        for (const feld of abschnitt.datenfelder) {
            const fehler = datenfeldFehler(feld);
            if (fehler) return fehler;
        }
    }
    return null;
}

/** Leere Prüfpunkte und Abschnitte ohne Inhalt entfernen, bevor gespeichert wird: Die Eingabemasken legen gern eine leere Zeile an. */
export function bereinige(m: ModulDetail): ModulDetail {
    return {
        ...m,
        name: m.name.trim(),
        abschnitte: m.abschnitte
            .map((a) => ({
                ...a,
                pruefpunkte: a.pruefpunkte.filter((p) => p.id !== null || p.label.trim() !== ''),
                datenfelder: a.datenfelder.filter((d) => d.id !== null || d.label.trim() !== ''),
            }))
            .filter((a) => a.id !== null || a.label.trim() !== '' || a.pruefpunkte.length > 0 || a.datenfelder.length > 0),
    };
}

/** Gibt jeder Zeile einen Schlüssel für React, damit eine Zeile beim Verschieben ihren Zustand behält. Vorhandene Schlüssel bleiben. */
export function schluesselVergeben(m: ModulDetail): ModulDetail {
    return {
        ...m,
        untertypen: m.untertypen.map((u) => ({ ...u, clientKey: u.clientKey ?? frischerSchluessel() })),
        abschnitte: m.abschnitte.map((a) => ({
            ...a,
            clientKey: a.clientKey ?? frischerSchluessel(),
            pruefpunkte: a.pruefpunkte.map((p) => ({ ...p, clientKey: p.clientKey ?? frischerSchluessel() })),
            datenfelder: a.datenfelder.map((d) => ({ ...d, clientKey: d.clientKey ?? frischerSchluessel() })),
        })),
    };
}

export const neueZeile = <T extends object>(zeile: T): T & { clientKey: string } => ({ ...zeile, clientKey: frischerSchluessel() });

/** Verschiebt einen Abschnitt nur innerhalb seiner Art: Prüfpunkt-Abschnitte tauschen nicht mit Datenfeld-Abschnitten. */
export function verschiebeImBereich(abschnitte: ModulAbschnitt[], index: number, richtung: -1 | 1): ModulAbschnitt[] {
    const bereich = abschnitte[index]?.bereich;
    if (!bereich) return abschnitte;
    let ziel = index + richtung;
    while (ziel >= 0 && ziel < abschnitte.length && abschnitte[ziel].bereich !== bereich) ziel += richtung;
    if (ziel < 0 || ziel >= abschnitte.length) return abschnitte;
    const kopie = [...abschnitte];
    [kopie[index], kopie[ziel]] = [kopie[ziel], kopie[index]];
    return kopie;
}

/** Entfernt eine Bauart. Prüfpunkte, die nur für sie galten, gelten danach für alle: Wegfallen sollen sie nicht stillschweigend. */
export function entferneBauart(m: ModulDetail, index: number): { modul: ModulDetail; betroffenePruefpunkte: number } {
    const schluessel = m.untertypen[index]?.schluessel ?? null;
    let betroffen = 0;
    const abschnitte = m.abschnitte.map((a) => ({
        ...a,
        pruefpunkte: a.pruefpunkte.map((p) => {
            if (schluessel !== null && p.nurBeiUntertyp === schluessel) {
                betroffen += 1;
                return { ...p, nurBeiUntertyp: null };
            }
            return p;
        }),
    }));
    return { modul: { ...m, untertypen: entferne(m.untertypen, index), abschnitte }, betroffenePruefpunkte: betroffen };
}
