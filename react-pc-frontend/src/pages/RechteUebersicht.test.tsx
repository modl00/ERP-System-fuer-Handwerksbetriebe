import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import RechteUebersichtSeite from './RechteUebersicht';
import { gruppiere, type RechteUebersicht } from '../features/rechte/rechteUebersichtApi';

/** Nur Dummy-Daten. */
const feld = (stufe: 'ALLES' | 'TEILWEISE' | 'NICHTS', text: string, grund: string) => ({ stufe, text, grund });

const DATEN: RechteUebersicht = {
    bereiche: [
        { schluessel: 'EINSTELLUNGEN', name: 'Einstellungen und Benutzer', gruppe: 'Programm', beschreibung: 'Nur Administratoren.' },
        { schluessel: 'FUNKTION_LAGER', name: 'Lager', gruppe: 'Funktionen', beschreibung: 'Lagerbestand ansehen und buchen.' },
        { schluessel: 'BELEGE', name: 'Belege und Kasse', gruppe: 'Buchhaltung', beschreibung: 'Belege scannen und ansehen, Kassenbuch.' },
    ],
    benutzer: [
        { profilId: 1, anzeigename: 'Admin Chef', benutzername: 'chef', administrator: true, mitarbeiterId: 10, mitarbeiterName: 'Max Mustermann', abteilungen: ['Büro'],
            felder: { EINSTELLUNGEN: feld('ALLES', 'Ja', 'Als Administrator.'), FUNKTION_LAGER: feld('ALLES', 'Alles', 'Als Administrator am PC nicht eingeschränkt.'), BELEGE: feld('ALLES', 'Sehen und scannen', 'Als Administrator.') } },
        { profilId: 2, anzeigename: 'Erika Musterfrau', benutzername: 'erika', administrator: false, mitarbeiterId: 11, mitarbeiterName: 'Erika Musterfrau', abteilungen: ['Montage'],
            felder: { EINSTELLUNGEN: feld('NICHTS', 'Nein', 'Nur Administratoren dürfen Einstellungen, Benutzer und Rechte ändern.'), FUNKTION_LAGER: feld('TEILWEISE', 'Sehen', 'Über die Abteilung Montage.'),
                BELEGE: feld('NICHTS', 'Nein', 'Keine seiner Abteilungen hat das Dokumentenrecht „Belege und Kasse“.') } },
        { profilId: 3, anzeigename: 'Hans Beispiel', benutzername: 'hans', administrator: false, mitarbeiterId: null, mitarbeiterName: null, abteilungen: [],
            felder: { EINSTELLUNGEN: feld('NICHTS', 'Nein', 'x'), FUNKTION_LAGER: feld('ALLES', 'Alles', 'Kein Mitarbeiter verknüpft. Dann ist alles erlaubt.'), BELEGE: feld('NICHTS', 'Nein', 'Kein Mitarbeiter mit diesem Benutzer verknüpft.') } },
    ],
    warnungen: [{ art: 'OHNE_MITARBEITER', text: 'Hans Beispiel ist mit keinem Mitarbeiter verknüpft.', ziel: '/benutzer', profilId: 3 },
        { art: 'NIEMAND', text: 'Niemand darf „Monate abschließen“.', ziel: '/abteilung-berechtigungen', profilId: null }],
};

const antwort = (daten: unknown, status = 200) => ({ ok: status < 300, status, json: async () => daten });
let fetcher: ReturnType<typeof vi.fn>;
const zeige = () => render(<MemoryRouter><RechteUebersichtSeite /></MemoryRouter>);

