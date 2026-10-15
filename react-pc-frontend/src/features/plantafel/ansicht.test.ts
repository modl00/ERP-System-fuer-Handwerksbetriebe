import { describe, expect, it } from 'vitest';
import { ZEITSTUFEN, arbeitstageDelta, auftragsZeile, breiteJeTag, fortschritt, monatsBaender, neueGroesse, ordneAuftraege, pfeile, verdichteTermine, wichtigkeit } from './ansicht';
import type { Auftrag, BelegungEintrag, Vorgang } from './plantafelApi';

/** Nur Dummy-Daten. */
const vorgang = (id: number, extra: Partial<Vorgang> = {}): Vorgang => ({
    id, typId: 1, typName: 'Fertigung', farbe: '#be123c', bezeichnung: `Vorgang ${id}`, dauerTage: 2, dauerHerkunft: 'GESCHAETZT', festerStart: null, start: '2026-10-12', ende: '2026-10-13', konflikt: false,
    status: 'GEPLANT', notiz: null, vorgaenger: [], mitarbeiterIds: [4], ressourceId: null, prioritaet: 'NORMAL', aufwandStunden: 16, aufwandHerkunft: 'GESCHAETZT', fruehestensAb: null, rang: null,
    hinweis: null, wartet: false, buero: false, ohneZuweisungGeschaetzt: false, ...extra,
});
const auftrag = (projektId: number, extra: Partial<Auftrag> = {}, vorgaenge: Vorgang[] = [vorgang(projektId * 10)]): Auftrag => ({
    projektId, bezeichnung: `Auftrag ${projektId}`, auftragsnummer: null, startDatum: null, zielDatum: null, liefertermin: '2026-10-20', ampel: 'OK', verspaetungTage: null, vorgaenge, ...extra,
});
const termin = (id: string, von: string): BelegungEintrag => ({
    id, quelle: 'TERMIN', titel: 'Termin', untertitel: null, von, bis: von, vonZeit: null, bisZeit: null, farbe: '#64748b', projektId: null, prioritaet: null, fest: false, status: null, hinweis: null,
});

describe('Zeitstufen', () => {
    it('reichen von 7 Tagen bis zu zwei Jahren und werden mit der Länge schmaler', () => {
        expect(ZEITSTUFEN.map(z => z.tage)).toEqual([7, 14, 28, 91, 182, 364, 728]);
        const breiten = ZEITSTUFEN.map(z => z.breite);
        expect([...breiten].sort((a, b) => b - a)).toEqual(breiten);
        expect(breiteJeTag(728)).toBe(3);
        expect(breiteJeTag(999)).toBe(64);
    });
});

describe('ordneAuftraege', () => {
    const a1 = auftrag(1, { liefertermin: '2026-10-30', ampel: 'OK' });
    const a2 = auftrag(2, { liefertermin: '2026-10-20', ampel: 'VERSPAETET', verspaetungTage: 2 });
    const a3 = auftrag(3, { liefertermin: '2026-11-05', ampel: 'VERSPAETET', verspaetungTage: 9 }, [vorgang(30, { prioritaet: 'DRINGEND' })]);
    const fertig = auftrag(4, {}, [vorgang(40, { status: 'FERTIG' })]);
    const ohneTermine = auftrag(5, {}, [vorgang(50, { start: null, ende: null })]);

    it('sortiert nach Lieferzeit, Verspätung oder Wichtigkeit', () => {
        const alle = [a1, a2, a3];
        expect(ordneAuftraege(alle, 'LIEFERZEIT', false, false).map(a => a.projektId)).toEqual([2, 1, 3]);
        expect(ordneAuftraege(alle, 'VERSPAETUNG', false, false).map(a => a.projektId)).toEqual([3, 2, 1]);
        expect(ordneAuftraege(alle, 'WICHTIGKEIT', false, false).map(a => a.projektId)).toEqual([3, 2, 1]);
    });

    it('filtert verspätete, blendet erledigte und ungeplante Aufträge aus', () => {
        expect(ordneAuftraege([a1, a2, a3], 'LIEFERZEIT', true, false).map(a => a.projektId)).toEqual([2, 3]);
        expect(ordneAuftraege([a1, fertig, ohneTermine], 'LIEFERZEIT', false, false).map(a => a.projektId)).toEqual([1]);
        expect(ordneAuftraege([a1, fertig], 'LIEFERZEIT', false, true).map(a => a.projektId)).toEqual([4, 1]);
    });

    it('die Wichtigkeit zählt nur offene Vorgänge', () => {
        expect(wichtigkeit(a3)).toBe(0);
        expect(wichtigkeit(fertig)).toBe(9);
        expect(wichtigkeit(auftrag(6, {}, [vorgang(60, { prioritaet: 'DRINGEND', status: 'FERTIG' }), vorgang(61, { prioritaet: 'NICHT_DRINGEND' })]))).toBe(2);
    });
});

