import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EigenePositionKalkulieren } from './EigenePositionKalkulieren';
import { ToastProvider } from '../ui/toast';
import type { DocBlock } from './types';

vi.mock('../../features/leistungen/LeistungKalkulation', () => ({
    LeistungKalkulation: ({ leistungId, onPreisGeaendert }: { leistungId: number | string; onPreisGeaendert: (p: number, k: boolean) => void }) => (
        <div data-testid="kalkulation">
            Kalkulation für Leistung {String(leistungId)}
            <button onClick={() => onPreisGeaendert(950, true)}>Preis speichern (kalkuliert)</button>
            <button onClick={() => onPreisGeaendert(100, false)}>Preis speichern (von Hand)</button>
        </div>
    ),
}));

/** Nur Dummy-Daten. */
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten, text: async () => JSON.stringify(daten) });
const block = (teil: Partial<DocBlock> = {}): DocBlock => ({ id: 'b1', type: 'SERVICE', title: 'Sondertreppe', description: '<p>Edelstahl</p>', quantity: 1, unit: 'lfm', price: 500, eigenePosition: true, ...teil });

let fetcher: ReturnType<typeof vi.fn>;
let temporaerAntwort: () => ReturnType<typeof antwort>;
const posts = () => fetcher.mock.calls.filter(([u, i]) => String(u) === '/api/leistungen/temporaer' && i?.method === 'POST');

beforeEach(() => {
    temporaerAntwort = () => antwort({ id: 77 });
    fetcher = vi.fn(async () => temporaerAntwort());
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (b: DocBlock = block(), onUpdate = vi.fn()) => ({ onUpdate, ...render(<ToastProvider><EigenePositionKalkulieren block={b} onUpdate={onUpdate} /></ToastProvider>) });

describe('Eigene Position kalkulieren', () => {
    it('ohne Titel ist der Knopf gesperrt', () => {
        zeige(block({ title: '' }));
        expect(screen.getByRole('button', { name: 'Kalkulieren' })).toBeDisabled();
    });

    it('beim ersten Mal entsteht eine temporäre Leistung mit Text, Preis und Einheit, die Position verweist darauf, und die Kalkulation öffnet sich', async () => {
        const user = userEvent.setup();
        const { onUpdate } = zeige();
        await user.click(screen.getByRole('button', { name: 'Kalkulieren' }));

        await waitFor(() => expect(posts()).toHaveLength(1));
        expect(JSON.parse(posts()[0][1].body as string)).toEqual({ name: 'Sondertreppe', description: '<p>Edelstahl</p>', price: 500, unit: 'LAUFENDE_METER' });
        await waitFor(() => expect(onUpdate).toHaveBeenCalledWith({ leistungId: 77, temporaereLeistung: true }));
        expect(await screen.findByTestId('kalkulation')).toHaveTextContent('Leistung 77');
        expect(screen.getByText(/nur intern sichtbar/)).toBeVisible();
    });

    it('der Preis der Position folgt einer gespeicherten Kalkulation, ein Handpreis ändert die Position nicht', async () => {
        const user = userEvent.setup();
        const { onUpdate } = zeige(block({ leistungId: 77, temporaereLeistung: true }));
        await user.click(screen.getByRole('button', { name: 'Kalkulation bearbeiten' }));
        expect(posts()).toHaveLength(0);

        await user.click(await screen.findByRole('button', { name: 'Preis speichern (von Hand)' }));
        expect(onUpdate).not.toHaveBeenCalled();
        await user.click(screen.getByRole('button', { name: 'Preis speichern (kalkuliert)' }));
        expect(onUpdate).toHaveBeenCalledWith({ price: 950 });
    });

    it('schlägt das Vorbereiten fehl, kommt eine Meldung und es wird nichts an der Position geändert', async () => {
        temporaerAntwort = () => antwort({ message: 'Die Leistung konnte nicht angelegt werden.' }, false, 500);
        const user = userEvent.setup();
        const { onUpdate } = zeige();
        await user.click(screen.getByRole('button', { name: 'Kalkulieren' }));
        expect(await screen.findByText('Die Leistung konnte nicht angelegt werden.')).toBeVisible();
        expect(onUpdate).not.toHaveBeenCalled();
        expect(screen.queryByTestId('kalkulation')).toBeNull();
    });
});
