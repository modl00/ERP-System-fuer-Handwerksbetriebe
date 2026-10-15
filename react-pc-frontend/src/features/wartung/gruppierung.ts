import type { ProtokollAbschnitt, ProtokollPruefpunkt, ProtokollStatistik, WartungBereich } from './types';

export interface Gruppe<T> {
    /** `null` für Einträge ohne Abschnitt (von Hand ergänzt). */
    abschnitt: ProtokollAbschnitt | null;
    eintraege: T[];
}

/**
 * Ordnet Einträge unter ihre Abschnitte. Die Abschnitte stehen in ihrer eigenen Reihenfolge, die Einträge
 * darin nach ihrer Sortierung, Einträge ohne Abschnitt kommen zuletzt. Leere Abschnitte entfallen.
 */
export function gruppiereNachAbschnitt<T extends { abschnittId: number | null; sortierung: number }>(
    abschnitte: ProtokollAbschnitt[],
    bereich: WartungBereich,
    eintraege: T[],
): Gruppe<T>[] {
    const nachSortierung = (a: T, b: T) => a.sortierung - b.sortierung;
    const meine = abschnitte.filter((a) => a.bereich === bereich).sort((a, b) => a.sortierung - b.sortierung);
    const bekannt = new Set(meine.map((a) => a.id));

    const gruppen: Gruppe<T>[] = [];
    for (const abschnitt of meine) {
        const treffer = eintraege.filter((e) => e.abschnittId === abschnitt.id).sort(nachSortierung);
        if (treffer.length > 0) gruppen.push({ abschnitt, eintraege: treffer });
    }
    const ohne = eintraege.filter((e) => e.abschnittId === null || !bekannt.has(e.abschnittId)).sort(nachSortierung);
    if (ohne.length > 0) gruppen.push({ abschnitt: null, eintraege: ohne });
    return gruppen;
}

/** Fortschritt in Prozent, auf ganze Zahlen gerundet. Ohne Prüfpunkte 0, nie `NaN`. */
export function fortschrittProzent(statistik: Pick<ProtokollStatistik, 'gesamt' | 'bewertet'>): number {
    if (statistik.gesamt <= 0) return 0;
    return Math.round((statistik.bewertet / statistik.gesamt) * 100);
}

/** Zählt die Bewertungen neu, wenn sich ein einzelner Prüfpunkt ändert (für sofortige Anzeige). */
export function berechneStatistik(pruefpunkte: Pick<ProtokollPruefpunkt, 'ergebnis'>[]): ProtokollStatistik {
    let inOrdnung = 0;
    let nichtInOrdnung = 0;
    let nichtAnwendbar = 0;
    for (const p of pruefpunkte) {
        if (p.ergebnis === 'IO') inOrdnung++;
        else if (p.ergebnis === 'NIO') nichtInOrdnung++;
        else if (p.ergebnis === 'NA') nichtAnwendbar++;
    }
    return {
        gesamt: pruefpunkte.length,
        bewertet: inOrdnung + nichtInOrdnung + nichtAnwendbar,
        inOrdnung,
        nichtInOrdnung,
        nichtAnwendbar,
    };
}
