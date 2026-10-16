import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KiFunktionenKarte } from './KiFunktionenKarte';
import { ToastProvider } from '../../ui/toast';

/** Nur Dummy-Daten. */
const anbieter = [
    { typ: 'GEMINI', anzeige: 'Gemini (Google)', lokal: false, eingerichtet: true },
    { typ: 'LOKAL', anzeige: 'Lokale KI (Ollama u. a.)', lokal: true, eingerichtet: true },
    { typ: 'CLAUDE', anzeige: 'Claude (Anthropic)', lokal: false, eingerichtet: false },
    { typ: 'MISTRAL', anzeige: 'Mistral', lokal: false, eingerichtet: true },
];
const standards = { GEMINI: 'gemini-3-flash-preview', LOKAL: 'qwen2.5:7b', CLAUDE: 'claude-sonnet-5-5', MISTRAL: 'mistral-small-latest' };
const keine = { GEMINI: '', LOKAL: '', CLAUDE: '', MISTRAL: '' };

const uebersicht = (aenderung: Record<string, unknown> = {}) => ({
    funktionen: [
        { funktion: 'hilfe', anzeige: 'KI-Hilfe (Chat)', anbieter: 'GEMINI', datenschutz: 'CLOUD_ERLAUBT', modell: 'gemini-3-flash-preview', standardModell: 'gemini-3-flash-preview', modellGewaehlt: false, auswahl: keine, standards },
        { funktion: 'assistent', anzeige: 'KI-Assistent mit Auskünften', anbieter: 'LOKAL', datenschutz: 'NUR_LOKAL', modell: 'qwen2.5:7b', standardModell: 'qwen2.5:7b', modellGewaehlt: false, auswahl: keine, standards },
        { funktion: 'pdf-auslesen', anzeige: 'PDF-Rechnungen und -Angebote auslesen', anbieter: 'GEMINI', datenschutz: 'CLOUD_ERLAUBT', modell: 'gemini-3-flash-preview', standardModell: 'gemini-3-flash-preview', modellGewaehlt: false, auswahl: keine, standards },
        { funktion: 'email-text', anzeige: 'E-Mail-Texte verbessern', anbieter: 'MISTRAL', datenschutz: 'CLOUD_ERLAUBT', modell: 'mistral-large-latest', standardModell: 'mistral-small-latest', modellGewaehlt: true,
            auswahl: { ...keine, MISTRAL: 'mistral-large-latest' }, standards },
    ],
    anbieter,
    ...aenderung,
});
const gemini = (aenderung: Record<string, unknown> = {}) => ({
    funktionen: [
        { schluessel: 'ki-hilfe', anzeige: 'KI-Hilfe (Chat)', modell: 'gemini-3-flash-preview', standard: 'gemini-3-flash-preview', gewaehlt: false },
        { schluessel: 'scanner', anzeige: 'Scanner', modell: 'gemini-3-flash-preview', standard: 'gemini-3-flash-preview', gewaehlt: false },
        { schluessel: 'pdf-extractor', anzeige: 'PDF-Auslesen', modell: 'gemini-3-flash-preview', standard: 'gemini-3-flash-preview', gewaehlt: false },
        { schluessel: 'pro', anzeige: 'Schwierige Fälle (genaues Modell)', modell: 'gemini-3.1-pro-preview', standard: 'gemini-3.1-pro-preview', gewaehlt: false },
    ],
    websuche: true,
    ...aenderung,
});
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let testAntwort: Record<string, unknown>;
const aufrufe = (methode: string, teil: string) => fetcher.mock.calls.filter(([u, i]) => String(u).endsWith(teil) && (i?.method ?? 'GET') === methode);
const body = (methode: string, teil: string) => JSON.parse(aufrufe(methode, teil)[0][1].body);

