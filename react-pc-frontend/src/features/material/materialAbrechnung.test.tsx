import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MaterialAbrechnungPanel } from './MaterialAbrechnungPanel';
import { abweichung, type MaterialAbrechnung } from './materialAbrechnungApi';
import { ToastProvider } from '../../components/ui/toast';

/** Nur Dummy-Daten. Gerechnet und geprüft wird im Backend; hier zählen Anzeige, Vorbelegung, Vorschau der Abweichung und die richtigen Aufrufe. */
let admin = false;
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ isAdmin: admin }) }));

const DATEN: MaterialAbrechnung = {
    schwelleProzent: 5,
    offenAnzahl: 2,
    offenGeplant: 500,
    rechnungen: [
        { dokumentId: 50, dokumentNummer: 'RE-2026-77', dokumentDatum: '2026-10-01', lieferantId: 3, lieferantName: 'Stahlhandel Muster', anteil: 1000, zugeordnet: 345, rest: 655,
            zeilen: [{ id: 1, artikelInProjektId: 3, bezeichnung: 'Blech 3 mm', betrag: 345, beschreibung: null }] },
    ],
    positionen: [
        { artikelInProjektId: 1, artikelId: 10, artikelnummer: 'R-4040', bezeichnung: 'Rohr 40x40', menge: 6.5, einheit: 'm', lieferantId: 3, lieferantName: 'Stahlhandel Muster', bestellt: true, geplant: 300, ist: null,
            abweichungProzent: null, abweichung: null, rechnungen: [] },
        { artikelInProjektId: 2, artikelId: 11, artikelnummer: null, bezeichnung: 'Schraube M8', menge: 40, einheit: 'Stk', lieferantId: 4, lieferantName: 'Schrauben Muster', bestellt: false, geplant: 200, ist: null,
            abweichungProzent: null, abweichung: null, rechnungen: [] },
        { artikelInProjektId: 3, artikelId: 12, artikelnummer: null, bezeichnung: 'Blech 3 mm', menge: 2, einheit: 'm²', lieferantId: 3, lieferantName: 'Stahlhandel Muster', bestellt: true, geplant: 300, ist: 345,
            abweichungProzent: 15, abweichung: 'TEURER', rechnungen: ['RE-2026-77'] },
    ],
};

const antwort = (daten: unknown, status = 200) => ({ ok: status < 300, status, json: async () => daten });
let fetcher: ReturnType<typeof vi.fn>;
const aufrufe = (methode: string) => fetcher.mock.calls.filter(([, i]) => ((i as RequestInit | undefined)?.method ?? 'GET') === methode);

