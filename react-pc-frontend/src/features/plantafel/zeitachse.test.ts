import { describe, expect, it } from 'vitest';
import { TAG_BREITE, anzeigeDatum, anzeigeKurz, balken, berechneAchse, formatDatum, istWochenende, monatskoepfe, parseDatum, tageZwischen, wochenenden, xVon } from './zeitachse';

/** Nur Dummy-Daten. 5. Oktober 2026 ist ein Montag. */
const MONTAG = new Date(2026, 9, 5);

describe('Zeitachse', () => {
    it('liest und schreibt Datumswerte ohne Zeitzonen-Verschiebung', () => {
        expect(formatDatum(parseDatum('2026-03-29'))).toBe('2026-03-29');
        expect(tageZwischen(parseDatum('2026-03-28'), parseDatum('2026-03-30'))).toBe(2); // Umstellung auf Sommerzeit
        expect(anzeigeDatum('2026-10-05')).toBe('05.10.2026');
        expect(anzeigeDatum(null)).toBe('–');
        expect(anzeigeKurz('2026-10-05')).toBe('05.10.26');
    });

    it('die Achse beginnt an einem Montag vor dem frühesten Datum und umfasst mindestens sechs volle Wochen', () => {
        const achse = berechneAchse(['2026-10-14', '2026-10-20'], MONTAG);
        expect(achse.start.getDay()).toBe(1);
        expect(achse.start.getTime()).toBeLessThan(MONTAG.getTime());
        expect(achse.tage).toBeGreaterThanOrEqual(42);
        expect(achse.tage % 7).toBe(0);
    });

    it('die Achse wächst mit einem späten Ende', () => {
        const achse = berechneAchse(['2026-10-06', '2027-02-26'], MONTAG);
        expect(tageZwischen(achse.start, parseDatum('2027-02-26'))).toBeLessThan(achse.tage);
    });

    it('ein Balken zählt Start- und Endtag mit', () => {
        const achse = { start: MONTAG, tage: 42 };
        expect(balken(achse, '2026-10-05', '2026-10-05')).toEqual({ x: 0, breite: TAG_BREITE });
        expect(balken(achse, '2026-10-07', '2026-10-09')).toEqual({ x: 2 * TAG_BREITE, breite: 3 * TAG_BREITE });
        expect(xVon(achse, '2026-10-12')).toBe(7 * TAG_BREITE);
    });

    it('Wochenenden und Monatsköpfe', () => {
        const achse = { start: MONTAG, tage: 28 };
        expect(istWochenende(new Date(2026, 9, 10))).toBe(true);
        expect(wochenenden(achse)).toHaveLength(4);
        expect(wochenenden(achse)[0]).toEqual({ x: 5 * TAG_BREITE, breite: 2 * TAG_BREITE });
    });

    it('richtet die freien Tage nach der Arbeitswoche aus: bei Montag bis Samstag ist nur der Sonntag frei', () => {
        const achse = { start: MONTAG, tage: 28 };
        const mitSamstag = [1, 2, 3, 4, 5, 6];
        expect(istWochenende(new Date(2026, 9, 10), mitSamstag)).toBe(false);
        expect(istWochenende(new Date(2026, 9, 11), mitSamstag)).toBe(true);
        expect(wochenenden(achse, mitSamstag)[0]).toEqual({ x: 6 * TAG_BREITE, breite: TAG_BREITE });
        expect(istWochenende(new Date(2026, 9, 5), [2, 3, 4, 5, 6, 7])).toBe(true); // Montag frei

        const monate = monatskoepfe(achse);
        expect(monate.map(m => m.text)).toEqual(['Oktober 2026', 'November 2026']);
        expect(monate[0].breite + monate[1].breite).toBe(28 * TAG_BREITE);
    });
});
