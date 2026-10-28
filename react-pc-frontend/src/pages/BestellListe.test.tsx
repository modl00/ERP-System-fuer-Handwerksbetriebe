import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BestellListe from './BestellListe';
import type { Bestellung } from '../features/bestellwesen/bestellwesenApi';

/** Nur Dummy-Daten. */
let darfAnlegen: boolean | null = true;
vi.mock('../features/funktionsrechte/useFunktionsrechte', () => ({ useDarf: () => darfAnlegen }));

const basis = (b: Partial<Bestellung>): Bestellung => ({
    id: 1, bestellnummer: null, status: 'ENTWURF', lieferstand: 'ENTWURF', lieferantId: 3, lieferantName: 'Stahlhandel Muster', lieferantEmails: [], notiz: null,
    erstelltAm: '2026-10-01T08:00:00', erstelltVon: 'admin', bestelltAm: null, bestelltVon: null, bestelltPer: null, storniertAm: null, stornoGrund: null, summe: 0,
    anzahlPositionen: 2, anzahlGeliefert: 0, tageSeitBestellung: null, ueberfaellig: false, ueberfaelligNachTagen: 7, projekte: [], positionen: [], ...b,
});

const LISTE: Bestellung[] = [
    basis({ id: 4, status: 'ENTWURF', lieferstand: 'ENTWURF', lieferantName: 'Schrauben Muster' }),
    basis({ id: 3, bestellnummer: 'B-2026-10-0003', status: 'BESTELLT', lieferstand: 'BESTELLT', bestelltAm: '2026-10-04T09:00:00', projekte: ['2026/014 Anbau Halle'], summe: 42.5 }),
    basis({ id: 2, bestellnummer: 'B-2026-09-0002', status: 'BESTELLT', lieferstand: 'TEILWEISE', anzahlGeliefert: 1, bestelltAm: '2026-09-20T09:00:00', tageSeitBestellung: 15, ueberfaellig: true,
        lieferantName: 'Rohre Muster', projekte: ['2026/015 Geländer', '2026/014 Anbau Halle'] }),
    basis({ id: 1, bestellnummer: 'B-2026-09-0001', status: 'BESTELLT', lieferstand: 'GELIEFERT', anzahlGeliefert: 2, bestelltAm: '2026-09-02T09:00:00', tageSeitBestellung: 33 }),
    basis({ id: 5, bestellnummer: 'B-2026-09-0003', status: 'STORNIERT', lieferstand: 'STORNIERT', bestelltAm: '2026-09-10T09:00:00' }),
];

const antwort = (daten: unknown, status = 200) => ({ ok: status < 300, status, json: async () => daten });
let fetcher: ReturnType<typeof vi.fn>;

function Ort() { return <p data-testid="ort">{useLocation().pathname}</p>; }
const zeige = () => render(
    <MemoryRouter initialEntries={['/bestellungen/liste']}>
        <Routes><Route path="/bestellungen/liste" element={<BestellListe />} /><Route path="*" element={<Ort />} /></Routes>
    </MemoryRouter>);

