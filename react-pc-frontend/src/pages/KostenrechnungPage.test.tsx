import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import KostenrechnungPage from './KostenrechnungPage';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';

/** Nur Dummy-Daten. */
const aufstellung = (aenderung: Record<string, unknown> = {}) => ({ abschreibung: 4, zins: 1.1, wartung: 1, platz: 0.5, versicherung: 0.2, energie: 3, summe: 9.8, hinweis: null, ...aenderung });
const maschine = (aenderung: Record<string, unknown> = {}) => ({
    id: 5, name: 'Schweißanlage', anschaffungswert: 60000, restwert: 6000, nutzungsdauerJahre: 9, zinssatzProzent: 5, wartungJeJahr: 1500, platzJeJahr: 750, versicherungJeJahr: 300,
    leistungKw: 20, strompreisJeKwh: 0.3, auslastungProzent: 50, laufstundenJeJahr: 1500, satzVonHand: null, kostensatz: 9.8, vonHand: false, aktiv: true, bemerkung: null,
    aufstellung: aufstellung(), arbeitsgaenge: [{ id: 3, beschreibung: 'Schweißen' }], ...aenderung,
});
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten, text: async () => JSON.stringify(daten) });

let fetcher: ReturnType<typeof vi.fn>;
let liste: ReturnType<typeof maschine>[];
const aufrufe = (methode: string, teil = '') => fetcher.mock.calls.filter(([u, i]) => (i?.method ?? 'GET') === methode && String(u).includes(teil));
/** Antwort des Reiters „Kostensätze" (Dummy-Daten); die Seite lädt sie nach kurzer Wartezeit, auch wenn der Test schon weiter ist. */
const satzZeile = { art: 'BETRIEB', name: 'Ganzer Betrieb', anzahl: 3, lohnkosten: 120000, stunden: 3100, personalkostensatz: 38.71, umlage: 9.68, kostensatz: 48.39, wagnisBetrag: 2.42, gewinnBetrag: 5.08, verrechnungssatz: 55.89, hinweis: null };
const kostensaetze = {
    jahr: 2026, modus: 'HOCHRECHNUNG', lohnkostenGesamt: 120000, stundenGesamt: 3100, gemeinkostenGesamt: 30000, umlage: 9.68, wagnisProzent: 5, gewinnProzent: 10,
    betrieb: satzZeile, gruppen: [], personen: [], hinweise: [], gemeinkosten: { kostenstellen: [], gesamtbetrag: null, gesamtbetragGilt: false, enthaeltSchaetzwerte: false, sollVorhanden: false },
};
/** Nur das Speichern, nicht die Vorschau des Rechners (die geht auch per POST an /api/maschinen/rechner). */
const speichern = (methode: string, teil = '/api/maschinen') => aufrufe(methode, teil).filter(([u]) => !String(u).endsWith('/rechner'));

