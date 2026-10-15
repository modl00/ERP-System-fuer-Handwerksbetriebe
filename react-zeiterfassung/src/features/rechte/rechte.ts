import { useSyncExternalStore } from 'react';

export type FunktionsName = 'LAGER' | 'WARTUNG' | 'ZEITERFASSUNG' | 'KUNDEN' | 'ANFRAGEN' | 'LIEFERSCHEINE';
export type FunktionsAktion = 'ANSEHEN' | 'ANLEGEN' | 'AENDERN' | 'LOESCHEN';
export interface Rechte { ansehen: boolean; anlegen: boolean; aendern: boolean; loeschen: boolean }
export interface AppRechte {
    funktionen: Partial<Record<FunktionsName, Rechte>>;
    /** Ist das Modul in den Einstellungen eingeschaltet? */
    module: { wartung?: boolean; lager?: boolean };
}

const SPEICHER = 'zeiterfassung_rechte';
const FELD: Record<FunktionsAktion, keyof Rechte> = { ANSEHEN: 'ansehen', ANLEGEN: 'anlegen', AENDERN: 'aendern', LOESCHEN: 'loeschen' };
/** Funktionen mit Modulschalter; die Zeiterfassung ist immer da und hat nur Rechte. */
const MODUL: Record<FunktionsName, 'wartung' | 'lager' | null> = { LAGER: 'lager', WARTUNG: 'wartung', ZEITERFASSUNG: null, KUNDEN: null, ANFRAGEN: null, LIEFERSCHEINE: null };

let stand: AppRechte | null = lesenAusSpeicher();
const zuhoerer = new Set<() => void>();

function lesenAusSpeicher(): AppRechte | null {
    try {
        const text = localStorage.getItem(SPEICHER);
        const wert = text ? JSON.parse(text) as AppRechte : null;
        return wert && typeof wert === 'object' && wert.funktionen ? wert : null;
    } catch {
        return null;
    }
}

function setze(neu: AppRechte | null) {
    stand = neu;
    try {
        if (neu) localStorage.setItem(SPEICHER, JSON.stringify(neu));
        else localStorage.removeItem(SPEICHER);
    } catch { /* Speicher nicht verfügbar: Stand gilt nur für diese Sitzung */ }
    zuhoerer.forEach((z) => z());
}

/**
 * Lädt die Rechte des Mitarbeiters zum Zugangslink: dieselben Rechte wie am PC (Rechteverwaltung). Gelingt das Laden nicht (kein Netz), bleibt der
 * zuletzt gespeicherte Stand gültig, damit die App auf der Baustelle weiter funktioniert. Ein ungültiger Zugang (401) leert den Stand.
 * Maßgeblich bleibt die Prüfung auf dem Server bei jedem Endpunkt; hier wird nur ausgeblendet, was nicht erlaubt ist.
 */
export async function ladeRechte(token: string): Promise<void> {
    try {
        const res = await fetch(`/api/zeiterfassung/rechte/${encodeURIComponent(token)}`);
        if (res.status === 401) { setze(null); return; }
        if (!res.ok) return;
        const daten = await res.json() as AppRechte;
        if (daten && typeof daten === 'object' && daten.funktionen) setze({ funktionen: daten.funktionen, module: daten.module ?? {} });
    } catch { /* offline: gespeicherter Stand bleibt */ }
}

/** Vergisst die Rechte (beim Abmelden). */
export function vergissRechte(): void {
    setze(null);
}

export function useRechte(): AppRechte | null {
    return useSyncExternalStore((fn) => { zuhoerer.add(fn); return () => { zuhoerer.delete(fn); }; }, () => stand, () => stand);
}

/**
 * Darf der Mitarbeiter das in der App? Solange die Rechte unbekannt sind (noch nie geladen), gilt "nein": Neue Bereiche erscheinen erst, wenn der
 * Server sie erlaubt hat. Ist das Modul ausgeschaltet, gilt auch "nein".
 */
export function darf(rechte: AppRechte | null, funktion: FunktionsName, aktion: FunktionsAktion = 'ANSEHEN'): boolean {
    if (!rechte) return false;
    const modul = MODUL[funktion];
    if (modul && rechte.module[modul] === false) return false;
    const r = rechte.funktionen[funktion];
    return r === undefined ? true : r[FELD[aktion]];
}

export function useDarf(funktion: FunktionsName, aktion: FunktionsAktion = 'ANSEHEN'): boolean {
    return darf(useRechte(), funktion, aktion);
}
