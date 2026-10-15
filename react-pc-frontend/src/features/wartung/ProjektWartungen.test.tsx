import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjektWartungen } from './ProjektWartungen';
import { ToastProvider } from '../../components/ui/toast';
import { setzeFeaturesStand, setzeFeaturesZurueck } from '../modulschalter/useFeatures';
import type { ProjektWartung, WartungObjekt } from './types';

const wartung = (extra: Partial<ProjektWartung> = {}): ProjektWartung => ({
    id: 3, bezeichnung: 'Heizungsanlage', objektBezeichnung: 'Werk Nord', kundeName: 'Max Mustermann GmbH', module: 'Heizungsanlage (Gas)',
    naechsteFaelligkeit: '2999-04-01', letztePruefungDatum: '2026-10-01', anzahlPruefungen: 2, offenerFeldeinsatzId: null, offenerFeldeinsatzNummer: null,
    berichte: [{ protokollId: 11, nummer: 'WA-2026-1002', datum: '2027-04-01', url: '/api/dokumente/b2.pdf', dateiname: 'Bericht2.pdf' },
        { protokollId: 10, nummer: 'WA-2026-1001', datum: '2026-10-01', url: '/api/dokumente/b1.pdf', dateiname: 'Bericht1.pdf' }],
    ...extra,
});

const objekt = (tueren: WartungObjekt['tueren']): WartungObjekt => ({
    id: 2, kundeId: 1, kundeName: 'Max Mustermann GmbH', bezeichnung: 'Werk Nord', strasse: null, plz: null, ort: null,
    ansprechpartnerName: null, ansprechpartnerTelefon: null, notizen: null, aktiv: true, tueren,
});

const tuer = (id: number, name: string, projektId: number | null, projektName: string | null, aktiv = true) => ({
    id, objektId: 2, bezeichnung: name, notizen: null, intervallMonate: 12, naechsteFaelligkeit: null, aktiv, module: [], projektId, projektName,
});

function json(status: number, body: unknown = {}): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

function stub(wartungen: () => Response, objekte: WartungObjekt[] = [], zuordnen?: (init: RequestInit) => Response) {
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/wartung/projekte/7/wartungen') return wartungen();
        if (url === '/api/wartung/kunden/1/objekte') return json(200, objekte);
        if (/\/api\/wartung\/tueren\/\d+\/projekt$/.test(url) && init?.method === 'PUT') return zuordnen ? zuordnen(init) : json(200, {});
        return json(404, {});
    });
    vi.stubGlobal('fetch', fetcher);
    return fetcher;
}

const zeige = (kundeId: number | null = 1) => render(
    <ToastProvider><MemoryRouter><ProjektWartungen projektId={7} kundeId={kundeId} /></MemoryRouter></ToastProvider>,
);

beforeEach(() => { setzeFeaturesZurueck(); setzeFeaturesStand({ wartung: true }); });
afterEach(() => { vi.unstubAllGlobals(); setzeFeaturesZurueck(); });

describe('ProjektWartungen (Bereich im Projekt)', () => {
    it('zeigt die Wartungen des Projekts mit Prüfungen, Fälligkeit und den abgelegten Berichten', async () => {
        stub(() => json(200, [wartung()]));
        zeige();

        const karte = await screen.findByTestId('projekt-wartung-3');
        expect(within(karte).getByRole('link', { name: 'Heizungsanlage' })).toHaveAttribute('href', '/wartung/anlagen/3');
        expect(karte).toHaveTextContent('Werk Nord · Heizungsanlage (Gas)');
        expect(karte).toHaveTextContent('2 Prüfungen, letzte 01.10.2026');
        expect(karte).toHaveTextContent('Nächste Prüfung 01.04.2999');
        const berichte = within(karte).getAllByRole('link', { name: /Bericht/ });
        expect(berichte.map((b) => b.getAttribute('href'))).toEqual(['/api/dokumente/b2.pdf', '/api/dokumente/b1.pdf']);
        expect(berichte[0]).toHaveAttribute('target', '_blank');
        expect(berichte[0]).toHaveTextContent('Bericht 01.04.2027 (WA-2026-1002)');
    });

    it('zeigt Überfälliges in Rose und einen laufenden Feldeinsatz mit Link zum Protokoll', async () => {
        stub(() => json(200, [wartung({ naechsteFaelligkeit: '2020-01-15', offenerFeldeinsatzId: 60, offenerFeldeinsatzNummer: 'WA-2026-1999', berichte: [] })]));
        zeige();

        const karte = await screen.findByTestId('projekt-wartung-3');
        expect(karte).toHaveTextContent('Überfällig seit 15.01.2020');
        expect(within(karte).getByRole('link', { name: 'Feldeinsatz läuft' })).toHaveAttribute('href', '/wartung/60');
        expect(within(karte).queryByRole('link', { name: /Bericht/ })).toBeNull();
    });

    it('sagt bei einer Wartung ohne Prüfung "Noch keine Prüfung"', async () => {
        stub(() => json(200, [wartung({ anzahlPruefungen: 0, letztePruefungDatum: null, naechsteFaelligkeit: null, berichte: [] })]));
        zeige();
        expect(await screen.findByText('Noch keine Prüfung')).toBeVisible();
    });

    it('zeigt bei einem Projekt ohne Wartungen einen klaren Hinweis', async () => {
        stub(() => json(200, []));
        zeige();
        expect(await screen.findByText('Diesem Projekt sind noch keine Wartungen zugeordnet.')).toBeVisible();
    });

    it('erscheint gar nicht und fragt nichts ab, wenn das Wartungsmodul ausgeschaltet ist', async () => {
        setzeFeaturesStand({ wartung: false });
        const fetcher = stub(() => json(200, [wartung()]));
        zeige();
        expect(screen.queryByTestId('projekt-wartungen')).toBeNull();
        await new Promise((r) => setTimeout(r, 30));
        expect(fetcher.mock.calls.some(([u]) => String(u).includes('/wartungen'))).toBe(false);
    });

    it('bietet bei einem Ladefehler einen neuen Versuch an', async () => {
        let aufrufe = 0;
        stub(() => (++aufrufe === 1 ? json(500, { message: 'kaputt' }) : json(200, [wartung()])));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Noch einmal versuchen' }));
        expect(await screen.findByTestId('projekt-wartung-3')).toBeVisible();
    });

    it('bietet das Zuordnen nur an, wenn das Projekt einen Kunden hat', async () => {
        stub(() => json(200, []));
        zeige(null);
        await screen.findByText('Diesem Projekt sind noch keine Wartungen zugeordnet.');
        expect(screen.queryByRole('button', { name: /Zuordnen/ })).toBeNull();
    });
});

