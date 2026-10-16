import { describe, expect, it } from 'vitest';
import { begrenze, groesseLinks, groesseRechts, leseRahmen, standardRahmen, verschiebe } from './kiChatRahmen';

describe('kiChatRahmen', () => {
    it('Standard: unten rechts, 420 × 600; auf kleinen Bildschirmen kleiner', () => {
        expect(standardRahmen(1920, 1080)).toEqual({ x: 1476, y: 456, w: 420, h: 600 });
        const klein = standardRahmen(500, 700);
        expect(klein.w).toBe(420);
        expect(klein.h).toBeLessThanOrEqual(700);
        expect(standardRahmen(360, 640).w).toBe(328);
    });

    it('Verschieben bleibt im Bild: Der Kopf lässt sich immer wieder anfassen', () => {
        const r = { x: 100, y: 100, w: 420, h: 600 };
        expect(verschiebe(r, -5000, -5000, 1920, 1080)).toMatchObject({ x: 80 - 420, y: 0 });
        expect(verschiebe(r, 5000, 5000, 1920, 1080)).toMatchObject({ x: 1920 - 80, y: 1080 - 80 });
        expect(verschiebe(r, 10, -20, 1920, 1080)).toEqual({ x: 110, y: 80, w: 420, h: 600 });
    });

    it('Größe ändern: nie kleiner als das Minimum und nie größer als der Bildschirm', () => {
        const r = { x: 100, y: 100, w: 420, h: 600 };
        expect(groesseRechts(r, -1000, -1000, 1920, 1080)).toMatchObject({ w: 320, h: 360, x: 100, y: 100 });
        expect(groesseRechts(r, 5000, 5000, 1920, 1080)).toMatchObject({ w: 1920, h: 1080 });
        expect(groesseRechts(r, 100, 50, 1920, 1080)).toMatchObject({ w: 520, h: 650 });
    });

    it('Ecke unten links: die rechte Kante bleibt stehen', () => {
        const r = { x: 1000, y: 100, w: 420, h: 600 };
        const neu = groesseLinks(r, -200, 100, 1920, 1080);
        expect(neu).toMatchObject({ x: 800, w: 620, h: 700, y: 100 });
        expect(neu.x + neu.w).toBe(1420);
        const eng = groesseLinks(r, 500, 0, 1920, 1080);
        expect(eng.w).toBe(320);
        expect(eng.x + eng.w).toBe(1420);
    });

    it('begrenze holt ein Fenster zurück, das nach einem Wechsel zu einem kleineren Bildschirm außerhalb liegt', () => {
        expect(begrenze({ x: 3000, y: 2000, w: 900, h: 900 }, 1280, 720)).toEqual({ x: 1200, y: 640, w: 900, h: 720 });
    });

    it('gespeicherte Werte werden geprüft', () => {
        expect(leseRahmen({ x: 1, y: 2, w: 3, h: 4 })).toEqual({ x: 1, y: 2, w: 3, h: 4 });
        expect(leseRahmen({ x: 1, y: 2, w: '3', h: 4 })).toBeNull();
        expect(leseRahmen(null)).toBeNull();
        expect(leseRahmen('x')).toBeNull();
        expect(leseRahmen({ x: NaN, y: 2, w: 3, h: 4 })).toBeNull();
    });
});
