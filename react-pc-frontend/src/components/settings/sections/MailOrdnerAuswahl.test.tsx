import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MailOrdnerAuswahl } from './MailOrdnerAuswahl';
import { ToastProvider } from '../../ui/toast';

/** Nur Dummy-Daten. */
const ordner = [
    { name: 'INBOX', anzeige: 'INBOX', vorschlag: 'EINGANG' },
    { name: 'INBOX.Gesendet', anzeige: 'Gesendet', vorschlag: 'GESENDET' },
    { name: 'INBOX.Rechnungen', anzeige: 'Rechnungen', vorschlag: 'EINGANG' },
];
let fetcher: ReturnType<typeof vi.fn>;
let uebersicht: unknown;
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body, text: async () => JSON.stringify(body) }) as Response;
const putBody = () => {
    const a = fetcher.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'PUT');
    return a ? JSON.parse((a[1] as RequestInit).body as string) : null;
};
const zeige = () => render(<ToastProvider><MailOrdnerAuswahl /></ToastProvider>);

beforeEach(() => {
    uebersicht = { konfiguriert: false, ausgewaehlt: [], ordner };
    fetcher = vi.fn(async (_url: string, init?: RequestInit) => (init?.method === 'PUT' ? json([]) : json(uebersicht)));
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('MailOrdnerAuswahl', () => {
    it('lädt die Ordner erst auf Klick; ohne gespeicherte Auswahl sind Posteingang und Gesendet vorgewählt', async () => {
        const u = userEvent.setup();
        zeige();
        expect(fetcher).not.toHaveBeenCalled();
        await u.click(screen.getByRole('button', { name: /Ordner vom Server laden/ }));

        expect(await screen.findByTestId('mail-ordner')).toBeInTheDocument();
        expect(screen.getByRole('checkbox', { name: 'INBOX lesen' })).toBeChecked();
        expect(screen.getByRole('checkbox', { name: 'INBOX.Gesendet lesen' })).toBeChecked();
        expect(screen.getByRole('checkbox', { name: 'INBOX.Rechnungen lesen' })).not.toBeChecked();
        expect(screen.getByLabelText('INBOX.Gesendet: Art')).toHaveValue('GESENDET');
    });

    it('speichert nur die gewählten Ordner, neu gewählte ohne Nachladen', async () => {
        const u = userEvent.setup();
        zeige();
        await u.click(screen.getByRole('button', { name: /Ordner vom Server laden/ }));
        await u.click(await screen.findByRole('checkbox', { name: 'INBOX.Rechnungen lesen' }));
        await u.click(screen.getByRole('button', { name: /Auswahl speichern \(3\)/ }));

        await waitFor(() => expect(putBody()).not.toBeNull());
        expect(putBody()).toEqual([
            { name: 'INBOX', richtung: 'EINGANG', nachladen: false },
            { name: 'INBOX.Gesendet', richtung: 'GESENDET', nachladen: false },
            { name: 'INBOX.Rechnungen', richtung: 'EINGANG', nachladen: false },
        ]);
    });

    it('Nachladen warnt vor vielen Mails und wird nur für neue Ordner mitgeschickt', async () => {
        const u = userEvent.setup();
        uebersicht = { konfiguriert: true, ausgewaehlt: [{ name: 'INBOX', richtung: 'EINGANG', seit: '2026-10-01' }], ordner };
        zeige();
        await u.click(screen.getByRole('button', { name: /Ordner vom Server laden/ }));
        await screen.findByTestId('mail-ordner');

        expect(screen.queryByRole('checkbox', { name: 'INBOX: ältere Mails nachladen' })).toBeNull();
        expect(screen.queryByRole('note')).toBeNull();
        await u.click(screen.getByRole('checkbox', { name: 'INBOX.Rechnungen lesen' }));
        await u.click(screen.getByRole('checkbox', { name: 'INBOX.Rechnungen: ältere Mails nachladen' }));
        expect(screen.getByRole('note')).toHaveTextContent('alle');
        await u.click(screen.getByRole('button', { name: /Auswahl speichern \(2\)/ }));

        await waitFor(() => expect(putBody()).not.toBeNull());
        expect(putBody()).toEqual([
            { name: 'INBOX', richtung: 'EINGANG', nachladen: false },
            { name: 'INBOX.Rechnungen', richtung: 'EINGANG', nachladen: true },
        ]);
    });

    it('zeigt die Meldung, wenn die Verbindung zum Postfach scheitert', async () => {
        const u = userEvent.setup();
        fetcher.mockResolvedValue(json({ message: 'Die Verbindung zum Postfach ist fehlgeschlagen: Login failed' }, 502));
        zeige();
        await u.click(screen.getByRole('button', { name: /Ordner vom Server laden/ }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Login failed');
        expect(screen.queryByTestId('mail-ordner')).toBeNull();
    });

    it('ohne gewählten Ordner lässt sich nicht speichern', async () => {
        const u = userEvent.setup();
        zeige();
        await u.click(screen.getByRole('button', { name: /Ordner vom Server laden/ }));
        await u.click(await screen.findByRole('checkbox', { name: 'INBOX lesen' }));
        await u.click(screen.getByRole('checkbox', { name: 'INBOX.Gesendet lesen' }));
        expect(screen.getByRole('button', { name: /Auswahl speichern \(0\)/ })).toBeDisabled();
    });
});