describe('Wartung dem Projekt zuordnen', () => {
    it('listet nur aktive Wartungen des Kunden, die noch nicht in diesem Projekt sind, und nennt ein anderes Projekt', async () => {
        stub(() => json(200, [wartung()]), [objekt([
            tuer(3, 'Heizungsanlage', 7, '2026-001 – Neubau Halle'),
            tuer(4, 'Lüftung', null, null),
            tuer(5, 'Brandschutztür', 8, '2026-002 – Umbau Lager'),
            tuer(6, 'Altes Tor', null, null, false),
        ])]);
        zeige();
        await screen.findByTestId('projekt-wartung-3');
        await userEvent.click(screen.getByRole('button', { name: /Zuordnen/ }));

        const dialog = await screen.findByRole('dialog');
        expect(await within(dialog).findByText('Lüftung')).toBeVisible();
        expect(within(dialog).getByText('Brandschutztür')).toBeVisible();
        expect(within(dialog).getByText(/gehört zu „2026-002 – Umbau Lager“/)).toBeVisible();
        expect(within(dialog).queryByText('Heizungsanlage')).toBeNull();
        expect(within(dialog).queryByText('Altes Tor')).toBeNull();
    });

    it('ordnet die gewählte Wartung zu, schließt den Dialog und lädt die Liste neu', async () => {
        let geladen = 0;
        const fetcher = stub(() => (++geladen === 1 ? json(200, []) : json(200, [wartung({ id: 4, bezeichnung: 'Lüftung' })])),
            [objekt([tuer(4, 'Lüftung', null, null)])]);
        zeige();
        await screen.findByText('Diesem Projekt sind noch keine Wartungen zugeordnet.');
        await userEvent.click(screen.getByRole('button', { name: /Zuordnen/ }));
        await userEvent.click(await screen.findByRole('button', { name: 'Lüftung diesem Projekt zuordnen' }));

        expect(await screen.findByTestId('projekt-wartung-4')).toBeVisible();
        expect(screen.queryByRole('dialog')).toBeNull();
        const put = fetcher.mock.calls.find(([u, init]) => String(u).endsWith('/tueren/4/projekt') && init?.method === 'PUT')!;
        expect(JSON.parse(put[1]!.body as string)).toEqual({ projektId: 7 });
    });

    it('zeigt die Meldung des Servers, wenn das Zuordnen scheitert, und lässt den Dialog offen', async () => {
        stub(() => json(200, []), [objekt([tuer(4, 'Lüftung', null, null)])], () => json(400, { message: 'Das Projekt gehört zu einem anderen Kunden.' }));
        zeige();
        await screen.findByText('Diesem Projekt sind noch keine Wartungen zugeordnet.');
        await userEvent.click(screen.getByRole('button', { name: /Zuordnen/ }));
        await userEvent.click(await screen.findByRole('button', { name: 'Lüftung diesem Projekt zuordnen' }));

        expect(await screen.findByText('Das Projekt gehört zu einem anderen Kunden.')).toBeVisible();
        expect(screen.getByRole('dialog')).toBeVisible();
    });

    it('sagt, wenn der Kunde keine weiteren Wartungen hat', async () => {
        stub(() => json(200, [wartung()]), [objekt([tuer(3, 'Heizungsanlage', 7, '2026-001 – Neubau Halle')])]);
        zeige();
        await screen.findByTestId('projekt-wartung-3');
        await userEvent.click(screen.getByRole('button', { name: /Zuordnen/ }));
        expect(await screen.findByText(/keine weiteren Wartungen/)).toBeVisible();
    });

    it('meldet, wenn die Wartungen des Kunden nicht geladen werden können', async () => {
        const fetcher = vi.fn(async (url: string) => {
            if (url === '/api/wartung/projekte/7/wartungen') return json(200, []);
            return json(500, { message: 'kaputt' });
        });
        vi.stubGlobal('fetch', fetcher);
        zeige();
        await screen.findByText('Diesem Projekt sind noch keine Wartungen zugeordnet.');
        await userEvent.click(screen.getByRole('button', { name: /Zuordnen/ }));
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('konnten nicht geladen werden'));
    });
});