describe('auftragsZeile', () => {
    it('macht aus den Vorgängen Balken mit Hinweis bei Konflikt und bei fehlender Zuweisung', () => {
        const zeile = auftragsZeile(auftrag(7, {}, [vorgang(1, { konflikt: true }), vorgang(2, { mitarbeiterIds: [], hinweis: 'Max: Urlaub am 13.10.' }), vorgang(3, { start: null, ende: null })]));

        expect(zeile.schluessel).toBe('P7');
        expect(zeile.gruppe).toBe('ALLGEMEIN');
        expect(zeile.eintraege.map(e => e.id)).toEqual(['V1', 'V2']);
        expect(zeile.eintraege[0].hinweis).toMatch(/feste Start/);
        expect(zeile.eintraege[1].untertitel).toBe('noch niemand zugewiesen');
        expect(zeile.eintraege[1].hinweis).toBe('Max: Urlaub am 13.10.');
    });

    it('ein Wartevorgang braucht keine Zuweisung', () => {
        const zeile = auftragsZeile(auftrag(7, {}, [vorgang(1, { mitarbeiterIds: [], wartet: true, typName: 'Material' })]));
        expect(zeile.eintraege[0].untertitel).toBe('Material');
    });
});

describe('verdichteTermine', () => {
    it('fasst die Termine einer Woche zu einem Eintrag zusammen und lässt alles andere stehen', () => {
        const vorgangEintrag: BelegungEintrag = { ...termin('V1', '2026-10-14'), quelle: 'VORGANG' };
        const ergebnis = verdichteTermine([termin('T1', '2026-10-12'), termin('T2', '2026-10-16'), termin('T3', '2026-10-19'), vorgangEintrag]);

        expect(ergebnis.filter(e => e.quelle === 'VORGANG')).toHaveLength(1);
        const sammel = ergebnis.filter(e => e.quelle === 'TERMIN_SAMMEL');
        expect(sammel.map(e => [e.von, e.bis, e.titel])).toEqual([['2026-10-12', '2026-10-18', '2 Termine'], ['2026-10-19', '2026-10-25', '1 Termin']]);
    });
});

describe('monatsBaender', () => {
    it('teilt die Tage in Monate', () => {
        const baender = monatsBaender(new Date(2026, 9, 29), 6);
        expect(baender).toEqual([{ text: 'Oktober 2026', von: 0, anzahl: 3 }, { text: 'November 2026', von: 3, anzahl: 3 }]);
    });
});

describe('arbeitstageDelta', () => {
    it('zählt nur Arbeitstage, wenn das Ende gezogen wird', () => {
        // Freitag 16.10.2026: zwei Kalendertage weiter ist Sonntag (kein Arbeitstag), drei Tage weiter ist Montag
        expect(arbeitstageDelta('2026-10-16', 2)).toBe(0);
        expect(arbeitstageDelta('2026-10-16', 3)).toBe(1);
        expect(arbeitstageDelta('2026-10-16', 7)).toBe(5);
        expect(arbeitstageDelta('2026-10-16', 0)).toBe(0);
    });

    it('verkürzt nach links und beachtet eine andere Arbeitswoche', () => {
        // Mittwoch 14.10.: ein Tag nach links nimmt den Mittwoch weg
        expect(arbeitstageDelta('2026-10-14', -1)).toBe(-1);
        expect(arbeitstageDelta('2026-10-12', -2)).toBe(-1); // Montag weg, Sonntag war keiner
        expect(arbeitstageDelta('2026-10-16', 1, [1, 2, 3, 4, 5, 6])).toBe(1); // Samstag zählt in dieser Firma
    });
});

