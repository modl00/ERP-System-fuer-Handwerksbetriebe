import { useEffect, useMemo, useSyncExternalStore } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { ladeLeiste, type LeisteStand } from './schnellzugriffApi';

/**
 * Die Schnellzugriffsleiste: ein kleiner gemeinsamer Speicher wie beim Menü, damit Leiste und Dialog nur eine Anfrage stellen. Solange der Stand unbekannt ist
 * (lädt, Netz weg), zeigt die Leiste nichts; nach kurzer Zeit wird neu versucht.
 */
let stand: LeisteStand | null = null;
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
    ladeLeiste().then(
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
export function setzeLeisteZurueck(): void {
    generation += 1;
    stand = null;
    laufend = false;
    melde();
}

/** Setzt den Stand direkt, ohne neue Anfrage (nach dem Speichern). */
export function setzeLeistenStand(neu: LeisteStand): void {
    generation += 1;
    stand = neu;
    laufend = false;
    melde();
}

/** Was gilt: die eigene Leiste, sonst die Vorgabe der Firma, sonst eine leere Leiste. */
export function eintraegeAus(s: LeisteStand): string[] {
    return s.eigene ?? s.vorgabe ?? [];
}

export interface LeisteAnsicht {
    /** {@code false}, solange der Stand unbekannt ist. */
    bekannt: boolean;
    stand: LeisteStand | null;
    eintraege: string[];
}

export function useSchnellzugriff(): LeisteAnsicht {
    const aktuell = useSyncExternalStore(abonniere, () => stand, () => stand);
    useEffect(() => {
        if (aktuell === null) lade();
    }, [aktuell]);
    return useMemo(() => (aktuell === null ? { bekannt: false, stand: null, eintraege: [] } : { bekannt: true, stand: aktuell, eintraege: eintraegeAus(aktuell) }), [aktuell]);
}

/** Lädt die Leiste nach einem Benutzerwechsel neu: Die eigene Leiste gehört zum Benutzer. */
let zuletztGesehen: number | null | undefined;

export function useSchnellzugriffJeBenutzer(): void {
    const { user } = useAuth();
    const benutzerId = user?.id ?? null;
    useEffect(() => {
        if (zuletztGesehen !== undefined && zuletztGesehen !== benutzerId) setzeLeisteZurueck();
        zuletztGesehen = benutzerId;
    }, [benutzerId]);
}
