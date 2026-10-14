import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtikelArchiv } from './ArtikelArchiv';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';

let istAdmin = false;
vi.mock('../../auth/AuthContext', () => ({ useOptionalAuth: () => ({ isAdmin: istAdmin }) }));

/** Nur Dummy-Daten. */
const ARCHIVIERT = [{ id: 5, produktname: 'Rundrohr 42,4 x 2', archiviert: true }, { id: 6, produktname: 'Flachstahl 40 x 8', archiviert: true }];

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
let aufrufe: { url: string; methode: string }[];
let wiederherstellenAntwort: () => Response;

beforeEach(() => {
    aufrufe = [];
    wiederherstellenAntwort = () => new Response(null, { status: 204 });
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        const u = String(url);
        aufrufe.push({ url: u, methode: init?.method ?? 'GET' });
        if (u.includes('/wiederherstellen')) return wiederherstellenAntwort();
        if (u.includes('/verwendungen')) return json({ id: 5, name: 'Rundrohr 42,4 x 2', archiviert: true, projekte: [], lieferantenpreise: 0, dokumente: 0, varianten: 0, grundartikelName: null });
        if (u.includes('/filteroptionen') || u.includes('/werkstoffe')) return json({ herstellverfahren: [], fertigungszustand: [] });
        return json({ artikel: ARCHIVIERT, gesamt: 2, seite: 0, seitenGroesse: 12 });
    }));
});
afterEach(() => { vi.unstubAllGlobals(); });

function rendern(admin: boolean) {
    istAdmin = admin;
    render(
        <MemoryRouter><ToastProvider><ConfirmProvider><ArtikelArchiv /></ConfirmProvider></ToastProvider></MemoryRouter>,
    );
}

describe('ArtikelArchiv', () => {
    it('fragt nur die archivierten Artikel ab', async () => {
        rendern(false);

        expect(await screen.findByLabelText('Rundrohr 42,4 x 2 wiederherstellen')).toBeVisible();
        const liste = aufrufe.find(a => a.url.startsWith('/api/artikel?'))!;
        expect(new URLSearchParams(liste.url.split('?')[1]).get('archiv')).toBe('true');
    });

    it('stellt einen Artikel wieder her und lädt die Liste neu', async () => {
        const user = userEvent.setup();
        rendern(false);

        await user.click(await screen.findByLabelText('Rundrohr 42,4 x 2 wiederherstellen'));

        expect(await screen.findByText(/ist wiederhergestellt/)).toBeVisible();
        expect(aufrufe.filter(a => a.methode === 'POST')).toEqual([{ url: '/api/artikel/5/wiederherstellen', methode: 'POST' }]);
        await waitFor(() => expect(aufrufe.filter(a => a.url.startsWith('/api/artikel?')).length).toBeGreaterThan(1));
    });

    it('meldet einen Fehler beim Wiederherstellen', async () => {
        wiederherstellenAntwort = () => json({ message: 'Den Artikel gibt es nicht mehr.' }, 404);
        const user = userEvent.setup();
        rendern(false);

        await user.click(await screen.findByLabelText('Rundrohr 42,4 x 2 wiederherstellen'));

        expect(await screen.findByText('Den Artikel gibt es nicht mehr.')).toBeVisible();
    });

    it('endgültig löschen gibt es nur für Administratoren und sagt es sonst', async () => {
        rendern(false);
        await screen.findByLabelText('Rundrohr 42,4 x 2 wiederherstellen');

        expect(screen.queryByLabelText('Rundrohr 42,4 x 2 endgültig löschen')).not.toBeInTheDocument();
        expect(screen.getByText(/Endgültig löschen darf nur ein Administrator/)).toBeVisible();
    });

    it('ein Administrator öffnet das Fenster mit den Verweisen', async () => {
        const user = userEvent.setup();
        rendern(true);

        await user.click(await screen.findByLabelText('Rundrohr 42,4 x 2 endgültig löschen'));

        const dialog = await screen.findByRole('dialog', { name: 'Artikel endgültig löschen' });
        expect(await within(dialog).findByText('In keinem Projekt.')).toBeVisible();
        expect(screen.queryByText(/Endgültig löschen darf nur ein Administrator/)).not.toBeInTheDocument();
    });
});
