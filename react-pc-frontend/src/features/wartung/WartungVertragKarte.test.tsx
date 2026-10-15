import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WartungVertragKarte } from './WartungVertragKarte';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import type { WartungVertrag } from './types';

/** Nur Dummy-Daten (DSGVO). */
const vertrag = (extra: Partial<WartungVertrag> = {}): WartungVertrag => ({
    id: 1, projektId: 3, vertragsnummer: 'WV-1', beginn: '2026-01-01', ende: '2028-12-31', intervallMonate: 12, preisNetto: 450.5,
    notiz: 'Jährliche Prüfung', status: 'LAEUFT', endetBald: false, ...extra,
});

function antwort(status: number, body: unknown = {}): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

let fetcher: ReturnType<typeof vi.fn>;
let aktuell: WartungVertrag | null;

beforeEach(() => {
    aktuell = vertrag();
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (methode === 'GET') return aktuell ? antwort(200, aktuell) : antwort(204);
        if (methode === 'PUT') {
            const e = JSON.parse(init!.body as string);
            aktuell = vertrag({ ...e, id: 1 });
            return antwort(200, aktuell);
        }
        if (methode === 'DELETE') { aktuell = null; return antwort(204); }
        return antwort(404);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><ConfirmProvider><WartungVertragKarte projektId={3} /></ConfirmProvider></ToastProvider>);
const aufrufe = (methode: string) => fetcher.mock.calls.filter(([, init]) => (init?.method ?? 'GET') === methode);

describe('WartungVertragKarte', () => {
    it('zeigt Laufzeit, Intervall, Preis und Status des Vertrags', async () => {
        zeige();
        const karte = await screen.findByTestId('wartungsvertrag');
        await waitFor(() => expect(within(karte).getByText('Läuft')).toBeVisible());
        expect(karte).toHaveTextContent('Nr. WV-1');
        expect(karte).toHaveTextContent('01.01.2026 bis 31.12.2028');
        expect(karte).toHaveTextContent('jährlich');
        expect(karte).toHaveTextContent('450,50');
        expect(karte).toHaveTextContent('Jährliche Prüfung');
    });

    it('zeigt unbefristete und bald endende Verträge', async () => {
        aktuell = vertrag({ ende: null });
        const { unmount } = zeige();
        expect(await screen.findByText(/bis unbefristet/)).toBeVisible();
        unmount();
        aktuell = vertrag({ ende: '2026-12-01', endetBald: true, status: 'LAEUFT' });
        zeige();
        expect(await screen.findByText('Endet bald')).toBeVisible();
    });

    it('bietet ohne Vertrag das Anlegen an und speichert die Eingaben als Zahlen', async () => {
        const user = userEvent.setup();
        aktuell = null;
        zeige();
        expect(await screen.findByText(/noch kein Wartungsvertrag hinterlegt/)).toBeVisible();

        await user.click(screen.getByRole('button', { name: /Vertrag anlegen/ }));
        const dialog = await screen.findByRole('dialog');
        await user.type(within(dialog).getByLabelText(/Vertragsnummer/), 'WV-2');
        await user.type(within(dialog).getByLabelText(/Preis je Wartung/), '99,9');
        await user.click(within(dialog).getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(aufrufe('PUT')).toHaveLength(1));
        const gesendet = JSON.parse(aufrufe('PUT')[0][1].body);
        expect(gesendet).toMatchObject({ vertragsnummer: 'WV-2', intervallMonate: 12, preisNetto: 99.9, ende: null });
        expect(typeof gesendet.beginn).toBe('string');
        expect(await screen.findByText('Nr. WV-2')).toBeVisible();
    });

    it('meldet einen falschen Preis, ohne zu speichern', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: /Bearbeiten/ }));
        const dialog = await screen.findByRole('dialog');
        const preis = within(dialog).getByLabelText(/Preis je Wartung/);
        await user.clear(preis);
        await user.type(preis, '12,');
        await user.click(within(dialog).getByRole('button', { name: /Speichern/ }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent(/Preis/);
        expect(aufrufe('PUT')).toHaveLength(0);
    });

    it('zeigt die Meldung des Servers und lässt den Dialog offen', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (_u: string, init?: RequestInit) => {
            if ((init?.method ?? 'GET') === 'PUT') return antwort(400, { message: 'Das Intervall muss zwischen 1 und 120 Monaten liegen.' });
            return antwort(200, aktuell);
        });
        zeige();
        await user.click(await screen.findByRole('button', { name: /Bearbeiten/ }));
        const dialog = await screen.findByRole('dialog');
        await user.click(within(dialog).getByRole('button', { name: /Speichern/ }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Das Intervall muss zwischen 1 und 120 Monaten liegen.');
        expect(dialog).toBeVisible();
    });

    it('löscht den Vertrag erst nach Rückfrage', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Wartungsvertrag löschen' }));
        expect(await screen.findByText('Wartungsvertrag löschen?')).toBeVisible();
        expect(aufrufe('DELETE')).toHaveLength(0);
        await user.click(screen.getByRole('button', { name: 'Löschen' }));

        await waitFor(() => expect(aufrufe('DELETE')).toHaveLength(1));
        expect(await screen.findByText(/noch kein Wartungsvertrag hinterlegt/)).toBeVisible();
    });

    it('zeigt einen Ladefehler mit Wiederholen', async () => {
        fetcher.mockImplementation(async () => antwort(500, { message: 'kaputt' }));
        zeige();
        expect(await screen.findByRole('button', { name: 'Noch einmal versuchen' })).toBeVisible();
    });
});