beforeEach(() => {
    admin = false;
    fetcher = vi.fn(async (_url: string, init?: RequestInit) => antwort(init?.method === 'POST' ? { ...DATEN, offenAnzahl: 0 } : DATEN));
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (onGeaendert = vi.fn()) => { render(<ToastProvider><MaterialAbrechnungPanel projektId={7} onGeaendert={onGeaendert} /></ToastProvider>); return onGeaendert; };

describe('Material: Plan und Rechnung', { timeout: 30000 }, () => {
    it('zeigt offene Positionen, bezahlte mit Rechnungsnummer und Markierung der Abweichung, Rest je Rechnung und den Hinweis „Rechnung fehlt"', async () => {
        zeige();

        expect(await screen.findByText(/Material erwartet, Rechnung fehlt noch: 2 Positionen/)).toBeInTheDocument();
        const blech = screen.getByRole('row', { name: /Blech 3 mm/ });
        expect(blech).toHaveTextContent('per Rechnung RE-2026-77');
        expect(blech).toHaveTextContent('teurer: 15 %');
        expect(screen.getByRole('row', { name: /Rohr 40x40/ })).toHaveTextContent('bestellt, Rechnung fehlt');
        expect(screen.getByRole('row', { name: /Schraube M8/ })).toHaveTextContent('noch nicht bestellt');
        expect(screen.getByText(/Rechnung RE-2026-77 · Stahlhandel Muster/)).toBeInTheDocument();
        expect(screen.getByText(/noch offen 655,00/)).toBeInTheDocument();
        expect(fetcher.mock.calls[0][0]).toBe('/api/projekte/7/material-abrechnung');
    });

    it('ohne Material und ohne Rechnungen erscheint nichts', async () => {
        fetcher.mockImplementation(async () => antwort({ ...DATEN, positionen: [], rechnungen: [], offenAnzahl: 0 }));
        zeige();

        await waitFor(() => expect(fetcher).toHaveBeenCalled());
        expect(screen.queryByTestId('material-abrechnung')).not.toBeInTheDocument();
    });

    it('Zuordnen: Positionen desselben Lieferanten sind vorgewählt und mit dem Plan-Preis vorbelegt, der andere Lieferant nicht', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async () => antwort({ ...DATEN, rechnungen: [{ ...DATEN.rechnungen[0], zeilen: [], zugeordnet: 0, rest: 1000 }],
            positionen: DATEN.positionen.map((p) => ({ ...p, ist: null, abweichung: null, abweichungProzent: null, rechnungen: [] })) }));
        zeige();
        await user.click(await screen.findByRole('button', { name: /Material zur Rechnung RE-2026-77 zuordnen/ }));

        const dialog = await screen.findByRole('dialog', { name: 'Material zur Rechnung zuordnen' });
        expect(within(dialog).getByRole('checkbox', { name: 'Rohr 40x40 gehört zu dieser Rechnung' })).toBeChecked();
        expect(within(dialog).getByRole('checkbox', { name: 'Blech 3 mm gehört zu dieser Rechnung' })).toBeChecked();
        expect(within(dialog).getByRole('checkbox', { name: 'Schraube M8 gehört zu dieser Rechnung' })).not.toBeChecked();
        expect(within(dialog).getByLabelText('Betrag Rohr 40x40')).toHaveValue('300,00');
        expect(within(dialog).getByText(/Zugeordnet 600,00 .* von 1\.000,00 .* noch offen 400,00/)).toBeInTheDocument();
    });

    it('Zuordnen speichert Positionen und Material ohne Plan, zeigt die Abweichung vorab und ruft danach onGeaendert', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (_u: string, init?: RequestInit) => antwort(init?.method === 'POST' ? { ...DATEN, offenAnzahl: 0 }
            : { ...DATEN, rechnungen: [{ ...DATEN.rechnungen[0], zeilen: [], zugeordnet: 0, rest: 1000 }], positionen: DATEN.positionen.map((p) => ({ ...p, ist: null })) }));
        const geaendert = zeige();
        await user.click(await screen.findByRole('button', { name: /Material zur Rechnung RE-2026-77 zuordnen/ }));
        const dialog = await screen.findByRole('dialog');

        await user.clear(within(dialog).getByLabelText('Betrag Rohr 40x40'));
        await user.type(within(dialog).getByLabelText('Betrag Rohr 40x40'), '345');
        expect(within(dialog).getByText(/teurer als geplant: 15 %/)).toBeInTheDocument();
        await user.click(within(dialog).getByRole('checkbox', { name: 'Blech 3 mm gehört zu dieser Rechnung' }));
        await user.type(within(dialog).getByLabelText('Beschreibung Material ohne Plan'), 'Kleinteile im Baumarkt');
        await user.click(within(dialog).getByRole('button', { name: 'Rest hier eintragen' }));
        expect(within(dialog).getByLabelText('Betrag Material ohne Plan')).toHaveValue('655,00');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('POST')).toHaveLength(1));
        expect(aufrufe('POST')[0][0]).toBe('/api/projekte/7/material-abrechnung/50');
        expect(JSON.parse((aufrufe('POST')[0][1] as RequestInit).body as string)).toEqual({ zeilen: [
            { artikelInProjektId: 1, betrag: 345, beschreibung: null }, { artikelInProjektId: null, betrag: 655, beschreibung: 'Kleinteile im Baumarkt' }] });
        await waitFor(() => expect(geaendert).toHaveBeenCalled());
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    });

    it('Zu viel zugeordnet sperrt das Speichern; ohne Beschreibung beim Material ohne Plan wird nachgefragt', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async () => antwort({ ...DATEN, rechnungen: [{ ...DATEN.rechnungen[0], zeilen: [], zugeordnet: 0, rest: 1000 }], positionen: DATEN.positionen.map((p) => ({ ...p, ist: null })) }));
        zeige();
        await user.click(await screen.findByRole('button', { name: /Material zur Rechnung RE-2026-77 zuordnen/ }));
        const dialog = await screen.findByRole('dialog');

        await user.click(within(dialog).getByRole('checkbox', { name: 'Rohr 40x40 gehört zu dieser Rechnung' }));
        await user.click(within(dialog).getByRole('checkbox', { name: 'Blech 3 mm gehört zu dieser Rechnung' }));
        await user.type(within(dialog).getByLabelText('Betrag Material ohne Plan'), '500');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        expect(await within(dialog).findByRole('alert')).toHaveTextContent('kurz beschreiben');

        await user.type(within(dialog).getByLabelText('Beschreibung Material ohne Plan'), 'Kleinteile');
        await user.clear(within(dialog).getByLabelText('Betrag Material ohne Plan'));
        await user.type(within(dialog).getByLabelText('Betrag Material ohne Plan'), '1100');
        expect(within(dialog).getByText(/zu viel/)).toBeInTheDocument();
        expect(within(dialog).getByRole('button', { name: 'Speichern' })).toBeDisabled();
        expect(aufrufe('POST')).toHaveLength(0);
    });

    it('Serverfehler beim Speichern steht im Dialog, der Dialog bleibt offen', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (_u: string, init?: RequestInit) => init?.method === 'POST' ? antwort({ message: 'Die Position gibt es nicht mehr.' }, 400)
            : antwort({ ...DATEN, rechnungen: [{ ...DATEN.rechnungen[0], zeilen: [], zugeordnet: 0, rest: 1000 }], positionen: DATEN.positionen.map((p) => ({ ...p, ist: null })) }));
        zeige();
        await user.click(await screen.findByRole('button', { name: /Material zur Rechnung RE-2026-77 zuordnen/ }));
        const dialog = await screen.findByRole('dialog');

        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Die Position gibt es nicht mehr.');
        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('bereits zugeordnete Rechnung: Dialog zeigt die gespeicherten Beträge zum Ändern, „alles zugeordnet" erscheint ohne Rest', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async () => antwort({ ...DATEN, rechnungen: [{ ...DATEN.rechnungen[0], anteil: 345, rest: 0 }] }));
        zeige();
        expect(await screen.findByText('alles zugeordnet')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: /Material zur Rechnung RE-2026-77 zuordnen/ }));

        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByRole('checkbox', { name: 'Blech 3 mm gehört zu dieser Rechnung' })).toBeChecked();
        expect(within(dialog).getByLabelText('Betrag Blech 3 mm')).toHaveValue('345,00');
        expect(within(dialog).getByRole('checkbox', { name: 'Rohr 40x40 gehört zu dieser Rechnung' })).not.toBeChecked();
    });

    it('nur Administratoren sehen und ändern die Schwelle', async () => {
        const { unmount } = render(<ToastProvider><MaterialAbrechnungPanel projektId={7} /></ToastProvider>);
        await screen.findByTestId('material-abrechnung');
        expect(screen.queryByLabelText('Abweichung markieren ab')).not.toBeInTheDocument();
        unmount();

        admin = true;
        const user = userEvent.setup();
        render(<ToastProvider><MaterialAbrechnungPanel projektId={7} /></ToastProvider>);
        const feld = await screen.findByLabelText('Abweichung markieren ab');
        expect(feld).toHaveValue('5');
        await user.clear(feld);
        await user.type(feld, '10');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT')).toHaveLength(1));
        expect(aufrufe('PUT')[0][0]).toBe('/api/admin/material-abrechnung/schwelle');
        expect(JSON.parse((aufrufe('PUT')[0][1] as RequestInit).body as string)).toEqual({ prozent: 10 });
    });

    it('Ladefehler: Meldung mit dem Satz des Servers', async () => {
        fetcher.mockImplementation(async () => antwort({ message: 'Das Projekt gibt es nicht.' }, 404));
        zeige();

        expect(await screen.findByRole('alert')).toHaveTextContent('Das Projekt gibt es nicht.');
    });

    it('abweichung: ab mehr als der Schwelle, genau die Schwelle nicht; ohne Plan-Preis nie', () => {
        expect(abweichung(300, 345, 5)).toEqual({ prozent: 15, art: 'TEURER' });
        expect(abweichung(200, 180, 5)).toEqual({ prozent: -10, art: 'GUENSTIGER' });
        expect(abweichung(200, 190, 5)).toBeNull();
        expect(abweichung(200, 210, 5)).toBeNull();
        expect(abweichung(200, 211, 5)).toEqual({ prozent: 5.5, art: 'TEURER' });
        expect(abweichung(null, 100, 5)).toBeNull();
        expect(abweichung(0, 100, 5)).toBeNull();
        expect(abweichung(200, 200, 0)).toBeNull();
        expect(abweichung(200, 201, 0)).toEqual({ prozent: 0.5, art: 'TEURER' });
    });
});