describe('neueGroesse', () => {
    it('ein Wartevorgang ändert die Dauer in Arbeitstagen (mindestens 1)', () => {
        expect(neueGroesse({ wartet: true, dauerTage: 5, aufwandStunden: null }, 2)).toMatchObject({ aenderung: { dauerTage: 7 }, probe: { dauerTage: 7 }, text: 'Dauer von 5 auf 7 Arbeitstage' });
        expect(neueGroesse({ wartet: true, dauerTage: 2, aufwandStunden: null }, -5)?.aenderung).toEqual({ dauerTage: 1 });
        expect(neueGroesse({ wartet: true, dauerTage: 1, aufwandStunden: null }, -1)).toBeNull();
    });

    it('ein Arbeitsvorgang ändert den Aufwand im Verhältnis zur Dauer, auf Viertelstunden gerundet', () => {
        expect(neueGroesse({ wartet: false, dauerTage: 5, aufwandStunden: 40 }, 2)).toMatchObject({ aenderung: { aufwandStunden: 56 }, text: 'Aufwand von 40 auf 56 Stunden' });
        expect(neueGroesse({ wartet: false, dauerTage: 3, aufwandStunden: 10 }, 1)?.aenderung.aufwandStunden).toBe(13.25);
        expect(neueGroesse({ wartet: false, dauerTage: 5, aufwandStunden: 7.5 }, -1)?.text).toBe('Aufwand von 7,5 auf 6 Stunden');
    });

    it('ohne Änderung oder ohne Aufwand gibt es nichts zu tun', () => {
        expect(neueGroesse({ wartet: false, dauerTage: 5, aufwandStunden: 40 }, 0)).toBeNull();
        expect(neueGroesse({ wartet: false, dauerTage: 5, aufwandStunden: null }, 2)).toBeNull();
        expect(neueGroesse({ wartet: false, dauerTage: 5, aufwandStunden: 0 }, 2)).toBeNull();
    });
});

describe('fortschritt', () => {
  it('ist der Anteil der gestempelten Stunden am Aufwand, höchstens 1', () => {
    expect(fortschritt({ aufwandStunden: 40, istStunden: 10 })).toBe(0.25);
    expect(fortschritt({ aufwandStunden: 40, istStunden: 80 })).toBe(1);
    expect(fortschritt({ aufwandStunden: 40, istStunden: 0 })).toBeNull();
    expect(fortschritt({ aufwandStunden: 40, istStunden: null })).toBeNull();
    expect(fortschritt({ aufwandStunden: null, istStunden: 5 })).toBeNull();
    expect(fortschritt({ aufwandStunden: 0, istStunden: 5 })).toBeNull();
  });
});

describe('pfeile', () => {
    const balken = new Map([[1, { x1: 0, x2: 100, y: 20 }], [2, { x1: 120, x2: 220, y: 20 }], [3, { x1: 90, x2: 150, y: 80 }]]);

    it('führt vom Ende des Vorgängers zum Beginn des Nachfolgers', () => {
        const p = pfeile(balken, new Map([[2, [1]]]));
        expect(p).toHaveLength(1);
        expect(p[0]).toMatchObject({ von: '1', nach: '2', x1: 100, y1: 20, x2: 120, y2: 20 });
        expect(p[0].pfad.startsWith('M 100 20')).toBe(true);
        expect(p[0].pfad.endsWith('H 120')).toBe(true);
    });

    it('läuft um den Balken herum, wenn der Nachfolger nicht weit genug rechts beginnt', () => {
        const p = pfeile(balken, new Map([[3, [1]]]));
        expect(p[0].pfad).toBe('M 100 20 H 107 V 50 H 83 V 80 H 90');
    });

    it('übergeht unbekannte Vorgänge und kennt mehrere Vorgänger', () => {
        expect(pfeile(balken, new Map([[2, [1, 99]], [77, [1]]]))).toHaveLength(1);
        expect(pfeile(balken, new Map([[3, [1, 2]]]))).toHaveLength(2);
    });
});
