import { useEffect, useSyncExternalStore } from 'react';
import { ladeFeatures, type Features } from './api';

/**
 * Welche Module sind eingeschaltet (derzeit: Wartung)?
 *
 * <p>Menü, Seiten und der Bereich im Projekt fragen das gleichzeitig. Ein kleiner gemeinsamer Speicher hält das
 * Ergebnis und bündelt laufende Anfragen, wie beim Telefon-Recht. {@link setzeFeaturesZurueck} leert ihn
 * (bei Benutzerwechsel), {@link setzeFeaturesStand} setzt ihn direkt (nach dem Umschalten in den Einstellungen).</p>
 *
 * <p>Rückgabe: {@code null}, solange noch geladen wird, danach der Stand. Wer {@code null} bekommt, zeigt noch nichts:
 * Ein Menüpunkt blitzt so nicht kurz auf und verschwindet wieder. Schlägt das Laden fehl (Netz, Server), bleibt der Stand unbekannt
 * und das Laden wird nach kurzer Zeit wiederholt: Ein kurzer Aussetzer blendet die Wartung nicht dauerhaft aus.</p>
 */
let stand: Features | null = null;
let laufend = false;
let generation = 0;
const zuhoerer = new Set<() => void>();

/** Wartezeit bis zum nächsten Versuch nach einem Fehler. */
export const WIEDERHOLUNG_MS = 5000;

function melde() {
    zuhoerer.forEach((z) => z());
}

function lade() {
    if (stand !== null || laufend) return;
    laufend = true;
    const meineGeneration = generation;
    ladeFeatures().then(
        (geladen) => {
            // Inzwischen zurückgesetzt (z. B. Abmelden): Ergebnis verwerfen, das Zurücksetzen hat laufend schon geleert
            if (meineGeneration !== generation) return;
            stand = geladen;
            laufend = false;
            melde();
        },
        () => {
            if (meineGeneration !== generation) return;
            laufend = false;
            // Stand bleibt unbekannt (null): Die Wartung bleibt verborgen, aber nicht für immer
            setTimeout(() => {
                if (meineGeneration === generation && stand === null && zuhoerer.size > 0) lade();
            }, WIEDERHOLUNG_MS);
        },
    );
}

function abonniere(zuhoererFn: () => void) {
    zuhoerer.add(zuhoererFn);
    return () => {
        zuhoerer.delete(zuhoererFn);
    };
}

function momentaufnahme() {
    return stand;
}

/** Vergisst den Stand; alle eingebundenen Stellen fragen neu. */
export function setzeFeaturesZurueck(): void {
    generation += 1;
    stand = null;
    laufend = false;
    melde();
}

/** Setzt den Stand direkt, ohne neue Anfrage. */
export function setzeFeaturesStand(neu: Features): void {
    generation += 1;
    stand = neu;
    laufend = false;
    melde();
}

export function useFeatures(): Features | null {
    const aktuell = useSyncExternalStore(abonniere, momentaufnahme, momentaufnahme);
    useEffect(() => {
        if (aktuell === null) lade();
    }, [aktuell]);
    return aktuell;
}

/** Ist das Wartungsmodul eingeschaltet? {@code null} = wird noch geprüft. */
export function useWartungAktiv(): boolean | null {
    return useFeatures()?.wartung ?? null;
}

/** Ist das Lagermodul eingeschaltet? {@code null} = wird noch geprüft. Fehlt die Angabe, gilt es als eingeschaltet. */
export function useLagerAktiv(): boolean | null {
    const features = useFeatures();
    return features === null ? null : features.lager !== false;
}

/** Module, die über den allgemeinen Schalter laufen (siehe {@code Modul} im Backend). */
export type AllgemeinesModul = 'miete' | 'website';

/** Ist das Modul eingeschaltet? {@code null} = wird noch geprüft. Fehlt die Angabe, gilt es als eingeschaltet. */
export function useModulAktiv(modul: AllgemeinesModul): boolean | null {
    const features = useFeatures();
    return features === null ? null : features[modul] !== false;
}
