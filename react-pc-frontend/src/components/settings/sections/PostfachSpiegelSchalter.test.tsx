import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PostfachSpiegelSchalter } from './PostfachSpiegelSchalter';
import { ToastProvider } from '../../ui/toast';

const json = (b: unknown, s = 200) => ({ ok: s < 300, status: s, json: async () => b, text: async () => JSON.stringify(b) }) as Response;
afterEach(() => { vi.unstubAllGlobals(); });

describe('PostfachSpiegelSchalter', () => {
    it('ist standardmäßig aus und schaltet per Klick ein', async () => {
        const f = vi.fn(async (_u: string, init?: RequestInit) => (init?.method === 'PUT' ? json({ aktiv: true }) : json({ aktiv: false })));
        vi.stubGlobal('fetch', f);
        const u = userEvent.setup();
        render(<ToastProvider><PostfachSpiegelSchalter /></ToastProvider>);

        const box = await screen.findByRole('checkbox', { name: /Postfach nachführen/ });
        expect(box).not.toBeChecked();
        expect(screen.getByText(/Papierkorb/)).toBeInTheDocument();
        await u.click(box);

        await waitFor(() => expect(screen.getByRole('checkbox', { name: /Postfach nachführen/ })).toBeChecked());
        const put = f.mock.calls.find(([, i]) => (i as RequestInit | undefined)?.method === 'PUT')!;
        expect(JSON.parse((put[1] as RequestInit).body as string)).toEqual({ aktiv: true });
    });

    it('bleibt verborgen, wenn die Einstellung nicht lesbar ist', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({}, 403)));
        render(<ToastProvider><PostfachSpiegelSchalter /></ToastProvider>);
        await new Promise(r => setTimeout(r, 20));
        expect(screen.queryByRole('checkbox')).toBeNull();
    });
});