beforeEach(() => {
    liste = [maschine(), maschine({ id: 6, name: 'Kappsäge', kostensatz: 40, vonHand: true, satzVonHand: 40, arbeitsgaenge: [], aufstellung: aufstellung({ summe: null, hinweis: 'Die Laufstunden je Jahr fehlen.' }) })];
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const m = init?.method ?? 'GET';
        const u = String(url);
        if (u === '/api/arbeitsgaenge') return antwort([{ id: 3, beschreibung: 'Schweißen', maschineId: 5, maschinenBezeichnung: 'Schweißanlage' }, { id: 4, beschreibung: 'Sägen', maschineId: null }, { id: 7, beschreibung: 'Bohren', maschineId: 6, maschinenBezeichnung: 'Kappsäge' }]);
        if (u.includes('/api/kostenrechnung/vorgaben')) return antwort({ wagnis: null, gewinn: null, interneZeit: null });
        if (u.includes('/api/kostenrechnung/kostensaetze')) return antwort(kostensaetze);
        if (u.endsWith('/rechner')) return antwort(aufstellung());
        if (u.endsWith('/verlauf')) return antwort([{ satz: 9.8, vonHand: false, gueltigAb: '2026-10-08', geaendertVon: 'max', geaendertAm: '2026-10-08T09:00:00' }]);
        if (m === 'POST' || m === 'PUT') return antwort(maschine({ ...JSON.parse(init!.body as string) }));
        if (m === 'DELETE') return u.endsWith('/5') ? antwort({ message: 'Die Maschine "Schweißanlage" wird von 1 Arbeitsgang genutzt. Lösen Sie zuerst die Zuordnung.' }, false, 409) : antwort(null, true, 204);
        return antwort(liste);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<MemoryRouter><ToastProvider><ConfirmProvider><KostenrechnungPage /></ConfirmProvider></ToastProvider></MemoryRouter>);

describe('KostenrechnungPage', { timeout: 30000 }, () => {
    it('zeigt die Maschinen mit Satz, Art des Satzes und den Arbeitsgängen', async () => {
        zeige();
        const erste = await screen.findByTestId('maschine-5');
        expect(erste).toHaveTextContent('Schweißanlage');
        expect(erste).toHaveTextContent('9,80');
        expect(erste).toHaveTextContent('errechnet');
        expect(erste).toHaveTextContent('Genutzt von: Schweißen');
        const zweite = screen.getByTestId('maschine-6');
        expect(zweite).toHaveTextContent('40,00');
        expect(zweite).toHaveTextContent('von Hand festgelegt');
        expect(zweite).toHaveTextContent('Noch keinem Arbeitsgang zugeordnet');
    });

    it('der Reiter „Kostensätze" zeigt die Übersicht statt der Maschinen, und „Neue Maschine" gibt es dort nicht', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('maschine-5');
        await user.click(screen.getByRole('tab', { name: 'Kostensätze' }));
        expect(await screen.findByTestId('kostensaetze')).toBeVisible();
        expect(screen.queryByTestId('maschinen')).toBeNull();
        expect(screen.queryByRole('button', { name: /Neue Maschine/ })).toBeNull();
        await user.click(screen.getByRole('tab', { name: 'Maschinen' }));
        expect(await screen.findByTestId('maschine-5')).toBeVisible();
    });

    it('ohne Maschinen erklärt die Seite, was zu tun ist', async () => {
        liste = [];
        zeige();
        expect(await screen.findByText(/Noch keine Maschine/)).toBeVisible();
    });

    it('legt eine Maschine an: die Vorschau zeigt den Satz vom Server, Zahlen gehen als Zahlen und die gewählten Arbeitsgänge mit', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('maschine-5');
        await user.click(screen.getByRole('button', { name: /Neue Maschine/ }));

        await user.type(screen.getByLabelText('Name der Maschine'), 'Bandsäge');
        await user.type(screen.getByLabelText('Anschaffungswert (€)'), '12000');
        await user.type(screen.getByLabelText('Nutzungsdauer (Jahre)'), '8');
        await user.type(screen.getByLabelText('Laufstunden je Jahr'), '1200');
        await user.click(screen.getByRole('checkbox', { name: /Sägen/ }));

        await waitFor(() => expect(screen.getByTestId('rechner-summe')).toHaveTextContent('9,80'));
        expect(aufrufe('POST', '/rechner').length).toBeGreaterThan(0);
        const letzte = JSON.parse(aufrufe('POST', '/rechner').at(-1)![1].body);
        expect(letzte).toMatchObject({ anschaffungswert: 12000, nutzungsdauerJahre: 8, laufstundenJeJahr: 1200, wartungJeJahr: null });

        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(speichern('POST')).toHaveLength(1));
        const body = JSON.parse(speichern('POST')[0][1].body);
        expect(body).toMatchObject({ name: 'Bandsäge', anschaffungswert: 12000, nutzungsdauerJahre: 8, laufstundenJeJahr: 1200, satzVonHand: null, aktiv: true, arbeitsgangIds: [4] });
    });

    it('zeigt statt der Summe den Hinweis, was für den Satz fehlt', async () => {
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => (String(url).endsWith('/rechner') ? antwort(aufstellung({ summe: null, hinweis: 'Die Laufstunden je Jahr fehlen: Ohne sie lassen sich die Jahreskosten nicht auf eine Stunde verteilen.' }))
            : String(url) === '/api/arbeitsgaenge' ? antwort([]) : init?.method ? antwort(maschine()) : antwort(liste)));
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('maschine-5');
        await user.click(screen.getByRole('button', { name: /Neue Maschine/ }));
        await user.type(screen.getByLabelText('Anschaffungswert (€)'), '1000');
        await waitFor(() => expect(screen.getByTestId('rechner-hinweis')).toHaveTextContent('Laufstunden je Jahr fehlen'));
    });

    it('einen Handsatz verlangt die Seite als Zahl und schickt ihn mit; der Rechner bleibt sichtbar', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('maschine-5');
        await user.click(screen.getByRole('button', { name: 'Schweißanlage bearbeiten' }));
        await screen.findByRole('dialog');

        await user.click(screen.getByRole('checkbox', { name: /Den Satz selbst festlegen/ }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Der Satz je Stunde');
        expect(aufrufe('PUT')).toHaveLength(0);

        await user.type(screen.getByLabelText('Satz je Stunde (€)'), '12,5');
        expect(screen.getByTestId('rechner-summe')).toHaveTextContent('9,80');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(aufrufe('PUT', '/api/maschinen/5')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT', '/api/maschinen/5')[0][1].body).satzVonHand).toBe(12.5);
    });

    it('beim Bearbeiten steht der Verlauf des Satzes, und ein Arbeitsgang einer anderen Maschine wird als „umgehängt" gekennzeichnet', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('maschine-5');
        await user.click(screen.getByRole('button', { name: 'Schweißanlage bearbeiten' }));
        const dialog = await screen.findByRole('dialog');

        expect(await within(dialog).findByText(/Verlauf des Satzes \(1\)/)).toBeVisible();
        expect(within(dialog).getByRole('checkbox', { name: /Schweißen/ })).toBeChecked();
        expect(within(dialog).getByText(/nutzt bisher „Kappsäge", wird umgehängt/)).toBeVisible();
        expect(within(dialog).getByRole('checkbox', { name: /Sägen/ })).not.toBeChecked();
    });

    it('zeigt die Ablehnung des Servers im Dialog und lässt ihn offen', async () => {
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
            const u = String(url);
            if (init?.method === 'POST' && !u.endsWith('/rechner')) return antwort({ message: 'Eine Maschine mit dem Namen "Kappsäge" gibt es schon.' }, false, 409);
            if (u.endsWith('/rechner')) return antwort(aufstellung());
            if (u === '/api/arbeitsgaenge') return antwort([]);
        if (u.includes('/api/kostenrechnung/vorgaben')) return antwort({ wagnis: null, gewinn: null, interneZeit: null });
        if (u.includes('/api/kostenrechnung/kostensaetze')) return antwort(kostensaetze);
            return antwort(liste);
        });
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('maschine-5');
        await user.click(screen.getByRole('button', { name: /Neue Maschine/ }));
        await user.type(screen.getByLabelText('Name der Maschine'), 'Kappsäge');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('gibt es schon');
        expect(screen.getByRole('dialog')).toBeVisible();
    });

    it('löscht nach Rückfrage; eine genutzte Maschine wird mit Erklärung abgelehnt', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('maschine-5');

        await user.click(screen.getByRole('button', { name: 'Schweißanlage löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        expect(await screen.findByText(/wird von 1 Arbeitsgang genutzt/)).toBeVisible();

        await user.click(screen.getByRole('button', { name: 'Kappsäge löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(aufrufe('DELETE', '/api/maschinen/6')).toHaveLength(1));
    });

    it('verlangt einen Namen und prüft Zahlen vor dem Speichern', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('maschine-5');
        await user.click(screen.getByRole('button', { name: /Neue Maschine/ }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText(/Bitte einen Namen eintragen/)).toBeVisible();

        await user.type(screen.getByLabelText('Name der Maschine'), 'Presse');
        fireEvent.change(screen.getByLabelText('Laufstunden je Jahr'), { target: { value: '9000' } });
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Laufstunden');
        expect(speichern('POST')).toHaveLength(0);
    });
});
