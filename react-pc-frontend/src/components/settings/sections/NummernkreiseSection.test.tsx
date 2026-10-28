import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NummernkreiseSection } from './NummernkreiseSection';
import { ToastProvider } from '../../ui/toast';

const zeile = (art: string, bezeichnung: string, aenderung: Record<string, unknown> = {}) => ({
    art, bezeichnung, eigenesFormat: false, praefix: art.slice(0, 2), datumsteil: 'JAHR', ruecksetzung: 'NIE', stellen: 4, startnummer: 1,
    naechsteNummer: `${art.slice(0, 2)}-2026/10/00001 (bisheriges Format)`, warnung: null, ...aenderung,
});
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let stand: ReturnType<typeof zeile>[];
const aufrufe = (methode: string) => fetcher.mock.calls.filter(([, i]) => i?.method === methode);

beforeEach(() => {
    stand = [zeile('RECHNUNG', 'Rechnung'), zeile('LIEFERSCHEIN', 'Lieferschein', { eigenesFormat: true, praefix: 'LS', naechsteNummer: 'LS-2026-0007' })];
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === 'PUT') {
            const art = url.split('/').pop()!;
            const b = JSON.parse(init.body as string);
            if (b.praefix === 'BAD') return antwort({ message: 'Das Präfix besteht aus 1 bis 10 Buchstaben oder Ziffern.' }, false, 400);
            const neu = zeile(art, 'Rechnung', { eigenesFormat: true, ...b, naechsteNummer: `${b.praefix}-2026-0010` });
            stand = stand.map(z => (z.art === art ? neu : z));
            return antwort(neu);
        }
        if (init?.method === 'DELETE') return antwort(zeile('LIEFERSCHEIN', 'Lieferschein'));
        return antwort(stand);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><NummernkreiseSection /></ToastProvider>);

describe('NummernkreiseSection', () => {
    it('zeigt je Dokumentart die nächste Nummer und kennzeichnet das eigene Format', async () => {
        zeige();
        const rechnung = await screen.findByTestId('nummernkreis-RECHNUNG');
        expect(within(rechnung).getByText(/bisheriges Format/)).toBeInTheDocument();
        expect(within(rechnung).queryByText('eigenes Format')).not.toBeInTheDocument();
        const ls = screen.getByTestId('nummernkreis-LIEFERSCHEIN');
        expect(within(ls).getByText('LS-2026-0007')).toBeInTheDocument();
        expect(within(ls).getByText('eigenes Format')).toBeInTheDocument();
    });

    it('zeigt beim Bearbeiten eine Vorschau und speichert Präfix, Datum, Zähler, Stellen und Start', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('nummernkreis-RECHNUNG');
        await user.click(screen.getByRole('button', { name: 'Rechnung ändern' }));
        const praefix = screen.getByLabelText('Präfix');
        await user.clear(praefix);
        await user.type(praefix, 'RG');
        await user.selectOptions(screen.getByLabelText('Datum in der Nummer'), 'JAHR');
        await user.selectOptions(screen.getByLabelText('Zähler'), 'NIE');
        const start = screen.getByLabelText('Startnummer');
        await user.clear(start);
        await user.type(start, '1000');
        expect(screen.getByTestId('nk-vorschau')).toHaveTextContent(`RG-${new Date().getFullYear()}-1000`);

        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(aufrufe('PUT')).toHaveLength(1));
        expect(aufrufe('PUT')[0][0]).toBe('/api/nummernkreise/RECHNUNG');
        expect(JSON.parse(aufrufe('PUT')[0][1].body)).toEqual({ praefix: 'RG', datumsteil: 'JAHR', ruecksetzung: 'NIE', stellen: 4, startnummer: 1000 });
        expect(await within(screen.getByTestId('nummernkreis-RECHNUNG')).findByText('eigenes Format')).toBeInTheDocument();
    });

    it('prüft Stellen und Startnummer vor dem Senden und zeigt die Meldung des Servers', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('nummernkreis-RECHNUNG');
        await user.click(screen.getByRole('button', { name: 'Rechnung ändern' }));

        const stellen = screen.getByLabelText('Stellen');
        await user.clear(stellen);
        await user.type(stellen, '2');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        expect(await screen.findByRole('alert')).toBeInTheDocument();
        expect(aufrufe('PUT')).toHaveLength(0);

        await user.clear(stellen);
        await user.type(stellen, '4');
        const praefix = screen.getByLabelText('Präfix');
        await user.clear(praefix);
        await user.type(praefix, 'BAD');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        expect(await screen.findByText(/1 bis 10 Buchstaben/)).toBeInTheDocument();
    });

    it('geht mit „Wieder das bisherige Format" zurück, nur bei eigenem Format angeboten', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('nummernkreis-RECHNUNG');
        await user.click(screen.getByRole('button', { name: 'Rechnung ändern' }));
        expect(screen.queryByRole('button', { name: /bisherige Format/ })).not.toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Rechnung ändern' }));

        await user.click(screen.getByRole('button', { name: 'Lieferschein ändern' }));
        await user.click(screen.getByRole('button', { name: /Wieder das bisherige Format/ }));

        await waitFor(() => expect(aufrufe('DELETE')).toHaveLength(1));
        expect(aufrufe('DELETE')[0][0]).toBe('/api/nummernkreise/LIEFERSCHEIN');
    });
});
