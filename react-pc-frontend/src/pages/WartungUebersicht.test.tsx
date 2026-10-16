import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import WartungUebersicht from './WartungUebersicht';

// Ob der angemeldete Benutzer Admin ist, steuert der Test über diesen Schalter
const anmeldung = vi.hoisted(() => ({ isAdmin: false }));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ isAdmin: anmeldung.isAdmin }) }));
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import type { WartungListeneintrag, WartungModul } from '../features/wartung/types';

const eintrag = (extra: Partial<WartungListeneintrag> = {}): WartungListeneintrag => ({
    id: 1, nummer: 'WA-2026-1001', titel: 'Jahreswartung', protokollTyp: 'Wartungsprotokoll', datum: '2026-05-10', naechsteWartung: '2027-05-10',
    status: 'IN_BEARBEITUNG', kundeId: 1, kundeName: 'Max Mustermann GmbH', objektId: 2, objektBezeichnung: 'Werk Nord', tuerId: 3, tuerBezeichnung: 'Tür 1.01',
    pruefpunkteGesamt: 37, pruefpunkteBewertet: 12, maengel: 2, ...extra,
});

const seite = (content: WartungListeneintrag[], extra: Record<string, unknown> = {}) => ({
    content, totalElements: content.length, totalPages: 1, number: 0, size: 20, ...extra,
});

const MODULE: WartungModul[] = [{
    id: 1, modulKey: 'm_bst', name: 'Brandschutztür', beschreibung: 'Feuerschutzabschluss', icon: 'DoorClosed', kategorie: 'BASIS', systemModul: true,
    standardIntervallMonate: 12, andockbarAnBasisModulIds: [], untertypen: [{ schluessel: 'einflg', label: '1-flügelig' }], anzahlPruefpunkte: 19,
}];

function antwort(status: number, body: unknown): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

function stubServer(liste: () => Response) {
    const fetcher = vi.fn(async (url: string) => {
        if (url.startsWith('/api/wartung/protokolle?')) return liste();
        if (url === '/api/wartung/module') return antwort(200, MODULE);
        if (url === '/api/mitarbeiter') return antwort(200, []);
        return antwort(404, {});
    });
    vi.stubGlobal('fetch', fetcher);
    return fetcher;
}

function zeige(pfad = '/wartung') {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={[pfad]}>
                    <Routes>
                        <Route path="/wartung" element={<WartungUebersicht />} />
                        <Route path="/wartung/:id" element={<p>Protokoll geöffnet</p>} />
                    </Routes>
                </MemoryRouter>
            </ConfirmProvider>
        </ToastProvider>,
    );
}

afterEach(() => { vi.unstubAllGlobals(); anmeldung.isAdmin = false; });

