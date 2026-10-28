import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KiAnbieterKarte } from './KiAnbieterKarte';
import { ToastProvider } from '../../ui/toast';

const uebersicht = (aenderung: Record<string, unknown> = {}) => ({
    anbieter: [
        { typ: 'GEMINI', anzeige: 'Gemini (Google)', lokal: false, eingerichtet: true, modell: 'gemini-3-flash-preview', schluesselGesetzt: true },
        { typ: 'LOKAL', anzeige: 'Lokale KI (Ollama u. a.)', lokal: true, eingerichtet: true, modell: 'qwen2.5:7b', schluesselGesetzt: false },
        { typ: 'CLAUDE', anzeige: 'Claude (Anthropic)', lokal: false, eingerichtet: false, modell: 'claude-sonnet-5-5', schluesselGesetzt: false },
        { typ: 'MISTRAL', anzeige: 'Mistral', lokal: false, eingerichtet: true, modell: 'mistral-small-latest', schluesselGesetzt: true },
    ],
    ...aenderung,
});
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let testAntwort: Record<string, unknown>;
const aufrufe = (methode: string, teil: string) => fetcher.mock.calls.filter(([u, i]) => String(u).includes(teil) && (i?.method ?? 'GET') === methode);

beforeEach(() => {
    testAntwort = { ok: true, art: 'OK', meldung: 'Mistral antwortet (Modell mistral-small-latest): OK' };
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url.endsWith('/test')) return antwort(testAntwort);
        if (url.endsWith('/MISTRAL/modelle')) return antwort([{ name: 'mistral-large-latest', anzeigename: 'Mistral Large' }, { name: 'mistral-small-latest', anzeigename: 'Mistral Small' }]);
        if (init?.method === 'PUT') return antwort(uebersicht());
        return antwort(uebersicht());
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><KiAnbieterKarte /></ToastProvider>);

/** Oben ist immer ein Anbieter gewählt (zuerst Gemini); die Zugänge von Claude und Mistral erscheinen erst, wenn man ihn in der Auswahlliste wählt. */
const waehleAnbieter = async (user: ReturnType<typeof userEvent.setup>, name: RegExp) => {
    await user.click(await screen.findByRole('combobox', { name: 'Anbieter wählen' }));
    await user.click(await screen.findByRole('option', { name }));
};

