import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtikelKurzDialog } from './ArtikelKurzDialog';
import { ToastProvider } from '../ui/toast';

vi.mock('../TiptapEditor', () => ({
    TiptapEditor: ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
        <textarea data-testid="tiptap" value={value} onChange={e => onChange(e.target.value)} />
    ),
}));

const detail = {
    artikel: {
        id: 7, produktname: 'Rundrohr 42,4 x 2', artikelnummer: 'ST-RR-042', ean: '4006381333931', werkstoffName: 'Baustahl', abmessung: '42,4 x 2',
        verrechnungseinheit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' }, guenstigsterPreis: 5.75,
        kurzbeschreibung: 'Handlauf Lager', beschreibung: '<p>Rundrohr</p>', verkaufsaufschlagProzent: 40,
    },
    lieferanten: [], preisverlauf: [],
};
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });
let fetcher: ReturnType<typeof vi.fn>;

beforeEach(() => {
    fetcher = vi.fn(async (url: string, init?: RequestInit) => (init?.method === 'PATCH' ? antwort({ id: 7 }) : antwort(detail)));
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (artikelId: number | null = 7, onSchliessen = vi.fn(), onGespeichert = vi.fn()) => {
    render(<ToastProvider><MemoryRouter><ArtikelKurzDialog artikelId={artikelId} onSchliessen={onSchliessen} onGespeichert={onGespeichert} /></MemoryRouter></ToastProvider>);
    return { onSchliessen, onGespeichert };
};

describe('ArtikelKurzDialog', () => {
    it('zeigt die wichtigsten Daten, die Texte und den Link zur Artikelseite', async () => {
        zeige();
        expect(await screen.findByText('ST-RR-042')).toBeVisible();
        expect(screen.getByText('4006381333931')).toBeVisible();
        expect(screen.getByText('Laufende Meter')).toBeVisible();
        expect(screen.getByLabelText('Kurzbeschreibung (intern)')).toHaveValue('Handlauf Lager');
        expect(screen.getByRole('link', { name: /Zur Artikelseite/ })).toHaveAttribute('href', '/artikel/7');
    });

    it('speichert nur die beiden Texte und lässt den Aufschlag unberührt', async () => {
        const user = userEvent.setup();
        const { onSchliessen, onGespeichert } = zeige();
        const feld = await screen.findByLabelText('Kurzbeschreibung (intern)');
        await user.clear(feld);
        await user.type(feld, 'Handlauf Rundrohr');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(onGespeichert).toHaveBeenCalled());
        const patch = fetcher.mock.calls.find(([, i]) => i?.method === 'PATCH')!;
        expect(patch[0]).toBe('/api/artikel/7/dokumenttexte');
        expect(JSON.parse(patch[1].body)).toEqual({ kurzbeschreibung: 'Handlauf Rundrohr', beschreibung: '<p>Rundrohr</p>' });
        expect(onSchliessen).toHaveBeenCalled();
    });

    it('sendet eine geleerte Beschreibung als null', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByLabelText('Kurzbeschreibung (intern)');
        await user.clear(screen.getByTestId('tiptap'));
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        await waitFor(() => expect(fetcher.mock.calls.some(([, i]) => i?.method === 'PATCH')).toBe(true));
        expect(JSON.parse(fetcher.mock.calls.find(([, i]) => i?.method === 'PATCH')![1].body).beschreibung).toBeNull();
    });

    it('meldet einen unbekannten Artikel und bleibt ohne Artikel geschlossen', async () => {
        fetcher.mockImplementation(async () => antwort({}, false, 404));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('nicht gefunden');
    });

    it('lädt nichts, solange kein Artikel gewählt ist', () => {
        zeige(null);
        expect(fetcher).not.toHaveBeenCalled();
        expect(screen.queryByRole('dialog')).toBeNull();
    });
});
