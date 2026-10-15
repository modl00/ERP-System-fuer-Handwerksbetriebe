import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KostensaetzeTab } from './KostensaetzeTab';
import { ToastProvider } from '../../components/ui/toast';

/** Nur Dummy-Daten. */
const zeile = (aenderung: Record<string, unknown> = {}) => ({ art: 'BETRIEB', name: 'Ganzer Betrieb', anzahl: 3, lohnkosten: 120000, stunden: 3100, personalkostensatz: 38.71, umlage: 9.68, kostensatz: 48.39,
    wagnisBetrag: 2.42, gewinnBetrag: 5.08, verrechnungssatz: 55.89, hinweis: null, ...aenderung });
const ergebnis = (aenderung: Record<string, unknown> = {}) => ({
    jahr: 2026, modus: 'HOCHRECHNUNG', lohnkostenGesamt: 120000, stundenGesamt: 3100, gemeinkostenGesamt: 30000, umlage: 9.68, wagnisProzent: 5, gewinnProzent: 10,
    betrieb: zeile(), gruppen: [zeile({ art: 'GRUPPE', name: 'Meister', anzahl: 1, personalkostensatz: 40, kostensatz: 49.68, verrechnungssatz: 57.3 })],
    personen: [zeile({ art: 'PERSON', name: 'Anna Beispiel', anzahl: 1, personalkostensatz: null, kostensatz: null, wagnisBetrag: null, gewinnBetrag: null, verrechnungssatz: null, hinweis: 'Keine verkäuflichen Stunden: Der Lohn dieser Zeile steckt im Betriebsdurchschnitt.' })],
    hinweise: [], gemeinkosten: { kostenstellen: [], gesamtbetrag: null, gesamtbetragGilt: false, enthaeltSchaetzwerte: false, sollVorhanden: false }, ...aenderung,
});
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten, text: async () => JSON.stringify(daten) });

let fetcher: ReturnType<typeof vi.fn>;
let antwortDaten: () => ReturnType<typeof antwort>;
const alleUrls = () => fetcher.mock.calls.map(([u]) => String(u));
/** Nur die Rechenaufrufe (ohne Vorgaben und Schätzung der internen Zeit). */
const urls = () => alleUrls().filter(u => u.includes('/kostensaetze?'));
let vorgaben: { wagnis: number | null; gewinn: number | null; interneZeit: number | null };
let schaetzung: Record<string, unknown>;

