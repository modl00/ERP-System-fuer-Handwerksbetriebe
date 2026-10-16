import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Startbildschirm from './Startbildschirm';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { setzeStartZurueck } from '../features/startbildschirm/useStart';

vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ isAdmin: true, user: { id: 1 } }) }));
vi.mock('../components/layout/PageLayout', () => ({
    PageLayout: ({ title, actions, children }: { title: string; actions?: React.ReactNode; children: React.ReactNode }) => <div><h1>{title}</h1>{actions}{children}</div>,
}));

/** Nur Dummy-Daten. */
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
let stand: { vorgabe: string[] | null; eigene: string[] | null };
let fetcher: ReturnType<typeof vi.fn>;

const zeige = () => render(<MemoryRouter><ToastProvider><ConfirmProvider><Startbildschirm /></ConfirmProvider></ToastProvider></MemoryRouter>);

beforeEach(() => {
    setzeStartZurueck();
    stand = { vorgabe: null, eigene: null };
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const u = String(url);
        if (u === '/api/startbildschirm/eigene' && init?.method === 'PUT') { stand = { ...stand, eigene: JSON.parse(init.body as string).eintraege }; return json(stand); }
        if (u === '/api/settings/startbildschirm/vorgabe' && init?.method === 'PUT') { stand = { ...stand, vorgabe: JSON.parse(init.body as string).eintraege }; return json(stand); }
        if (u === '/api/startbildschirm') return json(stand);
        if (u === '/api/notifications/summary') {
            return json({ totalCount: 3, categories: [{ type: 'EMAIL', label: 'Ungelesene E-Mails', count: 3, link: '/emails' }], recentItems: [{ type: 'EMAIL', title: 'Anfrage Tor', subtitle: 'Max Mustermann', timestamp: '', link: '/emails' }] });
        }
        if (u === '/api/plantafel/auftraege') return json([{ projektId: 7, bezeichnung: 'Geländer Musterstraße', auftragsnummer: 'A-1', startDatum: null, zielDatum: null, liefertermin: '2026-10-13', ampel: 'KNAPP', vorgaenge: [] }]);
        if (u === '/api/lager/zusammenfassung') return json({}, 403);
        return json({}, 404);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('Startbildschirm', { timeout: 30000 }, () => {
    it('zeigt ohne gespeicherten Aufbau die mitgelieferten Bausteine mit ihren Daten', async () => {
        zeige();
        expect(await screen.findByText('Ungelesene E-Mails')).toBeInTheDocument();
        expect(screen.getByText('Geländer Musterstraße')).toBeInTheDocument();
        expect(screen.getByText('Knapp')).toBeInTheDocument();
        expect(screen.getByText('Anfrage Tor')).toBeInTheDocument();
    });

    it('ein Baustein, dessen Daten nicht zu holen sind, sagt das ruhig, die übrigen laufen weiter', async () => {
        zeige();
        const lager = await screen.findByTestId('baustein-Lager');
        await waitFor(() => expect(lager).toHaveTextContent('nicht verfügbar'));
        expect(screen.getByText('Ungelesene E-Mails')).toBeInTheDocument();
    });

    it('die gespeicherte Auswahl bestimmt Bausteine und Reihenfolge', async () => {
        stand = { vorgabe: ['aufgaben'], eigene: ['plantafel', 'aufgaben'] };
        zeige();
        const bereich = await screen.findByTestId('start-bausteine');
        await waitFor(() => expect(within(bereich).getAllByRole('heading', { level: 2 }).map(h => h.textContent)).toEqual(['Lieferzeiten', 'Das ist offen']));
    });

    it('beim Anpassen lassen sich Bausteine wählen, ordnen und speichern', async () => {
        stand = { vorgabe: null, eigene: ['aufgaben'] };
        const user = userEvent.setup();
        zeige();
        await screen.findByText('Ungelesene E-Mails');
        await user.click(screen.getByRole('button', { name: /Anpassen/ }));
        await user.click(await screen.findByRole('checkbox', { name: 'Lieferzeiten auf den Startbildschirm' }));
        await user.click(screen.getByRole('button', { name: 'Lieferzeiten nach vorne' }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(stand.eigene).toEqual(['plantafel', 'aufgaben']));
    });

    it('der Admin kann den Aufbau als Vorgabe für alle speichern', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByText('Ungelesene E-Mails');
        await user.click(screen.getByRole('button', { name: /Anpassen/ }));
        await user.click(await screen.findByRole('checkbox', { name: 'Lager auf den Startbildschirm' }));
        await user.click(screen.getByRole('button', { name: 'Als Vorgabe für alle speichern' }));
        await user.click(await screen.findByRole('button', { name: 'Für alle speichern' }));
        await waitFor(() => expect(stand.vorgabe).toEqual(['aufgaben', 'plantafel', 'neues']));
    });
});
