import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LokaleKiKarte } from './LokaleKiKarte';
import { ToastProvider } from '../../ui/toast';

const konfig = (aenderung: Record<string, unknown> = {}) => ({ adresse: '', modell: '', schluesselGesetzt: false, hilfeLokal: false, ...aenderung });
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let gespeichert: ReturnType<typeof konfig>;
const aufrufe = (methode: string, teil: string) => fetcher.mock.calls.filter(([u, i]) => String(u).includes(teil) && (i?.method ?? 'GET') === methode);

beforeEach(() => {
    gespeichert = konfig();
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (url.endsWith('/modelle')) return antwort(['llama3.1:8b', 'qwen2.5:7b']);
        if (url.endsWith('/test')) return antwort({ ok: true, art: 'OK', meldung: 'Die lokale KI antwortet (Modell qwen2.5:7b): OK' });
        if (methode === 'PUT') {
            const b = JSON.parse(init!.body as string);
            gespeichert = konfig({ adresse: b.adresse, modell: b.modell, hilfeLokal: b.hilfeLokal, schluesselGesetzt: b.schluessel === null ? gespeichert.schluesselGesetzt : b.schluessel !== '' });
            return antwort(gespeichert);
        }
        return antwort(gespeichert);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><LokaleKiKarte /></ToastProvider>);

describe('LokaleKiKarte', () => {
    it('speichert Adresse, Modell und die Umstellung der KI-Hilfe', async () => {
        const user = userEvent.setup();
        zeige();
        await user.type(await screen.findByLabelText('Adresse'), 'http://192.168.178.50:11434');
        await user.type(screen.getByLabelText('Modell'), 'qwen2.5:7b');
        await user.click(screen.getByLabelText(/KI-Hilfe \(Chat\) nutzt die lokale KI/));

        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(aufrufe('PUT', '/ki-lokal')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT', '/ki-lokal')[0][1].body)).toEqual({
            adresse: 'http://192.168.178.50:11434', modell: 'qwen2.5:7b', hilfeLokal: true, schluessel: null,
        });
    });

    it('testet erst nach dem Speichern, denn getestet wird der gespeicherte Stand', async () => {
        const user = userEvent.setup();
        gespeichert = konfig({ adresse: 'http://pc:11434', modell: 'qwen2.5:7b' });
        zeige();
        const testen = await screen.findByRole('button', { name: 'Testen' });
        expect(testen).toBeEnabled();

        await user.type(screen.getByLabelText('Modell'), 'x');
        expect(testen).toBeDisabled();

        await user.clear(screen.getByLabelText('Modell'));
        await user.type(screen.getByLabelText('Modell'), 'qwen2.5:7b');
        await user.click(screen.getByRole('button', { name: 'Testen' }));
        expect(await screen.findByRole('status')).toHaveTextContent('Die lokale KI antwortet');
        expect(aufrufe('POST', '/test')).toHaveLength(1);
    });

    it('lädt die Modelle des Servers zur Auswahl', async () => {
        const user = userEvent.setup();
        gespeichert = konfig({ adresse: 'http://pc:11434' });
        const { container } = zeige();

        await user.click(await screen.findByRole('button', { name: /Modelle laden/ }));

        await waitFor(() => expect(container.querySelector('datalist option[value="llama3.1:8b"]')).not.toBeNull());
    });

    it('zeigt den Schlüssel nie an und sendet ihn nur, wenn er neu eingegeben oder entfernt wird', async () => {
        const user = userEvent.setup();
        gespeichert = konfig({ adresse: 'http://pc:11434', modell: 'm', schluesselGesetzt: true });
        zeige();
        const feld = await screen.findByLabelText('Schlüssel (optional)');
        expect(feld).toHaveValue('');
        expect(feld).toHaveAttribute('type', 'password');

        await user.click(screen.getByLabelText('Gespeicherten Schlüssel entfernen'));
        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(aufrufe('PUT', '/ki-lokal')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT', '/ki-lokal')[0][1].body).schluessel).toBe('');
    });

    it('zeigt einen Fehler beim Laden als Hinweis', async () => {
        fetcher.mockImplementationOnce(async () => antwort({ message: 'kaputt' }, false, 500));
        zeige();
        expect(await screen.findByRole('alert')).toBeVisible();
    });
});
