import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjektWartungen } from './ProjektWartungen';
import { ToastProvider } from '../../components/ui/toast';
import { setzeFeaturesStand, setzeFeaturesZurueck } from '../modulschalter/useFeatures';
import type { ProjektWartung, WartungObjekt } from './types';

/** Nur Dummy-Daten. */
const wartung = (id: number, name: string, extra: Partial<ProjektWartung> = {}): ProjektWartung => ({
    id, objektId: 2, bezeichnung: name, objektBezeichnung: 'Werk Nord', kundeName: 'Max Mustermann GmbH', module: 'Brandschutztür',
    naechsteFaelligkeit: null, letztePruefungDatum: null, anzahlPruefungen: 0, offenerFeldeinsatzId: null, offenerFeldeinsatzNummer: null, berichte: [], ...extra,
});

const tuer = (id: number, name: string, projektId: number | null = null, projektName: string | null = null) => ({
    id, objektId: 2, bezeichnung: name, notizen: null, intervallMonate: 12, naechsteFaelligkeit: null, aktiv: true, module: [], projektId, projektName,
});

const objekt = (tueren: WartungObjekt['tueren'], id = 2): WartungObjekt => ({
    id, kundeId: 1, kundeName: 'Max Mustermann GmbH', bezeichnung: 'Werk Nord', strasse: null, plz: null, ort: null,
    ansprechpartnerName: null, ansprechpartnerTelefon: null, notizen: null, aktiv: true, tueren,
});

function json(status: number, body: unknown = {}): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body, text: async () => JSON.stringify(body) } as Response;
}

/** Aufrufe der Zuordnung: Tür-ID und Projekt (null = entfernt) */
let zuordnungen: { tuerId: number; projektId: number | null }[];

function stub(wartungen: ProjektWartung[], objekte: WartungObjekt[], nichtZuordnen: number[] = []) {
    zuordnungen = [];
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/wartung/projekte/7/wartungen') return json(200, wartungen);
        if (url === '/api/wartung/kunden/1/objekte') return json(200, objekte);
        if (url === '/api/wartung/module') return json(200, []);
        const m = /\/api\/wartung\/tueren\/(\d+)\/projekt$/.exec(url);
        if (m && init?.method === 'PUT') {
            const body = JSON.parse(String(init.body)) as { projektId: number | null };
            if (nichtZuordnen.includes(Number(m[1]))) return json(409, { message: 'Diese Wartung ist gesperrt.' });
            zuordnungen.push({ tuerId: Number(m[1]), projektId: body.projektId });
            return json(200, {});
        }
        return json(404, {});
    });
    vi.stubGlobal('fetch', fetcher);
    return fetcher;
}

const zeige = () => render(<ToastProvider><MemoryRouter><ProjektWartungen projektId={7} kundeId={1} /></MemoryRouter></ToastProvider>);

beforeEach(() => { setzeFeaturesZurueck(); setzeFeaturesStand({ wartung: true }); });
afterEach(() => { vi.unstubAllGlobals(); setzeFeaturesZurueck(); });

