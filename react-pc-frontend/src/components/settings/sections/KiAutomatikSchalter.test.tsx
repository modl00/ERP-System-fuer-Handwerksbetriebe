import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { KiAutomatikSchalter } from './KiAutomatikSchalter';
import { ToastProvider } from '../../ui/toast';

const json = (b: unknown, s = 200) => ({ ok: s < 300, status: s, json: async () => b, text: async () => JSON.stringify(b) }) as Response;
afterEach(() => { vi.unstubAllGlobals(); });

describe('KiAutomatikSchalter', () => {
    it('ist standardmäßig aus und schaltet per Klick ein', async () => {
        const f = vi.fn(async (_u: string, init?: RequestInit) => (init?.method === 'PUT' ? json({ vollautomatisch: true, seit: '2026-10-10T08:00:00' }) : json({ vollautomatisch: false, seit: '' })));
        vi.stubGlobal('fetch', f);
        const u = userEvent.setup();
        render(<ToastProvider><KiAutomatikSchalter /></ToastProvider>);

        const box = await screen.findByRole('checkbox', { name: /vollautomatisch zuordnen/ });
        expect(box).not.toBeChecked();
        await u.click(box);

        await waitFor(() => expect(screen.getByRole('checkbox', { name: /vollautomatisch zuordnen/ })).toBeChecked());
        const put = f.mock.calls.find(([, i]) => (i as RequestInit | undefined)?.method === 'PUT')!;
        expect(JSON.parse((put[1] as RequestInit).body as string)).toEqual({ vollautomatisch: true });
        expect(screen.getByText(/Eingeschaltet für Mails ab/)).toBeInTheDocument();
    });

    it('bleibt verborgen, wenn die Einstellung nicht lesbar ist (zum Beispiel kein Administrator)', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({}, 403)));
        render(<ToastProvider><KiAutomatikSchalter /></ToastProvider>);
        await new Promise(r => setTimeout(r, 20));
        expect(screen.queryByRole('checkbox')).toBeNull();
    });
});
