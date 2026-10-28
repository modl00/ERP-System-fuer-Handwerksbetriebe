import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { RibbonNavigation } from './RibbonNav';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import { antwort, aufrufe, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { setzeFeaturesZurueck } from '../../features/modulschalter/useFeatures';
import { setzeTelefonBerechtigungZurueck } from '../../features/telefon/useTelefonBerechtigung';
import { setzeMenueZurueck } from '../../features/menue/useMenue';

/** Nur Dummy-Daten. */
type Menue = { vorgabe: string[] | null; eigene: string[] | null } | 'laedt';

function stubbe(menue: Menue, admin = false) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/auth/me'
            ? antwort({ id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: admin ? ['ADMIN'] : [], admin, requiresInitialSetup: false })
            : undefined),
        (url) => (url.pathname === '/api/features' ? antwort({ wartung: true, lager: true, miete: true, website: true }) : undefined),
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: false }) : undefined),
        (url) => (url.pathname === '/api/notifications/summary' ? antwort({ totalCount: 0, categories: [], recentItems: [] }) : undefined),
        (url, init) => {
            if (url.pathname === '/api/menue' && menue === 'laedt') return new Promise<Response>(() => undefined);
            if (menue === 'laedt') return undefined;
            if (url.pathname === '/api/menue') return antwort(menue);
            if (url.pathname === '/api/menue/eigene' && init?.method === 'PUT') return antwort({ vorgabe: menue.vorgabe, eigene: JSON.parse(String(init.body)).ausgeblendet });
            if (url.pathname === '/api/menue/eigene' && init?.method === 'DELETE') return antwort({ vorgabe: menue.vorgabe, eigene: null });
            if (url.pathname === '/api/settings/menue/vorgabe' && init?.method === 'PUT') return antwort({ vorgabe: JSON.parse(String(init.body)).ausgeblendet, eigene: menue.eigene });
            return undefined;
        },
    );
}

function zeige(pfad = '/projekte') {
    return render(
        <MemoryRouter initialEntries={[pfad]}>
            <ToastProvider><ConfirmProvider>
                <AuthProvider><RibbonNavigation /></AuthProvider>
            </ConfirmProvider></ToastProvider>
        </MemoryRouter>,
    );
}

const stammdaten = () => fireEvent.click(screen.getByRole('button', { name: 'Vorlagen & Stammdaten' }));

