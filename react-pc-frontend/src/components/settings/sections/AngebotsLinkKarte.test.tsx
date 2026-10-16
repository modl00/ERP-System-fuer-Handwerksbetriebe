import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AngebotsLinkKarte } from './AngebotsLinkKarte';
import { ToastProvider } from '../../ui/toast';

const konfig = (aenderung: Record<string, unknown> = {}) => ({
    aktiv: false, adresse: '', adresseAusKonfiguration: false, moeglich: false, gueltigkeitTage: 14, ...aenderung,
});
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let stand: ReturnType<typeof konfig>;
const puts = () => fetcher.mock.calls.filter(([, i]) => i?.method === 'PUT');

beforeEach(() => {
    stand = konfig();
    fetcher = vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === 'PUT') {
            const b = JSON.parse(init.body as string) as { aktiv: boolean; adresse: string; gueltigkeitTage: number | null };
            stand = konfig({ aktiv: b.aktiv, adresse: b.adresse, moeglich: b.aktiv && b.adresse !== '', gueltigkeitTage: b.gueltigkeitTage ?? stand.gueltigkeitTage });
            return antwort(stand);
        }
        return antwort(stand);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><AngebotsLinkKarte /></ToastProvider>);

describe('AngebotsLinkKarte', () => {
    it('neue Installation: Schalter aus, keine Adresse, Hinweis „ohne Link“, Speichern gesperrt', async () => {
        zeige();

        expect(await screen.findByText('Angebots-E-Mails gehen ohne Link raus.')).toBeInTheDocument();
        expect(screen.getByRole('switch', { name: /Link in Angebots-E-Mails einfügen/ })).toHaveAttribute('aria-checked', 'false');
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    });

    it('Einschalten ohne Adresse: Warnung, Speichern bleibt gesperrt', async () => {
        zeige();
        await userEvent.click(await screen.findByRole('switch'));

        expect(screen.getByText(/Zum Einschalten bitte die Adresse eintragen/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    });

    it('Einschalten mit Adresse und eigener Gültigkeit speichert alles', async () => {
        zeige();
        await userEvent.click(await screen.findByRole('switch'));
        await userEvent.type(screen.getByLabelText('Öffentliche Adresse'), ' https://erp.example.org ');
        await userEvent.clear(screen.getByLabelText(/Gültig für/));
        await userEvent.type(screen.getByLabelText(/Gültig für/), '30');
        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(puts()).toHaveLength(1));
        expect(JSON.parse(puts()[0][1].body)).toEqual({ aktiv: true, adresse: 'https://erp.example.org', gueltigkeitTage: 30 });
        expect(await screen.findByText('Angebots-E-Mails enthalten den Link.')).toBeInTheDocument();
    });

    it('Gültigkeit außerhalb 1 bis 365 sperrt das Speichern', async () => {
        zeige();
        const tage = await screen.findByLabelText(/Gültig für/);
        await userEvent.clear(tage);
        await userEvent.type(tage, '400');

        expect(screen.getByText(/zwischen 1 und 365/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    });

    it('Adresse aus der Konfigurationsdatei wird erklärt', async () => {
        stand = konfig({ aktiv: true, adresse: 'https://erp.example.org', adresseAusKonfiguration: true, moeglich: true });
        zeige();

        expect(await screen.findByText(/Adresse aus der Konfigurationsdatei/)).toBeInTheDocument();
        expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true');
    });

    it('Fehler des Servers (zum Beispiel ungültige Adresse) erscheint als Meldung', async () => {
        fetcher.mockImplementation(async (_url: string, init?: RequestInit) => init?.method === 'PUT'
            ? antwort({ message: 'Die Adresse muss mit https:// beginnen.' }, false, 400)
            : antwort(stand));
        zeige();
        await userEvent.click(await screen.findByRole('switch'));
        await userEvent.type(screen.getByLabelText('Öffentliche Adresse'), 'http://x');
        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText(/muss mit https:\/\/ beginnen/)).toBeInTheDocument();
    });

    it('Ladefehler zeigt eine Meldung statt einer leeren Karte', async () => {
        fetcher.mockImplementation(async () => antwort({ message: 'Kein Zugriff' }, false, 403));
        zeige();

        expect(await screen.findByRole('alert')).toBeInTheDocument();
    });
});
