import { describe, expect, it } from 'vitest';
import { erzeugePalette, FARB_PRESETS, istGueltigeFarbe, kontrastZuWeiss, normalisiereFarbe, STUFEN } from './farbpalette';

describe('farbpalette', () => {
    it('Stufe 600 ist genau die gewählte Hauptfarbe', () => {
        expect(erzeugePalette('#e11d48')[600]).toBe('225 29 72');
        expect(erzeugePalette('#0f766e')[600]).toBe('15 118 110');
    });

    it('hellere Stufen werden heller, dunklere dunkler', () => {
        const p = erzeugePalette('#0f766e');
        const summe = (s: string) => s.split(' ').reduce((a, k) => a + Number(k), 0);
        const reihenfolge = STUFEN.map((s) => summe(p[s]));
        for (let i = 1; i < reihenfolge.length; i += 1) expect(reihenfolge[i]).toBeLessThan(reihenfolge[i - 1]);
    });

    it('liefert für jede Stufe drei Zahlen von 0 bis 255', () => {
        for (const preset of FARB_PRESETS) {
            const p = erzeugePalette(preset.farbe);
            for (const stufe of STUFEN) {
                const teile = p[stufe].split(' ').map(Number);
                expect(teile).toHaveLength(3);
                teile.forEach((k) => { expect(k).toBeGreaterThanOrEqual(0); expect(k).toBeLessThanOrEqual(255); });
            }
        }
    });

    it('weigert sich bei ungültigen Farben', () => {
        expect(() => erzeugePalette('rot')).toThrow();
        expect(() => erzeugePalette('#12345')).toThrow();
    });

    it('prüft und bereinigt Eingaben', () => {
        expect(istGueltigeFarbe('#0F766E')).toBe(true);
        expect(istGueltigeFarbe('#0f766')).toBe(false);
        expect(istGueltigeFarbe('#0f766e;color:red')).toBe(false);
        expect(normalisiereFarbe(' 0F766E ')).toBe('#0f766e');
        expect(normalisiereFarbe('#abc')).toBeNull();
        expect(normalisiereFarbe("'; DROP TABLE x; --")).toBeNull();
        expect(normalisiereFarbe('<script>alert(1)</script>')).toBeNull();
    });

    it('meldet schwachen Kontrast bei hellen Farben', () => {
        expect(kontrastZuWeiss('#ffff00')).toBeLessThan(4.5);
        expect(kontrastZuWeiss('#e11d48')).toBeGreaterThan(4.5);
    });

    it('alle Vorgaben tragen weiße Schrift gut lesbar', () => {
        for (const p of FARB_PRESETS) expect(kontrastZuWeiss(p.farbe)).toBeGreaterThanOrEqual(4.5);
    });
});
