import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Kundeneditor } from './Kundeneditor';

/** DSGVO: nur Dummy-Daten. */
const KUNDE = { id: 1, kundennummer: 'K-1000', name: 'Max Mustermann', strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt', projekte: [], anfragen: [], geschaeftsdokumente: [], kommunikation: [], notizen: [] };

let anfragen: string[];

function ok(daten: unknown) {
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(daten), text: () => Promise.resolve(JSON.stringify(daten)) });
}

beforeEach(() => {
    anfragen = [];
    global.fetch = vi.fn((url: string) => {
        anfragen.push(url);
        if (url === '/api/kunden/1') return ok(KUNDE);
        if (url.startsWith('/api/kunden?')) return ok({ kunden: [KUNDE], gesamt: 1 });
        if (url.includes('naechste-auftragsnummer')) return ok({ auftragsnummer: '2026/10/00001', hinweis: null });
        return ok([]);
    }) as unknown as typeof fetch;
});
afterEach(() => { vi.restoreAllMocks(); });

function rendern() {
    return render(<MemoryRouter initialEntries={['/?kundeId=1']}><Kundeneditor /></MemoryRouter>);
}

describe('Kundenstamm: neues Projekt im Reiter Projekte', { timeout: 30000 }, () => {
    it('zeigt auch ohne Projekte den Knopf „Neues Projekt"', async () => {
        const user = userEvent.setup();
        rendern();

        await user.click(await screen.findByRole('button', { name: /Projekte/ }));

        expect(screen.getByText('Keine Projekte vorhanden.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Neues Projekt/ })).toBeInTheDocument();
    });

    it('öffnet die Maske mit vorgegebenem Kunden: kein Wechsel, keine Anfrage-Auswahl, Kunde in der Maske', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: /Projekte/ }));

        await user.click(screen.getByRole('button', { name: /Neues Projekt/ }));

        expect(await screen.findByRole('heading', { name: 'Neues Projekt anlegen' })).toBeInTheDocument();
        expect(screen.getByTestId('kunde-vorgegeben')).toHaveTextContent('Kunde ist vorgegeben');
        const maske = screen.getByRole('heading', { name: 'Neues Projekt anlegen' }).closest('div.fixed') as HTMLElement;
        expect(within(maske).getByText('Max Mustermann')).toBeInTheDocument();
        expect(within(maske).getByText('(K-1000)')).toBeInTheDocument();
        expect(within(maske).queryByRole('button', { name: /Von Anfrage übernehmen/ })).not.toBeInTheDocument();
        expect(within(maske).queryByRole('button', { name: /Ändern|Kunde auswählen/ })).not.toBeInTheDocument();
        // die Auftragsnummer folgt der Syntax dieses Kunden
        await vi.waitFor(() => expect(anfragen.some((u) => u.includes('naechste-auftragsnummer') && u.includes('kundeId=1'))).toBe(true));
    });

    it('Abbrechen schließt die Maske, ohne etwas anzulegen', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: /Projekte/ }));
        await user.click(screen.getByRole('button', { name: /Neues Projekt/ }));
        await screen.findByRole('heading', { name: 'Neues Projekt anlegen' });

        await user.click(screen.getAllByRole('button').find((b) => b.querySelector('svg.lucide-x')) as HTMLElement);

        expect(screen.queryByRole('heading', { name: 'Neues Projekt anlegen' })).not.toBeInTheDocument();
        expect(anfragen.some((u) => u === '/api/projekte')).toBe(false);
    });
});
