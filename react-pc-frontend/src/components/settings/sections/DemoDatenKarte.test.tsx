import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DemoDatenKarte, entferntText } from './DemoDatenKarte';
import { ToastProvider } from '../../ui/toast';
import { ConfirmProvider } from '../../ui/confirm-dialog';

/** Nur Dummy-Daten. */
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
let fetcher: ReturnType<typeof vi.fn>;
let vorhanden: boolean;
let entfernt = 0;
const zeige = () => render(<ToastProvider><ConfirmProvider><DemoDatenKarte /></ConfirmProvider></ToastProvider>);

beforeEach(() => {
    vorhanden = true;
    entfernt = 0;
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url) === '/api/demo/status') return json({ vorhanden });
        if (String(url) === '/api/demo/daten' && init?.method === 'DELETE') { entfernt++; vorhanden = false; return json({ mitarbeiter: 8, kunden: 6, lieferanten: 3, leistungen: 7, auftraege: 8, termine: 13 }); }
        return json({}, 404);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('DemoDatenKarte', { timeout: 30000 }, () => {
    it('bietet das Entfernen an und verweist auf die fertigen Metallbau-Leistungen', async () => {
        zeige();

        expect(await screen.findByRole('button', { name: 'Demo-Daten entfernen' })).toBeEnabled();
        expect(screen.getByText(/fertige Leistungen für Metallbau/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /laden/i })).not.toBeInTheDocument();
    });

    it('entfernt erst nach der Rückfrage und nennt, was entfernt wurde', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Demo-Daten entfernen' }));
        expect(await screen.findByText(/Das lässt sich nicht rückgängig machen/)).toBeInTheDocument();
        expect(entfernt).toBe(0);
        await user.click((await screen.findAllByRole('button', { name: 'Demo-Daten entfernen' })).at(-1) as HTMLElement);

        await waitFor(() => expect(entfernt).toBe(1));
        expect(await screen.findByTestId('demo-entfernt')).toHaveTextContent('Entfernt: 8 Mitarbeiter, 6 Kunden, 3 Lieferanten, 7 Leistungen, 8 Aufträge, 13 Termine.');
        expect(screen.queryByRole('button', { name: 'Demo-Daten entfernen' })).not.toBeInTheDocument();
    });

    it('ohne Demo-Daten gibt es nichts zu entfernen', async () => {
        vorhanden = false;
        zeige();

        expect(await screen.findByTestId('keine-demo')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Demo-Daten entfernen' })).not.toBeInTheDocument();
    });

    it('ein Fehler beim Entfernen steht im Klartext da', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => (init?.method === 'DELETE' ? json({ message: 'Es wurde nichts entfernt.' }, 409) : json({ vorhanden: true })));
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Demo-Daten entfernen' }));
        await user.click((await screen.findAllByRole('button', { name: 'Demo-Daten entfernen' })).at(-1) as HTMLElement);

        expect(await screen.findByText('Es wurde nichts entfernt.')).toBeInTheDocument();
    });
});

describe('entferntText', () => {
    it('nennt nur, was entfernt wurde', () => {
        expect(entferntText({ mitarbeiter: 0, kunden: 1, lieferanten: 0, leistungen: 2, auftraege: 0, termine: 0 })).toBe('Entfernt: 1 Kunde, 2 Leistungen.');
        expect(entferntText({ mitarbeiter: 0, kunden: 0, lieferanten: 0, leistungen: 0, auftraege: 0, termine: 0 })).toBe('Es gab nichts zu entfernen.');
    });
});
