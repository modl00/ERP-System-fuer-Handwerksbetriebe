import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    BestellFehler, alsEingabe, alleGeliefert, bestelle, datum, geld, ladeBestellungen, legeAn, liefere, mengeText, pdfAdresse, storniere, sucheLieferanten,
    vorschlaegeProjekt, type BestellPosition,
} from './bestellwesenApi';

const antwort = (daten: unknown, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => daten }) as unknown as Response;

afterEach(() => { vi.unstubAllGlobals(); });

describe('bestellwesenApi', () => {
    it('Filter werden zur Adresse; ohne Filter keine Parameter', async () => {
        const f = vi.fn(async () => antwort([]));
        vi.stubGlobal('fetch', f);

        await ladeBestellungen();
        await ladeBestellungen({ stand: 'TEILWEISE', lieferantId: 3, projektId: 7, nurUeberfaellig: true });

        expect(f.mock.calls.map((c) => c[0])).toEqual([
            '/api/lieferanten-bestellungen',
            '/api/lieferanten-bestellungen?stand=TEILWEISE&lieferantId=3&projektId=7&nurUeberfaellig=true',
        ]);
    });

    it('Aktionen gehen als POST mit den richtigen Adressen und Inhalten', async () => {
        const f = vi.fn(async () => antwort({ id: 5 }));
        vi.stubGlobal('fetch', f);

        await legeAn({ lieferantId: 3, notiz: null, positionen: [] });
        await bestelle(5, 'EMAIL');
        await liefere(5, 9, true);
        await alleGeliefert(5);
        await storniere(5, 'Zu spät');
        await vorschlaegeProjekt(7);

        const aufrufe = f.mock.calls.map((c) => [c[0], (c[1] as RequestInit | undefined)?.method ?? 'GET', (c[1] as RequestInit | undefined)?.body]);
        expect(aufrufe).toEqual([
            ['/api/lieferanten-bestellungen', 'POST', JSON.stringify({ lieferantId: 3, notiz: null, positionen: [] })],
            ['/api/lieferanten-bestellungen/5/bestellen', 'POST', JSON.stringify({ per: 'EMAIL' })],
            ['/api/lieferanten-bestellungen/5/positionen/9/geliefert', 'POST', JSON.stringify({ geliefert: true })],
            ['/api/lieferanten-bestellungen/5/alles-geliefert', 'POST', undefined],
            ['/api/lieferanten-bestellungen/5/stornieren', 'POST', JSON.stringify({ grund: 'Zu spät' })],
            ['/api/lieferanten-bestellungen/vorschlaege/projekt/7', 'GET', undefined],
        ]);
        expect(pdfAdresse(5)).toBe('/api/lieferanten-bestellungen/5/pdf');
    });

    it('Fehler: Satz des Servers, sonst verständlicher Ersatz; ohne Netz Status 0', async () => {
        vi.stubGlobal('fetch', vi.fn()
            .mockResolvedValueOnce(antwort({ message: 'Diese Bestellung ist schon bestellt.' }, 409))
            .mockResolvedValueOnce(antwort({}, 403))
            .mockResolvedValueOnce({ ok: false, status: 500, json: async () => { throw new Error('leer'); } })
            .mockRejectedValueOnce(new TypeError('Failed to fetch')));

        const e1 = await bestelle(1, 'EMAIL').catch((e) => e);
        expect(e1).toBeInstanceOf(BestellFehler);
        expect(e1.message).toBe('Diese Bestellung ist schon bestellt.');
        expect(e1.status).toBe(409);
        expect((await bestelle(1, 'EMAIL').catch((e) => e)).message).toMatch(/Berechtigung/);
        expect((await bestelle(1, 'EMAIL').catch((e) => e)).message).toMatch(/noch einmal/);
        const offline = await bestelle(1, 'EMAIL').catch((e) => e);
        expect(offline.status).toBe(0);
        expect(offline.message).toMatch(/Verbindung/);
    });

    it('Lieferantensuche liefert Name und Ort, ohne Suchtext keinen q-Parameter', async () => {
        const f = vi.fn(async () => antwort({ lieferanten: [{ id: 3, lieferantenname: 'Stahlhandel Muster', ort: 'Musterstadt' }, { id: 4, lieferantenname: 'Schrauben Muster' }] }));
        vi.stubGlobal('fetch', f);

        const treffer = await sucheLieferanten('  stahl ');
        await sucheLieferanten('');

        expect(treffer).toEqual([{ id: 3, name: 'Stahlhandel Muster', ort: 'Musterstadt' }, { id: 4, name: 'Schrauben Muster', ort: null }]);
        expect(f.mock.calls.map((c) => c[0])).toEqual(['/api/lieferanten?size=20&q=stahl', '/api/lieferanten?size=20']);
    });

    it('Position wird wieder zur Eingabe (nur, was der Server braucht)', () => {
        const p: BestellPosition = { id: 1, artikelId: 10, artikelnummer: 'R-1', bezeichnung: 'Rohr', menge: 6.5, einheit: 'm', einzelpreis: 4.2, summe: 27.3, herkunft: 'PROJEKT',
            projektId: 7, projektName: 'Anbau', artikelInProjektId: 55, kommentar: 'Zuschnitt', geliefertAm: null };

        expect(alsEingabe(p)).toEqual({ artikelId: 10, menge: 6.5, einheit: 'm', einzelpreis: 4.2, herkunft: 'PROJEKT', projektId: 7, artikelInProjektId: 55, kommentar: 'Zuschnitt' });
    });

    it('Formatierung: Menge ohne überflüssige Stellen, Geld in Euro, Datum deutsch, Unsinn bleibt leer', () => {
        expect(mengeText(12.5)).toBe('12,5');
        expect(mengeText(200)).toBe('200');
        expect(mengeText(0.125)).toBe('0,125');
        expect(geld(null)).toBe('–');
        expect(geld(42.5)).toMatch(/42,50\s?€/);
        expect(datum('2026-10-05T09:30:00')).toBe('05.10.2026');
        expect(datum(null)).toBe('');
        expect(datum('kaputt')).toBe('');
    });
});
