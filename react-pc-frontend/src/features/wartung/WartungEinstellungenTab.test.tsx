import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { WartungEinstellungenTab } from './WartungEinstellungenTab';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import type { GlobalesDatenfeld, ModulUebersicht } from './types';

const modul = (extra: Partial<ModulUebersicht> = {}): ModulUebersicht => ({
    id: 1, modulKey: 'm_bst', name: 'Brandschutztür', beschreibung: 'Feuerschutzabschluss', icon: 'DoorClosed', kategorie: 'BASIS', systemModul: false, aktiv: true,
    standardIntervallMonate: 12, anzahlUntertypen: 2, anzahlPruefpunkte: 19, anzahlDatenfelder: 4, anzahlTueren: 0, anzahlProtokolle: 0, ...extra,
});

function json(status: number, body: unknown = {}): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

interface Zustand { module: ModulUebersicht[]; felder: GlobalesDatenfeld[] }

function stub(zustand: Zustand, verhalten: Partial<Record<string, (init?: RequestInit) => Response>> = {}) {
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const schluessel = `${init?.method ?? 'GET'} ${url}`;
        const eigenes = verhalten[schluessel];
        if (eigenes) return eigenes(init);
        if (schluessel === 'GET /api/wartung/admin/module') return json(200, zustand.module);
        if (schluessel === 'GET /api/wartung/admin/globale-felder') return json(200, zustand.felder);
        return json(404, { message: 'unerwartet: ' + schluessel });
    });
    vi.stubGlobal('fetch', fetcher);
    return fetcher;
}

function zeige() {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={['/wartung?tab=einstellungen']}>
                    <Routes>
                        <Route path="/wartung" element={<WartungEinstellungenTab />} />
                        <Route path="/wartung/module/neu" element={<p>Editor neu</p>} />
                        <Route path="/wartung/module/:id" element={<p>Editor Modul</p>} />
                    </Routes>
                </MemoryRouter>
            </ConfirmProvider>
        </ToastProvider>,
    );
}

afterEach(() => { vi.unstubAllGlobals(); });