describe('Menüleiste – ausgeblendete Punkte', { timeout: 30000 }, () => {
    beforeEach(() => { setzeFeaturesZurueck(); setzeTelefonBerechtigungZurueck(); setzeMenueZurueck(); });
    afterEach(() => vi.unstubAllGlobals());

    it('markiert ohne eigene Auswahl den mitgelieferten Vorschlag als ausgeblendet und lässt Alltägliches unmarkiert', async () => {
        stubbe({ vorgabe: null, eigene: null });
        zeige();
        stammdaten();
        const menue = await screen.findByRole('menu', { name: 'Module von Vorlagen & Stammdaten' });
        await waitFor(() => expect(within(menue).getByRole('menuitem', { name: /Werkstoffe/ })).toHaveTextContent('ausgeblendet'));
        expect(within(menue).getByRole('menuitem', { name: /Artikel/ })).not.toHaveTextContent('ausgeblendet');
    });

    it('markiert nichts, solange noch geladen wird: Nichts blitzt auf und verschwindet', async () => {
        stubbe('laedt');
        zeige();
        stammdaten();
        expect(await screen.findByRole('menuitem', { name: /Werkstoffe/ })).not.toHaveTextContent('ausgeblendet');
    });

    it('folgt der eigenen Auswahl vor der Vorgabe der Firma', async () => {
        stubbe({ vorgabe: ['/kunden'], eigene: [] });
        zeige();
        stammdaten();
        expect(await screen.findByRole('menuitem', { name: /Werkstoffe/ })).not.toHaveTextContent('ausgeblendet');
        expect(screen.getByRole('menuitem', { name: /Kunden/ })).not.toHaveTextContent('ausgeblendet');
    });

    it('zählt am Knopf „Alle Funktionen", wie viele ausgeblendet sind', async () => {
        stubbe({ vorgabe: null, eigene: ['/werkstoffe', '/aufschlaege'] });
        zeige();
        expect(await screen.findByLabelText('2 ausgeblendet')).toBeInTheDocument();
    });

    it('im Dialog ist Ausgeblendetes ohne Haken und mit Haken wieder im Menü; gespeichert wird nur die eigene Auswahl', async () => {
        const fetchMock = stubbe({ vorgabe: null, eigene: ['/werkstoffe', '/aufschlaege'] });
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: /Alle Funktionen/ }));

        const haken = await screen.findByRole('checkbox', { name: 'Werkstoffe im Hauptmenü zeigen' });
        expect(haken).not.toBeChecked();
        expect(screen.getByRole('checkbox', { name: 'Kunden im Hauptmenü zeigen' })).toBeChecked();
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();

        await user.click(haken);
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe(fetchMock, '/api/menue/eigene', 'PUT')).toHaveLength(1));
        const [, init] = aufrufe(fetchMock, '/api/menue/eigene', 'PUT')[0];
        expect(JSON.parse(String(init?.body))).toEqual({ ausgeblendet: ['/aufschlaege'] });
        expect(aufrufe(fetchMock, '/api/settings/menue/vorgabe', 'PUT')).toHaveLength(0);
    });

    it('die Suche im Dialog findet auch Ausgeblendetes', async () => {
        stubbe({ vorgabe: null, eigene: ['/werkstoffe'] });
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: /Alle Funktionen/ }));
        await user.type(await screen.findByRole('searchbox', { name: 'Funktion suchen' }), 'werkst');
        expect(screen.getByRole('checkbox', { name: 'Werkstoffe im Hauptmenü zeigen' })).toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: 'Kunden im Hauptmenü zeigen' })).toBeNull();
        await user.clear(screen.getByRole('searchbox', { name: 'Funktion suchen' }));
        await user.type(screen.getByRole('searchbox', { name: 'Funktion suchen' }), 'gibtesnicht');
        expect(await screen.findByText('Keine Funktion gefunden.')).toBeInTheDocument();
    });

    it('„Wie die Vorgabe" setzt die eigene Auswahl zurück, geht aber nur, wenn es eine gibt', async () => {
        const fetchMock = stubbe({ vorgabe: ['/lager'], eigene: ['/werkstoffe'] });
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: /Alle Funktionen/ }));
        const knopf = await screen.findByRole('button', { name: /Wie die Vorgabe/ });
        await waitFor(() => expect(knopf).toBeEnabled());
        await user.click(knopf);
        await waitFor(() => expect(aufrufe(fetchMock, '/api/menue/eigene', 'DELETE')).toHaveLength(1));
    });

    it('ein normaler Benutzer sieht keinen Knopf für die Vorgabe der Firma', async () => {
        stubbe({ vorgabe: null, eigene: null }, false);
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: /Alle Funktionen/ }));
        await screen.findByRole('checkbox', { name: 'Kunden im Hauptmenü zeigen' });
        expect(screen.queryByRole('button', { name: /Als Vorgabe für alle speichern/ })).toBeNull();
    });

    it('der Admin speichert die Vorgabe für alle erst nach Rückfrage', async () => {
        const fetchMock = stubbe({ vorgabe: null, eigene: null }, true);
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: /Alle Funktionen/ }));
        await user.click(await screen.findByRole('checkbox', { name: 'Kunden im Hauptmenü zeigen' }));
        await user.click(screen.getByRole('button', { name: 'Als Vorgabe für alle speichern' }));
        expect(aufrufe(fetchMock, '/api/settings/menue/vorgabe', 'PUT')).toHaveLength(0);

        await user.click(await screen.findByRole('button', { name: 'Für alle speichern' }));
        await waitFor(() => expect(aufrufe(fetchMock, '/api/settings/menue/vorgabe', 'PUT')).toHaveLength(1));
        const [, init] = aufrufe(fetchMock, '/api/settings/menue/vorgabe', 'PUT')[0];
        expect((JSON.parse(String(init?.body)) as { ausgeblendet: string[] }).ausgeblendet).toContain('/kunden');
    });

    it('zeigt einen Fehler beim Speichern und lässt den Dialog offen', async () => {
        const fetchMock = stubbe({ vorgabe: null, eigene: [] });
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: /Alle Funktionen/ }));
        await user.click(await screen.findByRole('checkbox', { name: 'Kunden im Hauptmenü zeigen' }));
        fetchMock.mockImplementation(async (eingabe: RequestInfo | URL, init?: RequestInit) => {
            const url = new URL(String(eingabe), 'http://localhost');
            if (url.pathname === '/api/menue/eigene' && init?.method === 'PUT') return antwort({ message: 'Dein Menü konnte nicht gespeichert werden.' }, 500);
            return antwort({ vorgabe: null, eigene: [] });
        });
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Dein Menü konnte nicht gespeichert werden.')).toBeInTheDocument();
        expect(screen.getByRole('searchbox', { name: 'Funktion suchen' })).toBeInTheDocument();
    });

    it('den doppelten Knopf „Funktionen" oben rechts gibt es nicht mehr', async () => {
        stubbe({ vorgabe: null, eigene: ['/werkstoffe'] });
        zeige();
        await screen.findByRole('button', { name: /Alle Funktionen/ });
        expect(screen.queryByRole('button', { name: /^Ausgeblendete Funktionen/ })).toBeNull();
    });
});

