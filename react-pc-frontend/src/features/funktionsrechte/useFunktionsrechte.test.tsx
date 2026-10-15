import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setzeFunktionsrechteZurueck, useDarf, WIEDERHOLUNG_MS } from './useFunktionsrechte';

const json = (status: number, body: unknown = {}) => ({ ok: status >= 200 && status < 300, status, json: async () => body }) as Response;

beforeEach(() => { setzeFunktionsrechteZurueck(); });
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

describe('useDarf', () => {
    it('meldet erst null, dann das Recht', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json(200, { LAGER: { ansehen: true, anlegen: false, aendern: false, loeschen: false } })));
        const { result } = renderHook(() => ({ sehen: useDarf('LAGER'), anlegen: useDarf('LAGER', 'ANLEGEN') }));
        expect(result.current).toEqual({ sehen: null, anlegen: null });
        await waitFor(() => expect(result.current).toEqual({ sehen: true, anlegen: false }));
    });

    it('gilt für eine fehlende Funktion als erlaubt', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json(200, {})));
        const { result } = renderHook(() => useDarf('WARTUNG', 'LOESCHEN'));
        await waitFor(() => expect(result.current).toBe(true));
    });

    it('fragt für viele Stellen nur einmal nach', async () => {
        const fetcher = vi.fn(async () => json(200, {}));
        vi.stubGlobal('fetch', fetcher);
        renderHook(() => { useDarf('LAGER'); useDarf('WARTUNG'); });
        renderHook(() => useDarf('LAGER', 'ANLEGEN'));
        await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
    });

    it('bleibt nach einem Fehler unbekannt (nichts wird ausgeblendet) und versucht es noch einmal', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        let n = 0;
        vi.stubGlobal('fetch', vi.fn(async () => (++n === 1 ? json(500) : json(200, { LAGER: { ansehen: false, anlegen: false, aendern: false, loeschen: false } }))));
        const { result } = renderHook(() => useDarf('LAGER'));
        await waitFor(() => expect(n).toBe(1));
        expect(result.current).toBeNull();

        await act(async () => { await vi.advanceTimersByTimeAsync(WIEDERHOLUNG_MS + 100); });
        await waitFor(() => expect(result.current).toBe(false));
    });

    it('lädt nach dem Zurücksetzen (Benutzerwechsel) neu', async () => {
        const fetcher = vi.fn(async () => json(200, {}));
        vi.stubGlobal('fetch', fetcher);
        const { result } = renderHook(() => useDarf('LAGER'));
        await waitFor(() => expect(result.current).toBe(true));
        act(() => setzeFunktionsrechteZurueck());
        await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2));
    });
});
