import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import WartungProtokoll from './WartungProtokoll';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import type { ProtokollDatenfeld, WartungProtokoll as Protokoll } from '../features/wartung/types';

const feld = (id: number, label: string, extra: Partial<ProtokollDatenfeld> = {}): ProtokollDatenfeld => ({
    id, abschnittId: null, modulKey: null, label, feldtyp: 'TEXT', optionen: [], wert: null, sortierung: id, ...extra,
});

function protokoll(extra: Partial<Protokoll> = {}): Protokoll {
    return {
        id: 1, version: 0, nummer: 'WA-2026-1001',
        kunde: { id: 1, name: 'Max Mustermann GmbH' }, objekt: { id: 2, name: 'Werk Nord' }, tuer: { id: 3, name: 'Tür 1.01' },
        projekt: null, techniker: { id: 5, name: 'Max Mustermann' },
        titel: 'Jahreswartung', protokollTyp: 'Wartungsprotokoll', datum: '2026-05-10', intervallMonate: 12, naechsteWartung: '2027-05-10',
        status: 'IN_BEARBEITUNG', bearbeitbar: true, moeglicheStatus: ['ABGESCHLOSSEN', 'ARCHIVIERT', 'OFFEN'],
        bemerkungen: null, ansprechpartnerName: null,
        unterschriftTechniker: { bild: null, name: null }, unterschriftKunde: { bild: null, name: null },
        abgeschlossenAm: null, vorlageProtokollId: null, bericht: null, angelegtAm: '2026-05-10T08:00:00', geaendertAm: '2026-05-10T08:00:00',
        module: [
            { modulId: 1, modulKey: 'm_bst', name: 'Brandschutztür', untertypSchluessel: 'einflg', untertypLabel: '1-flügelig' },
            { modulId: 3, modulKey: 'm_fsa', name: 'Feststellanlage', untertypSchluessel: null, untertypLabel: null },
        ],
        abschnitte: [{ id: 10, bereich: 'PRUEFPUNKTE', label: 'Prüfpunkte Brandschutztür', sortierung: 0 }],
        pruefpunkte: [
            { id: 100, abschnittId: 10, modulKey: 'm_bst', label: 'Zulassungsschild lesbar', ergebnis: 'IO', bemerkung: null, vorherigesErgebnis: null, sortierung: 0 },
        ],
        datenfelder: [
            feld(1, 'Standort / Lage', { wert: 'Treppenhaus' }),
            feld(2, 'Hersteller'),
            feld(3, 'Feuerwiderstand / Rauchschutzklasse', { modulKey: 'm_bst' }),
            feld(4, 'Gebrauchs- u. Wartungsanweisung liegt vor', { modulKey: 'm_bst', feldtyp: 'JA_NEIN' }),
            feld(5, 'Zustand', { modulKey: 'm_bst', feldtyp: 'AUSWAHL', optionen: ['gut', 'mittel', 'schlecht'], wert: 'mittel' }),
        ],
        statistik: { gesamt: 1, bewertet: 1, inOrdnung: 1, nichtInOrdnung: 0, nichtAnwendbar: 0 },
        ...extra,
    };
}

function antwort(status: number, body: unknown): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

interface Aufruf { methode: string; url: string; body: unknown }

/** Ein kleiner Server: liefert das Protokoll, die Datenfeld-Wege antwortet der Test selbst. */
function stubServer(start: Protokoll, eigene?: (url: string, methode: string, body: unknown) => Response | undefined) {
    const aufrufe: Aufruf[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        const body = init?.body ? JSON.parse(init.body as string) : undefined;
        aufrufe.push({ methode, url, body });
        const e = eigene?.(url, methode, body);
        if (e) return e;
        if (url === '/api/wartung/kunden/1/projekte') return antwort(200, []);
        if (url === '/api/wartung/unterschrift/meine') return antwort(204, null);
        if (url === '/api/mitarbeiter') return antwort(200, [{ id: 5, vorname: 'Max', nachname: 'Mustermann', aktiv: true }]);
        if (url === '/api/wartung/protokolle/1' && methode === 'GET') return antwort(200, start);
        return antwort(404, { message: 'nicht gefunden: ' + url });
    }));
    return aufrufe;
}

function zeige() {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={['/wartung/1']}>
                    <Routes>
                        <Route path="/wartung/:id" element={<WartungProtokoll />} />
                    </Routes>
                </MemoryRouter>
            </ConfirmProvider>
        </ToastProvider>,
    );
}