describe('Menüleiste – Aufklappliste an den Hauptpunkten', { timeout: 30000 }, () => {
    beforeEach(() => { setzeFeaturesZurueck(); setzeTelefonBerechtigungZurueck(); setzeMenueZurueck(); });
    afterEach(() => vi.unstubAllGlobals());

    it('der Pfeil am Hauptpunkt listet alle Module der Kategorie, auch die ausgeblendeten', async () => {
        stubbe({ vorgabe: null, eigene: null });
        zeige();

        const user = userEvent.setup();
        await user.click(screen.getByRole('button', { name: 'Module von Vorlagen & Stammdaten' }));

        const menue = await screen.findByRole('menu', { name: 'Module von Vorlagen & Stammdaten' });
        expect(within(menue).getByRole('menuitem', { name: /Kunden/ })).toBeInTheDocument();
        const werkstoffe = within(menue).getByRole('menuitem', { name: /Werkstoffe/ });
        expect(werkstoffe).toHaveTextContent('ausgeblendet');
        expect(within(menue).getAllByRole('group').length).toBeGreaterThan(1);
    });

    it('ein Klick auf ein Modul schließt die Liste, Escape ebenso', async () => {
        stubbe({ vorgabe: null, eigene: null });
        zeige();
        const user = userEvent.setup();
        const pfeil = screen.getByRole('button', { name: 'Module von Vorlagen & Stammdaten' });

        await user.click(pfeil);
        expect(pfeil).toHaveAttribute('aria-expanded', 'true');
        fireEvent.keyDown(await screen.findByRole('menu', { name: 'Module von Vorlagen & Stammdaten' }), { key: 'Escape' });
        expect(screen.queryByRole('menu', { name: 'Module von Vorlagen & Stammdaten' })).toBeNull();

        await user.click(pfeil);
        await user.click(within(await screen.findByRole('menu', { name: 'Module von Vorlagen & Stammdaten' })).getByRole('menuitem', { name: /Kunden/ }));
        expect(screen.queryByRole('menu', { name: 'Module von Vorlagen & Stammdaten' })).toBeNull();
    });
});

describe('Menüleiste – Startbildschirm und Schnellzugriffsleiste (#326)', { timeout: 30000 }, () => {
    beforeEach(() => { setzeFeaturesZurueck(); setzeTelefonBerechtigungZurueck(); setzeMenueZurueck(); });
    afterEach(() => vi.unstubAllGlobals());

    it('der Startbildschirm ist von jeder Seite aus mit einem Klick erreichbar', async () => {
        stubbe({ vorgabe: null, eigene: null });
        zeige('/kunden');
        expect(await screen.findByRole('link', { name: 'Startbildschirm' })).toHaveAttribute('href', '/start');
    });

    it('ein Klick auf einen Hauptpunkt öffnet dieselbe Liste wie der Pfeil und ändert die Schnellzugriffsleiste nicht', async () => {
        const user = userEvent.setup();
        stubbe({ vorgabe: null, eigene: null });
        zeige();
        const leiste = await screen.findByTestId('schnellzugriff');
        const vorher = leiste.innerHTML;

        await user.click(screen.getByRole('button', { name: 'Vorlagen & Stammdaten' }));
        expect(await screen.findByRole('menu', { name: 'Module von Vorlagen & Stammdaten' })).toBeInTheDocument();
        expect(screen.getByTestId('schnellzugriff').innerHTML).toBe(vorher);

        await user.click(screen.getByRole('button', { name: 'Vorlagen & Stammdaten' }));
        expect(screen.queryByRole('menu')).toBeNull();
    });

    it('„Alle Funktionen" gibt es nur einmal: fest in der Leiste', async () => {
        stubbe({ vorgabe: null, eigene: null });
        zeige();
        await screen.findByTestId('schnellzugriff');
        expect(screen.getAllByRole('button', { name: /Alle Funktionen/ })).toHaveLength(1);
    });
});
