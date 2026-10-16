import { useEffect, useMemo, useSyncExternalStore } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { ladeMenue, type MenueStand } from './menueApi';
import { MENUE_VORSCHLAG } from './menueVorschlag';

/**
 * Welche Menüpunkte sind ausgeblendet? Ein kleiner gemeinsamer Speicher wie bei den Modulschaltern: Menü und Dialog
 * fragen gleichzeitig, es geht nur eine Anfrage raus. Solange der Stand unbekannt ist (lädt, Netz weg), bleibt das Menü
 * vollständig; ein Aussetzer blendet also nie Punkte aus, und es wird nach kurzer Zeit neu versucht.
 */
let stand: MenueStand | null = null;
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
    ladeMenue().then(
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
export function setzeMenueZurueck(): void {
    generation += 1;
    stand = null;
    laufend = false;
    melde();
}

/** Setzt den Stand direkt, ohne neue Anfrage (nach dem Speichern). */
export function setzeMenueStand(neu: MenueStand): void {
    generation += 1;
    stand = neu;
    laufend = false;
    melde();
}

/** Was gilt: die eigene Auswahl, sonst die Vorgabe der Firma, sonst der mitgelieferte Vorschlag. */
export function ausgeblendetAus(s: MenueStand): Set<string> {
    return new Set(s.eigene ?? s.vorgabe ?? MENUE_VORSCHLAG);
}

export interface MenueAnsicht {
    /** {@code false}, solange der Stand unbekannt ist: dann wird nichts ausgeblendet. */
    bekannt: boolean;
    stand: MenueStand | null;
    ausgeblendet: Set<string>;
}

const LEER: ReadonlySet<string> = new Set();

export function useMenue(): MenueAnsicht {
    const aktuell = useSyncExternalStore(abonniere, () => stand, () => stand);
    useEffect(() => {
        if (aktuell === null) lade();
    }, [aktuell]);
    return useMemo(
        () => (aktuell === null
            ? { bekannt: false, stand: null, ausgeblendet: LEER as Set<string> }
            : { bekannt: true, stand: aktuell, ausgeblendet: ausgeblendetAus(aktuell) }),
        [aktuell],
    );
}

/** Lädt das Menü nach einem Benutzerwechsel neu: Die eigene Auswahl gehört zum Benutzer. */
let zuletztGesehen: number | null | undefined;

export function useMenueJeBenutzer(): void {
    const { user } = useAuth();
    const benutzerId = user?.id ?? null;
    useEffect(() => {
        if (zuletztGesehen !== undefined && zuletztGesehen !== benutzerId) setzeMenueZurueck();
        zuletztGesehen = benutzerId;
    }, [benutzerId]);
}
