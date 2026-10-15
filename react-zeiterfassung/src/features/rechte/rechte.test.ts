import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { darf, ladeRechte, useDarf, vergissRechte, type AppRechte } from './rechte';

const ALLES = { ansehen: true, anlegen: true, aendern: true, loeschen: true };
const json = (status: number, body: unknown = {}) => ({ ok: status >= 200 && status < 300, status, json: async () => body }) as Response;

beforeEach(() => { localStorage.clear(); vergissRechte(); });
afterEach(() => { vi.unstubAllGlobals(); });

describe('darf', () => {
    const rechte: AppRechte = { funktionen: { LAGER: { ansehen: true, anlegen: true, aendern: false, loeschen: false }, WARTUNG: ALLES }, module: { lager: true, wartung: false } };

    it('ist ohne bekannte Rechte immer nein', () => {
        expect(darf(null, 'LAGER')).toBe(false);
    });

    it('wertet Funktion und Aktion aus', () => {
        expect(darf(rechte, 'LAGER', 'ANSEHEN')).toBe(true);
        expect(darf(rechte, 'LAGER', 'ANLEGEN')).toBe(true);
        expect(darf(rechte, 'LAGER', 'AENDERN')).toBe(false);
        expect(darf(rechte, 'LAGER', 'LOESCHEN')).toBe(false);
    });

    it('ist bei ausgeschaltetem Modul nein, auch wenn die Funktion erlaubt ist', () => {
        expect(darf(rechte, 'WARTUNG')).toBe(false);
    });

    it('gilt für eine fehlende Funktion als erlaubt (wie ohne Einschränkung)', () => {
        expect(darf({ funktionen: {}, module: {} }, 'LAGER', 'LOESCHEN')).toBe(true);
    });
});

describe('ladeRechte', () => {
    it('lädt mit dem Zugangslink, speichert den Stand und meldet ihn den Seiten', async () => {
        const fetcher = vi.fn(async () => json(200, { funktionen: { LAGER: ALLES }, module: { lager: true, wartung: true } }));
        vi.stubGlobal('fetch', fetcher);
        const { result } = renderHook(() => useDarf('LAGER', 'LOESCHEN'));
        expect(result.current).toBe(false);

        await act(async () => { await ladeRechte('abc/def'); });

        expect(fetcher).toHaveBeenCalledWith('/api/zeiterfassung/rechte/abc%2Fdef');
        expect(result.current).toBe(true);
        expect(JSON.parse(localStorage.getItem('zeiterfassung_rechte')!).funktionen.LAGER).toEqual(ALLES);
    });

    it('behält den gespeicherten Stand, wenn das Netz fehlt oder der Server einen Fehler meldet', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json(200, { funktionen: { LAGER: ALLES }, module: {} })));
        await ladeRechte('t');
        const { result } = renderHook(() => useDarf('LAGER'));
        expect(result.current).toBe(true);

        vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('offline'); }));
        await act(async () => { await ladeRechte('t'); });
        expect(result.current).toBe(true);

        vi.stubGlobal('fetch', vi.fn(async () => json(500)));
        await act(async () => { await ladeRechte('t'); });
        expect(result.current).toBe(true);
    });

    it('leert den Stand bei ungültigem Zugang (401)', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json(200, { funktionen: { LAGER: ALLES }, module: {} })));
        await ladeRechte('t');
        const { result } = renderHook(() => useDarf('LAGER'));
        expect(result.current).toBe(true);

        vi.stubGlobal('fetch', vi.fn(async () => json(401)));
        await act(async () => { await ladeRechte('t'); });

        expect(result.current).toBe(false);
        expect(localStorage.getItem('zeiterfassung_rechte')).toBeNull();
    });

    it('ignoriert eine unerwartete Antwort', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json(200, { irgendwas: 1 })));
        await ladeRechte('t');
        expect(localStorage.getItem('zeiterfassung_rechte')).toBeNull();
    });

    it('vergessen leert Speicher und Stand (Abmelden)', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json(200, { funktionen: { LAGER: ALLES }, module: {} })));
        await ladeRechte('t');
        const { result } = renderHook(() => useDarf('LAGER'));
        expect(result.current).toBe(true);

        act(() => vergissRechte());

        expect(result.current).toBe(false);
        expect(localStorage.getItem('zeiterfassung_rechte')).toBeNull();
    });
});