describe('WartungUebersicht', () => {
    it('zeigt die Wartungen mit Nummer, Status, Mängeln, Ort und Fortschritt', async () => {
        stubServer(() => antwort(200, seite([eintrag()])));
        zeige();
        const karte = await screen.findByTestId('wartung-1');
        expect(karte).toHaveTextContent('WA-2026-1001');
        expect(karte).toHaveTextContent('In Arbeit');
        expect(karte).toHaveTextContent('2 Mängel');
        expect(karte).toHaveTextContent('Max Mustermann GmbH · Werk Nord · Tür 1.01');
        expect(karte).toHaveTextContent('12/37');
        expect(karte).toHaveTextContent('Wartungsprotokoll vom 10.05.2026');
        expect(screen.getByRole('progressbar', { name: 'Fortschritt WA-2026-1001' })).toHaveAttribute('aria-valuenow', '32');
    });

    it('spricht bei genau einem Mangel im Singular und lässt ihn bei keinem Mangel weg', async () => {
        stubServer(() => antwort(200, seite([eintrag({ maengel: 1 }), eintrag({ id: 2, nummer: 'WA-2026-1002', maengel: 0 })])));
        zeige();
        expect(await screen.findByText('1 Mangel')).toBeVisible();
        expect(screen.queryByText('0 Mängel')).toBeNull();
    });

    it('führt über die Karte zum Protokoll', async () => {
        stubServer(() => antwort(200, seite([eintrag()])));
        zeige();
        await userEvent.click(await screen.findByRole('link', { name: 'Wartung WA-2026-1001 öffnen' }));
        expect(await screen.findByText('Protokoll geöffnet')).toBeVisible();
    });

    it('zeigt bei einer leeren Liste einen Hinweis und die Möglichkeit, die erste Wartung anzulegen', async () => {
        stubServer(() => antwort(200, seite([])));
        zeige();
        expect(await screen.findByText('Noch keine Wartungen angelegt.')).toBeVisible();
        expect(screen.getByRole('button', { name: /Erste Wartung anlegen/ })).toBeVisible();
    });

    it('unterscheidet "nichts gefunden" von "noch nichts angelegt"', async () => {
        stubServer(() => antwort(200, seite([])));
        zeige();
        await screen.findByText('Noch keine Wartungen angelegt.');
        await userEvent.type(screen.getByRole('textbox', { name: 'Suche' }), 'xyz');
        expect(await screen.findByText('Keine Wartungen zu diesen Filtern gefunden.')).toBeVisible();
        expect(screen.queryByRole('button', { name: /Erste Wartung anlegen/ })).toBeNull();
    });

    it('zeigt bei einem Fehler eine Meldung und lädt auf Wunsch neu', async () => {
        let fehler = true;
        const fetcher = stubServer(() => (fehler ? antwort(500, { message: 'Die Datenbank antwortet nicht.' }) : antwort(200, seite([eintrag()]))));
        zeige();
        expect(await screen.findByText('Die Wartungen konnten nicht geladen werden.')).toBeVisible();
        expect((await screen.findAllByText('Die Datenbank antwortet nicht.')).length).toBeGreaterThan(0);
        fehler = false;
        await userEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
        expect(await screen.findByTestId('wartung-1')).toBeVisible();
        expect(fetcher.mock.calls.filter(([u]) => String(u).startsWith('/api/wartung/protokolle?')).length).toBeGreaterThanOrEqual(2);
    });

    it('schickt den Suchtext erst nach einer kurzen Pause und nicht bei jedem Tastendruck', async () => {
        const fetcher = stubServer(() => antwort(200, seite([eintrag()])));
        zeige();
        await screen.findByTestId('wartung-1');
        const vorher = fetcher.mock.calls.length;
        await userEvent.type(screen.getByRole('textbox', { name: 'Suche' }), 'Nord');
        await waitFor(() => {
            const urls = fetcher.mock.calls.map(([u]) => String(u)).filter((u) => u.startsWith('/api/wartung/protokolle?'));
            expect(urls.at(-1)).toContain('suche=Nord');
        });
        const nachSuche = fetcher.mock.calls.slice(vorher).filter(([u]) => String(u).includes('suche=')).length;
        expect(nachSuche).toBe(1);
    });

    it('kann den Status filtern und fragt dann nur diesen Status an', async () => {
        const fetcher = stubServer(() => antwort(200, seite([eintrag()])));
        zeige();
        await screen.findByTestId('wartung-1');
        await userEvent.click(screen.getByRole('combobox', { name: 'Status' }));
        await userEvent.click(await screen.findByRole('option', { name: 'Abgeschlossen' }));
        await waitFor(() => {
            const urls = fetcher.mock.calls.map(([u]) => String(u));
            expect(urls.at(-1)).toContain('status=ABGESCHLOSSEN');
        });
    });

    it('blättert, wenn es mehr als eine Seite gibt', async () => {
        const fetcher = stubServer(() => antwort(200, seite([eintrag()], { totalElements: 45, totalPages: 3 })));
        zeige();
        expect(await screen.findByText(/45 Wartungen · Seite 1 von 3/)).toBeVisible();
        expect(screen.getByRole('button', { name: 'Zurück' })).toBeDisabled();
        await userEvent.click(screen.getByRole('button', { name: /Weiter/ }));
        await waitFor(() => expect(String(fetcher.mock.calls.at(-1)![0])).toContain('seite=1'));
    });

    it('wechselt zu Objekte und Türen und merkt sich das in der Adresse', async () => {
        stubServer(() => antwort(200, seite([eintrag()])));
        zeige();
        await screen.findByTestId('wartung-1');
        await userEvent.click(screen.getByRole('tab', { name: 'Objekte und Türen' }));
        expect(await screen.findByText(/Wählen Sie einen Kunden/)).toBeVisible();
        expect(screen.getByRole('tab', { name: 'Objekte und Türen' })).toHaveAttribute('aria-selected', 'true');
    });

    it('öffnet den Dialog "Neue Wartung" mit dem Hinweis, zuerst einen Kunden zu wählen', async () => {
        stubServer(() => antwort(200, seite([eintrag()])));
        zeige();
        await screen.findByTestId('wartung-1');
        await userEvent.click(screen.getByRole('button', { name: /^Neue Wartung$/ }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText('Neue Wartung')).toBeVisible();
        const anlegen = within(dialog).getByRole('button', { name: 'Wartung anlegen' });
        expect(anlegen).toBeDisabled();
        expect(anlegen).toHaveAttribute('title', 'Bitte zuerst einen Kunden wählen');
    });

    it('zeigt die Einstellungen nur Admins', async () => {
        stubServer(() => antwort(200, seite([eintrag()])));
        zeige();
        await screen.findByTestId('wartung-1');
        expect(screen.queryByRole('tab', { name: 'Einstellungen' })).not.toBeInTheDocument();
        expect(screen.getByRole('tab', { name: 'Sammelbericht' })).toBeVisible();
    });

    it('lässt einen Nicht-Admin über die Adresse nicht in die Einstellungen', async () => {
        const fetcher = stubServer(() => antwort(200, seite([eintrag()])));
        zeige('/wartung?tab=einstellungen');
        await screen.findByTestId('wartung-1');
        expect(screen.getByRole('tab', { name: 'Wartungen' })).toHaveAttribute('aria-selected', 'true');
        expect(fetcher.mock.calls.some(([url]) => String(url).includes('/api/wartung/admin/'))).toBe(false);
    });

    it('zeigt einem Admin die Einstellungen mit Modulen und gemeinsamen Datenfeldern', async () => {
        anmeldung.isAdmin = true;
        const fetcher = vi.fn(async (url: string) => {
            if (url.startsWith('/api/wartung/protokolle?')) return antwort(200, seite([eintrag()]));
            if (url === '/api/wartung/admin/module') return antwort(200, [{
                id: 1, modulKey: 'm_bst', name: 'Brandschutztür', beschreibung: null, icon: 'DoorClosed', kategorie: 'BASIS', systemModul: true, aktiv: true,
                standardIntervallMonate: 12, anzahlUntertypen: 2, anzahlPruefpunkte: 19, anzahlDatenfelder: 4, anzahlTueren: 3, anzahlProtokolle: 5,
            }]);
            if (url === '/api/wartung/admin/globale-felder') return antwort(200, [{ id: 1, label: 'Standort / Lage', feldtyp: 'TEXT', optionen: [] }]);
            return antwort(404, {});
        });
        vi.stubGlobal('fetch', fetcher);
        zeige();
        await screen.findByTestId('wartung-1');
        await userEvent.click(screen.getByRole('tab', { name: 'Einstellungen' }));

        const modul = await screen.findByTestId('modul-1');
        expect(modul).toHaveTextContent('Brandschutztür');
        expect(modul).toHaveTextContent('2 Bauarten · 19 Prüfpunkte · 4 Datenfelder');
        expect(modul).toHaveTextContent('an 3 Türen im Einsatz');
        expect(await screen.findByDisplayValue('Standort / Lage')).toBeVisible();
    });

    it('öffnet den Reiter Sammelbericht mit der Auswahl der Wartungen', async () => {
        stubServer(() => antwort(200, seite([eintrag(), eintrag({ id: 2, nummer: 'WA-2026-1002' })])));
        zeige('/wartung?tab=sammelbericht');
        expect(await screen.findByText('WA-2026-1002')).toBeVisible();
        expect(screen.getByRole('button', { name: /PDF öffnen/ })).toBeDisabled();
    });
});
