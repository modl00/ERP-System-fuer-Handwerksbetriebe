import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SatzUebernahmeKarte } from './SatzUebernahmeKarte';
import { ToastProvider } from '../../components/ui/toast';

/** Nur Dummy-Daten. */
const parameter = { jahr: 2026, wagnis: 5, gewinn: 10, interneQuote: null };
const vorschau = {
    jahr: 2026, neuerSatz: 80,
    zeilen: [
        { arbeitsgangId: 1, name: 'Schweißen', abteilung: 'Werkstatt', alt: 70, neu: 80, differenz: 10, art: 'AENDERT' },
        { arbeitsgangId: 2, name: 'Montage', abteilung: null, alt: 80, neu: 80, differenz: 0, art: 'GLEICH' },
        { arbeitsgangId: 3, name: 'Planung', abteilung: 'Büro', alt: null, neu: 80, differenz: null, art: 'NEU' },
    ],
};
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;

let fetcher: ReturnType<typeof vi.fn>;
let postAntwort: () => Response;
const zeige = (p = parameter, moeglich = true) => render(<ToastProvider><SatzUebernahmeKarte parameter={p} moeglich={moeglich} /></ToastProvider>);
const postBody = () => {
    const a = fetcher.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'POST');
    return a ? JSON.parse((a[1] as RequestInit).body as string) : null;
};

beforeEach(() => {
    postAntwort = () => json({ geschrieben: 2 });
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url).startsWith('/api/kostenrechnung/uebernahme/vorschau')) return json(vorschau);
        if (url === '/api/kostenrechnung/uebernahme' && init?.method === 'POST') return postAntwort();
        return json({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('SatzUebernahmeKarte', () => {
    it('lädt nichts von selbst; erst der Klick holt die Vorschau', async () => {
        zeige();
        expect(fetcher).not.toHaveBeenCalled();
        await userEvent.setup().click(screen.getByRole('button', { name: /Vorschau anzeigen/ }));
        expect(await screen.findByTestId('uebernahme-vorschau')).toBeInTheDocument();
        expect(String(fetcher.mock.calls[0][0])).toContain('jahr=2026');
        expect(String(fetcher.mock.calls[0][0])).toContain('wagnis=5');
    });

    it('zeigt bisher, neu und Unterschied; vorgewählt sind nur Arbeitsgänge, die sich ändern oder keinen Satz haben', async () => {
        zeige();
        await userEvent.setup().click(screen.getByRole('button', { name: /Vorschau anzeigen/ }));
        const schweissen = await screen.findByTestId('uebernahme-Schweißen');
        expect(schweissen).toHaveTextContent('70,00');
        expect(schweissen).toHaveTextContent('80,00');
        expect(schweissen).toHaveTextContent('+10,00');
        expect(screen.getByTestId('uebernahme-Planung')).toHaveTextContent('kein Satz');
        expect(screen.getByRole('checkbox', { name: 'Schweißen übernehmen' })).toBeChecked();
        expect(screen.getByRole('checkbox', { name: 'Planung übernehmen' })).toBeChecked();
        expect(screen.getByRole('checkbox', { name: 'Montage übernehmen' })).not.toBeChecked();
        expect(within(screen.getByTestId('uebernahme-Montage')).getByText('gleich')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Ausgewählte übernehmen \(2\)/ })).toBeEnabled();
    });

    it('übernimmt nur die angekreuzten Arbeitsgänge mit dem Satz aus der Vorschau', async () => {
        const u = userEvent.setup();
        zeige();
        await u.click(screen.getByRole('button', { name: /Vorschau anzeigen/ }));
        await u.click(await screen.findByRole('checkbox', { name: 'Planung übernehmen' }));
        await u.click(screen.getByRole('button', { name: /Ausgewählte übernehmen \(1\)/ }));

        await waitFor(() => expect(postBody()).not.toBeNull());
        expect(postBody()).toMatchObject({ jahr: 2026, wagnis: 5, gewinn: 10, erwarteterSatz: 80, arbeitsgangIds: [1] });
    });

    it('ohne Auswahl ist das Übernehmen gesperrt', async () => {
        const u = userEvent.setup();
        zeige();
        await u.click(screen.getByRole('button', { name: /Vorschau anzeigen/ }));
        await u.click(await screen.findByRole('checkbox', { name: 'Alle auswählen' }));
        await u.click(screen.getByRole('checkbox', { name: 'Alle auswählen' }));
        expect(screen.getByRole('button', { name: /Ausgewählte übernehmen \(0\)/ })).toBeDisabled();
    });

    it('zeigt den Konflikt des Servers und ändert die Auswahl nicht still', async () => {
        const u = userEvent.setup();
        postAntwort = () => json({ message: 'Die Zahlen haben sich seit der Vorschau geändert. Bitte die Vorschau neu laden.' }, 409);
        zeige();
        await u.click(screen.getByRole('button', { name: /Vorschau anzeigen/ }));
        await u.click(await screen.findByRole('button', { name: /Ausgewählte übernehmen/ }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Vorschau neu laden');
    });

    it('verlangt eine neue Vorschau, wenn sich die Eingaben oben geändert haben', async () => {
        const u = userEvent.setup();
        const { rerender } = zeige();
        await u.click(screen.getByRole('button', { name: /Vorschau anzeigen/ }));
        await screen.findByTestId('uebernahme-vorschau');
        rerender(<ToastProvider><SatzUebernahmeKarte parameter={{ ...parameter, gewinn: 12 }} moeglich /></ToastProvider>);
        expect(screen.getByRole('status')).toHaveTextContent('Vorschau neu laden');
        expect(screen.getByRole('button', { name: /Ausgewählte übernehmen/ })).toBeDisabled();
    });

    it('ist gesperrt, solange oben kein Verrechnungssatz berechnet ist', () => {
        zeige(parameter, false);
        expect(screen.getByRole('button', { name: /Vorschau anzeigen/ })).toBeDisabled();
    });

    it('zeigt, wem ein Arbeitsgang folgt, und sperrt Arbeitsgänge ohne Satz für die Qualifikation', async () => {
        fetcher.mockImplementation(async (url: string) => {
            if (String(url).startsWith('/api/kostenrechnung/uebernahme/vorschau')) {
                return json({ jahr: 2026, neuerSatz: 80, zeilen: [
                    { arbeitsgangId: 1, name: 'Schweißen', abteilung: 'Werkstatt', alt: 70, neu: 92.5, differenz: 22.5, art: 'AENDERT', quelle: 'VERRECHNUNG', gruppe: 'FACHARBEITER' },
                    { arbeitsgangId: 2, name: 'Planung', abteilung: 'Büro', alt: 60, neu: null, differenz: null, art: 'KEIN_SATZ', quelle: 'VERRECHNUNG', gruppe: 'MEISTER' },
                ] });
            }
            return json({});
        });
        zeige();
        await userEvent.setup().click(screen.getByRole('button', { name: /Vorschau anzeigen/ }));
        const schweissen = within(await screen.findByTestId('uebernahme-Schweißen'));
        expect(schweissen.getByText('folgt dem Verrechnungssatz (Facharbeiter)')).toBeInTheDocument();
        const planung = within(screen.getByTestId('uebernahme-Planung'));
        expect(planung.getByRole('checkbox', { name: 'Planung übernehmen' })).toBeDisabled();
        expect(planung.getByText('Für diese Qualifikation fehlen verkäufliche Stunden')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Ausgewählte übernehmen \(1\)/ })).toBeEnabled();
    });
});