beforeEach(() => {
    darfAnlegen = true;
    fetcher = vi.fn(async () => antwort(LISTE));
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('Bestellübersicht', { timeout: 30000 }, () => {
    it('zeigt Nummer, Lieferant, Projekte, Stand und markiert Überfälliges', async () => {
        zeige();

        const zeile = await screen.findByRole('link', { name: 'Bestellung B-2026-09-0002 öffnen' });
        expect(zeile).toHaveTextContent('Rohre Muster');
        expect(zeile).toHaveTextContent('2026/015 Geländer, 2026/014 Anbau Halle');
        expect(zeile).toHaveTextContent('1 von 2');
        expect(zeile).toHaveTextContent('Teilweise geliefert');
        expect(zeile).toHaveTextContent('seit 15 Tagen');
        expect(screen.getByRole('link', { name: 'Bestellung Entwurf öffnen' })).toHaveTextContent('Schrauben Muster');
        expect(screen.getByRole('link', { name: 'Bestellung B-2026-10-0003 öffnen' })).not.toHaveTextContent('seit');
    });

    it('Filter: Entwürfe, wartet auf Ware, geliefert, überfällig und Storno; die Zähler stimmen', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByRole('link', { name: 'Bestellung B-2026-09-0002 öffnen' });

        expect(screen.getByRole('button', { name: /^Alle 5/ })).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: /^Entwürfe 1/ }));
        expect(screen.getAllByRole('link')).toHaveLength(1);
        await user.click(screen.getByRole('button', { name: /^Bestellt, Ware kommt noch 1/ }));
        expect(screen.getByRole('link', { name: 'Bestellung B-2026-10-0003 öffnen' })).toBeInTheDocument();
        expect(screen.getAllByRole('link')).toHaveLength(1);
        await user.click(screen.getByRole('button', { name: /^Überfällig 1/ }));
        expect(screen.getByRole('link', { name: 'Bestellung B-2026-09-0002 öffnen' })).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: /^Geliefert 1/ }));
        expect(screen.getByRole('link', { name: 'Bestellung B-2026-09-0001 öffnen' })).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: /^Storniert 1/ }));
        expect(screen.getByRole('link', { name: 'Bestellung B-2026-09-0003 öffnen' })).toBeInTheDocument();
    });

    it('Suche findet nach Nummer, Lieferant und Projekt; ohne Treffer ein Satz', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByRole('link', { name: 'Bestellung B-2026-09-0002 öffnen' });

        await user.type(screen.getByLabelText('Bestellungen durchsuchen'), 'geländer');
        expect(screen.getAllByRole('link')).toHaveLength(1);
        await user.clear(screen.getByLabelText('Bestellungen durchsuchen'));
        await user.type(screen.getByLabelText('Bestellungen durchsuchen'), '0003');
        expect(screen.getAllByRole('link')).toHaveLength(2);
        await user.clear(screen.getByLabelText('Bestellungen durchsuchen'));
        await user.type(screen.getByLabelText('Bestellungen durchsuchen'), 'gibtesnicht');
        expect(screen.getByText('Dazu gibt es keine Bestellung.')).toBeInTheDocument();
    });

    it('Klick auf eine Zeile öffnet die Bestellung, „Neue Bestellung" den leeren Entwurf', async () => {
        const user = userEvent.setup();
        zeige();

        await user.click(await screen.findByRole('link', { name: 'Bestellung B-2026-10-0003 öffnen' }));
        expect(screen.getByTestId('ort')).toHaveTextContent('/bestellungen/bestellung/3');
    });

    it('„Neue Bestellung" führt zum leeren Entwurf, ohne Anlegen-Recht gibt es den Knopf nicht', async () => {
        const user = userEvent.setup();
        const { unmount } = zeige();
        await user.click(await screen.findByRole('button', { name: /Neue Bestellung/ }));
        expect(screen.getByTestId('ort')).toHaveTextContent('/bestellungen/bestellung/neu');
        unmount();

        darfAnlegen = false;
        zeige();
        await screen.findByRole('link', { name: 'Bestellung B-2026-10-0003 öffnen' });
        expect(screen.queryByRole('button', { name: /Neue Bestellung/ })).not.toBeInTheDocument();
    });

    it('leere Liste: freundlicher Hinweis; Serverfehler: Meldung mit dem Satz des Servers', async () => {
        fetcher.mockImplementation(async () => antwort([]));
        const { unmount } = zeige();
        expect(await screen.findByText('Noch keine Bestellung angelegt.')).toBeInTheDocument();
        unmount();

        fetcher.mockImplementation(async () => antwort({ message: 'Dafür fehlt die Berechtigung.' }, 403));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('Dafür fehlt die Berechtigung.');
        await waitFor(() => expect(within(screen.getByRole('alert')).queryByText(/geladen werden/)).not.toBeInTheDocument());
    });
});