describe('WartungEinstellungenTab: Module', () => {
    it('zeigt Art, Zähler und Einsatz je Modul und kennzeichnet mitgelieferte und deaktivierte', async () => {
        stub({
            module: [
                modul({ anzahlTueren: 3, systemModul: true }),
                modul({ id: 2, name: 'Feststellanlage', kategorie: 'ZUSATZ', aktiv: false, anzahlUntertypen: 0, anzahlPruefpunkte: 1, anzahlDatenfelder: 0, beschreibung: null }),
            ],
            felder: [],
        });
        zeige();
        const erstes = await screen.findByTestId('modul-1');
        expect(erstes).toHaveTextContent('Basis-Element');
        expect(erstes).toHaveTextContent('Mitgeliefert');
        expect(erstes).toHaveTextContent('2 Bauarten · 19 Prüfpunkte · 4 Datenfelder');
        expect(erstes).toHaveTextContent('an 3 Türen im Einsatz');
        const zweites = screen.getByTestId('modul-2');
        expect(zweites).toHaveTextContent('Zusatzmodul');
        expect(zweites).toHaveTextContent('Deaktiviert');
        expect(zweites).toHaveTextContent('0 Bauarten · 1 Prüfpunkt · 0 Datenfelder');
        expect(zweites).toHaveTextContent('an keiner Tür im Einsatz');
    });

    it('sperrt das Löschen für mitgelieferte und benutzte Module mit einer Begründung', async () => {
        stub({ module: [modul({ systemModul: true }), modul({ id: 2, name: 'Rolltor', anzahlProtokolle: 2 }), modul({ id: 3, name: 'Frei' })], felder: [] });
        zeige();
        await screen.findByTestId('modul-1');
        const mitgeliefert = within(screen.getByTestId('modul-1')).getByRole('button', { name: 'Brandschutztür löschen' });
        expect(mitgeliefert).toBeDisabled();
        expect(mitgeliefert).toHaveAttribute('title', 'Mitgelieferte Module lassen sich nur deaktivieren.');
        const benutzt = within(screen.getByTestId('modul-2')).getByRole('button', { name: 'Rolltor löschen' });
        expect(benutzt).toBeDisabled();
        expect(benutzt).toHaveAttribute('title', expect.stringContaining('benutzt'));
        expect(within(screen.getByTestId('modul-3')).getByRole('button', { name: 'Frei löschen' })).toBeEnabled();
    });

    it('löscht nach Rückfrage und nimmt das Modul aus der Liste', async () => {
        const fetcher = stub({ module: [modul(), modul({ id: 2, name: 'Rolltor' })], felder: [] }, { 'DELETE /api/wartung/admin/module/2': () => json(204) });
        zeige();
        await screen.findByTestId('modul-2');
        await userEvent.click(within(screen.getByTestId('modul-2')).getByRole('button', { name: 'Rolltor löschen' }));
        expect(await screen.findByText('Modul löschen?')).toBeVisible();
        await userEvent.click(screen.getByRole('button', { name: 'Löschen' }));

        await waitFor(() => expect(screen.queryByTestId('modul-2')).not.toBeInTheDocument());
        expect(fetcher).toHaveBeenCalledWith('/api/wartung/admin/module/2', { method: 'DELETE' });
        expect(screen.getByTestId('modul-1')).toBeVisible();
    });

    it('löscht nichts, wenn die Rückfrage abgebrochen wird', async () => {
        const fetcher = stub({ module: [modul()], felder: [] });
        zeige();
        await screen.findByTestId('modul-1');
        await userEvent.click(within(screen.getByTestId('modul-1')).getByRole('button', { name: 'Brandschutztür löschen' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }));

        expect(screen.getByTestId('modul-1')).toBeVisible();
        expect(fetcher.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'DELETE')).toBe(false);
    });

    it('zeigt die Meldung des Servers, wenn das Löschen abgelehnt wird, und behält das Modul', async () => {
        stub({ module: [modul()], felder: [] }, { 'DELETE /api/wartung/admin/module/1': () => json(409, { message: 'Dieses Modul wird noch benutzt (1 Türen, 0 Protokolle).' }) });
        zeige();
        await screen.findByTestId('modul-1');
        await userEvent.click(within(screen.getByTestId('modul-1')).getByRole('button', { name: 'Brandschutztür löschen' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Löschen' }));

        expect(await screen.findByText('Dieses Modul wird noch benutzt (1 Türen, 0 Protokolle).')).toBeVisible();
        expect(screen.getByTestId('modul-1')).toBeVisible();
    });

    it('deaktiviert und aktiviert ein Modul', async () => {
        const fetcher = stub({ module: [modul()], felder: [] }, { 'PUT /api/wartung/admin/module/1/aktiv': () => json(200, {}) });
        zeige();
        await screen.findByTestId('modul-1');
        await userEvent.click(screen.getByRole('button', { name: 'Brandschutztür deaktivieren' }));

        expect(await within(screen.getByTestId('modul-1')).findByText('Deaktiviert')).toBeVisible();
        expect(JSON.parse((fetcher.mock.calls.find(([u, i]) => u.endsWith('/1/aktiv') && (i as RequestInit)?.method === 'PUT')![1] as RequestInit).body as string)).toEqual({ aktiv: false });

        await userEvent.click(screen.getByRole('button', { name: 'Brandschutztür aktivieren' }));
        await waitFor(() => expect(within(screen.getByTestId('modul-1')).queryByText('Deaktiviert')).not.toBeInTheDocument());
    });

    it('speichert eine neue Reihenfolge und stellt die alte wieder her, wenn das scheitert', async () => {
        const alle = [modul(), modul({ id: 2, name: 'Rolltor' })];
        let antwort: () => Response = () => json(200, [alle[1], alle[0]]);
        const fetcher = stub({ module: alle, felder: [] }, { 'PUT /api/wartung/admin/module/reihenfolge': () => antwort() });
        zeige();
        await screen.findByTestId('modul-1');
        await userEvent.click(screen.getByRole('button', { name: 'Modul Brandschutztür nach unten' }));

        await waitFor(() => expect(screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent).slice(0, 2)).toEqual(['Rolltor', 'Brandschutztür']));
        const aufruf = fetcher.mock.calls.find(([u]) => u === '/api/wartung/admin/module/reihenfolge')!;
        expect(JSON.parse((aufruf[1] as RequestInit).body as string)).toEqual({ ids: [2, 1] });

        antwort = () => json(400, { message: 'Die Reihenfolge passt nicht zu den vorhandenen Modulen. Bitte neu laden.' });
        await userEvent.click(screen.getByRole('button', { name: 'Modul Rolltor nach unten' }));
        expect(await screen.findByText(/Die Reihenfolge passt nicht/)).toBeVisible();
        await waitFor(() => expect(screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent).slice(0, 2)).toEqual(['Rolltor', 'Brandschutztür']));
    });

    it('sperrt die Pfeile am Rand der Liste', async () => {
        stub({ module: [modul(), modul({ id: 2, name: 'Rolltor' })], felder: [] });
        zeige();
        await screen.findByTestId('modul-1');
        expect(screen.getByRole('button', { name: 'Modul Brandschutztür nach oben' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Modul Rolltor nach unten' })).toBeDisabled();
    });

    it('führt zu Editor, Kopie und neuem Modul', async () => {
        stub({ module: [modul()], felder: [] });
        zeige();
        await screen.findByTestId('modul-1');
        expect(screen.getByRole('link', { name: 'Brandschutztür bearbeiten' })).toHaveAttribute('href', '/wartung/module/1');
        await userEvent.click(screen.getByRole('button', { name: /Neues Modul/ }));
        expect(await screen.findByText('Editor neu')).toBeVisible();
    });

    it('bietet bei einem Ladefehler einen neuen Versuch an', async () => {
        const fetcher = stub({ module: [], felder: [] }, { 'GET /api/wartung/admin/module': () => json(500, { message: 'kaputt' }) });
        zeige();
        expect(await screen.findByRole('button', { name: 'Noch einmal versuchen' })).toBeVisible();
        fetcher.mockClear();
        stub({ module: [modul()], felder: [] });
        await userEvent.click(screen.getByRole('button', { name: 'Noch einmal versuchen' }));
        expect(await screen.findByTestId('modul-1')).toBeVisible();
    });
});

describe('WartungEinstellungenTab: gemeinsame Datenfelder', () => {
    const felder: GlobalesDatenfeld[] = [
        { id: 1, label: 'Standort / Lage', feldtyp: 'TEXT', optionen: [] },
        { id: 2, label: 'Zustand', feldtyp: 'AUSWAHL', optionen: ['gut', 'schlecht'] },
    ];

    it('zeigt die Felder, lässt erst nach einer Änderung speichern und schickt die ganze Liste', async () => {
        const fetcher = stub({ module: [], felder }, { 'PUT /api/wartung/admin/globale-felder': (init) => json(200, JSON.parse(init!.body as string)) });
        zeige();
        expect(await screen.findByDisplayValue('Standort / Lage')).toBeVisible();
        expect((screen.getByLabelText('Möglichkeiten von Datenfeld 2') as HTMLTextAreaElement).value).toBe('gut\nschlecht');
        const speichern = screen.getByRole('button', { name: 'Speichern' });
        expect(speichern).toBeDisabled();

        await userEvent.clear(screen.getByDisplayValue('Standort / Lage'));
        await userEvent.type(screen.getAllByLabelText(/^Bezeichnung von Datenfeld/)[0], 'Einbauort');
        expect(speichern).toBeEnabled();
        await userEvent.click(speichern);

        expect(await screen.findByText('Gemeinsame Datenfelder gespeichert.')).toBeVisible();
        const aufruf = fetcher.mock.calls.find(([u, i]) => u === '/api/wartung/admin/globale-felder' && (i as RequestInit)?.method === 'PUT')!;
        const gesendet = JSON.parse((aufruf[1] as RequestInit).body as string);
        expect(gesendet.map((f: GlobalesDatenfeld) => [f.id, f.label])).toEqual([[1, 'Einbauort'], [2, 'Zustand']]);
        expect(gesendet[1].optionen).toEqual(['gut', 'schlecht']);
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    });

    it('zeigt, solange etwas ungespeichert ist, einen Hinweis, und warnt beim Schließen des Tabs', async () => {
        stub({ module: [], felder }, { 'PUT /api/wartung/admin/globale-felder': (init) => json(200, JSON.parse(init!.body as string)) });
        zeige();
        await screen.findByDisplayValue('Standort / Lage');
        expect(screen.queryByText('Noch nicht gespeichert')).not.toBeInTheDocument();

        await userEvent.type(screen.getAllByLabelText(/^Bezeichnung von Datenfeld/)[0], '!');
        expect(screen.getByText('Noch nicht gespeichert')).toBeVisible();
        const ereignis = new Event('beforeunload', { cancelable: true });
        window.dispatchEvent(ereignis);
        expect(ereignis.defaultPrevented).toBe(true);

        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(screen.queryByText('Noch nicht gespeichert')).not.toBeInTheDocument());
        const danach = new Event('beforeunload', { cancelable: true });
        window.dispatchEvent(danach);
        expect(danach.defaultPrevented).toBe(false);
    });

    it('fügt ein Feld hinzu, prüft den Namen vor dem Senden und sendet nichts bei einem Fehler', async () => {
        const fetcher = stub({ module: [], felder: [] });
        zeige();
        await screen.findByText('Noch keine gemeinsamen Datenfelder.');
        await userEvent.click(screen.getByRole('button', { name: /Datenfeld hinzufügen/ }));
        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText('Bitte jedes Datenfeld benennen.')).toBeVisible();
        expect(fetcher.mock.calls.some(([, i]) => (i as RequestInit | undefined)?.method === 'PUT')).toBe(false);
    });

    it('verschiebt und entfernt Felder', async () => {
        stub({ module: [], felder });
        zeige();
        await screen.findByDisplayValue('Standort / Lage');
        await userEvent.click(screen.getByRole('button', { name: 'Datenfeld 1 nach unten' }));
        expect(screen.getAllByLabelText(/^Bezeichnung von Datenfeld/).map((e) => (e as HTMLInputElement).value)).toEqual(['Zustand', 'Standort / Lage']);

        await userEvent.click(screen.getByRole('button', { name: 'Datenfeld 1 entfernen' }));
        expect(screen.getAllByLabelText(/^Bezeichnung von Datenfeld/).map((e) => (e as HTMLInputElement).value)).toEqual(['Standort / Lage']);
    });
});
