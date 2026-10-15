import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import { ModuleSection } from '../../components/settings/sections/ModuleSection';
import { RequireWartungModul } from './RequireWartungModul';
import { RequireLagerModul } from './RequireLagerModul';
import { setzeFeaturesStand, setzeFeaturesZurueck, useLagerAktiv, useWartungAktiv, WIEDERHOLUNG_MS } from './useFeatures';

// Ob der angemeldete Benutzer Admin ist, steuert der Test über diesen Schalter
const anmeldung = vi.hoisted(() => ({ isAdmin: false }));
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ isAdmin: anmeldung.isAdmin, user: { id: 1 } }) }));

function json(status: number, body: unknown = {}): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

function stub(wartung: boolean | 'fehler', puts: boolean[] = []) {
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/features') return wartung === 'fehler' ? json(500, { message: 'kaputt' }) : json(200, { wartung });
        if (url === '/api/settings/features/wartung' && init?.method === 'PUT') {
            const aktiv = (JSON.parse(init.body as string) as { aktiv: boolean }).aktiv;
            puts.push(aktiv);
            return json(200, { wartung: aktiv });
        }
        return json(404, {});
    });
    vi.stubGlobal('fetch', fetcher);
    return fetcher;
}

beforeEach(() => { setzeFeaturesZurueck(); anmeldung.isAdmin = false; });
afterEach(() => { vi.unstubAllGlobals(); });

describe('Modulschalter: gemeinsamer Stand', () => {
    it('meldet erst "wird geprüft" (null) und dann den Stand, damit im Menü nichts aufblitzt', async () => {
        stub(true);
        const { result } = renderHook(() => useWartungAktiv());
        expect(result.current).toBeNull();
        await waitFor(() => expect(result.current).toBe(true));
    });

    it('fragt für viele Stellen gleichzeitig nur einmal nach', async () => {
        const fetcher = stub(false);
        renderHook(() => { useWartungAktiv(); useWartungAktiv(); });
        renderHook(() => useWartungAktiv());
        await waitFor(() => expect(fetcher.mock.calls.filter(([u]) => u === '/api/features')).toHaveLength(1));
    });

    it('bleibt nach einem Ladefehler unbekannt (verborgen) und versucht es nach kurzer Zeit noch einmal', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        try {
            let aufrufe = 0;
            const fetcher = vi.fn(async (url: string) => {
                if (url === '/api/features') return ++aufrufe === 1 ? json(500, { message: 'kaputt' }) : json(200, { wartung: true });
                return json(404, {});
            });
            vi.stubGlobal('fetch', fetcher);
            const { result } = renderHook(() => useWartungAktiv());
            await waitFor(() => expect(aufrufe).toBe(1));
            expect(result.current).toBeNull();

            await act(async () => { await vi.advanceTimersByTimeAsync(WIEDERHOLUNG_MS + 100); });
            await waitFor(() => expect(result.current).toBe(true));
            expect(aufrufe).toBe(2);
        } finally {
            vi.useRealTimers();
        }
    });

    it('übernimmt einen direkt gesetzten Stand und lädt nach dem Zurücksetzen neu', async () => {
        const fetcher = stub(true);
        const { result } = renderHook(() => useWartungAktiv());
        await waitFor(() => expect(result.current).toBe(true));

        act(() => setzeFeaturesStand({ wartung: false }));
        expect(result.current).toBe(false);

        fetcher.mockClear();
        act(() => setzeFeaturesZurueck());
        await waitFor(() => expect(result.current).toBe(true));
        expect(fetcher.mock.calls.filter(([u]) => u === '/api/features')).toHaveLength(1);
    });
});

describe('RequireWartungModul', () => {
    const seite = () => render(
        <MemoryRouter><RequireWartungModul><p>Wartungsseite</p></RequireWartungModul></MemoryRouter>,
    );

    it('zeigt die Seite, solange das Modul eingeschaltet ist, und währenddessen noch nichts anderes als "wird geladen"', async () => {
        stub(true);
        seite();
        expect(screen.getByRole('status')).toHaveTextContent('Wird geladen');
        expect(screen.queryByText('Wartungsseite')).toBeNull();
        expect(await screen.findByText('Wartungsseite')).toBeVisible();
    });

    it('zeigt bei ausgeschaltetem Modul einen klaren Hinweis statt der Seite und sagt, dass nichts gelöscht ist', async () => {
        stub(false);
        seite();
        expect(await screen.findByText('Das Wartungsmodul ist ausgeschaltet.')).toBeVisible();
        expect(screen.getByText(/nicht gelöscht/)).toBeVisible();
        expect(screen.queryByText('Wartungsseite')).toBeNull();
        expect(screen.getByText(/Ein Administrator kann das Modul wieder einschalten/)).toBeVisible();
        expect(screen.queryByRole('link', { name: 'Zu den Einstellungen' })).toBeNull();
    });

    it('führt einen Admin direkt zu den Einstellungen', async () => {
        anmeldung.isAdmin = true;
        stub(false);
        seite();
        expect(await screen.findByRole('link', { name: 'Zu den Einstellungen' })).toHaveAttribute('href', '/einstellungen#module');
    });
});

