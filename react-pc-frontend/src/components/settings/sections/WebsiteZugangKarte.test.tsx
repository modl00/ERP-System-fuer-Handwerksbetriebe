import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WebsiteZugangKarte } from './WebsiteZugangKarte';
import { ToastProvider } from '../../ui/toast';

const konfig = (aenderung: Record<string, unknown> = {}) => ({ adresse: '', tokenGesetzt: false, adresseAusFirmendaten: false, eingerichtet: false, ...aenderung });
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let stand: ReturnType<typeof konfig>;
let testAntwort: Record<string, unknown>;
const aufrufe = (methode: string, teil: string) => fetcher.mock.calls.filter(([u, i]) => String(u).includes(teil) && (i?.method ?? 'GET') === methode);

beforeEach(() => {
    stand = konfig();
    testAntwort = { ok: true, art: 'OK', meldung: 'Die Website antwortet. Es gibt 3 Beiträge.' };
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url.endsWith('/test')) return antwort(testAntwort);
        if (init?.method === 'PUT') {
            const b = JSON.parse(init.body as string) as { adresse: string; token: string | null };
            const tokenGesetzt = b.token === null ? stand.tokenGesetzt : b.token !== '';
            stand = konfig({ adresse: b.adresse, tokenGesetzt, eingerichtet: tokenGesetzt && b.adresse !== '' });
            return antwort(stand);
        }
        return antwort(stand);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><WebsiteZugangKarte /></ToastProvider>);

describe('WebsiteZugangKarte', () => {
    it('zeigt ohne Einrichtung „Noch keine Website verbunden" und sperrt den Test', async () => {
        zeige();
        expect(await screen.findByText('Noch keine Website verbunden.')).toBeVisible();
        expect(screen.getByRole('button', { name: /Verbindung testen/ })).toBeDisabled();
    });

    it('speichert Adresse und Schlüssel, der Schlüssel wird nie angezeigt', async () => {
        const user = userEvent.setup();
        zeige();
        await user.type(await screen.findByLabelText('Adresse'), 'https://www.muster-metallbau.example');
        await user.type(screen.getByLabelText('Schlüssel'), 'geheim');
        expect(screen.getByLabelText('Schlüssel')).toHaveAttribute('type', 'password');

        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(aufrufe('PUT', '/website-zugang')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT', '/website-zugang')[0][1].body)).toEqual({ adresse: 'https://www.muster-metallbau.example', token: 'geheim' });
        await waitFor(() => expect(screen.getByLabelText('Schlüssel')).toHaveValue(''));
        expect(screen.getByLabelText('Schlüssel')).toHaveAttribute('placeholder', expect.stringContaining('Gespeichert'));
    });

    it('lässt den Schlüssel unverändert (null), wenn nur die Adresse geändert wird, und entfernt ihn auf Wunsch', async () => {
        const user = userEvent.setup();
        stand = konfig({ adresse: 'https://alt.example.test', tokenGesetzt: true, eingerichtet: true });
        zeige();
        const feld = await screen.findByLabelText('Adresse');
        await user.clear(feld);
        await user.type(feld, 'https://neu.example.test');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        await waitFor(() => expect(aufrufe('PUT', '/website-zugang')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT', '/website-zugang')[0][1].body)).toEqual({ adresse: 'https://neu.example.test', token: null });

        await user.click(await screen.findByLabelText('Gespeicherten Schlüssel entfernen'));
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        await waitFor(() => expect(aufrufe('PUT', '/website-zugang')).toHaveLength(2));
        expect(JSON.parse(aufrufe('PUT', '/website-zugang')[1][1].body).token).toBe('');
    });

    it('testet erst nach dem Speichern und zeigt das Ergebnis in Klartext', async () => {
        const user = userEvent.setup();
        stand = konfig({ adresse: 'https://w.example.test', tokenGesetzt: true, eingerichtet: true });
        zeige();
        const testen = await screen.findByRole('button', { name: /Verbindung testen/ });
        expect(testen).toBeEnabled();
        await user.type(screen.getByLabelText('Adresse'), 'x');
        expect(testen).toBeDisabled();
        await user.clear(screen.getByLabelText('Adresse'));
        await user.type(screen.getByLabelText('Adresse'), 'https://w.example.test');

        await user.click(screen.getByRole('button', { name: /Verbindung testen/ }));
        expect(await screen.findByRole('status')).toHaveTextContent('Die Website antwortet. Es gibt 3 Beiträge.');
    });

    it('zeigt einen abgelehnten Schlüssel als Fehler', async () => {
        const user = userEvent.setup();
        stand = konfig({ adresse: 'https://w.example.test', tokenGesetzt: true, eingerichtet: true });
        testAntwort = { ok: false, art: 'KEIN_ZUGRIFF', meldung: 'Die Website lehnt den Schlüssel ab (Fehler 401). Bitte den Schlüssel prüfen.' };
        zeige();
        await user.click(await screen.findByRole('button', { name: /Verbindung testen/ }));
        expect(await screen.findByRole('status')).toHaveTextContent('lehnt den Schlüssel ab');
    });

    it('zeigt die Fehlermeldung des Servers beim Speichern (zum Beispiel ohne https)', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (_url: string, init?: RequestInit) => (init?.method === 'PUT'
            ? antwort({ message: 'Die Adresse muss mit https:// beginnen.' }, false, 400)
            : antwort(stand)));
        zeige();
        await user.type(await screen.findByLabelText('Adresse'), 'http://w.example.test');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        expect(await screen.findByText('Die Adresse muss mit https:// beginnen.')).toBeVisible();
    });
});
