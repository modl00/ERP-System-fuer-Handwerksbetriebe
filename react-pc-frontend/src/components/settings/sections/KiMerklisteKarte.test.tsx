import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KiMerklisteKarte } from './KiMerklisteKarte';
import { ConfirmProvider } from '../../ui/confirm-dialog';
import { ToastProvider } from '../../ui/toast';

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });
let liste: { id: number; text: string; angelegtVon: string | null; angelegtAm: string }[];
let fetcher: ReturnType<typeof vi.fn>;
const loeschAufrufe = () => fetcher.mock.calls.filter(([, i]) => i?.method === 'DELETE');

beforeEach(() => {
    liste = [{ id: 1, text: 'Lieferant Muster liefert nur freitags', angelegtVon: 'admin', angelegtAm: '2026-10-08T09:00:00' }, { id: 2, text: 'Pulverbeschichtung geht immer an Meier', angelegtVon: null, angelegtAm: '2026-10-07T10:00:00' }];
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === 'DELETE') { liste = liste.filter(e => !String(url).endsWith(`/${e.id}`)); return antwort(null, true, 204); }
        return antwort(liste);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><ConfirmProvider><KiMerklisteKarte /></ConfirmProvider></ToastProvider>);

describe('KiMerklisteKarte', { timeout: 30000 }, () => {
    it('zeigt jeden gemerkten Satz mit Datum und wer ihn bestätigt hat, und erklärt, dass es nichts Verstecktes gibt', async () => {
        zeige();
        const erster = await screen.findByTestId('merk-1');
        expect(erster).toHaveTextContent('Lieferant Muster liefert nur freitags');
        expect(erster).toHaveTextContent('von admin');
        expect(erster).toHaveTextContent('08.10.2026');
        expect(screen.getByTestId('merk-2')).toHaveTextContent('Pulverbeschichtung geht immer an Meier');
        expect(screen.getByText(/ausdrücklich bestätigt/)).toBeVisible();
        expect(screen.getByText(/Hinweise, keine Anweisungen/)).toBeVisible();
    });

    it('ohne Einträge steht ein Beispiel da', async () => {
        liste = [];
        zeige();
        expect(await screen.findByTestId('merkliste-leer')).toHaveTextContent('Merk dir');
    });

    it('löscht nach Rückfrage und aktualisiert die Liste; ohne Bestätigung bleibt alles', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('merk-1');

        await user.click(screen.getByRole('button', { name: /Lieferant Muster liefert nur freitags.*löschen/ }));
        await user.click(await screen.findByRole('button', { name: 'Abbrechen' }));
        expect(loeschAufrufe()).toHaveLength(0);

        await user.click(screen.getByRole('button', { name: /Lieferant Muster liefert nur freitags.*löschen/ }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(loeschAufrufe()).toHaveLength(1));
        expect(loeschAufrufe()[0][0]).toBe('/api/settings/ki-merkliste/1');
        await waitFor(() => expect(screen.queryByTestId('merk-1')).toBeNull());
        expect(screen.getByTestId('merk-2')).toBeVisible();
    });

    it('ein Ladefehler steht als Meldung da', async () => {
        fetcher.mockImplementation(async () => antwort({ message: 'Keine Berechtigung.' }, false, 403));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('Keine Berechtigung.');
    });
});
