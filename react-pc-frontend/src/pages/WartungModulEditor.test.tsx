import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import WartungModulEditor from './WartungModulEditor';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import type { ModulDetail, ModulUebersicht } from '../features/wartung/types';

const detail = (extra: Partial<ModulDetail> = {}): ModulDetail => ({
    id: 1, version: 3, modulKey: 'm_bst', name: 'Brandschutztür', beschreibung: null, icon: 'DoorClosed', kategorie: 'BASIS', systemModul: false, aktiv: true,
    standardIntervallMonate: 12, andockbarAn: [],
    untertypen: [{ id: 1, schluessel: '1fluegelig', label: '1-flügelig' }, { id: 2, schluessel: '2fluegelig', label: '2-flügelig' }],
    abschnitte: [
        {
            id: 10, bereich: 'PRUEFPUNKTE', label: 'Prüfpunkte Brandschutztür', datenfelder: [],
            pruefpunkte: [{ id: 100, label: 'Zulassungsschild lesbar', nurBeiUntertyp: null }, { id: 101, label: 'Zweiter Flügel schließt mit', nurBeiUntertyp: '2fluegelig' }],
        },
        { id: 11, bereich: 'DATENFELDER', label: 'Datenaufnahme', pruefpunkte: [], datenfelder: [{ id: 200, label: 'Hersteller', feldtyp: 'TEXT', optionen: [] }] },
    ],
    anzahlTueren: 0, anzahlProtokolle: 0, ...extra,
});

const uebersicht = (id: number, name: string, extra: Partial<ModulUebersicht> = {}): ModulUebersicht => ({
    id, modulKey: `m_${id}`, name, beschreibung: null, icon: null, kategorie: 'BASIS', systemModul: false, aktiv: true, standardIntervallMonate: 12,
    anzahlUntertypen: 0, anzahlPruefpunkte: 0, anzahlDatenfelder: 0, anzahlTueren: 0, anzahlProtokolle: 0, ...extra,
});

function json(status: number, body: unknown = {}): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

interface Stub { fetcher: ReturnType<typeof vi.fn>; gesendet: (methode: string, endung: string) => ModulDetail | null }

function stub(geladen: ModulDetail = detail(), antworten: Partial<Record<string, (init?: RequestInit) => Response>> = {}): Stub {
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const schluessel = `${init?.method ?? 'GET'} ${url}`;
        if (antworten[schluessel]) return antworten[schluessel]!(init);
        if (schluessel === 'GET /api/wartung/admin/module') return json(200, [uebersicht(1, 'Brandschutztür'), uebersicht(2, 'Rauchschutztür'), uebersicht(3, 'Feststellanlage', { kategorie: 'ZUSATZ' })]);
        if (schluessel === `GET /api/wartung/admin/module/${geladen.id}`) return json(200, geladen);
        if (schluessel === 'GET /api/wartung/admin/module/1') return json(200, detail());
        if (schluessel.startsWith('PUT /api/wartung/admin/module/')) return json(200, { ...JSON.parse(init!.body as string), version: 4 });
        if (schluessel === 'POST /api/wartung/admin/module') return json(201, { ...JSON.parse(init!.body as string), id: 5, version: 0, modulKey: 'm_neu' });
        return json(404, { message: 'unerwartet: ' + schluessel });
    });
    vi.stubGlobal('fetch', fetcher);
    return {
        fetcher,
        gesendet: (methode, endung) => {
            const aufruf = fetcher.mock.calls.find(([u, i]) => String(u).endsWith(endung) && (i as RequestInit | undefined)?.method === methode);
            return aufruf ? JSON.parse((aufruf[1] as RequestInit).body as string) : null;
        },
    };
}

function zeige(pfad = '/wartung/module/1') {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={[pfad]}>
                    <Routes>
                        <Route path="/wartung" element={<p>Einstellungen offen</p>} />
                        <Route path="/wartung/module/:id" element={<WartungModulEditor />} />
                    </Routes>
                </MemoryRouter>
            </ConfirmProvider>
        </ToastProvider>,
    );
}

