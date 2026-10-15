import { useEffect, useSyncExternalStore } from 'react';

export type FunktionsName = 'LAGER' | 'WARTUNG' | 'BESTELLUNGEN' | 'KUNDEN' | 'ANFRAGEN';
export type FunktionsAktion = 'ANSEHEN' | 'ANLEGEN' | 'AENDERN' | 'LOESCHEN';
export interface Rechte { ansehen: boolean; anlegen: boolean; aendern: boolean; loeschen: boolean }
export type MeineRechte = Partial<Record<FunktionsName, Rechte>>;

/**
 * Rechte des angemeldeten Benutzers an den Funktionen des Programms (Rechteverwaltung, je Abteilung). Mehrere Stellen fragen das gleichzeitig; ein
 * kleiner gemeinsamer Speicher hält das Ergebnis. {@code null} = wird noch geladen oder ist unbekannt: Wer {@code null} bekommt, zeigt nichts weg
 * (der Server prüft jede Anfrage ohnehin). Wirkt nur als Hilfe, damit Menüpunkte ohne Recht gar nicht erst erscheinen.
 */
let stand: MeineRechte | null = null;
let laufend = false;
let generation = 0;
const zuhoerer = new Set<() => void>();

export const WIEDERHOLUNG_MS = 5000;

function melde() {
    zuhoerer.forEach((z) => z());
}

function lade() {
    if (stand !== null || laufend) return;
    laufend = true;
    const meine = generation;
    fetch('/api/funktionsrechte/meine')
        .then((res) => (res.ok ? (res.json() as Promise<MeineRechte>) : Promise.reject(new Error('Rechte konnten nicht geladen werden.'))))
        .then(
            (geladen) => {
                if (meine !== generation) return;
                stand = geladen;
                laufend = false;
                melde();
            },
            () => {
                if (meine !== generation) return;
                laufend = false;
                setTimeout(() => {
                    if (meine === generation && stand === null && zuhoerer.size > 0) lade();
                }, WIEDERHOLUNG_MS);
            },
        );
}

function abonniere(fn: () => void) {
    zuhoerer.add(fn);
    return () => { zuhoerer.delete(fn); };
}

/** Vergisst den Stand (bei Benutzerwechsel), alle eingebundenen Stellen fragen neu. */
export function setzeFunktionsrechteZurueck(): void {
    generation += 1;
    stand = null;
    laufend = false;
    melde();
}

export function useMeineFunktionsrechte(): MeineRechte | null {
    const aktuell = useSyncExternalStore(abonniere, () => stand, () => stand);
    useEffect(() => {
        if (aktuell === null) lade();
    }, [aktuell]);
    return aktuell;
}

const FELD: Record<FunktionsAktion, keyof Rechte> = { ANSEHEN: 'ansehen', ANLEGEN: 'anlegen', AENDERN: 'aendern', LOESCHEN: 'loeschen' };

/**
 * Darf der Benutzer das? {@code null} = noch unbekannt. Fehlt die Funktion in der Antwort (älterer Server), gilt sie als erlaubt, wie ohne Einschränkung.
 */
export function useDarf(funktion: FunktionsName, aktion: FunktionsAktion = 'ANSEHEN'): boolean | null {
    const rechte = useMeineFunktionsrechte();
    if (rechte === null) return null;
    const r = rechte[funktion];
    return r === undefined ? true : r[FELD[aktion]];
}
