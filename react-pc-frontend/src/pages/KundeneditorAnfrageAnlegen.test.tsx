import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Kundeneditor } from './Kundeneditor';

/** DSGVO: nur Dummy-Daten. */
const KUNDE = { id: 1, kundennummer: 'K-1000', name: 'Max Mustermann', strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt', kundenEmails: ['max@example.org'], projekte: [], anfragen: [], geschaeftsdokumente: [], kommunikation: [], notizen: [] };

let aufrufe: { url: string; methode: string; body?: string }[];
let kundeGeladen: number;

function ok(daten: unknown, status = 200) {
    return Promise.resolve({ ok: status < 300, status, json: () => Promise.resolve(daten), text: () => Promise.resolve(JSON.stringify(daten)) });
}

beforeEach(() => {
    aufrufe = [];
    kundeGeladen = 0;
    global.fetch = vi.fn((url: string, init?: RequestInit) => {
        aufrufe.push({ url, methode: init?.method ?? 'GET', body: typeof init?.body === 'string' ? init.body : undefined });
        if (url === '/api/kunden/1') { kundeGeladen++; return ok(KUNDE); }
        if (url.startsWith('/api/kunden?')) return ok({ kunden: [KUNDE], gesamt: 1 });
        if (url === '/api/anfragen' && init?.method === 'POST') return ok({ id: 55 });
        return ok([]);
    }) as unknown as typeof fetch;
});
afterEach(() => { vi.restoreAllMocks(); });

const rendern = () => render(<MemoryRouter initialEntries={['/?kundeId=1']}><Kundeneditor /></MemoryRouter>);

describe('Kundenstamm: neue Anfrage im Reiter Anfragen', { timeout: 30000 }, () => {
    it('zeigt auch ohne Anfragen den Knopf „Neue Anfrage“', async () => {
        const user = userEvent.setup();
        rendern();

        await user.click(await screen.findByRole('button', { name: /Anfragen/ }));

        expect(screen.getByText('Keine Anfragen vorhanden.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Neue Anfrage/ })).toBeInTheDocument();
    });

    it('öffnet dieselbe Maske wie „Neue Anfrage anlegen“ mit vorgegebenem Kunden ohne Wechsel', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: /Anfragen/ }));

        await user.click(screen.getByRole('button', { name: /Neue Anfrage/ }));

        expect(await screen.findByRole('heading', { name: 'Neue Anfrage anlegen' })).toBeInTheDocument();
        expect(screen.getByTestId('kunde-vorgegeben')).toHaveTextContent('Kunde ist vorgegeben');
        const maske = screen.getByRole('heading', { name: 'Neue Anfrage anlegen' }).closest('div.fixed') as HTMLElement;
        expect(within(maske).getByText('Max Mustermann')).toBeInTheDocument();
        expect(within(maske).queryByRole('button', { name: 'Ändern' })).not.toBeInTheDocument();
        expect(within(maske).queryByRole('button', { name: /Kunde auswählen oder anlegen/ })).not.toBeInTheDocument();
    });

    it('speichert die Anfrage mit der Kunden-ID und lädt den Kunden danach neu', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: /Anfragen/ }));
        await user.click(screen.getByRole('button', { name: /Neue Anfrage/ }));
        const maske = (await screen.findByRole('heading', { name: 'Neue Anfrage anlegen' })).closest('div.fixed') as HTMLElement;
        const vorher = kundeGeladen;

        await user.type(within(maske).getByPlaceholderText('Beschreibung des Bauvorhabens'), 'Geländer Musterweg');
        await user.click(within(maske).getByRole('button', { name: 'Anfrage anlegen' }));

        await vi.waitFor(() => expect(aufrufe.some((a) => a.url === '/api/anfragen' && a.methode === 'POST')).toBe(true));
        const post = aufrufe.find((a) => a.url === '/api/anfragen' && a.methode === 'POST')!;
        expect(JSON.parse(post.body!)).toMatchObject({ bauvorhaben: 'Geländer Musterweg', kundenId: 1 });
        await vi.waitFor(() => expect(kundeGeladen).toBeGreaterThan(vorher));
        expect(screen.queryByRole('heading', { name: 'Neue Anfrage anlegen' })).not.toBeInTheDocument();
    });
});