describe('Projekt, Reiter Wartung: mehrere Wartungen zuordnen und entfernen', { timeout: 30000 }, () => {
    it('Alle auswählen markiert alle Wartungen des Projekts; Entfernen fragt erst nach und entfernt dann jede Zuordnung', async () => {
        const user = userEvent.setup();
        stub([wartung(3, 'Heizung'), wartung(4, 'Lüftung')], []);
        zeige();
        await screen.findByTestId('projekt-wartung-3');

        await user.click(screen.getByLabelText('Alle auswählen'));
        expect(screen.getByText('2 markiert')).toBeVisible();
        await user.click(screen.getByRole('button', { name: /Alle markierten entfernen/ }));
        expect(zuordnungen).toEqual([]);
        expect(screen.getByRole('alertdialog')).toHaveTextContent('2 Wartungen aus diesem Projekt entfernen? Die Wartungen selbst bleiben erhalten.');
        await user.click(screen.getByRole('button', { name: 'Ja, entfernen' }));

        await waitFor(() => expect(zuordnungen).toEqual([{ tuerId: 3, projektId: null }, { tuerId: 4, projektId: null }]));
    });

    it('einzelne Wartungen markieren; Abbrechen entfernt nichts', async () => {
        const user = userEvent.setup();
        stub([wartung(3, 'Heizung'), wartung(4, 'Lüftung')], []);
        zeige();
        await screen.findByTestId('projekt-wartung-3');

        await user.click(screen.getByLabelText('Lüftung markieren'));
        expect(screen.getByText('1 markiert')).toBeVisible();
        await user.click(screen.getByRole('button', { name: /Alle markierten entfernen/ }));
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));

        expect(zuordnungen).toEqual([]);
        expect(screen.queryByRole('alertdialog')).toBeNull();
    });

    it('Zuordnen-Dialog: Alle auswählen gilt nur für die gefilterte Liste, vor dem Zuordnen kommt eine Rückfrage mit Hinweis auf andere Projekte', async () => {
        const user = userEvent.setup();
        stub([], [objekt([tuer(10, 'Tor Nord'), tuer(11, 'Tor Süd'), tuer(12, 'Heizung', 9, '2026-002 – Umbau Lager')])]);
        zeige();
        await screen.findByText('Diesem Projekt sind noch keine Wartungen zugeordnet.');
        await user.click(screen.getByRole('button', { name: /^Zuordnen$/ }));
        const dialog = await screen.findByRole('dialog');
        await within(dialog).findByText('Tor Nord');

        await user.type(within(dialog).getByLabelText('Wartungen filtern'), 'tor');
        expect(within(dialog).queryByText('Heizung')).toBeNull();
        await user.click(within(dialog).getByLabelText(/Alle auswählen/));
        expect(within(dialog).getByLabelText('Tor Nord markieren')).toBeChecked();
        expect(within(dialog).getByLabelText('Tor Süd markieren')).toBeChecked();
        await user.click(within(dialog).getByRole('button', { name: /Alle markierten zuordnen \(2\)/ }));
        expect(zuordnungen).toEqual([]);
        expect(within(dialog).getByRole('alertdialog')).toHaveTextContent('2 Wartungen diesem Projekt zuordnen?');
        await user.click(within(dialog).getByRole('button', { name: 'Ja, zuordnen' }));

        await waitFor(() => expect(zuordnungen).toEqual([{ tuerId: 10, projektId: 7 }, { tuerId: 11, projektId: 7 }]));
    });

    it('Zuordnen-Dialog: gehört eine markierte Wartung zu einem anderen Projekt, steht das in der Rückfrage', async () => {
        const user = userEvent.setup();
        stub([], [objekt([tuer(10, 'Tor Nord'), tuer(12, 'Heizung', 9, '2026-002 – Umbau Lager')])]);
        zeige();
        await screen.findByText('Diesem Projekt sind noch keine Wartungen zugeordnet.');
        await user.click(screen.getByRole('button', { name: /^Zuordnen$/ }));
        const dialog = await screen.findByRole('dialog');
        await within(dialog).findByText('Heizung');

        await user.click(within(dialog).getByLabelText(/Alle auswählen/));
        await user.click(within(dialog).getByRole('button', { name: /Alle markierten zuordnen \(2\)/ }));

        expect(within(dialog).getByRole('alertdialog')).toHaveTextContent('1 davon gehört noch zu einem anderen Projekt und wird verschoben.');
    });

    it('scheitert eine Zuordnung, bleibt sie markiert und steht im Klartext da; die anderen sind zugeordnet', async () => {
        const user = userEvent.setup();
        stub([], [objekt([tuer(10, 'Tor Nord'), tuer(11, 'Tor Süd')])], [11]);
        zeige();
        await screen.findByText('Diesem Projekt sind noch keine Wartungen zugeordnet.');
        await user.click(screen.getByRole('button', { name: /^Zuordnen$/ }));
        const dialog = await screen.findByRole('dialog');
        await within(dialog).findByText('Tor Nord');

        await user.click(within(dialog).getByLabelText(/Alle auswählen/));
        await user.click(within(dialog).getByRole('button', { name: /Alle markierten zuordnen/ }));
        await user.click(within(dialog).getByRole('button', { name: 'Ja, zuordnen' }));

        await waitFor(() => expect(zuordnungen).toEqual([{ tuerId: 10, projektId: 7 }]));
        expect(await screen.findByText('Nicht zugeordnet: Tor Süd')).toBeVisible();
        expect(within(dialog).getByLabelText('Tor Süd markieren')).toBeChecked();
    });
});

describe('Projekt, Reiter Wartung: Wartung starten und neue Anlage', () => {
    it('„Wartung starten“ öffnet die neue Wartung mit Kunde, Objekt und Anlage; läuft schon ein Feldeinsatz, gibt es den Knopf nicht', async () => {
        const user = userEvent.setup();
        stub([wartung(3, 'Heizung'), wartung(4, 'Lüftung', { offenerFeldeinsatzId: 60, offenerFeldeinsatzNummer: 'WA-1' })], [objekt([tuer(3, 'Heizung'), tuer(4, 'Lüftung')])]);
        zeige();
        await screen.findByTestId('projekt-wartung-3');

        expect(screen.queryByRole('button', { name: 'Wartung für Lüftung starten' })).toBeNull();
        await user.click(screen.getByRole('button', { name: 'Wartung für Heizung starten' }));

        expect(await screen.findByRole('heading', { name: 'Neue Wartung' })).toBeVisible();
    });

    it('„Neue Anlage“ öffnet die Auswahl des Objekts; mit genau einem Objekt ist es schon gewählt, „Neues Objekt“ öffnet die Objekt-Maske', async () => {
        const user = userEvent.setup();
        stub([], [objekt([])]);
        zeige();
        await screen.findByText('Diesem Projekt sind noch keine Wartungen zugeordnet.');

        await user.click(screen.getByRole('button', { name: /Neue Anlage/ }));
        expect(await screen.findByRole('heading', { name: 'Neue Anlage' })).toBeVisible();
        const dialog = await screen.findByRole('dialog');
        await waitFor(() => expect(within(dialog).getByRole('button', { name: /Anlage anlegen/ })).toBeEnabled());
        await user.click(within(dialog).getByRole('button', { name: /Neues Objekt/ }));

        expect(await screen.findByRole('heading', { name: 'Objekt anlegen' })).toBeVisible();
    });

    it('„Neue Anlage“ ohne Objekt: erst ein Objekt anlegen, der Knopf ist bis dahin gesperrt', async () => {
        const user = userEvent.setup();
        stub([], []);
        zeige();
        await screen.findByText('Diesem Projekt sind noch keine Wartungen zugeordnet.');

        await user.click(screen.getByRole('button', { name: /Neue Anlage/ }));
        expect(await screen.findByRole('heading', { name: 'Neue Anlage' })).toBeVisible();
        const dialog = await screen.findByRole('dialog');

        await within(dialog).findByText(/Noch kein Objekt/);
        expect(within(dialog).getByRole('button', { name: /Anlage anlegen/ })).toBeDisabled();
    });
});
