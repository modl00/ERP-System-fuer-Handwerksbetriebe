import { describe, expect, it } from 'vitest';
import { hinweiseZurAuswahl, MAX_PROTOKOLLE } from './sammelbericht';
import { dateinameAusKopf } from './api';
import type { WartungListeneintrag } from './types';

function eintrag(teil: Partial<WartungListeneintrag>): WartungListeneintrag {
    return {
        id: 1, nummer: 'WA-2026-1001', titel: null, protokollTyp: 'Wartungsprotokoll', datum: '2026-05-10', naechsteWartung: null,
        status: 'ABGESCHLOSSEN', kundeId: 1, kundeName: 'Max Mustermann GmbH', objektId: 1, objektBezeichnung: 'Werk Nord',
        tuerId: 1, tuerBezeichnung: 'Tür 1.01', pruefpunkteGesamt: 10, pruefpunkteBewertet: 10, maengel: 0, ...teil,
    } as WartungListeneintrag;
}

describe('Hinweise zur Auswahl', () => {
    it('zählt Entwürfe, offene Punkte und Mängel getrennt', () => {
        const h = hinweiseZurAuswahl([
            eintrag({ id: 1 }),
            eintrag({ id: 2, status: 'IN_BEARBEITUNG', pruefpunkteBewertet: 4 }),
            eintrag({ id: 3, status: 'OFFEN', pruefpunkteBewertet: 0 }),
            eintrag({ id: 4, maengel: 2 }),
        ]);
        expect(h).toEqual({ anzahl: 4, entwuerfe: 2, mitOffenenPunkten: 2, mitMaengeln: 1, zuViele: false });
    });

    it('meldet zu viele Protokolle ab der Grenze', () => {
        const viele = Array.from({ length: MAX_PROTOKOLLE + 1 }, (_, i) => eintrag({ id: i + 1 }));
        expect(hinweiseZurAuswahl(viele.slice(0, MAX_PROTOKOLLE)).zuViele).toBe(false);
        expect(hinweiseZurAuswahl(viele).zuViele).toBe(true);
    });

    it('kommt mit einer leeren Auswahl zurecht', () => {
        expect(hinweiseZurAuswahl([])).toEqual({ anzahl: 0, entwuerfe: 0, mitOffenenPunkten: 0, mitMaengeln: 0, zuViele: false });
    });
});

describe('Dateiname aus dem Antwort-Kopf', () => {
    it('liest die UTF-8-Form mit Umlauten', () => {
        expect(dateinameAusKopf("inline; filename*=UTF-8''Wartungsprotokoll_M%C3%BCller_2026-05-10_%281_Protokolle%29.pdf", 'x.pdf'))
            .toBe('Wartungsprotokoll_Müller_2026-05-10_(1_Protokolle).pdf');
    });

    it('liest die einfache Form mit und ohne Anführungszeichen', () => {
        expect(dateinameAusKopf('inline; filename="Bericht.pdf"', 'x.pdf')).toBe('Bericht.pdf');
        expect(dateinameAusKopf('attachment; filename=Bericht.pdf', 'x.pdf')).toBe('Bericht.pdf');
    });

    it('fällt auf den Ersatznamen zurück, auch bei kaputter Kodierung', () => {
        expect(dateinameAusKopf(null, 'x.pdf')).toBe('x.pdf');
        expect(dateinameAusKopf('inline', 'x.pdf')).toBe('x.pdf');
        expect(dateinameAusKopf("inline; filename*=UTF-8''%E0%A4%A", 'x.pdf')).toBe('x.pdf');
    });
});
