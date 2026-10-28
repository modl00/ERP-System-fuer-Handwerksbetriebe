import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KiWunschKarte } from './KiWunschKarte';
import { ToastProvider } from '../../ui/toast';

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });
const stand = (teil: Record<string, unknown> = {}) => ({ adresse: 'http://192.168.1.20:3050', repository: 'M/ERP', tokenGesetzt: true, verschluesselt: false, bereit: true, ...teil });

let fetcher: ReturnType<typeof vi.fn>;
let aktuell: Record<string, unknown>;
const aufrufe = (methode: string, teil = '') => fetcher.mock.calls.filter(([u, i]) => (i?.method ?? 'GET') === methode && String(u).includes(teil));

beforeEach(() => {
    aktuell = stand();
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url).endsWith('/test')) return antwort({ ok: true, meldung: 'Die Verbindung klappt: Das Repository M/ERP ist erreichbar.' });
        if (init?.method === 'PUT') return antwort(aktuell);
        return antwort(aktuell);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><KiWunschKarte /></ToastProvider>);

describe('KiWunschKarte', { timeout: 30000 }, () => {
    it('zeigt Adresse und Repository, den Schlüssel nie, und sagt ehrlich, ob er verschlüsselt liegt', async () => {
        zeige();
        expect(await screen.findByLabelText('Adresse von Gitea')).toHaveValue('http://192.168.1.20:3050');
        expect(screen.getByLabelText('Repository')).toHaveValue('M/ERP');
        expect(screen.getByLabelText(/Zugangsschlüssel/)).toHaveValue('');
        expect(screen.getByText(/unverschlüsselt in den Einstellungen/)).toBeVisible();
    });

    it('mit eingerichtetem Server-Schlüssel steht „verschlüsselt gespeichert" da', async () => {
        aktuell = stand({ verschluesselt: true });
        zeige();
        expect(await screen.findByText(/verschlüsselt gespeichert/)).toBeVisible();
    });

    it('speichert Adresse, Repository und einen neuen Schlüssel; ohne neuen Schlüssel wird keiner gesendet (null)', async () => {
        const user = userEvent.setup();
        zeige();
        const adresse = await screen.findByLabelText('Adresse von Gitea');
        await user.clear(adresse);
        await user.type(adresse, 'http://nas:3050');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(aufrufe('PUT')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT')[0][1].body)).toEqual({ adresse: 'http://nas:3050', repository: 'M/ERP', token: null });

        await user.type(screen.getByLabelText(/Zugangsschlüssel/), 'dummy-token-1234567890');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(aufrufe('PUT')).toHaveLength(2));
        expect(JSON.parse(aufrufe('PUT')[1][1].body).token).toBe('dummy-token-1234567890');
    });

    it('einen gespeicherten Schlüssel kann man entfernen (leerer Text wird gesendet)', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByLabelText('Gespeicherten Schlüssel entfernen'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(aufrufe('PUT')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT')[0][1].body).token).toBe('');
    });

    it('getestet wird nur der gespeicherte Stand, und das Ergebnis steht in Klartext da', async () => {
        const user = userEvent.setup();
        zeige();
        const knopf = await screen.findByRole('button', { name: /Verbindung testen/ });
        expect(knopf).toBeEnabled();
        await user.click(knopf);
        expect(await screen.findByRole('status')).toHaveTextContent('Die Verbindung klappt');
        expect(aufrufe('POST', '/test')).toHaveLength(1);

        await user.type(screen.getByLabelText('Repository'), 'x');
        expect(screen.getByRole('button', { name: /Verbindung testen/ })).toBeDisabled();
    });

    it('ohne vollständigen Zugang ist der Test gesperrt', async () => {
        aktuell = stand({ tokenGesetzt: false, bereit: false });
        zeige();
        expect(await screen.findByRole('button', { name: /Verbindung testen/ })).toBeDisabled();
    });
});