beforeEach(() => {
    testAntwort = { ok: true, art: 'OK', meldung: 'Mistral antwortet (Modell mistral-large-latest): OK' };
    fetcher = vi.fn(async (url: string) => {
        if (url.endsWith('/test')) return antwort(testAntwort);
        if (url.endsWith('/MISTRAL/modelle')) return antwort([{ name: 'mistral-large-latest', anzeigename: 'Mistral Large' }, { name: 'ministral-3b-latest', anzeigename: 'ministral-3b-latest' }]);
        if (url.endsWith('/ki-modelle')) return antwort(gemini());
        return antwort(uebersicht());
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><KiFunktionenKarte /></ToastProvider>);
const zeile = async (id: string) => within(await screen.findByTestId(id.startsWith('ki-') ? id : `ki-anbieter-${id}`));

describe('KiFunktionenKarte: eine Tabelle für Anbieter, Modell und Datenschutz', { timeout: 30000 }, () => {
    it('zeigt alle Funktionen in einer Tabelle mit Spaltenüberschriften, auch die, die noch fest bei Gemini laufen', async () => {
        zeige();
        await screen.findByTestId('ki-funktionen-karte');
        const kopf = screen.getAllByRole('columnheader').map(h => h.textContent?.trim());
        expect(kopf.slice(0, 4)).toEqual(['Funktion', 'Anbieter', 'Modell', 'Datenschutz']);

        const hilfe = await zeile('hilfe');
        expect(hilfe.getByRole('combobox', { name: 'Anbieter für KI-Hilfe (Chat)' })).toBeVisible();
        expect(hilfe.getByRole('combobox', { name: 'Modell für KI-Hilfe (Chat)' })).toBeVisible();
        expect(hilfe.getByRole('combobox', { name: 'Datenschutz für KI-Hilfe (Chat)' })).toBeVisible();

        const scanner = await zeile('ki-modell-scanner');
        expect(scanner.getByText('Gemini (fest)')).toBeVisible();
        expect(scanner.getByText('Cloud (Google)')).toBeVisible();
        expect(scanner.getByRole('combobox', { name: 'Modell für Scanner' })).toBeVisible();
        // Funktionen mit Anbieterwahl stehen nicht doppelt in der Tabelle
        expect(screen.getAllByText('KI-Hilfe (Chat)')).toHaveLength(1);
        // PDF-Auslesen ist wählbar (#232) und steht nicht zusätzlich als „Gemini (fest)“ da
        expect((await zeile('pdf-auslesen')).getByRole('combobox', { name: 'Anbieter für PDF-Rechnungen und -Angebote auslesen' })).toBeVisible();
        expect(screen.queryByTestId('ki-modell-pdf-extractor')).not.toBeInTheDocument();
    });

    it('erklärt je Funktion, wohin die Daten gehen', async () => {
        zeige();
        expect((await zeile('assistent')).getByText('Daten bleiben im Haus')).toBeVisible();
        expect((await zeile('hilfe')).getByText('Fragen gehen an den Anbieter')).toBeVisible();
    });

    it('sperrt Cloud-Anbieter bei „Nur lokal“', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('ki-anbieter-assistent');
        await user.click(screen.getByRole('combobox', { name: 'Anbieter für KI-Assistent mit Auskünften' }));
        expect(await screen.findByRole('option', { name: /Gemini/ })).toHaveAttribute('aria-disabled', 'true');
        expect(screen.getByRole('option', { name: /Lokale KI/ })).not.toHaveAttribute('aria-disabled', 'true');
    });

    it('zeigt das gewählte Modell oder den Standard, und „Speichern“ geht erst nach einer Änderung', async () => {
        zeige();
        const mail = await zeile('email-text');
        expect(mail.getByRole('combobox', { name: 'Modell für E-Mail-Texte verbessern' })).toHaveTextContent('mistral-large-latest');
        const hilfe = await zeile('hilfe');
        expect(hilfe.getByRole('combobox', { name: 'Modell für KI-Hilfe (Chat)' })).toHaveTextContent('Standard (gemini-3-flash-preview)');
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    });

    it('lädt die Modelle eines Anbieters und bietet sie in der Zeile zur Auswahl an', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('ki-funktionen-karte');
        await user.click(screen.getByRole('button', { name: 'Verfügbare Modelle von Mistral laden' }));
        await waitFor(() => expect(aufrufe('GET', '/ki-anbieter/MISTRAL/modelle')).toHaveLength(1));

        await user.click((await zeile('email-text')).getByRole('combobox', { name: 'Modell für E-Mail-Texte verbessern' }));
        expect(await screen.findByRole('option', { name: 'Mistral Large (mistral-large-latest)' })).toBeVisible();
        expect(screen.getByRole('option', { name: 'ministral-3b-latest' })).toBeVisible();
        expect(screen.getByRole('option', { name: 'Standard (mistral-small-latest)' })).toBeVisible();
    });

    it('sperrt „Modelle laden“ und „Testen“ für einen Anbieter ohne Zugang', async () => {
        zeige();
        await screen.findByTestId('ki-funktionen-karte');
        expect(screen.getByRole('button', { name: 'Verfügbare Modelle von Claude (Anthropic) laden' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Verfügbare Modelle von Mistral laden' })).toBeEnabled();
        expect(aufrufe('GET', '/modelle')).toHaveLength(0);
    });

    it('speichert Anbieter, Datenschutz und Modell je Funktion; „Nur lokal“ stellt auf die lokale KI', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('ki-anbieter-hilfe');
        await user.click(screen.getByRole('combobox', { name: 'Datenschutz für KI-Hilfe (Chat)' }));
        await user.click(await screen.findByRole('option', { name: 'Nur lokal' }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/ki-anbieter')).toHaveLength(1));
        const b = body('PUT', '/ki-anbieter');
        expect(b.funktionen.hilfe).toEqual({ anbieter: 'LOKAL', datenschutz: 'NUR_LOKAL', modell: '' });
        expect(b.funktionen['email-text']).toEqual({ anbieter: 'MISTRAL', datenschutz: 'CLOUD_ERLAUBT', modell: 'mistral-large-latest' });
        // Zugangsdaten gehören der Karte darüber und werden hier nie mitgeschickt
        expect(b).not.toHaveProperty('claude');
        expect(b).not.toHaveProperty('mistral');
    });

    it('beim Wechsel des Anbieters gilt dessen gespeicherte Wahl, nicht die des alten', async () => {
        const user = userEvent.setup();
        zeige();
        const mail = await zeile('email-text');
        await user.click(mail.getByRole('combobox', { name: 'Anbieter für E-Mail-Texte verbessern' }));
        await user.click(await screen.findByRole('option', { name: /Lokale KI/ }));
        expect(mail.getByRole('combobox', { name: 'Modell für E-Mail-Texte verbessern' })).toHaveTextContent('Standard (qwen2.5:7b)');
        await user.click(mail.getByRole('combobox', { name: 'Anbieter für E-Mail-Texte verbessern' }));
        await user.click(await screen.findByRole('option', { name: /Mistral/ }));
        expect(mail.getByRole('combobox', { name: 'Modell für E-Mail-Texte verbessern' })).toHaveTextContent('mistral-large-latest');
    });

    it('speichert die Modelle der festen Gemini-Funktionen und die Websuche über die bestehende Modellwahl', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('ki-modell-scanner');
        await user.click(screen.getByRole('checkbox', { name: /KI-Hilfe darf im Internet suchen/ }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/ki-modelle')).toHaveLength(1));
        expect(body('PUT', '/ki-modelle')).toEqual({ modelle: { scanner: '', pro: '' }, websuche: false });
    });

    it('testet das Modell der Zeile und zeigt das Ergebnis darunter', async () => {
        const user = userEvent.setup();
        zeige();
        const mail = await zeile('email-text');
        await user.click(mail.getByRole('button', { name: 'Modell für E-Mail-Texte verbessern testen' }));

        expect(await screen.findByRole('status')).toHaveTextContent('Mistral antwortet');
        expect(body('POST', '/ki-anbieter/test')).toEqual({ anbieter: 'MISTRAL', modell: 'mistral-large-latest' });
    });

    it('testet ohne Wahl den Standard und zeigt einen Fehler in Klartext', async () => {
        const user = userEvent.setup();
        testAntwort = { ok: false, art: 'FEHLER', meldung: 'Gemini lehnt den Zugang ab (Fehler 403).' };
        zeige();
        await user.click((await zeile('hilfe')).getByRole('button', { name: 'Modell für KI-Hilfe (Chat) testen' }));
        expect(await screen.findByText(/lehnt den Zugang ab/)).toBeVisible();
        expect(body('POST', '/ki-anbieter/test')).toEqual({ anbieter: 'GEMINI', modell: 'gemini-3-flash-preview' });
    });

    it('zeigt einen Fehler des Servers beim Speichern und lässt die Eingaben stehen', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
            if (init?.method === 'PUT') return antwort({ message: 'Der Modellname für „E-Mail-Texte verbessern“ ist ungültig.' }, false, 400);
            return url.endsWith('/ki-modelle') ? antwort(gemini()) : antwort(uebersicht());
        });
        zeige();
        await screen.findByTestId('ki-anbieter-hilfe');
        await user.click(screen.getByRole('combobox', { name: 'Datenschutz für KI-Hilfe (Chat)' }));
        await user.click(await screen.findByRole('option', { name: 'Nur lokal' }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Der Modellname für „E-Mail-Texte verbessern“ ist ungültig.')).toBeVisible();
        expect(screen.getByRole('combobox', { name: 'Datenschutz für KI-Hilfe (Chat)' })).toHaveTextContent('Nur lokal');
    });

    it('zeigt einen Ladefehler als Meldung', async () => {
        fetcher.mockImplementation(async () => antwort({ message: 'Nicht erlaubt.' }, false, 403));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('Nicht erlaubt.');
    });
});
