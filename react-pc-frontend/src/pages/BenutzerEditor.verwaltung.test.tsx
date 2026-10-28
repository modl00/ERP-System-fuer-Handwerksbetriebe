import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import BenutzerEditor from './BenutzerEditor';

/** Nur Dummy-Daten. */
const MAX = { id: 1, displayName: 'Max Mustermann', username: 'max', shortCode: 'MM', active: true, roles: ['ADMIN'], defaultSignature: null, emailAbsender: null, mitarbeiter: { id: 3, vorname: 'Max', nachname: 'Mustermann' } };
const MITARBEITER = [{ id: 3, vorname: 'Max', nachname: 'Mustermann' }, { id: 4, vorname: 'Erika', nachname: 'Muster' }];

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let speichernAntwort: () => ReturnType<typeof antwort>;
let loeschenAntwort: () => ReturnType<typeof antwort>;
const aufrufe = (methode: string) => fetcher.mock.calls.filter(([, i]) => (i?.method ?? 'GET') === methode);

beforeEach(() => {
    speichernAntwort = () => antwort({ id: 2 });
    loeschenAntwort = () => antwort({}, true, 204);
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/frontend-users' && init?.method === 'POST') return speichernAntwort();
        if (url.startsWith('/api/frontend-users/') && init?.method === 'DELETE') return loeschenAntwort();
        if (url === '/api/frontend-users') return antwort([MAX]);
        if (url === '/api/mitarbeiter') return antwort(MITARBEITER);
        return antwort([]);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<MemoryRouter><ConfirmProvider><ToastProvider><BenutzerEditor /></ToastProvider></ConfirmProvider></MemoryRouter>);

async function neuerBenutzer(user: ReturnType<typeof userEvent.setup>, name = 'Erika Muster', login = 'erika', passwort = 'passwort123') {
    await screen.findByText('Max Mustermann');
    if (name) await user.type(screen.getByLabelText('Anzeigename *'), name);
    if (login) await user.type(screen.getByLabelText('Benutzername *'), login);
    if (passwort) await user.type(screen.getByLabelText(/Passwort/), passwort);
}

describe('Benutzerverwaltung (#241)', { timeout: 30000 }, () => {
    it('legt einen Benutzer an und sagt, dass es geklappt hat', async () => {
        const user = userEvent.setup();
        zeige();
        await neuerBenutzer(user);
        await user.click(screen.getByRole('button', { name: 'Erstellen' }));

        await waitFor(() => expect(aufrufe('POST')).toHaveLength(1));
        expect(JSON.parse(aufrufe('POST')[0][1].body as string)).toMatchObject({ displayName: 'Erika Muster', username: 'erika', password: 'passwort123', roles: ['USER'] });
        expect(await screen.findByText('Der Benutzer ist angelegt.')).toBeVisible();
    });

    it('sagt bei fehlendem Benutzernamen, was fehlt: Der Knopf ist aus, der Grund steht darunter und am Knopf', async () => {
        const user = userEvent.setup();
        zeige();
        await neuerBenutzer(user, 'Erika Muster', '', 'passwort123');

        const knopf = screen.getByRole('button', { name: 'Erstellen' });
        expect(knopf).toBeDisabled();
        expect(knopf).toHaveAttribute('title', 'Bitte Anzeigename und Benutzername eintragen.');
        expect(screen.getByTestId('speichern-hindernis')).toHaveTextContent('Bitte Anzeigename und Benutzername eintragen.');
        expect(aufrufe('POST')).toHaveLength(0);
    });

    it('sobald alles da ist, verschwindet der Hinweis und der Knopf geht an', async () => {
        const user = userEvent.setup();
        zeige();
        await neuerBenutzer(user);

        expect(screen.queryByTestId('speichern-hindernis')).toBeNull();
        expect(screen.getByRole('button', { name: 'Erstellen' })).toBeEnabled();
    });

    it('zeigt die Meldung des Servers, zum Beispiel wenn der Mitarbeiter schon einen Benutzer hat', async () => {
        const user = userEvent.setup();
        speichernAntwort = () => antwort({ message: 'Der Mitarbeiter „Erika Muster" gehört schon zum Benutzer „Anna".' }, false, 400);
        zeige();
        await neuerBenutzer(user);
        await user.click(screen.getByRole('button', { name: 'Erstellen' }));

        expect(await screen.findByText('Der Mitarbeiter „Erika Muster" gehört schon zum Benutzer „Anna".')).toBeVisible();
    });

    it('ein zu kurzes Passwort wird erklärt, statt einen toten Knopf zu zeigen', async () => {
        const user = userEvent.setup();
        zeige();
        await neuerBenutzer(user, 'Erika Muster', 'erika', 'kurz');

        expect(screen.getByRole('button', { name: 'Erstellen' })).toBeDisabled();
        expect(screen.getByTestId('speichern-hindernis')).toHaveTextContent('Das Passwort braucht mindestens 8 Zeichen.');
        expect(aufrufe('POST')).toHaveLength(0);
    });

    it('ein Mitarbeiter, der schon zu einem anderen Benutzer gehört, ist gesperrt und nennt den Benutzer', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByText('Max Mustermann');
        await user.click(screen.getAllByRole('combobox').find(c => /Mitarbeiter/.test(c.textContent ?? ''))!);

        const vergeben = await screen.findByRole('option', { name: 'Max Mustermann (gehört schon zu Max Mustermann)' });
        expect(vergeben).toHaveAttribute('aria-disabled', 'true');
        expect(screen.getByRole('option', { name: 'Erika Muster' })).not.toHaveAttribute('aria-disabled', 'true');
    });

    it('zeigt beim Löschen den Grund des Servers', async () => {
        const user = userEvent.setup();
        loeschenAntwort = () => antwort({ message: 'Dieser Benutzer hat schon Arbeit im Programm hinterlassen.' }, false, 400);
        zeige();
        await screen.findByText('Max Mustermann');
        await user.click(screen.getAllByRole('button').find(b => /löschen/i.test(b.getAttribute('aria-label') ?? b.getAttribute('title') ?? ''))!);
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));

        expect(await screen.findByText('Dieser Benutzer hat schon Arbeit im Programm hinterlassen.')).toBeVisible();
    });
});