describe('KiAnbieterKarte', () => {
    it('sendet einen neuen Schlüssel, lässt vorhandene unverändert (null) und entfernt auf Wunsch', async () => {
        const user = userEvent.setup();
        zeige();
        await waehleAnbieter(user, /Claude/);
        const claude = await screen.findByTestId('ki-zugang-CLAUDE');
        await user.type(within(claude).getByLabelText('Schlüssel'), 'sk-neu');
        expect(within(claude).getByLabelText('Schlüssel')).toHaveAttribute('type', 'password');
        await waehleAnbieter(user, /Mistral/);
        const mistral = screen.getByTestId('ki-zugang-MISTRAL');
        await user.click(within(mistral).getByLabelText('Gespeicherten Schlüssel entfernen'));
        await user.click(screen.getByRole('button', { name: 'Zugang speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/ki-anbieter')).toHaveLength(1));
        const body = JSON.parse(aufrufe('PUT', '/ki-anbieter')[0][1].body);
        expect(body.claude.schluessel).toBe('sk-neu');
        expect(body.mistral.schluessel).toBe('');
    });

    it('testet nur den gespeicherten Stand und zeigt das Ergebnis', async () => {
        const user = userEvent.setup();
        zeige();
        await waehleAnbieter(user, /Claude/);
        const claudeTest = within(await screen.findByTestId('ki-zugang-CLAUDE')).getByRole('button', { name: 'Claude (Anthropic) testen' });
        expect(claudeTest).toBeDisabled();
        await waehleAnbieter(user, /Mistral/);
        const mistral = await screen.findByTestId('ki-zugang-MISTRAL');

        await user.click(within(mistral).getByRole('button', { name: 'Mistral testen' }));

        expect(await within(mistral).findByRole('status')).toHaveTextContent('Mistral antwortet');
        expect(aufrufe('POST', '/test')).toHaveLength(1);
        expect(JSON.parse(aufrufe('POST', '/test')[0][1].body)).toEqual({ anbieter: 'MISTRAL' });

        await user.type(within(mistral).getByLabelText('Modell'), 'x');
        expect(within(mistral).getByRole('button', { name: 'Mistral testen' })).toBeDisabled();
    });

    it('zeigt einen Fehlertest in Klartext', async () => {
        const user = userEvent.setup();
        testAntwort = { ok: false, art: 'FEHLER', meldung: 'Mistral lehnt den Zugang ab (Fehler 401). Bitte den Schlüssel in den Einstellungen prüfen.' };
        zeige();
        await waehleAnbieter(user, /Mistral/);
        await user.click(within(await screen.findByTestId('ki-zugang-MISTRAL')).getByRole('button', { name: 'Mistral testen' }));
        expect(await screen.findByText(/lehnt den Zugang ab/)).toBeVisible();
    });

    it('zeigt die Fehlermeldung des Servers beim Speichern', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (_u: string, init?: RequestInit) => (init?.method === 'PUT'
            ? antwort({ message: 'Der Modellname für Claude ist ungültig.' }, false, 400) : antwort(uebersicht())));
        zeige();
        await waehleAnbieter(user, /Claude/);
        await user.type(await screen.findByLabelText('Schlüssel', { selector: '#ki-CLAUDE-schluessel' }), 'x');
        await user.click(screen.getByRole('button', { name: 'Zugang speichern' }));
        expect(await screen.findByText('Der Modellname für Claude ist ungültig.')).toBeVisible();
    });

    it('lädt die Modelle des Anbieters als Vorschläge für das Feld „Modell"', async () => {
        const user = userEvent.setup();
        zeige();
        await waehleAnbieter(user, /Mistral/);
        await screen.findByTestId('ki-zugang-MISTRAL');

        await user.click(screen.getByRole('button', { name: 'Verfügbare Modelle von Mistral laden' }));

        expect(await screen.findByText('2 Modelle zur Auswahl im Feld „Modell".')).toBeVisible();
        expect(aufrufe('GET', '/ki-anbieter/MISTRAL/modelle')).toHaveLength(1);
        const liste = document.getElementById('ki-MISTRAL-modelle')!;
        expect([...liste.querySelectorAll('option')].map(o => o.getAttribute('value'))).toEqual(['mistral-large-latest', 'mistral-small-latest']);
        expect(screen.getByLabelText('Modell', { selector: '#ki-MISTRAL-modell' })).toHaveAttribute('list', 'ki-MISTRAL-modelle');
    });

    it('ohne gespeicherten Schlüssel ist „Modelle laden" gesperrt und fragt den Anbieter nicht', async () => {
        const user = userEvent.setup();
        zeige();
        await waehleAnbieter(user, /Claude/);
        await screen.findByTestId('ki-zugang-CLAUDE');
        expect(screen.getByRole('button', { name: 'Verfügbare Modelle von Claude (Anthropic) laden' })).toBeDisabled();
        await waehleAnbieter(user, /Mistral/);
        expect(await screen.findByRole('button', { name: 'Verfügbare Modelle von Mistral laden' })).toBeEnabled();
        expect(aufrufe('GET', '/modelle')).toHaveLength(0);
    });

    it('zeigt oben den Zugang des gewählten Anbieters; die Auswahlliste nennt den Stand jedes Anbieters', async () => {
        const user = userEvent.setup();
        render(<ToastProvider><KiAnbieterKarte geminiZugang={<p>Gemini-Zugang</p>} lokalZugang={<p>Lokale-KI-Zugang</p>} /></ToastProvider>);
        const ueberschriften = (await screen.findAllByRole('heading', { level: 3 })).map(h => h.textContent);
        expect(ueberschriften).toEqual(['1. Anbieter und Zugang']);
        expect(screen.getByText('Gemini-Zugang')).toBeVisible();

        await user.click(screen.getByRole('combobox', { name: 'Anbieter wählen' }));
        expect(await screen.findByRole('option', { name: /Claude \(Anthropic\) \(noch nicht eingerichtet\)/ })).toBeVisible();
        expect(screen.getByRole('option', { name: /Mistral \(eingerichtet\)/ })).toBeVisible();
        await user.click(screen.getByRole('option', { name: /Lokale KI/ }));
        expect(await screen.findByText('Lokale-KI-Zugang')).toBeVisible();
        expect(screen.queryByText('Gemini-Zugang')).toBeNull();
    });

    it('speichert nur den Zugang und lässt die Funktionen unberührt (die haben ihre eigene Tabelle)', async () => {
        const user = userEvent.setup();
        zeige();
        await waehleAnbieter(user, /Claude/);
        await user.type(await screen.findByLabelText('Schlüssel', { selector: '#ki-CLAUDE-schluessel' }), 'sk-neu');
        await user.click(screen.getByRole('button', { name: 'Zugang speichern' }));
        await waitFor(() => expect(aufrufe('PUT', '/ki-anbieter')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT', '/ki-anbieter')[0][1].body)).not.toHaveProperty('funktionen');
    });
});
