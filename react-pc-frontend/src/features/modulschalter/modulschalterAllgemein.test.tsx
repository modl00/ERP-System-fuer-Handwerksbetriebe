import { render, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import { ModuleSection } from '../../components/settings/sections/ModuleSection';
import { RequireModul } from './RequireModul';
import { setzeFeaturesZurueck, useModulAktiv } from './useFeatures';

const anmeldung = vi.hoisted(() => ({ isAdmin: false }));
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ isAdmin: anmeldung.isAdmin, user: { id: 1 } }) }));

const json = (status: number, body: unknown = {}) => ({ ok: status >= 200 && status < 300, status, json: async () => body }) as Response;

function stub(stand: Record<string, boolean>, puts: { url: string; aktiv: boolean }[] = []) {
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/features') return json(200, stand);
        if (url.startsWith('/api/settings/features/') && init?.method === 'PUT') {
            const aktiv = (JSON.parse(init.body as string) as { aktiv: boolean }).aktiv;
            puts.push({ url, aktiv });
            return json(200, { ...stand, [url.split('/').pop()!]: aktiv });
        }
        return json(404, {});
    });
    vi.stubGlobal('fetch', fetcher);
    return fetcher;
}

beforeEach(() => { setzeFeaturesZurueck(); anmeldung.isAdmin = false; });
afterEach(() => { vi.unstubAllGlobals(); });

describe('useModulAktiv', () => {
    it('meldet erst null, dann den Stand des jeweiligen Moduls', async () => {
        stub({ wartung: true, miete: false, website: true });
        const { result } = renderHook(() => ({ miete: useModulAktiv('miete'), website: useModulAktiv('website') }));
        expect(result.current).toEqual({ miete: null, website: null });
        await waitFor(() => expect(result.current).toEqual({ miete: false, website: true }));
    });

    it('gilt ohne Angabe (älterer Server) als eingeschaltet', async () => {
        stub({ wartung: true });
        const { result } = renderHook(() => useModulAktiv('miete'));
        await waitFor(() => expect(result.current).toBe(true));
    });
});

describe('RequireModul', () => {
    const seite = () => render(
        <MemoryRouter>
            <RequireModul modul="miete" titel="Mietabrechnung" ribbonCategory="Finanzen & Controlling" modulName="Das Mietabrechnungsmodul" erhalten="Die bisherigen Mietobjekte">
                <p>Mietseite</p>
            </RequireModul>
        </MemoryRouter>,
    );

    it('zeigt die Seite bei eingeschaltetem Modul', async () => {
        stub({ wartung: true, miete: true });
        seite();
        expect(screen.getByRole('status')).toHaveTextContent('Wird geladen');
        expect(await screen.findByText('Mietseite')).toBeVisible();
    });

    it('zeigt bei ausgeschaltetem Modul einen klaren Hinweis, nennt was erhalten bleibt und führt einen Admin zu den Einstellungen', async () => {
        anmeldung.isAdmin = true;
        stub({ wartung: true, miete: false });
        seite();
        expect(await screen.findByText('Das Mietabrechnungsmodul ist ausgeschaltet.')).toBeVisible();
        expect(screen.getByText(/Die bisherigen Mietobjekte sind nicht gelöscht/)).toBeVisible();
        expect(screen.queryByText('Mietseite')).toBeNull();
        expect(screen.getByRole('link', { name: 'Zu den Einstellungen' })).toHaveAttribute('href', '/einstellungen#module');
    });

    it('verweist Nicht-Admins auf einen Administrator', async () => {
        stub({ wartung: true, miete: false });
        seite();
        expect(await screen.findByText(/Ein Administrator kann das Modul wieder einschalten/)).toBeVisible();
        expect(screen.queryByRole('link', { name: 'Zu den Einstellungen' })).toBeNull();
    });
});

describe('ModuleSection: Mietabrechnung und Neuigkeiten', () => {
    const bereich = () => render(<ToastProvider><ConfirmProvider><ModuleSection /></ConfirmProvider></ToastProvider>);

    it('schaltet die Mietabrechnung nach Rückfrage über die allgemeine Adresse aus', async () => {
        const puts: { url: string; aktiv: boolean }[] = [];
        stub({ wartung: true, lager: true, miete: true, website: true }, puts);
        bereich();
        const schalter = await screen.findByRole('switch', { name: 'Mietabrechnung' });
        await waitFor(() => expect(schalter).toBeEnabled());
        expect(schalter).toHaveAttribute('aria-checked', 'true');

        await userEvent.click(schalter);
        expect(await screen.findByText('Mietabrechnungsmodul ausschalten?')).toBeVisible();
        expect(puts).toEqual([]);
        await userEvent.click(screen.getByRole('button', { name: 'Ausschalten' }));

        await waitFor(() => expect(schalter).toHaveAttribute('aria-checked', 'false'));
        expect(puts).toEqual([{ url: '/api/settings/features/miete', aktiv: false }]);
        expect(await screen.findByText('Das Mietabrechnungsmodul ist ausgeschaltet. Die Daten bleiben erhalten.')).toBeVisible();
        // die anderen Module bleiben unberührt
        expect(screen.getByRole('switch', { name: 'Neuigkeiten (Website)' })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('switch', { name: 'Lager' })).toHaveAttribute('aria-checked', 'true');
    });

    it('schaltet Neuigkeiten ohne Rückfrage wieder ein', async () => {
        const puts: { url: string; aktiv: boolean }[] = [];
        stub({ wartung: true, lager: true, miete: true, website: false }, puts);
        bereich();
        const schalter = await screen.findByRole('switch', { name: 'Neuigkeiten (Website)' });
        await waitFor(() => expect(schalter).toBeEnabled());
        expect(schalter).toHaveAttribute('aria-checked', 'false');

        await userEvent.click(schalter);

        await waitFor(() => expect(schalter).toHaveAttribute('aria-checked', 'true'));
        expect(puts).toEqual([{ url: '/api/settings/features/website', aktiv: true }]);
    });
});
