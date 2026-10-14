import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtikelVarianten, VarianteHinweis } from './ArtikelVarianten';
import { ToastProvider } from '../ui/toast';

vi.mock('./VariantenRaster', () => ({
    VariantenRaster: ({ onAngelegt, onAbbrechen }: { onAngelegt: (n: number) => void; onAbbrechen: () => void }) => (
        <div><button type="button" onClick={() => onAngelegt(2)}>Raster fertig</button><button type="button" onClick={onAbbrechen}>Raster abbrechen</button></div>
    ),
}));

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const ZEILEN = [
    { id: 11, produktname: 'QH 20x20x2', artikelnummer: 'QH-20x20x2', abmessung: '20 x 20 x 2', kgProMeter: 1.05, kgProQm: null, guenstigsterPreis: 3.5 },
    { id: 12, produktname: 'QH 25x25x2', artikelnummer: 'QH-25x25x2', abmessung: '25 x 25 x 2', kgProMeter: 1.4, kgProQm: null, guenstigsterPreis: null },
];

let antwort: () => Response;
beforeEach(() => { antwort = () => json(ZEILEN); vi.stubGlobal('fetch', vi.fn(async () => antwort())); });
afterEach(() => { vi.unstubAllGlobals(); });

const rendern = (onGeaendert = vi.fn()) => {
    render(<MemoryRouter><ToastProvider><ArtikelVarianten artikelId={7} onGeaendert={onGeaendert} /></ToastProvider></MemoryRouter>);
    return onGeaendert;
};

describe('ArtikelVarianten', () => {
    it('listet die Varianten mit Gewicht und Preis; ohne Preis steht „kein Preis“', async () => {
        rendern();

        expect(await screen.findByRole('link', { name: 'QH 20x20x2' })).toHaveAttribute('href', '/artikel/11');
        expect(screen.getByText('1,05 kg/m')).toBeVisible();
        expect(screen.getByText('kein Preis')).toBeVisible();
    });

    it('erklärt die leere Liste', async () => {
        antwort = () => json([]);
        rendern();

        expect(await screen.findByText(/Es gibt noch keine Varianten/)).toBeVisible();
    });

    it('öffnet das Raster, lädt nach dem Anlegen neu und meldet die Änderung nach oben', async () => {
        const user = userEvent.setup();
        const onGeaendert = rendern();
        await screen.findByRole('link', { name: 'QH 20x20x2' });

        await user.click(screen.getByRole('button', { name: 'Varianten anlegen' }));
        await user.click(await screen.findByRole('button', { name: 'Raster fertig' }));

        await waitFor(() => expect(onGeaendert).toHaveBeenCalled());
        expect(screen.queryByRole('button', { name: 'Raster fertig' })).not.toBeInTheDocument();
    });

    it('meldet einen Ladefehler als Text und Toast', async () => {
        antwort = () => json({ message: 'Den Artikel gibt es nicht.' }, 404);
        rendern();

        expect((await screen.findAllByText('Den Artikel gibt es nicht.')).length).toBeGreaterThan(0);
    });

    it('die Variante verweist auf ihren Grundartikel', () => {
        render(<MemoryRouter><VarianteHinweis grundartikelId={7} grundartikelName="Quadratrohr" /></MemoryRouter>);

        expect(screen.getByRole('link', { name: 'Quadratrohr' })).toHaveAttribute('href', '/artikel/7');
    });
});
