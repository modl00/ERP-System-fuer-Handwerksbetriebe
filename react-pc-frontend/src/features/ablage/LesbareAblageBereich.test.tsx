import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { LesbareAblageBereich } from './LesbareAblageBereich';
import { ToastProvider } from '../../components/ui/toast';
import type { LesbareAblageErgebnis } from './ablageApi';

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten });

const ergebnis = (aenderung: Partial<LesbareAblageErgebnis> = {}): LesbareAblageErgebnis => ({
    probelauf: false, zielordner: '/app/uploads/_lesbar', dateien: 12, neu: 10, unveraendert: 2, ersetzt: 0, entfernt: 1, kopiert: 0, fehlend: 3,
    beispiele: ['Projekte/2026/2026 014 - Anbau Halle/Rechnung.pdf'], fehler: [], ...aenderung,
});

let fetcher: ReturnType<typeof vi.fn>;
let status: { zielordner: string; automatisch: boolean };

beforeEach(() => {
    status = { zielordner: '/app/uploads/_lesbar', automatisch: false };
    fetcher = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
        if (url === '/api/admin/ablage/lesbar') return antwort(status);
        if (url.startsWith('/api/admin/ablage/lesbar/aufbauen') && init?.method === 'POST') return antwort(ergebnis({ probelauf: url.includes('probelauf=true') }));
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});

afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><LesbareAblageBereich /></ToastProvider>);

it('zeigt Zielordner und erklärt, dass nur auf Knopfdruck aufgebaut wird', async () => {
    zeige();

    expect(await screen.findByTestId('ablage-ordner')).toHaveTextContent('/app/uploads/_lesbar');
    expect(screen.getByText(/Nur per Knopfdruck/)).toBeVisible();
    expect(screen.getByText(/nicht verschoben und nicht doppelt gespeichert/)).toBeVisible();
});

it('nennt die nächtliche Automatik, wenn sie eingeschaltet ist', async () => {
    status = { zielordner: '/app/uploads/_lesbar', automatisch: true };
    zeige();

    expect(await screen.findByText(/Jede Nacht automatisch/)).toBeVisible();
});

it('die Vorschau ändert nichts und sagt das', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('ablage-ordner');

    await user.click(screen.getByRole('button', { name: /Vorschau/ }));

    const box = await screen.findByTestId('ablage-ergebnis');
    expect(box).toHaveTextContent('Vorschau (es wurde nichts geändert): 12 Dateien');
    expect(fetcher.mock.calls.some(([u]) => String(u).includes('probelauf=true'))).toBe(true);
    expect(box).toHaveTextContent('Datei nicht gefunden:');
});

it('Jetzt aufbauen zeigt die Zahlen, die Beispiele und eine Erfolgsmeldung', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('ablage-ordner');

    await user.click(screen.getByRole('button', { name: /Jetzt aufbauen/ }));

    expect(await screen.findByText('Lesbare Ablage aufgebaut: 12 Dateien.')).toBeVisible();
    const box = screen.getByTestId('ablage-ergebnis');
    expect(box).toHaveTextContent('Aufgebaut: 12 Dateien');
    expect(box).toHaveTextContent('neu: 10');
    expect(box).toHaveTextContent('entfernt: 1');
    expect(fetcher.mock.calls.some(([u]) => String(u).includes('probelauf=false'))).toBe(true);
});

it('warnt, wenn kopiert werden musste, weil das doppelten Platz kostet', async () => {
    const user = userEvent.setup();
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        if (url === '/api/admin/ablage/lesbar') return antwort(status);
        if (init?.method === 'POST') return antwort(ergebnis({ kopiert: 7 }));
        return antwort({});
    });
    zeige();
    await screen.findByTestId('ablage-ordner');

    await user.click(screen.getByRole('button', { name: /Jetzt aufbauen/ }));

    expect(await screen.findByText(/7 Dateien wurden kopiert und belegen doppelten Platz/)).toBeVisible();
});

it('zeigt Probleme beim Aufbau und eine Fehlermeldung des Servers', async () => {
    const user = userEvent.setup();
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        if (url === '/api/admin/ablage/lesbar') return antwort(status);
        if (init?.method === 'POST') return antwort({ message: 'Die lesbare Ablage wird gerade aufgebaut. Bitte einen Moment warten.' }, false, 409);
        return antwort({});
    });
    zeige();
    await screen.findByTestId('ablage-ordner');

    await user.click(screen.getByRole('button', { name: /Jetzt aufbauen/ }));

    expect(await screen.findByText(/gerade aufgebaut/)).toBeVisible();
    expect(screen.queryByTestId('ablage-ergebnis')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: /Jetzt aufbauen/ })).toBeEnabled());
});

it('listet Probleme einzelner Dateien auf', async () => {
    const user = userEvent.setup();
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        if (url === '/api/admin/ablage/lesbar') return antwort(status);
        if (init?.method === 'POST') return antwort(ergebnis({ fehler: ['Projekte/x/A.pdf: Zugriff verweigert'] }));
        return antwort({});
    });
    zeige();
    await screen.findByTestId('ablage-ordner');

    await user.click(screen.getByRole('button', { name: /Jetzt aufbauen/ }));

    expect(await screen.findByText(/Zugriff verweigert/)).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent('1 Probleme');
});
