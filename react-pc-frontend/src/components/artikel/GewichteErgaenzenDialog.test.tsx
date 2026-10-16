import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GewichteErgaenzenDialog } from './GewichteErgaenzenDialog';
import { ToastProvider } from '../ui/toast';

/** Nur Dummy-Daten. */
const vorschau = {
    gesamt: 3, gekuerzt: false,
    zeilen: [
        { id: 1, bezeichnung: 'Rundrohr 42,4 x 2', form: 'Rundrohr', status: 'BERECHENBAR', hinweis: null, kg: 1.9926, flaeche: 0.1332, einheit: 'je Meter', dichte: 7.85 },
        { id: 3, bezeichnung: 'Blech 3 mm', form: 'Blech', status: 'BERECHENBAR', hinweis: null, kg: 23.55, flaeche: 2, einheit: 'je m²', dichte: 7.85 },
        { id: 2, bezeichnung: 'Rohr ohne Maße', form: 'Rundrohr', status: 'MASSE_FEHLEN', hinweis: 'Bitte den Durchmesser angeben.', kg: null, flaeche: null, einheit: null, dichte: null },
    ],
};
let fetcher: ReturnType<typeof vi.fn>;
const json = (b: unknown, s = 200) => ({ ok: s < 300, status: s, json: async () => b, text: async () => JSON.stringify(b) }) as Response;
const zeige = (fertig = vi.fn()) => { render(<ToastProvider><GewichteErgaenzenDialog offen onOpenChange={vi.fn()} onFertig={fertig} /></ToastProvider>); return fertig; };

beforeEach(() => {
    fetcher = vi.fn(async (url: string) => (url.endsWith('/vorschau') ? json(vorschau) : json({ ergaenzt: 2, uebersprungen: 0 })));
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('GewichteErgaenzenDialog', () => {
    it('zeigt berechnete Gewichte mit Einheit und bei fehlenden Maßen den Grund', async () => {
        zeige();
        const z = await screen.findByTestId('gewicht-1');
        expect(z).toHaveTextContent('1,993 kg je Meter');
        expect(z).toHaveTextContent('0,133 m² je Meter');
        expect(screen.getByTestId('gewicht-3')).toHaveTextContent('23,55 kg je m²');
        expect(screen.getByTestId('gewicht-2')).toHaveTextContent('Bitte den Durchmesser angeben.');
        expect(screen.getByRole('checkbox', { name: /Rohr ohne Maße/ })).toBeDisabled();
        expect(screen.getByText(/3 Artikel ohne Gewicht, davon 2 berechenbar/)).toBeInTheDocument();
    });

    it('übernimmt nur die angekreuzten, berechenbaren Artikel', async () => {
        const u = userEvent.setup();
        const fertig = zeige();
        await u.click(await screen.findByRole('checkbox', { name: /Blech 3 mm/ }));
        await u.click(screen.getByRole('button', { name: /Ausgewählte übernehmen \(1\)/ }));

        await waitFor(() => expect(fertig).toHaveBeenCalled());
        const post = fetcher.mock.calls.find(([u2]) => String(u2).endsWith('/uebernehmen'))!;
        expect(JSON.parse((post[1] as RequestInit).body as string)).toEqual({ artikelIds: [1] });
    });

    it('sagt es, wenn alle Artikel schon ein Gewicht haben, und sperrt das Übernehmen', async () => {
        fetcher.mockImplementation(async () => json({ gesamt: 0, zeilen: [], gekuerzt: false }));
        zeige();
        expect(await screen.findByText('Alle Artikel mit Form haben schon ein Gewicht.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Ausgewählte übernehmen \(0\)/ })).toBeDisabled();
    });

    it('meldet fehlende Rechte und Fehler klar', async () => {
        fetcher.mockImplementation(async () => json({}, 403));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('nur für Administratoren');
    });
});
