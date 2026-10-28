import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import FremdleistungenPage from './FremdleistungenPage';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';

/** Nur Dummy-Daten. */
const fl = (aenderung: Record<string, unknown> = {}) => ({
    id: 31, lieferantId: 1, lieferantName: 'Verzinkerei Muster A', bezeichnung: 'Feuerverzinken', einheit: 'KG', preisJeEinheit: 1.2, mindestpreis: 45, einmalBetrag: null, anteilBetrag: null,
    anteilChargenmenge: null, prozentZuschlag: 10, transportArt: 'PAUSCHAL', transportBetrag: 35, transportKm: null, transportFahrten: 1, sonstigeBezeichnung: null, sonstigeBetrag: null,
    gueltigAb: '2026-10-01', aktiv: true, verwendet: 0, ...aenderung,
});
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten, text: async () => JSON.stringify(daten) });

let fetcher: ReturnType<typeof vi.fn>;
let liste: ReturnType<typeof fl>[];
const aufrufe = (methode: string) => fetcher.mock.calls.filter(([, i]) => (i?.method ?? 'GET') === methode);

beforeEach(() => {
    liste = [fl(), fl({ id: 32, lieferantId: 2, lieferantName: 'Pulverbeschichtung Muster', bezeichnung: 'Pulverbeschichten', einheit: 'QUADRATMETER', preisJeEinheit: 12, mindestpreis: null, prozentZuschlag: null, transportArt: 'KEINE', transportBetrag: null, aktiv: false })];
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const m = init?.method ?? 'GET';
        if (String(url).startsWith('/api/lieferanten')) return antwort({ lieferanten: [{ id: 1, lieferantenname: 'Verzinkerei Muster A' }, { id: 2, lieferantenname: 'Pulverbeschichtung Muster' }] });
        if (m === 'POST' || m === 'PUT') return antwort(fl({ ...JSON.parse(init!.body as string) }));
        if (m === 'DELETE') return String(url).endsWith('/31') ? antwort({ message: '„Feuerverzinken" kommt in 2 Kalkulationszeilen vor und lässt sich nicht löschen. Bitte stattdessen ausschalten.' }, false, 409) : antwort({}, true, 204);
        return antwort(liste);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<MemoryRouter><ToastProvider><ConfirmProvider><FremdleistungenPage /></ConfirmProvider></ToastProvider></MemoryRouter>);

describe('FremdleistungenPage', () => {
    it('zeigt die Fremdleistungen je Veredler mit Kurztext und Preisstand', async () => {
        zeige();
        const erste = await screen.findByTestId('fremdleistung-31');
        expect(erste).toHaveTextContent('Feuerverzinken');
        expect(erste).toHaveTextContent('1,20');
        expect(erste).toHaveTextContent('mind. 45,00');
        expect(erste).toHaveTextContent('Transport pauschal je auftrag 35,00');
        expect(erste).toHaveTextContent('+ 10 %');
        expect(erste).toHaveTextContent('Preisstand 01.10.2026');
        expect(screen.getByTestId('fremdleistung-32')).toHaveTextContent('ausgeschaltet');
        expect(screen.getByRole('heading', { name: /Verzinkerei Muster A/ })).toBeVisible();
    });

    it('erklärt im Formular, was eine Ladung (Charge) ist', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('fremdleistung-31');
        await user.click(screen.getByRole('button', { name: /Neue Fremdleistung/ }));

        const hilfe = await screen.findByTestId('charge-hilfe');
        expect(hilfe).toHaveTextContent('Ladung (Charge)');
        expect(hilfe).toHaveTextContent('Ein Auftrag mit 150 kg bezahlt davon 22,50 €');
        expect(screen.getByLabelText('Kosten je Ladung / Charge (€)')).toBeInTheDocument();
        expect(screen.getByLabelText(/Eine Ladung fasst/)).toBeInTheDocument();
    });

    it('legt eine Fremdleistung an und schickt Zahlen als Zahlen', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('fremdleistung-31');
        await user.click(screen.getByRole('button', { name: /Neue Fremdleistung/ }));

        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText(/Veredler \(Lieferanten\) wählen/)).toBeInTheDocument();
        expect(aufrufe('POST')).toHaveLength(0);

        await user.click(screen.getByText('Veredler wählen'));
        // Die Überschrift der Liste heißt gleich: erst warten, bis auch der Treffer der Suche da ist
        await waitFor(() => expect(screen.getAllByText('Pulverbeschichtung Muster').length).toBeGreaterThan(1));
        const treffer = screen.getAllByText('Pulverbeschichtung Muster');
        await user.click(treffer[treffer.length - 1]);
        await user.type(screen.getByLabelText('Was macht der Veredler?'), 'Pulverbeschichten');
        await user.type(screen.getByLabelText('Preis je kg (€)'), '2,5');
        await user.type(screen.getByLabelText('Mindestpreis je Auftrag (€)'), '40');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('POST')).toHaveLength(1));
        expect(JSON.parse(aufrufe('POST')[0][1].body)).toMatchObject({ lieferantId: 2, bezeichnung: 'Pulverbeschichten', einheit: 'KG', preisJeEinheit: 2.5, mindestpreis: 40, transportArt: 'KEINE', transportFahrten: 1, aktiv: true });
    });

    it('prüft Zahlen vor dem Senden', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('fremdleistung-31');
        await user.click(screen.getByRole('button', { name: 'Feuerverzinken bei Verzinkerei Muster A bearbeiten' }));
        const preis = await screen.findByLabelText('Preis je kg (€)');
        await user.clear(preis);
        await user.type(preis, '2,');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect((await screen.findAllByText(/vollständige Zahl/)).length).toBeGreaterThan(0);
        expect(aufrufe('PUT')).toHaveLength(0);
    });

    it('Ausschalten ändert nur den Schalter', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('fremdleistung-31');
        await user.click(screen.getByRole('button', { name: 'Feuerverzinken ausschalten' }));
        await waitFor(() => expect(aufrufe('PUT')).toHaveLength(1));
        expect(aufrufe('PUT')[0][0]).toBe('/api/fremdleistungen/31');
        expect(JSON.parse(aufrufe('PUT')[0][1].body)).toMatchObject({ aktiv: false, bezeichnung: 'Feuerverzinken', preisJeEinheit: 1.2, mindestpreis: 45, transportArt: 'PAUSCHAL' });
    });

    it('Löschen fragt nach und zeigt die Sperre des Servers im Klartext', async () => {
        const user = userEvent.setup();
        zeige();
        const zeile = await screen.findByTestId('fremdleistung-31');
        await user.click(within(zeile).getByRole('button', { name: 'Feuerverzinken bei Verzinkerei Muster A löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(aufrufe('DELETE')).toHaveLength(1));
        expect(await screen.findByText(/lässt sich nicht löschen/)).toBeInTheDocument();
    });
});