beforeEach(() => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    Element.prototype.scrollIntoView = vi.fn();
});
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('Datenfelder im Feldeinsatz bearbeiten', () => {
    it('zeigt "Allgemein" und jedes Modul als Gruppe mit eigenem Knopf; auch ein Modul ohne Felder bleibt erreichbar', async () => {
        stubServer(protokoll());
        zeige();

        expect(await screen.findByTestId('datengruppe-allgemein')).toHaveTextContent('Standort / Lage');
        expect(screen.getByTestId('datengruppe-m_bst')).toHaveTextContent('Feuerwiderstand / Rauchschutzklasse');
        const leer = screen.getByTestId('datengruppe-m_fsa');
        expect(leer).toHaveTextContent('Feststellanlage');
        expect(leer).toHaveTextContent('Hier gibt es noch keine Datenfelder');
        expect(within(leer).getByRole('button', { name: 'Datenfeld hinzufügen: Feststellanlage' })).toBeVisible();
        expect(screen.getByRole('button', { name: 'Datenfeld hinzufügen: Allgemein' })).toBeVisible();
    });

    it('ergänzt ein Textfeld in der Gruppe, deren Knopf man gedrückt hat, und zeigt es sofort', async () => {
        const start = protokoll();
        const aufrufe = stubServer(start, (url, methode) => {
            if (url === '/api/wartung/protokolle/1/datenfelder' && methode === 'POST') {
                return antwort(201, protokoll({ datenfelder: [...start.datenfelder, feld(6, 'Rauchmelder Hersteller/Typ', { modulKey: 'm_fsa', sortierung: 6 })] }));
            }
            return undefined;
        });
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld hinzufügen: Feststellanlage' }));

        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByRole('combobox', { name: /In Gruppe/ })).toHaveTextContent('Feststellanlage');
        await userEvent.type(within(dialog).getByLabelText('Bezeichnung'), '  Rauchmelder Hersteller/Typ ');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }));

        expect(await within(screen.getByTestId('datengruppe-m_fsa')).findByText('Rauchmelder Hersteller/Typ')).toBeVisible();
        expect(screen.queryByRole('dialog')).toBeNull();
        const post = aufrufe.find((a) => a.methode === 'POST')!;
        expect(post.body).toEqual({ modulKey: 'm_fsa', label: 'Rauchmelder Hersteller/Typ', feldtyp: 'TEXT', optionen: null });
    });

    it('ergänzt ein Ja/Nein-Feld unter "Allgemein" (Gruppe ohne Modul = modulKey null)', async () => {
        const start = protokoll();
        const aufrufe = stubServer(start, (url, methode) => (url === '/api/wartung/protokolle/1/datenfelder' && methode === 'POST'
            ? antwort(201, protokoll({ datenfelder: [...start.datenfelder, feld(6, 'Handauslösetaster vorhanden', { feldtyp: 'JA_NEIN', sortierung: 6 })] })) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld hinzufügen: Allgemein' }));
        const dialog = await screen.findByRole('dialog');
        await userEvent.type(within(dialog).getByLabelText('Bezeichnung'), 'Handauslösetaster vorhanden');
        await userEvent.click(within(dialog).getByRole('combobox', { name: /Art des Feldes/ }));
        await userEvent.click(await screen.findByRole('option', { name: 'Ja oder Nein' }));
        await userEvent.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
        expect(aufrufe.find((a) => a.methode === 'POST')!.body).toEqual({ modulKey: null, label: 'Handauslösetaster vorhanden', feldtyp: 'JA_NEIN', optionen: null });
    });

    it('verlangt eine Bezeichnung und bei einer Auswahl mindestens zwei Möglichkeiten und schickt vorher nichts ab', async () => {
        const aufrufe = stubServer(protokoll());
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld hinzufügen: Allgemein' }));
        const dialog = await screen.findByRole('dialog');

        await userEvent.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }));
        expect(await within(dialog).findByText(/Bitte das Datenfeld benennen/)).toBeVisible();

        await userEvent.type(within(dialog).getByLabelText('Bezeichnung'), 'Zustand Dichtung');
        await userEvent.click(within(dialog).getByRole('combobox', { name: /Art des Feldes/ }));
        await userEvent.click(await screen.findByRole('option', { name: /Auswahl aus eigenen Möglichkeiten/ }));
        await userEvent.type(within(dialog).getByLabelText(/Möglichkeiten/), 'gut{enter}gut');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }));
        expect(await within(dialog).findByText(/mindestens zwei Möglichkeiten/)).toBeVisible();

        expect(aufrufe.some((a) => a.methode === 'POST')).toBe(false);
    });

    it('schickt die Möglichkeiten einer Auswahl getrimmt und ohne Leere und Doppelte', async () => {
        const start = protokoll();
        const aufrufe = stubServer(start, (url, methode) => (url === '/api/wartung/protokolle/1/datenfelder' && methode === 'POST' ? antwort(201, start) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld hinzufügen: Allgemein' }));
        const dialog = await screen.findByRole('dialog');
        await userEvent.type(within(dialog).getByLabelText('Bezeichnung'), 'Zustand Dichtung');
        await userEvent.click(within(dialog).getByRole('combobox', { name: /Art des Feldes/ }));
        await userEvent.click(await screen.findByRole('option', { name: /Auswahl aus eigenen Möglichkeiten/ }));
        await userEvent.type(within(dialog).getByLabelText(/Möglichkeiten/), ' gut {enter}{enter}mittel{enter}gut{enter}schlecht ');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }));

        await waitFor(() => expect(aufrufe.some((a) => a.methode === 'POST')).toBe(true));
        expect(aufrufe.find((a) => a.methode === 'POST')!.body).toMatchObject({ feldtyp: 'AUSWAHL', optionen: ['gut', 'mittel', 'schlecht'] });
    });

    it('zeigt die Meldung des Servers, wenn das Ergänzen scheitert, und lässt den Dialog offen', async () => {
        stubServer(protokoll(), (url, methode) => (url === '/api/wartung/protokolle/1/datenfelder' && methode === 'POST'
            ? antwort(400, { message: 'Ein Feldeinsatz kann höchstens 200 Datenfelder haben.' }) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld hinzufügen: Allgemein' }));
        const dialog = await screen.findByRole('dialog');
        await userEvent.type(within(dialog).getByLabelText('Bezeichnung'), 'Eins zu viel');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }));

        expect(await screen.findByText('Ein Feldeinsatz kann höchstens 200 Datenfelder haben.')).toBeVisible();
        expect(screen.getByRole('dialog')).toBeVisible();
    });

    it('benennt ein Datenfeld um, der Eintrag bleibt stehen', async () => {
        const start = protokoll();
        const aufrufe = stubServer(start, (url, methode) => (url === '/api/wartung/protokolle/1/datenfelder/1/definition' && methode === 'PUT'
            ? antwort(200, protokoll({ datenfelder: start.datenfelder.map((f) => (f.id === 1 ? { ...f, label: 'Standort / Raum' } : f)) })) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld umbenennen: Standort / Lage' }));

        const eingabe = screen.getByRole('textbox', { name: 'Datenfeld umbenennen: Standort / Lage' });
        await userEvent.clear(eingabe);
        await userEvent.type(eingabe, 'Standort / Raum');
        await userEvent.click(screen.getByRole('button', { name: 'Änderung übernehmen' }));

        expect(await screen.findByText('Standort / Raum')).toBeVisible();
        expect(screen.getByTestId('datenfeld-1')).toBeVisible();
        const put = aufrufe.find((a) => a.methode === 'PUT' && a.url.endsWith('/definition'))!;
        expect(put.body).toEqual({ label: 'Standort / Raum', optionen: null });
    });

    it('bei einer Auswahl lassen sich die Möglichkeiten mitändern; weniger als zwei werden abgelehnt', async () => {
        const start = protokoll();
        const aufrufe = stubServer(start, (url, methode) => (url === '/api/wartung/protokolle/1/datenfelder/5/definition' && methode === 'PUT' ? antwort(200, start) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld umbenennen: Zustand' }));

        const optionen = screen.getByLabelText(/Möglichkeiten \(eine pro Zeile\)/);
        expect(optionen).toHaveValue('gut\nmittel\nschlecht');
        await userEvent.clear(optionen);
        await userEvent.type(optionen, 'gut');
        await userEvent.click(screen.getByRole('button', { name: 'Änderung übernehmen' }));
        expect(await screen.findByText(/mindestens zwei Möglichkeiten/)).toBeVisible();
        expect(aufrufe.some((a) => a.methode === 'PUT')).toBe(false);

        await userEvent.clear(optionen);
        await userEvent.type(optionen, 'gut{enter}defekt');
        await userEvent.click(screen.getByRole('button', { name: 'Änderung übernehmen' }));
        await waitFor(() => expect(aufrufe.some((a) => a.methode === 'PUT')).toBe(true));
        expect(aufrufe.find((a) => a.methode === 'PUT')!.body).toEqual({ label: 'Zustand', optionen: ['gut', 'defekt'] });
    });

    it('schickt nichts ab, wenn beim Umbenennen nichts geändert wurde', async () => {
        const aufrufe = stubServer(protokoll());
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld umbenennen: Hersteller' }));
        await userEvent.click(screen.getByRole('button', { name: 'Änderung übernehmen' }));

        expect(screen.queryByRole('button', { name: 'Änderung übernehmen' })).toBeNull();
        expect(aufrufe.some((a) => a.methode === 'PUT')).toBe(false);
    });

    it('zeigt beim Umbenennen die Meldung des Servers, wenn es scheitert, und das Feld bleibt zum Bearbeiten offen', async () => {
        stubServer(protokoll(), (url, methode) => (url.endsWith('/definition') && methode === 'PUT'
            ? antwort(409, { message: 'Dieses Protokoll ist „abgeschlossen“ und lässt sich nicht mehr ändern.' }) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld umbenennen: Hersteller' }));
        const eingabe = screen.getByRole('textbox', { name: 'Datenfeld umbenennen: Hersteller' });
        await userEvent.type(eingabe, ' neu');
        await userEvent.click(screen.getByRole('button', { name: 'Änderung übernehmen' }));

        expect(await screen.findByText(/lässt sich nicht mehr ändern/)).toBeVisible();
        expect(screen.getByRole('textbox', { name: 'Datenfeld umbenennen: Hersteller' })).toHaveValue('Hersteller neu');
    });

    it('löscht ein Feld ohne Eintrag sofort, ein Feld mit Eintrag erst nach Rückfrage', async () => {
        const start = protokoll();
        const aufrufe = stubServer(start, (url, methode) => {
            if (methode === 'DELETE' && url === '/api/wartung/protokolle/1/datenfelder/2') return antwort(200, protokoll({ datenfelder: start.datenfelder.filter((f) => f.id !== 2) }));
            if (methode === 'DELETE' && url === '/api/wartung/protokolle/1/datenfelder/1') return antwort(200, protokoll({ datenfelder: start.datenfelder.filter((f) => f.id !== 1 && f.id !== 2) }));
            return undefined;
        });
        zeige();

        // "Hersteller" ist leer: kein Fenster
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld löschen: Hersteller' }));
        await waitFor(() => expect(screen.queryByTestId('datenfeld-2')).toBeNull());
        expect(screen.queryByText(/Datenfeld löschen\?/)).toBeNull();

        // "Standort / Lage" hat einen Eintrag: erst fragen
        await userEvent.click(screen.getByRole('button', { name: 'Datenfeld löschen: Standort / Lage' }));
        expect(await screen.findByText(/samt Eintrag aus diesem Feldeinsatz entfernt/)).toBeVisible();
        expect(aufrufe.filter((a) => a.methode === 'DELETE')).toHaveLength(1);
        await userEvent.click(screen.getByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(screen.queryByTestId('datenfeld-1')).toBeNull());
        expect(aufrufe.filter((a) => a.methode === 'DELETE')).toHaveLength(2);
    });

    it('ein Doppelklick auf den Papierkorb löscht nur einmal und zeigt keinen Fehler "nicht gefunden"', async () => {
        const start = protokoll();
        let fertig: (r: Response) => void = () => undefined;
        const aufrufe = stubServer(start, (url, methode) => {
            if (methode === 'DELETE' && url === '/api/wartung/protokolle/1/datenfelder/2') {
                return new Promise<Response>((ok) => { fertig = ok; }) as unknown as Response;
            }
            return undefined;
        });
        zeige();
        const knopf = await screen.findByRole('button', { name: 'Datenfeld löschen: Hersteller' });
        await userEvent.dblClick(knopf);

        expect(aufrufe.filter((a) => a.methode === 'DELETE')).toHaveLength(1);
        fertig(antwort(200, protokoll({ datenfelder: start.datenfelder.filter((f) => f.id !== 2) })));
        await waitFor(() => expect(screen.queryByTestId('datenfeld-2')).toBeNull());
        expect(screen.queryByText(/nicht gefunden/)).toBeNull();
    });

    it('löscht nichts, wenn die Rückfrage abgebrochen wird', async () => {
        const aufrufe = stubServer(protokoll());
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Datenfeld löschen: Standort / Lage' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }));

        expect(screen.getByTestId('datenfeld-1')).toBeVisible();
        expect(aufrufe.some((a) => a.methode === 'DELETE')).toBe(false);
    });

    it('bei einem abgeschlossenen Protokoll gibt es weder Stift, Papierkorb noch "Feld hinzufügen", und auch keine leeren Gruppen', async () => {
        stubServer(protokoll({
            status: 'ABGESCHLOSSEN', bearbeitbar: false, abgeschlossenAm: '2026-05-10T10:00:00',
            statistik: { gesamt: 1, bewertet: 1, inOrdnung: 1, nichtInOrdnung: 0, nichtAnwendbar: 0 },
        }));
        zeige();

        await screen.findByTestId('datenfeld-1');
        expect(screen.queryByRole('button', { name: /Datenfeld umbenennen/ })).toBeNull();
        expect(screen.queryByRole('button', { name: /Datenfeld löschen/ })).toBeNull();
        expect(screen.queryByRole('button', { name: /Datenfeld hinzufügen/ })).toBeNull();
        expect(screen.queryByTestId('datengruppe-m_fsa')).toBeNull();
    });
});