const speichernKnoepfe = () => screen.getAllByRole('button', { name: 'Speichern' });

async function waehle(feld: HTMLElement, option: string) {
    await userEvent.click(feld);
    await userEvent.click(await screen.findByRole('option', { name: option }));
}

afterEach(() => { vi.unstubAllGlobals(); });

describe('WartungModulEditor: bestehendes Modul', () => {
    it('zeigt Name, Bauarten, Prüfpunkte und Datenfelder und lässt ungeändert nicht speichern', async () => {
        stub();
        zeige();
        expect(await screen.findByDisplayValue('Brandschutztür')).toBeVisible();
        expect(screen.getByDisplayValue('1-flügelig')).toBeVisible();
        expect(screen.getByDisplayValue('2-flügelig')).toBeVisible();
        expect(screen.getByDisplayValue('Zulassungsschild lesbar')).toBeVisible();
        expect(screen.getByDisplayValue('Prüfpunkte Brandschutztür')).toBeVisible();
        expect(screen.getByDisplayValue('Hersteller')).toBeVisible();
        expect(screen.getByRole('heading', { name: 'Modul bearbeiten' })).toBeVisible();
        expect(screen.getByRole('note')).toHaveTextContent('Änderungen gelten nur für neue Wartungen');
        speichernKnoepfe().forEach((k) => expect(k).toBeDisabled());
    });

    it('speichert Änderungen mit Version und Nummern und legt neue Einträge ohne Nummer an', async () => {
        const s = stub();
        zeige();
        const name = await screen.findByLabelText('Name');
        await userEvent.clear(name);
        await userEvent.type(name, 'Brandschutztür T30');
        await userEvent.click(screen.getByRole('button', { name: 'Prüfpunkt hinzufügen' }));
        await userEvent.type(screen.getByLabelText('Prüfpunkt 3 in Prüfpunkt-Abschnitt 1'), 'Dichtungen prüfen');
        await userEvent.click(speichernKnoepfe()[0]);

        expect(await screen.findByText('„Brandschutztür T30“ gespeichert. Es gilt für neue Wartungen.')).toBeVisible();
        const body = s.gesendet('PUT', '/api/wartung/admin/module/1')!;
        expect(body).toMatchObject({ id: 1, version: 3, name: 'Brandschutztür T30', kategorie: 'BASIS', aktiv: true, standardIntervallMonate: 12 });
        expect(body.abschnitte[0].pruefpunkte.map((p) => [p.id, p.label, p.nurBeiUntertyp])).toEqual([
            [100, 'Zulassungsschild lesbar', null], [101, 'Zweiter Flügel schließt mit', '2fluegelig'], [null, 'Dichtungen prüfen', null],
        ]);
        expect(body.untertypen.map((u) => [u.id, u.schluessel])).toEqual([[1, '1fluegelig'], [2, '2fluegelig']]);
        await waitFor(() => speichernKnoepfe().forEach((k) => expect(k).toBeDisabled()));
    });

    it('verwirft eine neu angelegte leere Zeile beim Speichern, statt sie zu schicken', async () => {
        const s = stub();
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Prüfpunkt hinzufügen' }));
        await userEvent.type(screen.getByLabelText('Name'), ' neu');
        await userEvent.click(speichernKnoepfe()[0]);

        await waitFor(() => expect(s.gesendet('PUT', '/api/wartung/admin/module/1')).not.toBeNull());
        expect(s.gesendet('PUT', '/api/wartung/admin/module/1')!.abschnitte[0].pruefpunkte).toHaveLength(2);
    });

    it('prüft vor dem Senden: ohne Namen wird nichts gespeichert', async () => {
        const s = stub();
        zeige();
        await userEvent.clear(await screen.findByLabelText('Name'));
        await userEvent.click(speichernKnoepfe()[0]);

        expect((await screen.findAllByText('Bitte einen Namen eintragen.')).length).toBeGreaterThan(0);
        expect(s.gesendet('PUT', '/api/wartung/admin/module/1')).toBeNull();
    });

    it('verlangt bei einer Auswahl Möglichkeiten und schickt die Zeilen als Liste', async () => {
        const s = stub();
        zeige();
        await screen.findByDisplayValue('Hersteller');
        await waehle(screen.getByRole('combobox', { name: 'Art von Datenfeld 1 in Datenfeld-Abschnitt 1' }), 'Auswahl aus Liste');
        await userEvent.click(speichernKnoepfe()[0]);
        expect((await screen.findAllByText('Beim Datenfeld „Hersteller“ fehlen die Auswahlmöglichkeiten.')).length).toBeGreaterThan(0);
        expect(s.gesendet('PUT', '/api/wartung/admin/module/1')).toBeNull();

        await userEvent.type(screen.getByLabelText('Möglichkeiten von Datenfeld 1 in Datenfeld-Abschnitt 1'), 'Hörmann{Enter}Teckentrup{Enter}{Enter}');
        await userEvent.click(speichernKnoepfe()[0]);
        await waitFor(() => expect(s.gesendet('PUT', '/api/wartung/admin/module/1')).not.toBeNull());
        expect(s.gesendet('PUT', '/api/wartung/admin/module/1')!.abschnitte[1].datenfelder[0]).toMatchObject({ id: 200, feldtyp: 'AUSWAHL', optionen: ['Hörmann', 'Teckentrup'] });
    });

    it('zeigt die Meldung des Servers bei einem Konflikt und behält die Eingaben', async () => {
        stub(detail(), { 'PUT /api/wartung/admin/module/1': () => json(409, { message: 'Jemand anders hat dieses Modul gerade geändert. Bitte neu laden und die Änderung noch einmal machen.' }) });
        zeige();
        await userEvent.type(await screen.findByLabelText('Name'), ' neu');
        await userEvent.click(speichernKnoepfe()[0]);

        expect(await screen.findByText(/Jemand anders hat dieses Modul gerade geändert/)).toBeVisible();
        expect(screen.getByDisplayValue('Brandschutztür neu')).toBeVisible();
        expect(speichernKnoepfe()[0]).toBeEnabled();
    });

    it('verschiebt Prüfpunkte, und die Reihenfolge geht mit in den Speichern-Aufruf', async () => {
        const s = stub();
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Prüfpunkt 1 in Prüfpunkt-Abschnitt 1 nach unten' }));
        await userEvent.click(speichernKnoepfe()[0]);

        await waitFor(() => expect(s.gesendet('PUT', '/api/wartung/admin/module/1')).not.toBeNull());
        expect(s.gesendet('PUT', '/api/wartung/admin/module/1')!.abschnitte[0].pruefpunkte.map((p) => p.id)).toEqual([101, 100]);
    });

    it('lässt Prüfpunkte einer entfernten Bauart für alle gelten und sagt es', async () => {
        const s = stub();
        zeige();
        await screen.findByDisplayValue('2-flügelig');
        await userEvent.click(screen.getByRole('button', { name: 'Bauart 2 entfernen' }));

        expect(await screen.findByText('1 Prüfpunkt gilt jetzt für alle Bauarten.')).toBeVisible();
        expect(screen.queryByDisplayValue('2-flügelig')).not.toBeInTheDocument();
        await userEvent.click(speichernKnoepfe()[0]);
        await waitFor(() => expect(s.gesendet('PUT', '/api/wartung/admin/module/1')).not.toBeNull());
        const body = s.gesendet('PUT', '/api/wartung/admin/module/1')!;
        expect(body.untertypen.map((u) => u.id)).toEqual([1]);
        expect(body.abschnitte[0].pruefpunkte[1]).toMatchObject({ id: 101, nurBeiUntertyp: null });
    });

    it('entfernt einen Abschnitt erst nach Rückfrage', async () => {
        const s = stub();
        zeige();
        await screen.findByDisplayValue('Prüfpunkte Brandschutztür');
        await userEvent.click(screen.getByRole('button', { name: 'Prüfpunkt-Abschnitt 1 entfernen' }));
        expect(await screen.findByText('Abschnitt entfernen?')).toBeVisible();
        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(screen.getByDisplayValue('Prüfpunkte Brandschutztür')).toBeVisible();

        await userEvent.click(screen.getByRole('button', { name: 'Prüfpunkt-Abschnitt 1 entfernen' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Entfernen' }));
        await waitFor(() => expect(screen.queryByDisplayValue('Prüfpunkte Brandschutztür')).not.toBeInTheDocument());
        await userEvent.click(speichernKnoepfe()[0]);
        await waitFor(() => expect(s.gesendet('PUT', '/api/wartung/admin/module/1')).not.toBeNull());
        expect(s.gesendet('PUT', '/api/wartung/admin/module/1')!.abschnitte.map((a) => a.id)).toEqual([11]);
    });

    it('sperrt die Art, solange Türen das Modul verwenden', async () => {
        stub(detail({ anzahlTueren: 4 }));
        zeige();
        await screen.findByDisplayValue('Brandschutztür');
        expect(screen.getByText(/Die Art lässt sich nicht ändern, solange Türen das Modul verwenden/)).toBeVisible();
        expect(screen.getByRole('note')).toHaveTextContent('an 4 Türen im Einsatz');
    });

    it('zeigt bei einem Zusatzmodul die Basis-Elemente zum Andocken, ohne das Modul selbst', async () => {
        const s = stub(detail({ id: 3, name: 'Feststellanlage', kategorie: 'ZUSATZ', andockbarAn: [1] }));
        zeige('/wartung/module/3');
        await screen.findByDisplayValue('Feststellanlage');
        const gruppe = screen.getByRole('group', { name: 'Passt zu diesen Basis-Elementen' });
        expect(within(gruppe).getByLabelText('Brandschutztür')).toBeChecked();
        expect(within(gruppe).getByLabelText('Rauchschutztür')).not.toBeChecked();
        expect(within(gruppe).queryByLabelText('Feststellanlage')).not.toBeInTheDocument();

        await userEvent.click(within(gruppe).getByLabelText('Rauchschutztür'));
        await userEvent.click(speichernKnoepfe()[0]);
        await waitFor(() => expect(s.gesendet('PUT', '/api/wartung/admin/module/3')).not.toBeNull());
        expect(s.gesendet('PUT', '/api/wartung/admin/module/3')!.andockbarAn).toEqual([1, 2]);
    });

    it('fragt beim Verlassen nach, wenn etwas ungespeichert ist, und geht ohne Änderung gleich zurück', async () => {
        stub();
        zeige();
        await userEvent.type(await screen.findByLabelText('Name'), '!');
        await userEvent.click(screen.getByRole('button', { name: /Zu den Einstellungen/ }));
        expect(await screen.findByText('Änderungen verwerfen?')).toBeVisible();
        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(screen.getByLabelText('Name')).toBeVisible();

        await userEvent.click(screen.getByRole('button', { name: /Zu den Einstellungen/ }));
        await userEvent.click(await screen.findByRole('button', { name: 'Verwerfen' }));
        expect(await screen.findByText('Einstellungen offen')).toBeVisible();
    });

    it('geht ohne Rückfrage zurück, wenn nichts geändert wurde', async () => {
        stub();
        zeige();
        await screen.findByLabelText('Name');
        await userEvent.click(screen.getByRole('button', { name: /Zu den Einstellungen/ }));
        expect(await screen.findByText('Einstellungen offen')).toBeVisible();
    });

    it('meldet einen Ladefehler und bietet einen neuen Versuch an', async () => {
        stub(detail(), { 'GET /api/wartung/admin/module/1': () => json(404, { message: 'Das Modul wurde nicht gefunden.' }) });
        zeige();
        expect(await screen.findByText('Das Modul konnte nicht geladen werden.')).toBeVisible();
        expect(screen.getByRole('button', { name: 'Noch einmal versuchen' })).toBeVisible();
    });
});

describe('WartungModulEditor: neues Modul', () => {
    it('legt ein Modul mit Bauart, Abschnitt und Prüfpunkt an, ohne Nummern', async () => {
        const s = stub(detail({ id: 5, name: 'Rolltor' }));
        zeige('/wartung/module/neu');
        expect(await screen.findByRole('heading', { name: 'Neues Modul' })).toBeVisible();
        await userEvent.type(screen.getByLabelText('Name'), 'Rolltor');
        await userEvent.click(screen.getByRole('button', { name: 'Bauart hinzufügen' }));
        await userEvent.type(screen.getByLabelText('Bauart 1'), 'Einflügelig');
        await userEvent.click(screen.getAllByRole('button', { name: 'Abschnitt hinzufügen' })[0]);
        await userEvent.type(screen.getByLabelText('Überschrift von Prüfpunkt-Abschnitt 1'), 'Prüfpunkte Rolltor');
        await userEvent.type(screen.getByLabelText('Prüfpunkt 1 in Prüfpunkt-Abschnitt 1'), 'Antrieb läuft ruhig');
        await userEvent.click(speichernKnoepfe()[0]);

        expect(await screen.findByText('„Rolltor“ gespeichert. Es gilt für neue Wartungen.')).toBeVisible();
        const body = s.gesendet('POST', '/api/wartung/admin/module')!;
        expect(body).toMatchObject({ id: null, version: null, name: 'Rolltor', kategorie: 'BASIS', aktiv: true });
        expect(body.untertypen).toEqual([expect.objectContaining({ id: null, schluessel: null, label: 'Einflügelig' })]);
        expect(body.abschnitte).toHaveLength(1);
        expect(body.abschnitte[0]).toMatchObject({ id: null, bereich: 'PRUEFPUNKTE', label: 'Prüfpunkte Rolltor' });
        expect(body.abschnitte[0].pruefpunkte).toEqual([expect.objectContaining({ id: null, label: 'Antrieb läuft ruhig' })]);
    });

    it('wirft einen leeren neuen Abschnitt beim Speichern weg', async () => {
        const s = stub(detail({ id: 5, name: 'Rolltor' }));
        zeige('/wartung/module/neu');
        await userEvent.type(await screen.findByLabelText('Name'), 'Rolltor');
        await userEvent.click(screen.getAllByRole('button', { name: 'Abschnitt hinzufügen' })[0]);
        await userEvent.click(speichernKnoepfe()[0]);

        await waitFor(() => expect(s.gesendet('POST', '/api/wartung/admin/module')).not.toBeNull());
        expect(s.gesendet('POST', '/api/wartung/admin/module')!.abschnitte).toEqual([]);
    });

    it('übernimmt beim Kopieren den Inhalt, aber keine Nummern', async () => {
        const s = stub(detail({ id: 5, name: 'Brandschutztür (Kopie)' }));
        zeige('/wartung/module/neu?kopie=1');
        expect(await screen.findByDisplayValue('Brandschutztür (Kopie)')).toBeVisible();
        expect(screen.getByDisplayValue('Zulassungsschild lesbar')).toBeVisible();
        await userEvent.click(speichernKnoepfe()[0]);

        await waitFor(() => expect(s.gesendet('POST', '/api/wartung/admin/module')).not.toBeNull());
        const body = s.gesendet('POST', '/api/wartung/admin/module')!;
        expect(body).toMatchObject({ id: null, version: null, modulKey: null, systemModul: false, name: 'Brandschutztür (Kopie)' });
        expect(body.untertypen.map((u) => [u.id, u.schluessel])).toEqual([[null, '1fluegelig'], [null, '2fluegelig']]);
        expect(body.abschnitte.every((a) => a.id === null)).toBe(true);
        expect(body.abschnitte[0].pruefpunkte.map((p) => [p.id, p.nurBeiUntertyp])).toEqual([[null, null], [null, '2fluegelig']]);
    });
});
