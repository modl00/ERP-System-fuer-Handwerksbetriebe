import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MeinePostfaecherDialog } from './MeinePostfaecherDialog';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';

/** Nur Dummy-Daten. */
const postfach = { id: 3, bezeichnung: 'Mein Büro', emailAdresse: 'max@mustermann.example', imapHost: 'imap.gmx.net', imapPort: 993, smtpHost: 'mail.gmx.net', smtpPort: 465, benutzername: 'max@mustermann.example', passwortGesetzt: true, aktiv: true };
const json = (b: unknown, s = 200) => ({ ok: s < 300, status: s, json: async () => b, text: async () => JSON.stringify(b) }) as Response;
let fetcher: ReturnType<typeof vi.fn>;
let liste: unknown[];
const zeige = () => render(<ToastProvider><ConfirmProvider><MeinePostfaecherDialog offen onSchliessen={vi.fn()} /></ConfirmProvider></ToastProvider>);
const aufrufe = (methode: string) => fetcher.mock.calls.filter(([, i]) => ((i as RequestInit | undefined)?.method ?? 'GET') === methode);

beforeEach(() => {
    liste = [postfach];
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const m = init?.method ?? 'GET';
        if (url === '/api/mein/postfaecher' && m === 'GET') return json(liste);
        if (url === '/api/mein/postfaecher' && m === 'POST') { liste = [...liste, { ...postfach, id: 4 }]; return json({ ...postfach, id: 4 }, 201); }
        if (url.endsWith('/test')) return json({ ok: true, meldung: 'Die Anmeldung hat geklappt.' });
        if (m === 'DELETE') { liste = []; return json({}, 204); }
        return json({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('MeinePostfaecherDialog', () => {
    it('zeigt die eigenen Postfächer ohne Passwort', async () => {
        zeige();
        const l = await screen.findByTestId('postfach-liste');
        expect(l).toHaveTextContent('Mein Büro');
        expect(l).toHaveTextContent('max@mustermann.example');
        expect(l).toHaveTextContent('imap.gmx.net:993');
    });

    it('ein neues Postfach: Anbieter trägt die Server ein, testen, speichern, Liste neu', async () => {
        const u = userEvent.setup();
        zeige();
        await u.click(await screen.findByRole('button', { name: /Postfach hinzufügen/ }));
        await u.selectOptions(screen.getByLabelText('Anbieter'), 'gmx');
        expect(screen.getByLabelText(/Empfangs-Server/)).toHaveValue('imap.gmx.net');
        expect(screen.getByLabelText(/Sende-Server/)).toHaveValue('mail.gmx.net');
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();

        await u.type(screen.getByLabelText('Name des Postfachs'), 'Privat');
        await u.type(screen.getByLabelText('E-Mail-Adresse'), 'anna@beispiel.example');
        await u.type(screen.getByLabelText('Passwort'), 'geheim');
        await u.click(screen.getByRole('button', { name: /Verbindung testen/ }));
        expect(await screen.findByText('Die Anmeldung hat geklappt.')).toBeInTheDocument();
        await u.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('POST').some(([url]) => url === '/api/mein/postfaecher')).toBe(true));
        const post = aufrufe('POST').find(([url]) => url === '/api/mein/postfaecher')!;
        expect(JSON.parse((post[1] as RequestInit).body as string)).toMatchObject({ bezeichnung: 'Privat', emailAdresse: 'anna@beispiel.example', imapHost: 'imap.gmx.net', imapPort: 993, passwort: 'geheim', smtpHost: 'mail.gmx.net', smtpPort: 465 });
        expect(await screen.findByTestId('postfach-liste')).toBeInTheDocument();
    });

    it('beim Ändern bleibt das Passwort leer und wird als „unverändert“ gemeldet; Testen nimmt das gespeicherte', async () => {
        const u = userEvent.setup();
        zeige();
        await u.click(await screen.findByRole('button', { name: 'Mein Büro bearbeiten' }));
        expect(screen.getByLabelText(/Passwort \(leer = unverändert\)/)).toHaveValue('');
        await u.click(screen.getByRole('button', { name: /Verbindung testen/ }));
        await screen.findByText('Die Anmeldung hat geklappt.');
        expect(aufrufe('POST').some(([url]) => url === '/api/mein/postfaecher/3/test')).toBe(true);
    });

    it('Entfernen fragt nach und löscht dann', async () => {
        const u = userEvent.setup();
        zeige();
        await u.click(await screen.findByRole('button', { name: 'Mein Büro entfernen' }));
        await u.click(await screen.findByRole('button', { name: 'Entfernen' }));
        await waitFor(() => expect(aufrufe('DELETE')).toHaveLength(1));
        expect(await screen.findByText(/noch kein eigenes Postfach/)).toBeInTheDocument();
    });

    it('zeigt den Fehler des Servers beim Speichern', async () => {
        const u = userEvent.setup();
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
            if ((init?.method ?? 'GET') === 'GET') return json([]);
            return json({ message: 'Für Empfang sind nur die Ports 143 oder 993 erlaubt.' }, 400);
        });
        zeige();
        await u.click(await screen.findByRole('button', { name: /Postfach hinzufügen/ }));
        await u.type(screen.getByLabelText('Name des Postfachs'), 'X');
        await u.type(screen.getByLabelText('E-Mail-Adresse'), 'a@b.example');
        await u.type(screen.getByLabelText(/Empfangs-Server/), 'imap.b.example');
        await u.type(screen.getByLabelText('Passwort'), 'pw');
        await u.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Ports 143 oder 993');
    });
});