beforeEach(() => {
    fetcher = vi.fn(async () => antwort(DATEN));
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('Wer darf was?', { timeout: 30000 }, () => {
    it('zeigt jeden Benutzer mit Mitarbeiter und Abteilung, die Felder mit Klartext und Gruppen als Kopfzeile', async () => {
        zeige();

        const erika = await screen.findByRole('row', { name: /Erika Musterfrau/ });
        expect(erika).toHaveTextContent('Erika Musterfrau · Montage');
        expect(within(erika).getByRole('button', { name: 'Erika Musterfrau, Lager: Sehen' })).toBeInTheDocument();
        expect(within(erika).getByRole('button', { name: 'Erika Musterfrau, Belege und Kasse: Nein' })).toBeInTheDocument();
        expect(screen.getByRole('row', { name: /Admin Chef/ })).toHaveTextContent('Admin');
        expect(screen.getByRole('row', { name: /Hans Beispiel/ })).toHaveTextContent('kein Mitarbeiter verknüpft');
        ['Programm', 'Funktionen', 'Buchhaltung'].forEach((g) => expect(screen.getByRole('columnheader', { name: g })).toBeInTheDocument());
        expect(fetcher).toHaveBeenCalledWith('/api/admin/rechte-uebersicht');
    });

    it('Klick auf ein Feld erklärt, woher das Recht kommt', async () => {
        const user = userEvent.setup();
        zeige();
        expect(await screen.findByText(/Auf ein Feld klicken/)).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Erika Musterfrau, Lager: Sehen' }));
        expect(screen.getByText('Erika Musterfrau: Lager')).toBeInTheDocument();
        expect(screen.getByText('Über die Abteilung Montage.')).toBeInTheDocument();
        expect(screen.getByText('Lagerbestand ansehen und buchen.')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Hans Beispiel, Lager: Alles' }));
        expect(screen.getByText(/Kein Mitarbeiter verknüpft. Dann ist alles erlaubt./)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Hans Beispiel, Lager: Alles' })).toHaveAttribute('aria-pressed', 'true');
    });

    it('Warnungen stehen oben mit Link zur Behebung', async () => {
        zeige();

        expect(await screen.findByText('2 Hinweise zur Einrichtung')).toBeInTheDocument();
        expect(screen.getByText('Hans Beispiel ist mit keinem Mitarbeiter verknüpft.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Benutzer öffnen' })).toHaveAttribute('href', '/benutzer');
        expect(screen.getByRole('link', { name: 'Rechte der Abteilungen öffnen' })).toHaveAttribute('href', '/abteilung-berechtigungen');
    });

    it('ohne Warnungen steht ein ruhiger Satz statt der Liste', async () => {
        fetcher.mockImplementation(async () => antwort({ ...DATEN, warnungen: [] }));
        zeige();

        expect(await screen.findByText(/Die Einrichtung ist vollständig/)).toBeInTheDocument();
        expect(screen.queryByText(/Hinweise? zur Einrichtung/)).not.toBeInTheDocument();
    });

    it('Suche filtert nach Benutzer, Mitarbeiter und Abteilung; ohne Treffer ein Satz', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByRole('row', { name: /Erika Musterfrau/ });

        await user.type(screen.getByLabelText('Benutzer suchen'), 'montage');
        expect(screen.queryByRole('row', { name: /Admin Chef/ })).not.toBeInTheDocument();
        expect(screen.getByRole('row', { name: /Erika Musterfrau/ })).toBeInTheDocument();
        await user.clear(screen.getByLabelText('Benutzer suchen'));
        await user.type(screen.getByLabelText('Benutzer suchen'), 'gibtesnicht');
        expect(screen.getByText('Dazu gibt es keinen Benutzer.')).toBeInTheDocument();
    });

    it('Fehler: Nicht-Administrator bekommt einen klaren Satz, ohne Netz ebenfalls', async () => {
        fetcher.mockImplementation(async () => antwort({}, 403));
        const { unmount } = zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('nur für Administratoren');
        unmount();

        fetcher.mockImplementation(async () => { throw new TypeError('Failed to fetch'); });
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('Keine Verbindung');
    });

    it('gruppiere fasst benachbarte Bereiche derselben Gruppe zusammen', () => {
        expect(gruppiere(DATEN.bereiche).map((g) => [g.gruppe, g.bereiche.length])).toEqual([['Programm', 1], ['Funktionen', 1], ['Buchhaltung', 1]]);
        expect(gruppiere([{ schluessel: 'A', name: 'A', gruppe: 'X', beschreibung: '' }, { schluessel: 'B', name: 'B', gruppe: 'X', beschreibung: '' }]).map((g) => g.bereiche.length)).toEqual([2]);
    });
});
