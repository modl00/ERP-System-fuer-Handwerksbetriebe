import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { GemeinkostenKarte } from './GemeinkostenKarte';
import type { Gemeinkosten } from './kostensaetzeApi';

/** Nur Dummy-Daten. */
const daten = (aenderung: Partial<Gemeinkosten> = {}): Gemeinkosten => ({
    kostenstellen: [
        { id: 1, name: 'Miete', soll: 6000, ist: 500, verwendet: 6000, sollGilt: true },
        { id: 2, name: 'Telefon', soll: null, ist: 240, verwendet: 240, sollGilt: false },
    ],
    gesamtbetrag: null, gesamtbetragGilt: false, enthaeltSchaetzwerte: true, sollVorhanden: true, ...aenderung,
});
const antwort = (body: unknown, ok = true, status = 200) => ({ ok, status, json: async () => body, text: async () => JSON.stringify(body) }) as Response;

let fetcher: ReturnType<typeof vi.fn>;
const aufrufe = (methode: string, teil: string) => fetcher.mock.calls.filter(([u, i]) => String(u).includes(teil) && (i?.method ?? 'GET') === methode);

beforeEach(() => {
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url).endsWith('/gemeinkosten-schaetzung') && (init?.method ?? 'GET') === 'GET') return antwort({ jahresbetrag: null });
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (d = daten(), rueckblick = false, onGeaendert = vi.fn()) =>
    render(<ToastProvider><GemeinkostenKarte gemeinkosten={d} rueckblick={rueckblick} onGeaendert={onGeaendert} /></ToastProvider>);

describe('GemeinkostenKarte', { timeout: 30000 }, () => {
    it('zeigt je Kostenstelle Schätzung, echte Kosten und den Rechenwert und sagt, wann die Schätzung gilt', async () => {
        zeige();
        const miete = await screen.findByTestId('gemeinkosten-Miete');
        expect(miete).toHaveTextContent('500,00');
        expect(miete).toHaveTextContent('6.000,00');
        expect(miete).toHaveTextContent('Schätzung gilt');
        expect(screen.getByTestId('gemeinkosten-Telefon')).not.toHaveTextContent('Schätzung gilt');
        expect(screen.getByText('Enthält Schätzwerte')).toBeInTheDocument();
    });

    it('speichert die Schätzung einer Kostenstelle und meldet die Änderung', async () => {
        const onGeaendert = vi.fn();
        zeige(daten(), false, onGeaendert);
        const telefon = await screen.findByTestId('gemeinkosten-Telefon');

        await userEvent.type(within(telefon).getByLabelText(/Geschätzt im Jahr für Telefon/), '1200');
        await userEvent.click(within(telefon).getByRole('button', { name: 'Schätzung für Telefon speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/kostenstellen/2/soll')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT', '/kostenstellen/2/soll')[0][1].body)).toEqual({ jahresSoll: 1200 });
        await waitFor(() => expect(onGeaendert).toHaveBeenCalled());
    });

    it('löscht die Schätzung, wenn das Feld geleert wird', async () => {
        zeige();
        const miete = await screen.findByTestId('gemeinkosten-Miete');
        await userEvent.clear(within(miete).getByLabelText(/Geschätzt im Jahr für Miete/));
        await userEvent.click(within(miete).getByRole('button', { name: 'Schätzung für Miete speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/kostenstellen/1/soll')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT', '/kostenstellen/1/soll')[0][1].body)).toEqual({ jahresSoll: null });
    });

    it('zeigt die Meldung des Servers, wenn das Speichern abgelehnt wird', async () => {
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
            if ((init?.method ?? 'GET') === 'PUT') return antwort({ message: 'Einen geschätzten Jahresbetrag gibt es nur für Gemeinkosten (Fixkosten).' }, false, 400);
            return antwort({ jahresbetrag: null });
        });
        zeige();
        const telefon = await screen.findByTestId('gemeinkosten-Telefon');
        await userEvent.type(within(telefon).getByLabelText(/Geschätzt im Jahr für Telefon/), '5');
        await userEvent.click(within(telefon).getByRole('button', { name: 'Schätzung für Telefon speichern' }));

        expect(await screen.findByText(/nur für Gemeinkosten/)).toBeInTheDocument();
    });

    it('der Speichern-Knopf bleibt aus, solange nichts geändert ist oder der Betrag ungültig ist', async () => {
        zeige();
        const miete = await screen.findByTestId('gemeinkosten-Miete');
        const knopf = within(miete).getByRole('button', { name: 'Schätzung für Miete speichern' });
        expect(knopf).toBeDisabled();

        await userEvent.type(within(miete).getByLabelText(/Geschätzt im Jahr für Miete/), '9');
        expect(knopf).toBeEnabled();
    });

    it('der Gesamtbetrag ruht, solange bei einer Kostenstelle eine Schätzung steht', async () => {
        zeige(daten({ sollVorhanden: true }));
        const feld = await screen.findByLabelText(/alle Gemeinkosten zusammen/);
        expect(feld).toBeDisabled();
        expect(screen.getByText(/ruht, solange bei einer Kostenstelle eine Schätzung steht/)).toBeInTheDocument();
    });

    it('ohne Schätzung je Kostenstelle lässt sich ein Gesamtbetrag speichern', async () => {
        zeige(daten({ sollVorhanden: false, enthaeltSchaetzwerte: false, kostenstellen: [] }));
        const feld = await screen.findByLabelText(/alle Gemeinkosten zusammen/);
        expect(feld).toBeEnabled();

        await userEvent.type(feld, '36000');
        await userEvent.click(screen.getByRole('button', { name: 'Gesamtbetrag speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/gemeinkosten-schaetzung')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT', '/gemeinkosten-schaetzung')[0][1].body)).toEqual({ jahresbetrag: 36000 });
        expect(screen.getByText(/Es gibt noch keine Kostenstelle für Gemeinkosten/)).toBeInTheDocument();
    });

    it('lädt einen schon gespeicherten Gesamtbetrag ins Feld', async () => {
        fetcher.mockImplementation(async () => antwort({ jahresbetrag: 24000 }));
        zeige(daten({ sollVorhanden: false, kostenstellen: [] }));

        await waitFor(() => expect((screen.getByLabelText(/alle Gemeinkosten zusammen/) as HTMLInputElement).value).toBe('24000'));
    });

    it('sagt bei einem Rückblick, dass vergangene Jahre nur die echten Kosten zählen', async () => {
        zeige(daten(), true);
        expect(await screen.findByText(/Für vergangene Jahre zählen nur die echten Kosten/)).toBeInTheDocument();
    });
});
