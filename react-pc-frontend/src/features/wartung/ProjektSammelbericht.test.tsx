import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjektSammelbericht, type ProjektBericht } from './ProjektSammelbericht';
import { ToastProvider } from '../../components/ui/toast';

/** Nur Dummy-Daten. */
const berichte: ProjektBericht[] = [
    { protokollId: 11, nummer: 'WA-2026-1002', datum: '2027-04-01', anlage: 'Heizungsanlage' },
    { protokollId: 10, nummer: 'WA-2026-1001', datum: '2026-10-01', anlage: 'Brandschutztür 1' },
];

let fetcher: ReturnType<typeof vi.fn>;
const pdf = () => ({ ok: true, status: 200, blob: async () => new Blob(['%PDF-1.4']), headers: new Headers({ 'Content-Disposition': 'inline; filename="Sammelbericht.pdf"' }) }) as Response;
const zeige = (b = berichte, titel = '') => render(<ToastProvider><ProjektSammelbericht berichte={b} titelVorschlag={titel} /></ToastProvider>);
const body = () => JSON.parse((fetcher.mock.calls.find(([u]) => u === '/api/wartung/sammelbericht/pdf')![1] as RequestInit).body as string);

beforeEach(() => {
    fetcher = vi.fn(async () => pdf());
    vi.stubGlobal('fetch', fetcher);
    vi.stubGlobal('open', vi.fn(() => null));
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('ProjektSammelbericht', () => {
    it('zeigt nichts, wenn das Projekt noch keinen Bericht hat', () => {
        zeige([]);
        expect(screen.queryByTestId('projekt-sammelbericht')).toBeNull();
    });

    it('wählt alle Berichte vor und speichert sie mit Titel und Deckblatt', async () => {
        const u = userEvent.setup();
        zeige(berichte, 'Wartung 2026');
        expect(screen.getByText('2 von 2 gewählt')).toBeInTheDocument();

        await u.click(screen.getByRole('button', { name: /Als PDF speichern/ }));

        await waitFor(() => expect(fetcher).toHaveBeenCalled());
        expect(body()).toEqual({ protokollIds: [11, 10], titel: 'Wartung 2026', mitDeckblatt: true });
    });

    it('lässt abgewählte Berichte und das Deckblatt weg', async () => {
        const u = userEvent.setup();
        zeige();
        await u.click(screen.getByRole('checkbox', { name: /Heizungsanlage/ }));
        await u.click(screen.getByRole('checkbox', { name: /Mit Deckblatt/ }));
        expect(screen.getByText('1 von 2 gewählt')).toBeInTheDocument();

        await u.click(screen.getByRole('button', { name: /Als PDF speichern/ }));

        await waitFor(() => expect(fetcher).toHaveBeenCalled());
        expect(body()).toMatchObject({ protokollIds: [10], mitDeckblatt: false });
    });

    it('sperrt die Knöpfe, wenn nichts gewählt ist', async () => {
        const u = userEvent.setup();
        zeige();
        await u.click(screen.getByRole('checkbox', { name: /Heizungsanlage/ }));
        await u.click(screen.getByRole('checkbox', { name: /Brandschutztür 1/ }));
        expect(screen.getByRole('button', { name: /Sammelbericht öffnen/ })).toBeDisabled();
        expect(screen.getByRole('button', { name: /Als PDF speichern/ })).toBeDisabled();
    });

    it('zeigt den Fehler des Servers, wenn der Bericht nicht entsteht', async () => {
        const u = userEvent.setup();
        fetcher.mockResolvedValue({ ok: false, status: 400, json: async () => ({ message: 'Protokoll fehlt.' }), headers: new Headers() } as Response);
        zeige();
        await u.click(screen.getByRole('button', { name: /Als PDF speichern/ }));
        expect(await screen.findByText(/Protokoll fehlt|konnte nicht erstellt/)).toBeInTheDocument();
    });
});
