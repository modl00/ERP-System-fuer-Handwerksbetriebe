import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { BelegProjektSektion } from './BelegProjektSektion';
import { ToastProvider } from '../ui/toast';

/** Nur Dummy-Daten. */
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
let fetcher: ReturnType<typeof vi.fn>;

function stub(zuordnungen: unknown[], extra: (url: string, init?: RequestInit) => Response | undefined = () => undefined) {
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const e = extra(url, init);
        if (e) return e;
        if (url.startsWith('/api/bestellungen-uebersicht/zuordnungen/')) return json(zuordnungen);
        if (url.startsWith('/api/projekte/simple')) return json([{ id: 4, bauvorhaben: 'Neubau Halle', auftragsnummer: 'A-1', kunde: 'Max Mustermann GmbH' }]);
        return json({});
    });
    vi.stubGlobal('fetch', fetcher);
}
const zeige = (id: number | null) => render(<ToastProvider><BelegProjektSektion eingangsrechnungId={id} /></ToastProvider>);
afterEach(() => { vi.unstubAllGlobals(); });

describe('BelegProjektSektion', () => {
    it('sagt, warum noch kein Projekt wählbar ist, wenn es keine Eingangsrechnung gibt', () => {
        stub([]);
        zeige(null);
        expect(screen.getByText(/sobald der Beleg als Rechnung mit Lieferant erfasst ist/)).toBeInTheDocument();
        expect(fetcher).not.toHaveBeenCalled();
    });

    it('zeigt eine bestehende Zuordnung und bietet keine Auswahl an, die sie überschreiben würde', async () => {
        stub([{ id: 1, projektId: 4, projektName: 'Neubau Halle', prozentanteil: 60, betrag: 71.4 }, { id: 2, kostenstelleId: 2, kostenstelleName: 'Lager', prozentanteil: 40, betrag: 47.6 }]);
        zeige(90);
        expect(await screen.findByText('Neubau Halle')).toBeInTheDocument();
        expect(screen.getByText('Lager')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Projekt wählen/ })).toBeNull();
        expect(screen.getByText(/Bestellungen → Rechnung → Projekten zuordnen/)).toBeInTheDocument();
    });

    it('ordnet ein gewähltes Projekt zu 100 Prozent zu und zeigt es danach', async () => {
        const u = userEvent.setup();
        let zugeordnet = false;
        stub([], (url, init) => {
            if (url === '/api/bestellungen-uebersicht/zuordnen' && init?.method === 'POST') { zugeordnet = true; return json({ success: true }); }
            if (url.startsWith('/api/bestellungen-uebersicht/zuordnungen/') && zugeordnet) return json([{ id: 9, projektId: 4, projektName: 'Neubau Halle', prozentanteil: 100, betrag: 119 }]);
            return undefined;
        });
        zeige(90);

        await u.click(await screen.findByRole('button', { name: /Projekt wählen/ }));
        await u.click(await screen.findByText('Neubau Halle'));

        await waitFor(() => expect(screen.getByText('100 %', { exact: false })).toBeInTheDocument());
        const post = fetcher.mock.calls.find(([url]) => url === '/api/bestellungen-uebersicht/zuordnen')!;
        expect(JSON.parse((post[1] as RequestInit).body as string)).toEqual({ geschaeftsdokumentId: 90, projektAnteile: [{ projektId: 4, prozentanteil: 100 }] });
    });

    it('zeigt den Fehler des Servers und ändert nichts', async () => {
        const u = userEvent.setup();
        stub([], (url, init) => (url === '/api/bestellungen-uebersicht/zuordnen' && init?.method === 'POST' ? json({ error: 'Geschäftsdokument nicht gefunden' }, 400) : undefined));
        zeige(90);
        await u.click(await screen.findByRole('button', { name: /Projekt wählen/ }));
        await u.click(await screen.findByText('Neubau Halle'));
        expect(await screen.findByText('Geschäftsdokument nicht gefunden')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Projekt wählen/ })).toBeInTheDocument();
    });

    it('meldet, wenn die Zuordnung nicht geladen werden kann', async () => {
        stub([], (url) => (url.startsWith('/api/bestellungen-uebersicht/zuordnungen/') ? json({}, 500) : undefined));
        zeige(90);
        expect(await screen.findByText('Die Zuordnung konnte nicht geladen werden.')).toBeInTheDocument();
    });
});
