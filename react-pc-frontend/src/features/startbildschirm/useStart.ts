import { useEffect, useMemo, useSyncExternalStore } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { ladeStart, type StartStand } from './startApi';
import { STANDARD_BAUSTEINE } from './bausteine';

/**
 * Der Aufbau des Startbildschirms: ein kleiner gemeinsamer Speicher wie beim Menü. Solange der Stand unbekannt ist (lädt, Netz weg), gilt der mitgelieferte Aufbau;
 * nach kurzer Zeit wird neu versucht.
 */
let stand: StartStand | null = null;
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
    ladeStart().then(
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

function abonniere(z: () => void) {
    zuhoerer.add(z);
    return () => {
        zuhoerer.delete(z);
    };
}

/** Vergisst den Stand; alle eingebundenen Stellen fragen neu (Benutzerwechsel). */
export function setzeStartZurueck(): void {
    generation += 1;
    stand = null;
    laufend = false;
    melde();
}

/** Setzt den Stand direkt, ohne neue Anfrage (nach dem Speichern). */
export function setzeStartStand(neu: StartStand): void {
    generation += 1;
    stand = neu;
    laufend = false;
    melde();
}

/** Was gilt: der eigene Aufbau, sonst die Vorgabe der Firma, sonst der mitgelieferte Aufbau. */
export function bausteineAus(s: StartStand | null): string[] {
    return s?.eigene ?? s?.vorgabe ?? STANDARD_BAUSTEINE;
}

export interface StartAnsicht {
    bekannt: boolean;
    stand: StartStand | null;
    eintraege: string[];
}

export function useStart(): StartAnsicht {
    const aktuell = useSyncExternalStore(abonniere, () => stand, () => stand);
    useEffect(() => {
        if (aktuell === null) lade();
    }, [aktuell]);
    return useMemo(() => ({ bekannt: aktuell !== null, stand: aktuell, eintraege: bausteineAus(aktuell) }), [aktuell]);
}

/** Lädt den Aufbau nach einem Benutzerwechsel neu: Der eigene Aufbau gehört zum Benutzer. */
let zuletztGesehen: number | null | undefined;

export function useStartJeBenutzer(): void {
    const { user } = useAuth();
    const benutzerId = user?.id ?? null;
    useEffect(() => {
        if (zuletztGesehen !== undefined && zuletztGesehen !== benutzerId) setzeStartZurueck();
        zuletztGesehen = benutzerId;
    }, [benutzerId]);
}
