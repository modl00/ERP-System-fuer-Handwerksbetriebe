import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtikelLagerBereich } from './ArtikelLagerBereich';
import { ToastProvider } from '../../components/ui/toast';

let lagerAktiv: boolean | null = true;
vi.mock('../modulschalter/useFeatures', () => ({ useLagerAktiv: () => lagerAktiv }));

const bestand = (aenderung: Record<string, unknown> = {}) => ({
    artikelId: 7, bezeichnung: 'Rundrohr 42,4 x 2', artikelnummer: 'ST-RR-042', einheit: 'm', bestand: 12.5, mindestbestand: 20,
    unterMindestbestand: true, negativ: false, orte: [{ lagerortId: 1, lagerortName: 'Hauptlager', bestand: 12.5 }], ...aenderung,
});
const buchungen = [
    { id: 1, art: 'ZUGANG', menge: 20, lagerortId: 1, lagerortName: 'Hauptlager', gebuchtAm: '2026-09-30T08:00:00', projektId: null, projekt: null, notiz: 'Lieferschein 17', benutzer: 'Max Mustermann' },
    { id: 2, art: 'ABGANG', menge: -7.5, lagerortId: 1, lagerortName: 'Hauptlager', gebuchtAm: '2026-10-01T08:00:00', projektId: 3, projekt: 'Geländer Musterweg', notiz: null, benutzer: 'Max Mustermann' },
];
const orte = [{ id: 1, name: 'Hauptlager', beschreibung: null, aktiv: true, sortOrder: 0 }];

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });
let fetcher: ReturnType<typeof vi.fn>;
let aktuellerBestand: ReturnType<typeof bestand>;
let aktuelleBuchungen: unknown[];

beforeEach(() => {
    lagerAktiv = true;
    aktuellerBestand = bestand();
    aktuelleBuchungen = buchungen;
    fetcher = vi.fn(async (url: string) => {
        if (url.endsWith('/bestand')) return antwort(aktuellerBestand);
        if (url.endsWith('/buchungen')) return antwort(aktuelleBuchungen);
        if (url.endsWith('/orte')) return antwort(orte);
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><MemoryRouter><ArtikelLagerBereich artikelId={7} /></MemoryRouter></ToastProvider>);

describe('ArtikelLagerBereich', () => {
    it('zeigt Bestand, Lagerorte, Mindestbestand und die letzten Buchungen', async () => {
        zeige();
        expect(await screen.findByTestId('lager-gesamt')).toHaveTextContent('12,5 m');
        expect(screen.getByText('Hauptlager: 12,5 m')).toBeVisible();
        expect(screen.getByText(/Unter dem Mindestbestand: es fehlen 7,5 m/)).toBeVisible();
        const liste = screen.getByTestId('lager-buchungen');
        expect(within(liste).getAllByRole('listitem')).toHaveLength(2);
        expect(within(liste).getByText(/Geländer Musterweg/)).toBeVisible();
        expect(screen.getByRole('link', { name: /Im Lager öffnen/ })).toHaveAttribute('href', '/lager');
        expect(fetcher).toHaveBeenCalledWith('/api/lager/artikel/7/bestand', undefined);
    });

    it('erscheint nicht, wenn das Lagermodul ausgeschaltet ist, und ruft dann nichts ab', () => {
        lagerAktiv = false;
        zeige();
        expect(screen.queryByTestId('artikel-lager')).toBeNull();
        expect(fetcher).not.toHaveBeenCalled();
    });

    it('warnt bei negativem Bestand', async () => {
        aktuellerBestand = bestand({ bestand: -2, negativ: true, unterMindestbestand: true });
        zeige();
        expect(await screen.findByText(/mehr entnommen als eingebucht/)).toBeVisible();
        expect(screen.queryByText(/Unter dem Mindestbestand/)).toBeNull();
    });

    it('bietet bei einem Artikel ohne Lager den ersten Zugang an', async () => {
        aktuellerBestand = bestand({ bestand: 0, mindestbestand: null, unterMindestbestand: false, orte: [] });
        aktuelleBuchungen = [];
        zeige();
        expect(await screen.findByText('Dieser Artikel ist noch nicht im Lager.')).toBeVisible();
        expect(screen.getByRole('button', { name: /Ersten Zugang buchen/ })).toBeEnabled();
    });

    it('öffnet den Buchungsdialog mit dem Artikel und lädt nach dem Buchen neu', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: /^Zugang/ }));
        expect(await screen.findByRole('dialog')).toBeVisible();
        const vorher = fetcher.mock.calls.filter(([u]) => String(u).endsWith('/bestand')).length;

        fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
            if (url.endsWith('/buchungen') && init?.method === 'POST') return antwort(bestand({ bestand: 32.5, unterMindestbestand: false }));
            if (url.endsWith('/bestand')) return antwort(bestand({ bestand: 32.5, unterMindestbestand: false }));
            if (url.endsWith('/buchungen')) return antwort(buchungen);
            return antwort(orte);
        });
        await user.type(screen.getByLabelText(/Menge/), '20');
        await user.click(screen.getByRole('button', { name: /Zugang buchen|Buchen/ }));

        await waitFor(() => expect(fetcher.mock.calls.filter(([u]) => String(u).endsWith('/bestand')).length).toBeGreaterThan(vorher));
        expect(await screen.findByTestId('lager-gesamt')).toHaveTextContent('32,5 m');
    });

    it('zeigt einen Fehler beim Laden als Hinweis', async () => {
        fetcher.mockImplementation(async () => antwort({ message: 'Das Lagermodul ist ausgeschaltet.' }, false, 403));
        zeige();
        expect((await screen.findAllByRole('alert')).some(a => a.textContent?.includes('Das Lagermodul ist ausgeschaltet.'))).toBe(true);
    });
});