beforeEach(() => {
    antwortDaten = () => antwort(ergebnis());
    vorgaben = { wagnis: null, gewinn: null, interneZeit: null };
    schaetzung = { jahr: 2025, prozent: 7, interneStunden: 140, sollstunden: 2000, hinweis: null };
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const u = String(url);
        if (u.includes('/api/kostenrechnung/vorgaben')) {
            if (init?.method === 'PUT') vorgaben = JSON.parse(init.body as string);
            return antwort(vorgaben);
        }
        if (u.includes('/interne-zeit-schaetzung')) return antwort(schaetzung);
        return antwortDaten();
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('KostensaetzeTab', { timeout: 30000 }, () => {
    it('zeigt Betrieb, Qualifikation und Personen mit der Herleitung bis zum Verrechnungssatz', async () => {
        render(<ToastProvider><KostensaetzeTab /></ToastProvider>);
        const betrieb = await screen.findByTestId('zeile-Ganzer Betrieb');
        expect(betrieb).toHaveTextContent('38,71');
        expect(betrieb).toHaveTextContent('9,68');
        expect(betrieb).toHaveTextContent('48,39');
        expect(betrieb).toHaveTextContent('55,89');
        expect(betrieb).toHaveTextContent('(3 Personen)');
        expect(screen.getByTestId('zeile-Meister')).toHaveTextContent('57,30');
        const anna = screen.getByTestId('zeile-Anna Beispiel');
        expect(anna).toHaveTextContent('Keine verkäuflichen Stunden');
        expect(within(anna).getAllByText('–').length).toBeGreaterThan(0);
        const summen = screen.getByTestId('summen');
        expect(summen).toHaveTextContent('120.000,00');
        expect(summen).toHaveTextContent('30.000,00');
        expect(screen.getByText(/Hochrechnung 2026/)).toBeVisible();
        expect(screen.getByText(/Es wird nichts verändert/)).toBeVisible();
    });

    it('schickt Jahr, Wagnis und Gewinn mit und rechnet nach einer Änderung neu', async () => {
        const user = userEvent.setup();
        render(<ToastProvider><KostensaetzeTab /></ToastProvider>);
        await screen.findByTestId('zeile-Ganzer Betrieb');
        expect(urls()[0]).toContain('/api/kostenrechnung/kostensaetze?');
        expect(urls()[0]).toContain('wagnis=0');
        expect(urls()[0]).toContain('gewinn=10');

        const wagnis = screen.getByLabelText('Wagnis (%)');
        await user.clear(wagnis);
        await user.type(wagnis, '5');
        await waitFor(() => expect(urls().some(u => u.includes('wagnis=5'))).toBe(true));
    });

    it('zeigt Fehler des Servers und Hinweise zu fehlenden Daten', async () => {
        antwortDaten = () => antwort(ergebnis({ hinweise: ['Für dieses Jahr fehlen Angaben (2). Die Sätze sind dann nur eine Schätzung.'] }));
        render(<ToastProvider><KostensaetzeTab /></ToastProvider>);
        expect(await screen.findByText(/nur eine Schätzung/)).toBeVisible();
    });

    it('ein Fehler beim Berechnen steht als Meldung da', async () => {
        antwortDaten = () => antwort({ message: 'Das Wagnis muss zwischen 0 und 100 Prozent liegen.' }, false, 400);
        render(<ToastProvider><KostensaetzeTab /></ToastProvider>);
        expect(await screen.findByRole('alert')).toHaveTextContent('Das Wagnis muss zwischen 0 und 100 Prozent liegen.');
    });

    it('eine unmögliche Eingabe wird gemeldet und nicht an den Server geschickt', async () => {
        const user = userEvent.setup();
        render(<ToastProvider><KostensaetzeTab /></ToastProvider>);
        await screen.findByTestId('zeile-Ganzer Betrieb');
        const vorher = urls().length;
        const gewinn = screen.getByLabelText('Gewinn (%)');
        await user.clear(gewinn);
        await user.type(gewinn, '500');
        expect(await screen.findByText(/Der Gewinn/)).toBeVisible();
        await new Promise(r => setTimeout(r, 600));
        expect(urls().length).toBe(vorher);
    });

    it('füllt die Felder mit den gespeicherten Vorgaben, bevor zum ersten Mal gerechnet wird', async () => {
        vorgaben = { wagnis: 3, gewinn: 12, interneZeit: 6 };
        render(<ToastProvider><KostensaetzeTab /></ToastProvider>);
        await screen.findByTestId('zeile-Ganzer Betrieb');
        expect(screen.getByLabelText('Wagnis (%)')).toHaveValue('3');
        expect(screen.getByLabelText('Gewinn (%)')).toHaveValue('12');
        expect(screen.getByLabelText('Interne Zeit (%)')).toHaveValue('6');
        expect(urls()).toHaveLength(1);
        expect(urls()[0]).toContain('wagnis=3');
        expect(urls()[0]).toContain('gewinn=12');
        expect(urls()[0]).toContain('interneQuote=6');
    });

    it('speichert die Werte erst auf Knopfdruck als Vorgabe', async () => {
        const user = userEvent.setup();
        render(<ToastProvider><KostensaetzeTab /></ToastProvider>);
        await screen.findByTestId('zeile-Ganzer Betrieb');
        const wagnis = screen.getByLabelText('Wagnis (%)');
        await user.clear(wagnis);
        await user.type(wagnis, '4');
        expect(alleUrls().some(u => u.includes('/vorgaben') && fetcher.mock.calls.some(([x, i]) => String(x).includes('/vorgaben') && (i as RequestInit | undefined)?.method === 'PUT'))).toBe(false);

        await user.click(screen.getByRole('button', { name: 'Als Vorgabe speichern' }));

        await waitFor(() => expect(vorgaben).toEqual({ wagnis: 4, gewinn: 10, interneZeit: null }));
        expect(await screen.findByRole('button', { name: 'Als Vorgabe gespeichert' })).toBeDisabled();
    });

    it('schätzt die interne Zeit aus den gebuchten Stunden und übernimmt sie nur auf Wunsch ins Feld', async () => {
        const user = userEvent.setup();
        render(<ToastProvider><KostensaetzeTab /></ToastProvider>);
        await screen.findByTestId('zeile-Ganzer Betrieb');

        await user.click(screen.getByRole('button', { name: /Interne Zeit aus gebuchten Stunden schätzen/ }));
        const kasten = await screen.findByTestId('interne-schaetzung');
        expect(kasten).toHaveTextContent('7 %');
        expect(kasten).toHaveTextContent('140 von 2.000 Sollstunden');
        expect(alleUrls().some(u => u.includes('interne-zeit-schaetzung?jahr=2025'))).toBe(true);
        expect(screen.getByLabelText('Interne Zeit (%)')).toHaveValue('');

        await user.click(within(kasten).getByRole('button', { name: 'In das Feld übernehmen' }));
        expect(screen.getByLabelText('Interne Zeit (%)')).toHaveValue('7');
    });

    it('ohne mögliche Schätzung steht der Grund da', async () => {
        schaetzung = { jahr: 2025, prozent: null, interneStunden: null, sollstunden: null, hinweis: 'Für 2025 sind keine Zeitbuchungen vorhanden.' };
        const user = userEvent.setup();
        render(<ToastProvider><KostensaetzeTab /></ToastProvider>);
        await screen.findByTestId('zeile-Ganzer Betrieb');
        await user.click(screen.getByRole('button', { name: /Interne Zeit aus gebuchten Stunden schätzen/ }));
        expect(await screen.findByTestId('interne-schaetzung')).toHaveTextContent('keine Zeitbuchungen');
    });
});
