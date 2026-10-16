import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HeroVerbindungKarte } from './HeroVerbindungKarte';
import { ToastProvider } from '../../ui/toast';

/** Nur Dummy-Daten. */
const json = (b: unknown, s = 200) => ({ ok: s < 300, status: s, json: async () => b, text: async () => JSON.stringify(b) }) as Response;
let fetcher: ReturnType<typeof vi.fn>;
let status: { schluesselGesetzt: boolean; adresse: string };
const zeige = () => render(<ToastProvider><HeroVerbindungKarte /></ToastProvider>);
const aufruf = (teil: string) => fetcher.mock.calls.filter(([u]) => String(u).includes(teil));

beforeEach(() => {
    status = { schluesselGesetzt: false, adresse: 'https://login.hero-software.de/api/external/v7/graphql' };
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/settings/import/hero' ) return json(status);
        if (url.endsWith('/schluessel')) { status = { ...status, schluesselGesetzt: !!JSON.parse(init!.body as string).schluessel }; return json(status); }
        if (url.endsWith('/test')) return json({ ok: true, meldung: 'Die Verbindung zu Hero klappt.', antwort: '{}' });
        if (url.endsWith('/schnittstelle')) return json([{ name: 'contacts', argumente: 'offset: Int', typ: '[Contact]!' }]);
        if (url.endsWith('/abfrage')) return json({ ok: true, meldung: 'OK', antwort: '{"data":{"contacts":[{"id":1}]}}' });
        return json({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('HeroVerbindungKarte', () => {
    it('ohne Schlüssel gibt es nur das Feld zum Speichern, keine Abfragen', async () => {
        zeige();
        expect(await screen.findByText(/Adresse der Schnittstelle/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Verbindung testen/ })).toBeNull();
    });

    it('speichert den Schlüssel, leert das Feld und zeigt dann Test, Schnittstelle und Abfrage', async () => {
        const u = userEvent.setup();
        zeige();
        await u.type(await screen.findByLabelText(/Hero-Schlüssel/), 'geheim-abc');
        await u.click(screen.getByRole('button', { name: /Schlüssel speichern/ }));

        expect(await screen.findByRole('button', { name: /Verbindung testen/ })).toBeInTheDocument();
        expect(JSON.parse((aufruf('/schluessel')[0][1] as RequestInit).body as string)).toEqual({ schluessel: 'geheim-abc' });
        expect(screen.getByLabelText(/Hero-Schlüssel/)).toHaveValue('');
    });

    it('Verbindung testen zeigt die Meldung; Schnittstelle zeigt die Abfragen mit Angaben', async () => {
        const u = userEvent.setup();
        status.schluesselGesetzt = true;
        zeige();
        await u.click(await screen.findByRole('button', { name: /Verbindung testen/ }));
        expect(await screen.findByText('Die Verbindung zu Hero klappt.')).toBeInTheDocument();
        await u.click(screen.getByRole('button', { name: /Schnittstelle anzeigen/ }));
        const tabelle = await screen.findByTestId('hero-schnittstelle');
        expect(tabelle).toHaveTextContent('contacts');
        expect(tabelle).toHaveTextContent('offset: Int');
    });

    it('die Beispielabfrage geht als Text an den Server und die Antwort steht darunter', async () => {
        const u = userEvent.setup();
        status.schluesselGesetzt = true;
        zeige();
        await u.click(await screen.findByRole('button', { name: /Abfrage ausführen/ }));
        const antwort = await screen.findByTestId('hero-antwort');
        expect(antwort).toHaveTextContent('"contacts"');
        expect(JSON.parse((aufruf('/abfrage')[0][1] as RequestInit).body as string).abfrage).toContain('contacts');
    });

    it('zeigt den Fehler des Servers, zum Beispiel einen abgelehnten Schlüssel', async () => {
        const u = userEvent.setup();
        status.schluesselGesetzt = true;
        fetcher.mockImplementation(async (url: string) => (url === '/api/settings/import/hero' ? json(status) : json({ message: 'Es sind nur Abfragen erlaubt, keine Änderungen an Hero.' }, 400)));
        zeige();
        await u.click(await screen.findByRole('button', { name: /Abfrage ausführen/ }));
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('keine Änderungen'));
    });
});
