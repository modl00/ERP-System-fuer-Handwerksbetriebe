import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FunktionsrechteBereich } from './FunktionsrechteBereich';
import { ToastProvider } from '../../components/ui/toast';

const ALLES = { ansehen: true, anlegen: true, aendern: true, loeschen: true };
const uebersicht = () => ({
    funktionen: [
        { name: 'LAGER', anzeige: 'Lager', beschreibung: 'Lagerbestand und Buchungen' },
        { name: 'WARTUNG', anzeige: 'Wartung', beschreibung: 'Wartungen und Feldeinsätze' },
    ],
    aktionen: ['ANSEHEN', 'ANLEGEN', 'AENDERN', 'LOESCHEN'],
    abteilungen: [
        { abteilungId: 1, abteilungName: 'Montage', rechte: { LAGER: { ...ALLES, aendern: false, loeschen: false }, WARTUNG: ALLES } },
        { abteilungId: 2, abteilungName: 'Büro', rechte: { LAGER: ALLES, WARTUNG: ALLES } },
    ],
});
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
const puts = () => fetcher.mock.calls.filter(([, i]) => i?.method === 'PUT');

beforeEach(() => {
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === 'PUT') {
            const id = Number(url.split('/')[3]);
            const b = JSON.parse(init.body as string) as { rechte: Record<string, unknown> };
            return antwort({ abteilungId: id, abteilungName: id === 1 ? 'Montage' : 'Büro', rechte: b.rechte });
        }
        return antwort(uebersicht());
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><FunktionsrechteBereich /></ToastProvider>);

describe('FunktionsrechteBereich', () => {
    it('zeigt je Abteilung und Funktion die vier Rechte', async () => {
        zeige();
        const montage = await screen.findByTestId('funktionsrechte-1');
        expect(within(montage).getByRole('checkbox', { name: 'Montage: Lager Sehen' })).toBeChecked();
        expect(within(montage).getByRole('checkbox', { name: 'Montage: Lager Anlegen' })).toBeChecked();
        expect(within(montage).getByRole('checkbox', { name: 'Montage: Lager Ändern' })).not.toBeChecked();
        expect(within(montage).getByRole('checkbox', { name: 'Montage: Wartung Löschen' })).toBeChecked();
        expect(screen.getByTestId('funktionsrechte-2')).toBeVisible();
    });

    it('ohne Sehen geht nichts anderes, und wer Ändern erlaubt, darf auch sehen', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByTestId('funktionsrechte-1');

        await user.click(screen.getByRole('checkbox', { name: 'Büro: Lager Sehen' }));
        for (const s of ['Sehen', 'Anlegen', 'Ändern', 'Löschen']) {
            expect(screen.getByRole('checkbox', { name: `Büro: Lager ${s}` })).not.toBeChecked();
        }
        await user.click(screen.getByRole('checkbox', { name: 'Büro: Lager Ändern' }));
        expect(screen.getByRole('checkbox', { name: 'Büro: Lager Ändern' })).toBeChecked();
        expect(screen.getByRole('checkbox', { name: 'Büro: Lager Sehen' })).toBeChecked();
        expect(screen.getByRole('checkbox', { name: 'Büro: Lager Anlegen' })).not.toBeChecked();
    });

    it('speichert nur die Abteilung, deren Knopf geklickt wurde, mit allen Funktionen', async () => {
        const user = userEvent.setup();
        zeige();
        const montage = await screen.findByTestId('funktionsrechte-1');
        await user.click(within(montage).getByRole('checkbox', { name: 'Montage: Lager Anlegen' }));
        await user.click(within(montage).getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(puts()).toHaveLength(1));
        expect(puts()[0][0]).toBe('/api/abteilungen/1/funktionsrechte');
        expect(JSON.parse(puts()[0][1].body)).toEqual({
            rechte: { LAGER: { ansehen: true, anlegen: false, aendern: false, loeschen: false }, WARTUNG: ALLES },
        });
        expect(await within(montage).findByRole('button', { name: /Gespeichert/ })).toBeVisible();
    });

    it('erklärt, wenn nur Administratoren speichern dürfen', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (_u: string, init?: RequestInit) => (init?.method === 'PUT' ? antwort({}, false, 403) : antwort(uebersicht())));
        zeige();
        await user.click(within(await screen.findByTestId('funktionsrechte-2')).getByRole('button', { name: /Speichern/ }));
        expect(await screen.findByText('Nur Administratoren dürfen Rechte ändern.')).toBeVisible();
    });

    it('zeigt einen Ladefehler mit „Erneut laden"', async () => {
        fetcher.mockImplementation(async () => antwort({ message: 'kaputt' }, false, 500));
        zeige();
        expect((await screen.findAllByRole('alert')).some(a => a.textContent?.includes('kaputt'))).toBe(true);
        expect(screen.getByRole('button', { name: 'Erneut laden' })).toBeVisible();
    });

    it('lehnt eine unerwartete Antwort ab, statt abzustürzen', async () => {
        fetcher.mockImplementation(async () => antwort([{ irgendwas: 1 }]));
        zeige();
        expect((await screen.findAllByRole('alert')).some(a => a.textContent?.includes('nicht gelesen'))).toBe(true);
    });
});
