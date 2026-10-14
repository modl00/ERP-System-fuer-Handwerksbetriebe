import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { VariantenRaster } from './VariantenRaster';
import { ToastProvider } from '../ui/toast';

/** Nur Dummy-Daten. */
const VORGABE = { profilform: 'QUADRATROHR', form: 'Quadratrohr', achseA: 'Kantenlänge (mm)', hinweisA: 'zum Beispiel 20 25 30', aMitZweitemMass: false, achseB: 'Wandstärke (mm)', hinweisB: 'zum Beispiel 1,5 2 3' };
const KOMBIS = [
    { schluessel: '20|2', name: 'QH 20x20x2', artikelnummer: 'QH-20x20x2', status: 'NEU', grund: null, kgProMeter: 1.0495, kgProQm: null },
    { schluessel: '25|2', name: 'QH 25x25x2', artikelnummer: 'QH-25x25x2', status: 'SCHON_DA', grund: 'Gibt es schon.', kgProMeter: null, kgProQm: null },
    { schluessel: '15|4', name: 'QH 15x15x4', artikelnummer: 'QH-15x15x4', status: 'NICHT_MOEGLICH', grund: 'Die Wandstärke ist zu groß für diese Kantenlänge: Die Rundungen in den Ecken passen nicht mehr hinein.', kgProMeter: null, kgProQm: null },
    { schluessel: '30|2', name: 'QH 30x30x2', artikelnummer: 'QH-30x30x2', status: 'NEU', grund: null, kgProMeter: 1.7, kgProQm: null },
];

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
let aufrufe: { url: string; body?: Record<string, unknown> }[];
let serverFehler: string | null;

beforeEach(() => {
    aufrufe = [];
    serverFehler = null;
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        const u = String(url);
        const body = init?.body ? JSON.parse(String(init.body)) : undefined;
        aufrufe.push({ url: u, body });
        if (u.endsWith('/raster')) return json(VORGABE);
        if (serverFehler && init?.method === 'POST') return json({ message: serverFehler }, 400);
        return json(KOMBIS);
    }));
});
afterEach(() => { vi.unstubAllGlobals(); });

const rendern = (onAngelegt = vi.fn(), onAbbrechen = vi.fn()) => {
    render(<ToastProvider><VariantenRaster artikelId={7} onAngelegt={onAngelegt} onAbbrechen={onAbbrechen} /></ToastProvider>);
    return { onAngelegt, onAbbrechen };
};

describe('VariantenRaster', () => {
    it('beschriftet die Eingaben nach der Form und verlangt erst die Maße', async () => {
        rendern();

        expect(await screen.findByLabelText('Kantenlänge (mm)')).toHaveAttribute('placeholder', 'zum Beispiel 20 25 30');
        expect(screen.getByLabelText('Wandstärke (mm)')).toBeVisible();
        expect(screen.getByRole('button', { name: 'Vorschau' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Varianten anlegen' })).toBeDisabled();
    });

    it('zeigt die Vorschau mit Status je Kombination und legt nur die gewählten neuen an', async () => {
        const user = userEvent.setup();
        const { onAngelegt } = rendern();
        await user.type(await screen.findByLabelText('Kantenlänge (mm)'), '20 25 30');
        await user.type(screen.getByLabelText('Wandstärke (mm)'), '2');

        await user.click(screen.getByRole('button', { name: 'Vorschau' }));

        expect(await screen.findByTestId('varianten-vorschau')).toBeVisible();
        expect(screen.getByText('Gibt es schon')).toBeVisible();
        expect(screen.getByText(/Rundungen in den Ecken/)).toBeVisible();
        expect(screen.getByText('1,05 kg/m')).toBeVisible();
        expect(screen.getByRole('status')).toHaveTextContent('2 von 2 neuen Varianten werden angelegt.');
        expect(screen.queryByRole('checkbox', { name: 'QH 25x25x2 anlegen' })).not.toBeInTheDocument();

        await user.click(screen.getByRole('checkbox', { name: 'QH 20x20x2 anlegen' }));
        expect(screen.getByRole('status')).toHaveTextContent('1 von 2 neuen Varianten werden angelegt.');
        await user.click(screen.getByRole('button', { name: '1 Variante anlegen' }));

        await waitFor(() => expect(onAngelegt).toHaveBeenCalledWith(1));
        const post = aufrufe.filter(a => a.url.endsWith('/varianten') && a.body)[0];
        expect(post.body).toEqual({ a: ['20 25 30'], b: ['2'], ausgeschlossen: ['20|2'] });
        expect(await screen.findByText('1 Variante ist angelegt.')).toBeVisible();
    });

    it('verwirft die Vorschau, sobald sich die Eingabe ändert', async () => {
        const user = userEvent.setup();
        rendern();
        await user.type(await screen.findByLabelText('Kantenlänge (mm)'), '20');
        await user.type(screen.getByLabelText('Wandstärke (mm)'), '2');
        await user.click(screen.getByRole('button', { name: 'Vorschau' }));
        await screen.findByTestId('varianten-vorschau');

        await user.type(screen.getByLabelText('Kantenlänge (mm)'), '5');

        expect(screen.queryByTestId('varianten-vorschau')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Varianten anlegen' })).toBeDisabled();
    });

    it('zeigt die Meldung des Servers bei falscher Eingabe', async () => {
        const user = userEvent.setup();
        serverFehler = '„abc“ ist kein gültiges Maß für „Kantenlänge“.';
        rendern();
        await user.type(await screen.findByLabelText('Kantenlänge (mm)'), 'abc');
        await user.type(screen.getByLabelText('Wandstärke (mm)'), '2');

        await user.click(screen.getByRole('button', { name: 'Vorschau' }));

        expect(await screen.findAllByText(/kein gültiges Maß/)).not.toHaveLength(0);
        expect(screen.queryByTestId('varianten-vorschau')).not.toBeInTheDocument();
    });

    it('meldet, wenn es nichts Neues gibt, und sperrt das Anlegen', async () => {
        const user = userEvent.setup();
        vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL) => (String(url).endsWith('/raster') ? json(VORGABE) : json([KOMBIS[1]]))));
        rendern();
        await user.type(await screen.findByLabelText('Kantenlänge (mm)'), '25');
        await user.type(screen.getByLabelText('Wandstärke (mm)'), '2');

        await user.click(screen.getByRole('button', { name: 'Vorschau' }));

        expect(await screen.findByRole('status')).toHaveTextContent('Hier gibt es nichts Neues anzulegen.');
        expect(screen.getByRole('button', { name: 'Varianten anlegen' })).toBeDisabled();
    });

    it('bricht ab ohne etwas zu senden', async () => {
        const user = userEvent.setup();
        const { onAbbrechen } = rendern();
        await screen.findByLabelText('Kantenlänge (mm)');

        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));

        expect(onAbbrechen).toHaveBeenCalled();
        expect(aufrufe.filter(a => a.body)).toHaveLength(0);
    });

    it('meldet, wenn die Eingabe-Vorgabe nicht geladen werden kann', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'Dieser Artikel ist kein Grundartikel.' }, 400)));
        rendern();

        expect(await screen.findAllByText('Dieser Artikel ist kein Grundartikel.')).not.toHaveLength(0);
    });
});
