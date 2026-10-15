import { afterEach, describe, expect, it, vi } from 'vitest';
import { aufbewahrungText, auskunft, ereignisText, heute, ladeImSystem, ladeMeinenTag, loescheVon, speichereEinstellungen, tagVerschieben, uhrzeit } from './aktivitaetApi';

const antwort = (daten: unknown, status = 200) => ({ ok: status < 300, status, json: async () => daten }) as unknown as Response;

afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

describe('aktivitaetApi', () => {
    it('Adressen und Filter: Tag, Objektart, Art, Zeitraum; ohne Filter keine Parameter', async () => {
        const f = vi.fn(async () => antwort([]));
        vi.stubGlobal('fetch', f);

        await ladeMeinenTag('2026-10-03');
        await ladeImSystem({});
        await ladeImSystem({ objektTyp: 'Projekt', art: 'VERSENDET', tage: 7, limit: 50 });
        await auskunft(2);
        await loescheVon(2);
        await speichereEinstellungen({ mitarbeiterAktiv: true, aufbewahrungTage: 0 });

        expect(f.mock.calls.map((c) => [c[0], (c[1] as RequestInit | undefined)?.method ?? 'GET'])).toEqual([
            ['/api/aktivitaet/mein?datum=2026-10-03', 'GET'],
            ['/api/admin/aktivitaet', 'GET'],
            ['/api/admin/aktivitaet?objektTyp=Projekt&art=VERSENDET&tage=7&limit=50', 'GET'],
            ['/api/admin/aktivitaet/person/2', 'GET'],
            ['/api/admin/aktivitaet/person/2', 'DELETE'],
            ['/api/admin/aktivitaet/einstellungen', 'PUT'],
        ]);
        expect(JSON.parse((f.mock.calls[5][1] as RequestInit).body as string)).toEqual({ mitarbeiterAktiv: true, aufbewahrungTage: 0 });
    });

    it('Fehler: Satz des Servers, 403 als Hinweis für Administratoren, ohne Netz ein Satz zur Verbindung', async () => {
        vi.stubGlobal('fetch', vi.fn()
            .mockResolvedValueOnce(antwort({ message: 'Die Aufbewahrung muss zwischen 0 und 36500 Tagen liegen.' }, 400))
            .mockResolvedValueOnce(antwort({}, 403))
            .mockResolvedValueOnce({ ok: false, status: 500, json: async () => { throw new Error('leer'); } })
            .mockRejectedValueOnce(new TypeError('Failed to fetch')));

        await expect(speichereEinstellungen({ aufbewahrungTage: -1 })).rejects.toThrow('36500');
        await expect(ladeImSystem({})).rejects.toThrow('nur für Administratoren');
        await expect(ladeImSystem({})).rejects.toThrow('noch einmal');
        await expect(ladeImSystem({})).rejects.toThrow('Verbindung');
    });

    it('Anzeigesatz: mit Namen, sonst mit Art und Nummer, sonst nur die Handlung', () => {
        expect(ereignisText({ text: 'Projekt geändert', objektName: '2026/014 Anbau Halle', objektId: 7, objektTyp: 'Projekt' })).toBe('Projekt geändert: 2026/014 Anbau Halle');
        expect(ereignisText({ text: 'Anfrage gelöscht', objektName: null, objektId: 12, objektTyp: 'Anfrage' })).toBe('Anfrage gelöscht (Anfrage 12)');
        expect(ereignisText({ text: 'E-Mail gesendet', objektName: null, objektId: null, objektTyp: null })).toBe('E-Mail gesendet');
    });

    it('Tage verschieben in Ortszeit, über Monats- und Jahresgrenzen; heute im Format JJJJ-MM-TT', () => {
        expect(tagVerschieben('2026-10-01', -1)).toBe('2026-09-30');
        expect(tagVerschieben('2026-12-31', 1)).toBe('2027-01-01');
        expect(tagVerschieben('2026-03-01', -1)).toBe('2026-02-28');
        expect(heute()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 9, 5, 23, 59));
        expect(heute()).toBe('2026-10-05');
    });

    it('Uhrzeit und Aufbewahrung als Text; ungültige Zeit bleibt leer', () => {
        expect(uhrzeit('2026-10-03T08:05:00')).toMatch(/08:05/);
        expect(uhrzeit('kaputt')).toBe('');
        expect(aufbewahrungText(0)).toBe('unbegrenzt');
        expect(aufbewahrungText(1)).toBe('1 Tag');
        expect(aufbewahrungText(365)).toBe('365 Tage');
    });
});
