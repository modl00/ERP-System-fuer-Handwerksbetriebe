import { describe, expect, it } from 'vitest';
import { berechneStatistik, fortschrittProzent, gruppiereNachAbschnitt } from './gruppierung';
import type { ProtokollAbschnitt } from './types';

const abschnitte: ProtokollAbschnitt[] = [
    { id: 2, bereich: 'PRUEFPUNKTE', label: 'Zweiflügelig', sortierung: 1 },
    { id: 1, bereich: 'PRUEFPUNKTE', label: 'Brandschutztür', sortierung: 0 },
    { id: 3, bereich: 'DATENFELDER', label: 'Daten', sortierung: 2 },
];

describe('gruppiereNachAbschnitt', () => {
    it('ordnet die Abschnitte nach ihrer Sortierung, nicht nach ihrer Id', () => {
        const gruppen = gruppiereNachAbschnitt(abschnitte, 'PRUEFPUNKTE', [
            { abschnittId: 2, sortierung: 3 },
            { abschnittId: 1, sortierung: 0 },
        ]);
        expect(gruppen.map((g) => g.abschnitt?.label)).toEqual(['Brandschutztür', 'Zweiflügelig']);
    });

    it('sortiert die Einträge innerhalb eines Abschnitts nach ihrer eigenen Sortierung', () => {
        const gruppen = gruppiereNachAbschnitt(abschnitte, 'PRUEFPUNKTE', [
            { abschnittId: 1, sortierung: 5 },
            { abschnittId: 1, sortierung: 1 },
            { abschnittId: 1, sortierung: 3 },
        ]);
        expect(gruppen[0].eintraege.map((e) => e.sortierung)).toEqual([1, 3, 5]);
    });

    it('lässt Abschnitte ohne Einträge weg (zum Beispiel, wenn "nur offene" gefiltert wird)', () => {
        const gruppen = gruppiereNachAbschnitt(abschnitte, 'PRUEFPUNKTE', [{ abschnittId: 2, sortierung: 0 }]);
        expect(gruppen).toHaveLength(1);
        expect(gruppen[0].abschnitt?.id).toBe(2);
    });

    it('stellt Einträge ohne Abschnitt ans Ende', () => {
        const gruppen = gruppiereNachAbschnitt(abschnitte, 'PRUEFPUNKTE', [
            { abschnittId: null, sortierung: 0 },
            { abschnittId: 1, sortierung: 0 },
        ]);
        expect(gruppen.map((g) => g.abschnitt?.id ?? null)).toEqual([1, null]);
    });

    it('behandelt Einträge mit unbekanntem Abschnitt wie Einträge ohne Abschnitt', () => {
        const gruppen = gruppiereNachAbschnitt(abschnitte, 'PRUEFPUNKTE', [{ abschnittId: 999, sortierung: 0 }]);
        expect(gruppen).toHaveLength(1);
        expect(gruppen[0].abschnitt).toBeNull();
    });

    it('nimmt nur Abschnitte des verlangten Bereichs', () => {
        const gruppen = gruppiereNachAbschnitt(abschnitte, 'DATENFELDER', [
            { abschnittId: 3, sortierung: 0 },
            { abschnittId: 1, sortierung: 0 },
        ]);
        expect(gruppen.map((g) => g.abschnitt?.label ?? null)).toEqual(['Daten', null]);
    });

    it('liefert bei leerer Liste nichts', () => {
        expect(gruppiereNachAbschnitt(abschnitte, 'PRUEFPUNKTE', [])).toEqual([]);
    });

    it('verändert die übergebenen Listen nicht', () => {
        const eingabe = [{ abschnittId: 1, sortierung: 2 }, { abschnittId: 1, sortierung: 1 }];
        const kopie = JSON.parse(JSON.stringify(eingabe));
        gruppiereNachAbschnitt(abschnitte, 'PRUEFPUNKTE', eingabe);
        expect(eingabe).toEqual(kopie);
    });
});

describe('fortschrittProzent', () => {
    it('rundet auf ganze Prozent', () => {
        expect(fortschrittProzent({ gesamt: 3, bewertet: 1 })).toBe(33);
        expect(fortschrittProzent({ gesamt: 3, bewertet: 2 })).toBe(67);
        expect(fortschrittProzent({ gesamt: 37, bewertet: 37 })).toBe(100);
        expect(fortschrittProzent({ gesamt: 37, bewertet: 0 })).toBe(0);
    });

    it('liefert ohne Prüfpunkte 0 und nie NaN', () => {
        expect(fortschrittProzent({ gesamt: 0, bewertet: 0 })).toBe(0);
        expect(Number.isNaN(fortschrittProzent({ gesamt: 0, bewertet: 5 }))).toBe(false);
    });
});

describe('berechneStatistik', () => {
    it('zählt die Ergebnisse', () => {
        const s = berechneStatistik([{ ergebnis: 'IO' }, { ergebnis: 'IO' }, { ergebnis: 'NIO' }, { ergebnis: 'NA' }, { ergebnis: null }]);
        expect(s).toEqual({ gesamt: 5, bewertet: 4, inOrdnung: 2, nichtInOrdnung: 1, nichtAnwendbar: 1 });
    });

    it('kommt mit einer leeren Liste zurecht', () => {
        expect(berechneStatistik([])).toEqual({ gesamt: 0, bewertet: 0, inOrdnung: 0, nichtInOrdnung: 0, nichtAnwendbar: 0 });
    });
});
