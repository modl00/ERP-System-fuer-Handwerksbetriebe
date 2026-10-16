import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Kundeneditor } from './Kundeneditor';

/** DSGVO: nur Dummy-Daten. */
const KUNDE = { id: 1, kundennummer: 'K-1000', name: 'Max Mustermann', zahlungsziel: 30, kundenEmails: [], projekte: [], anfragen: [], geschaeftsdokumente: [], kommunikation: [], notizen: [] };

let puts: Record<string, unknown>[];

function ok(daten: unknown, status = 200) {
    return Promise.resolve({ ok: status < 300, status, json: () => Promise.resolve(daten), text: () => Promise.resolve(JSON.stringify(daten)) });
}

beforeEach(() => {
    puts = [];
    global.fetch = vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/kunden/1' && init?.method === 'PUT') { puts.push(JSON.parse(String(init.body))); return ok(KUNDE); }
        if (url === '/api/kunden/1') return ok(KUNDE);
        if (url.startsWith('/api/kunden?')) return ok({ kunden: [KUNDE], gesamt: 1 });
        return ok([]);
    }) as unknown as typeof fetch;
});
afterEach(() => { vi.restoreAllMocks(); });

const rendern = () => render(<MemoryRouter initialEntries={['/?kundeId=1']}><Kundeneditor /></MemoryRouter>);

describe('Kundenstamm: Zahlungsziel', { timeout: 30000 }, () => {
    it('zeigt das gespeicherte Zahlungsziel des Kunden, nicht den Standard', async () => {
        rendern();

        expect(await screen.findByText('30 Tage')).toBeInTheDocument();
    });

    it('lässt sich ändern und wird mit dem neuen Wert gespeichert', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: /Bearbeiten/ }));

        const feld = await screen.findByLabelText('Zahlungsziel (Tage)');
        expect(feld).toHaveValue(30);
        await user.clear(feld);
        await user.type(feld, '14');
        await user.click(screen.getByRole('button', { name: 'Aktualisieren' }));

        await vi.waitFor(() => expect(puts).toHaveLength(1));
        expect(puts[0]).toMatchObject({ zahlungsziel: 14 });
    });

    it('0 Tage (sofort fällig) bleibt 0 und wird nicht zu 8', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: /Bearbeiten/ }));

        const feld = await screen.findByLabelText('Zahlungsziel (Tage)');
        await user.clear(feld);
        await user.type(feld, '0');
        await user.click(screen.getByRole('button', { name: 'Aktualisieren' }));

        await vi.waitFor(() => expect(puts).toHaveLength(1));
        expect(puts[0]).toMatchObject({ zahlungsziel: 0 });
    });
});
