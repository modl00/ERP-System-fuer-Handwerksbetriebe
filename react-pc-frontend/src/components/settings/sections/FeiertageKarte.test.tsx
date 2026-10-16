import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FeiertageKarte } from './FeiertageKarte';
import { ToastProvider } from '../../ui/toast';

const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
const auswahl = [{ kuerzel: 'BY', name: 'Bayern' }, { kuerzel: 'NW', name: 'Nordrhein-Westfalen' }];
let fetcher: ReturnType<typeof vi.fn>;

beforeEach(() => {
    fetcher = vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === 'PUT') return json({ bundesland: JSON.parse(String(init.body)).bundesland, auswahl });
        return json({ bundesland: 'BY', auswahl });
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('FeiertageKarte', { timeout: 30000 }, () => {
    it('zeigt das eingestellte Land; Speichern geht erst nach einer Änderung', async () => {
        render(<ToastProvider><FeiertageKarte /></ToastProvider>);
        const wahl = await screen.findByLabelText('Bundesland');
        expect(wahl).toHaveValue('BY');
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    });

    it('speichert das gewählte Land', async () => {
        const user = userEvent.setup();
        render(<ToastProvider><FeiertageKarte /></ToastProvider>);
        await user.selectOptions(await screen.findByLabelText('Bundesland'), 'NW');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(fetcher.mock.calls.some(([, init]) => init?.method === 'PUT' && JSON.parse(String(init.body)).bundesland === 'NW')).toBe(true));
        expect((await screen.findAllByRole('status')).map(s => s.textContent).join(' ')).toContain('gespeichert');
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    });
});
