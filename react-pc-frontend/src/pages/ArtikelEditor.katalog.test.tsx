import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import ArtikelEditor from './ArtikelEditor';

/** Aufschläge und Werkstoffe neben den Kategorien in der Artikelübersicht (#242, #246). Nur Dummy-Daten. */
const AUFSCHLAEGE = { allgemein: 20, kategorien: [{ id: 1, oberkategorieId: null, pfad: 'Stahl', prozent: 25, wirksamProzent: 25, wirksamQuelle: 'KATEGORIE', wirksamVon: 'Stahl' }] };
const WERKSTOFFE = [{ id: 7, name: 'S235JR', anzeigename: 'Baustahl', werkstattname: null, dichte: 7.85, werkstoffnorm: 'EN 10025', verzinkungsgeeignet: true, pulverbeschichtungsgeeignet: true, beschichtungshinweis: null }];

const antwort = (daten: unknown) => ({ ok: true, status: 200, json: async () => daten, text: async () => JSON.stringify(daten) });
let fetcher: ReturnType<typeof vi.fn>;

beforeEach(() => {
    fetcher = vi.fn(async (url: string) => {
        if (url.startsWith('/api/aufschlaege')) return antwort(AUFSCHLAEGE);
        if (url === '/api/werkstoffe' || url.startsWith('/api/werkstoffe?')) return antwort(WERKSTOFFE);
        if (url.startsWith('/api/artikel/werkstoffe')) return antwort(['S235JR']);
        if (url.startsWith('/api/artikel/filteroptionen')) return antwort({ herstellverfahren: [], fertigungszustand: [], profilform: [] });
        if (url.startsWith('/api/artikel?')) return antwort({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 12 });
        return antwort([]);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<MemoryRouter><ToastProvider><ConfirmProvider><ArtikelEditor /></ConfirmProvider></ToastProvider></MemoryRouter>);
const aufrufe = (teil: string) => fetcher.mock.calls.filter(([u]) => String(u).startsWith(teil));

describe('Artikelübersicht: Aufschläge und Werkstoffe neben den Kategorien', { timeout: 30000 }, () => {
    it('hat neben „Kategorien anlegen" die Knöpfe „Aufschläge" und „Werkstoffe"', async () => {
        zeige();
        expect(await screen.findByRole('button', { name: 'Kategorien anlegen' })).toBeVisible();
        expect(screen.getByRole('button', { name: 'Aufschläge' })).toBeVisible();
        expect(screen.getByRole('button', { name: 'Werkstoffe' })).toBeVisible();
    });

    it('„Aufschläge" öffnet die Pflege der Aufschläge in einem Fenster, ohne die Seite zu verlassen', async () => {
        const user = userEvent.setup();
        zeige();
        expect(aufrufe('/api/aufschlaege')).toHaveLength(0);
        await user.click(await screen.findByRole('button', { name: 'Aufschläge' }));

        const fenster = await screen.findByRole('dialog', { name: 'Aufschläge' });
        expect(await within(fenster).findByText('Aufschlag je Kategorie')).toBeVisible();
        expect(within(fenster).getByRole('textbox', { name: 'Aufschlag für Stahl' })).toHaveValue('25');
        // Die Seitenüberschrift der eigenständigen Seite gibt es im Fenster nicht doppelt
        expect(within(fenster).queryByRole('heading', { level: 1 })).toBeNull();
    });

    it('„Werkstoffe" öffnet die Liste der Werkstoffe mit „Neuer Werkstoff" im Fenster', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Werkstoffe' }));

        const fenster = await screen.findByRole('dialog', { name: 'Werkstoffe' });
        expect(await within(fenster).findByText('Baustahl')).toBeVisible();
        await user.click(within(fenster).getByRole('button', { name: /Neuer Werkstoff/ }));
        expect(await within(fenster).findByTestId('werkstoff-formular')).toBeVisible();
    });

    it('nach dem Schließen der Werkstoffe lädt die Suche ihre Auswahllisten neu', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByRole('button', { name: 'Werkstoffe' });
        await waitFor(() => expect(aufrufe('/api/artikel/werkstoffe').length).toBeGreaterThan(0));
        const vorher = aufrufe('/api/artikel/werkstoffe').length;

        await user.click(screen.getByRole('button', { name: 'Werkstoffe' }));
        const fenster = await screen.findByRole('dialog', { name: 'Werkstoffe' });
        await user.click(within(fenster).getByRole('button', { name: 'Schließen' }));

        await waitFor(() => expect(aufrufe('/api/artikel/werkstoffe').length).toBeGreaterThan(vorher));
        expect(screen.queryByRole('dialog', { name: 'Werkstoffe' })).toBeNull();
    });

    it('die eigenständigen Seiten bleiben unverändert mit Titel erreichbar', async () => {
        const { default: AufschlaegeSeite } = await import('./Aufschlaege');
        render(<ToastProvider><AufschlaegeSeite /></ToastProvider>);
        expect(await screen.findByRole('heading', { level: 1, name: /Aufschläge/i })).toBeVisible();
    });
});
