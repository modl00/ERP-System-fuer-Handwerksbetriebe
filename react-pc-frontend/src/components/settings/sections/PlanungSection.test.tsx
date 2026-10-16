import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PlanungSection } from './PlanungSection';
import { ToastProvider } from '../../ui/toast';

/** Nur Dummy-Daten. */
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
let fetcher: ReturnType<typeof vi.fn>;
let gespeichert: unknown;

const zeige = () => render(<ToastProvider><PlanungSection /></ToastProvider>);

beforeEach(() => {
    gespeichert = null;
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url) === '/api/plantafel/einstellungen') {
            return json({ arbeitstage: [1, 2, 3, 4, 5], ampelSchwelleTage: 2, betriebsurlaub: [{ von: '2026-08-03', bis: '2026-08-14', bezeichnung: 'Sommer' }] });
        }
        if (String(url) === '/api/settings/planung' && init?.method === 'PUT') {
            gespeichert = JSON.parse(String(init.body));
            return json(gespeichert);
        }
        return json({}, 404);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('PlanungSection', { timeout: 30000 }, () => {
    it('zeigt Arbeitswoche, Schwelle und Betriebsurlaub aus den Einstellungen', async () => {
        zeige();

        expect(await screen.findByLabelText('Montag')).toBeChecked();
        expect(screen.getByLabelText('Samstag')).not.toBeChecked();
        expect(screen.getByLabelText(/Ampel „Knapp“ ab/)).toHaveValue(2);
        expect(screen.getByLabelText('Betriebsurlaub 1 Bezeichnung')).toHaveValue('Sommer');
    });

    it('speichert Samstag als Arbeitstag, eine neue Schwelle und einen weiteren Zeitraum', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByLabelText('Samstag'));
        await user.clear(screen.getByLabelText(/Ampel „Knapp“ ab/));
        await user.type(screen.getByLabelText(/Ampel „Knapp“ ab/), '4');
        await user.click(screen.getByRole('button', { name: /Zeitraum hinzufügen/ }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(gespeichert).not.toBeNull());
        const g = gespeichert as { arbeitstage: number[]; ampelSchwelleTage: number; betriebsurlaub: unknown[] };
        expect(g.arbeitstage).toEqual([1, 2, 3, 4, 5, 6]);
        expect(g.ampelSchwelleTage).toBe(4);
        expect(g.betriebsurlaub).toHaveLength(2);
    });

    it('entfernt einen Betriebsurlaub', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Betriebsurlaub 1 entfernen' }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect((gespeichert as { betriebsurlaub: unknown[] }).betriebsurlaub).toEqual([]));
    });

    it('meldet einen Fehler vom Server im Klartext', async () => {
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => (init?.method === 'PUT'
            ? json({ message: 'Mindestens ein Arbeitstag in der Woche ist nötig.' }, 400)
            : json({ arbeitstage: [1], ampelSchwelleTage: 2, betriebsurlaub: [] })));
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText('Mindestens ein Arbeitstag in der Woche ist nötig.')).toBeInTheDocument();
    });
});