describe('ModuleSection (Einstellungen)', () => {
    const bereich = () => render(<ToastProvider><ConfirmProvider><ModuleSection /></ConfirmProvider></ToastProvider>);

    it('zeigt den Stand und schaltet nach Rückfrage aus, ohne etwas zu löschen', async () => {
        const puts: boolean[] = [];
        stub(true, puts);
        bereich();
        const schalter = await screen.findByRole('switch', { name: 'Wartung' });
        await waitFor(() => expect(schalter).toBeEnabled());
        expect(schalter).toHaveAttribute('aria-checked', 'true');

        await userEvent.click(schalter);
        expect(await screen.findByText('Wartungsmodul ausschalten?')).toBeVisible();
        expect(screen.getByText(/werden nicht gelöscht/)).toBeVisible();
        expect(puts).toEqual([]);
        await userEvent.click(screen.getByRole('button', { name: 'Ausschalten' }));

        await waitFor(() => expect(schalter).toHaveAttribute('aria-checked', 'false'));
        expect(puts).toEqual([false]);
        expect(await screen.findByText('Das Wartungsmodul ist ausgeschaltet. Die Daten bleiben erhalten.')).toBeVisible();
    });

    it('schaltet ohne Rückfrage wieder ein', async () => {
        const puts: boolean[] = [];
        stub(false, puts);
        bereich();
        const schalter = await screen.findByRole('switch', { name: 'Wartung' });
        await waitFor(() => expect(schalter).toBeEnabled());
        expect(schalter).toHaveAttribute('aria-checked', 'false');

        await userEvent.click(schalter);

        await waitFor(() => expect(schalter).toHaveAttribute('aria-checked', 'true'));
        expect(puts).toEqual([true]);
        expect(screen.queryByText('Wartungsmodul ausschalten?')).toBeNull();
    });

    it('ändert nichts, wenn die Rückfrage abgebrochen wird', async () => {
        const puts: boolean[] = [];
        stub(true, puts);
        bereich();
        const schalter = await screen.findByRole('switch', { name: 'Wartung' });
        await waitFor(() => expect(schalter).toBeEnabled());
        await userEvent.click(schalter);
        await userEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }));

        expect(schalter).toHaveAttribute('aria-checked', 'true');
        expect(puts).toEqual([]);
    });

    it('zeigt die Meldung des Servers und lässt den Schalter stehen, wenn das Umschalten scheitert', async () => {
        const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
            if (url === '/api/features') return json(200, { wartung: false });
            if (init?.method === 'PUT') return json(403, { message: 'Dafür fehlt die Berechtigung.' });
            return json(404, {});
        });
        vi.stubGlobal('fetch', fetcher);
        bereich();
        const schalter = await screen.findByRole('switch', { name: 'Wartung' });
        await waitFor(() => expect(schalter).toBeEnabled());
        await userEvent.click(schalter);

        expect(await screen.findByText('Dafür fehlt die Berechtigung.')).toBeVisible();
        expect(schalter).toHaveAttribute('aria-checked', 'false');
    });

    it('sperrt den Schalter, solange der Stand noch geladen wird', () => {
        vi.stubGlobal('fetch', vi.fn(() => new Promise(() => undefined)));
        bereich();
        expect(screen.getByRole('switch', { name: 'Wartung' })).toBeDisabled();
        expect(screen.getAllByText('Wird geladen …')).toHaveLength(4);
    });
});

describe('Lagermodul', () => {
    it('gilt ohne Angabe im Server-Stand (älterer Server) als eingeschaltet', async () => {
        stub(true);
        const { result } = renderHook(() => useLagerAktiv());
        expect(result.current).toBeNull();
        await waitFor(() => expect(result.current).toBe(true));
    });

    it('wird ausgeblendet, wenn der Server "lager: false" meldet', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json(200, { wartung: true, lager: false })));
        const { result } = renderHook(() => useLagerAktiv());
        await waitFor(() => expect(result.current).toBe(false));
    });

    it('RequireLagerModul zeigt bei ausgeschaltetem Modul den Hinweis statt der Seite', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json(200, { wartung: true, lager: false })));
        render(<MemoryRouter><RequireLagerModul><p>Lagerseite</p></RequireLagerModul></MemoryRouter>);
        expect(await screen.findByText('Das Lagermodul ist ausgeschaltet.')).toBeVisible();
        expect(screen.queryByText('Lagerseite')).toBeNull();
    });

    it('der Schalter in den Einstellungen sendet an /api/settings/features/lager, ohne den Wartungsstand anzufassen', async () => {
        const puts: { url: string; aktiv: boolean }[] = [];
        vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
            if (url === '/api/features') return json(200, { wartung: true, lager: true });
            if (init?.method === 'PUT') {
                const aktiv = (JSON.parse(init.body as string) as { aktiv: boolean }).aktiv;
                puts.push({ url, aktiv });
                return json(200, { wartung: true, lager: aktiv });
            }
            return json(404, {});
        }));
        render(<ToastProvider><ConfirmProvider><ModuleSection /></ConfirmProvider></ToastProvider>);
        const schalter = await screen.findByRole('switch', { name: 'Lager' });
        await waitFor(() => expect(schalter).toBeEnabled());
        await userEvent.click(schalter);
        expect(await screen.findByText('Lagermodul ausschalten?')).toBeVisible();
        await userEvent.click(screen.getByRole('button', { name: 'Ausschalten' }));

        await waitFor(() => expect(schalter).toHaveAttribute('aria-checked', 'false'));
        expect(puts).toEqual([{ url: '/api/settings/features/lager', aktiv: false }]);
        expect(screen.getByRole('switch', { name: 'Wartung' })).toHaveAttribute('aria-checked', 'true');
    });
});
