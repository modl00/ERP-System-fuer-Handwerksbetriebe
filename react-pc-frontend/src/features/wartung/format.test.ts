import { describe, expect, it } from 'vitest';
import { ERGEBNIS_ANZEIGE, formatDatum, istPngDataUrl, istUeberfaellig, STATUS_ANZEIGE, tageBis } from './format';

describe('formatDatum', () => {
    it('schreibt ein ISO-Datum auf Deutsch', () => {
        expect(formatDatum('2026-03-09')).toBe('09.03.2026');
        expect(formatDatum('2026-12-31')).toBe('31.12.2026');
    });

    it('schneidet eine Uhrzeit ab', () => {
        expect(formatDatum('2026-03-09T14:30:00')).toBe('09.03.2026');
    });

    it('zeigt einen Strich, wenn kein Datum da ist', () => {
        expect(formatDatum(null)).toBe('–');
        expect(formatDatum(undefined)).toBe('–');
        expect(formatDatum('')).toBe('–');
    });

    it('gibt Unlesbares unverändert zurück statt "Invalid Date" oder einem erfundenen Datum', () => {
        expect(formatDatum('gestern')).toBe('gestern');
        expect(formatDatum('2026-02-31')).toBe('2026-02-31');
    });
});

describe('Fälligkeit', () => {
    it('erkennt überfällige Termine', () => {
        expect(istUeberfaellig('2026-05-01', '2026-05-02')).toBe(true);
        expect(istUeberfaellig('2026-05-02', '2026-05-02')).toBe(false);
        expect(istUeberfaellig('2026-05-03', '2026-05-02')).toBe(false);
        expect(istUeberfaellig(null, '2026-05-02')).toBe(false);
    });

    it('rechnet die Tage bis zur Fälligkeit, auch über Monats- und Jahresgrenzen', () => {
        expect(tageBis('2026-05-12', '2026-05-02')).toBe(10);
        expect(tageBis('2027-01-01', '2026-12-31')).toBe(1);
        expect(tageBis('2026-05-02', '2026-05-02')).toBe(0);
        expect(tageBis('2026-05-01', '2026-05-02')).toBe(-1);
    });

    it('rechnet über die Zeitumstellung hinweg richtig (ganze Tage)', () => {
        // Sommerzeit beginnt am 29.03.2026: der Tag hat nur 23 Stunden.
        expect(tageBis('2026-03-30', '2026-03-28')).toBe(2);
        // Winterzeit beginnt am 25.10.2026: der Tag hat 25 Stunden.
        expect(tageBis('2026-10-26', '2026-10-24')).toBe(2);
    });

    it('liefert null bei fehlendem oder ungültigem Datum', () => {
        expect(tageBis(null, '2026-05-02')).toBeNull();
        expect(tageBis('quatsch', '2026-05-02')).toBeNull();
        expect(tageBis('2026-05-02', 'quatsch')).toBeNull();
    });
});

describe('istPngDataUrl', () => {
    const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ';

    it('akzeptiert ein PNG als Data-URL', () => {
        expect(istPngDataUrl(PNG)).toBe(true);
    });

    it('lehnt alles andere ab: SVG, HTML, Skript-URLs, fremde Adressen, leer', () => {
        expect(istPngDataUrl('data:image/svg+xml;base64,PHN2Zy8+')).toBe(false);
        expect(istPngDataUrl('data:text/html;base64,PHNjcmlwdD4=')).toBe(false);
        expect(istPngDataUrl('javascript:alert(1)')).toBe(false);
        expect(istPngDataUrl('https://boese.example/x.png')).toBe(false);
        expect(istPngDataUrl('data:image/png;base64,')).toBe(false);
        expect(istPngDataUrl('')).toBe(false);
        expect(istPngDataUrl(null)).toBe(false);
        expect(istPngDataUrl(undefined)).toBe(false);
    });

    it('lehnt ein PNG-Etikett mit eingeschmuggelten Zeichen ab', () => {
        expect(istPngDataUrl('data:image/png;base64,AAAA" onerror="alert(1)')).toBe(false);
        expect(istPngDataUrl('data:image/png;base64,AAAA<script>')).toBe(false);
    });
});

describe('Anzeigetexte', () => {
    it('hat für jeden Status und jedes Ergebnis eine Beschriftung', () => {
        for (const status of ['OFFEN', 'IN_BEARBEITUNG', 'ABGESCHLOSSEN', 'ARCHIVIERT'] as const) {
            expect(STATUS_ANZEIGE[status].label).not.toBe('');
        }
        expect(ERGEBNIS_ANZEIGE.IO.kurz).toBe('i.O.');
        expect(ERGEBNIS_ANZEIGE.NIO.kurz).toBe('n.i.O.');
        expect(ERGEBNIS_ANZEIGE.NA.kurz).toBe('n.a.');
    });
});
