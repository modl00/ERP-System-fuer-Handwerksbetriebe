import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import WartungProtokoll from './WartungProtokoll';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import type { WartungProtokoll as Protokoll } from '../features/wartung/types';

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
        module: [{ modulId: 1, modulKey: 'm_bst', name: 'Brandschutztür', untertypSchluessel: 'einflg', untertypLabel: '1-flügelig' }],
        abschnitte: [{ id: 10, bereich: 'PRUEFPUNKTE', label: 'Prüfpunkte Brandschutztür', sortierung: 0 }],
        pruefpunkte: [
            { id: 100, abschnittId: 10, modulKey: 'm_bst', label: 'Zulassungsschild lesbar', ergebnis: 'IO', bemerkung: null, vorherigesErgebnis: null, sortierung: 0 },
            { id: 101, abschnittId: 10, modulKey: 'm_bst', label: 'Bänder auf festen Sitz prüfen', ergebnis: null, bemerkung: null, vorherigesErgebnis: 'NIO', sortierung: 1 },
        ],
        datenfelder: [
            { id: 200, abschnittId: null, modulKey: null, label: 'Standort / Lage', feldtyp: 'TEXT', optionen: [], wert: null, sortierung: 0 },
        ],
        statistik: { gesamt: 2, bewertet: 1, inOrdnung: 1, nichtInOrdnung: 0, nichtAnwendbar: 0 },
        ...extra,
    };
}

function antwort(status: number, body: unknown): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

/** Ein kleiner Server: liefert das Protokoll und nimmt Änderungen an. */
function stubServer(start: Protokoll, handler?: (url: string, init?: RequestInit) => Response | undefined) {
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const eigene = handler?.(url, init);
        if (eigene) return eigene;
        if (url === '/api/wartung/kunden/1/projekte') return antwort(200, [{ id: 7, name: '2026-001 – Neubau Halle' }, { id: 8, name: '2026-002 – Umbau Lager' }]);
        if (url === '/api/wartung/unterschrift/meine') return antwort(204, null);
        if (url === '/api/mitarbeiter') return antwort(200, [{ id: 5, vorname: 'Max', nachname: 'Mustermann', aktiv: true }]);
        if (url === '/api/wartung/protokolle/1' && (!init || !init.method || init.method === 'GET')) return antwort(200, start);
        return antwort(404, { message: 'nicht gefunden' });
    });
    vi.stubGlobal('fetch', fetcher);
    return fetcher;
}

function zeige() {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={['/wartung/1']}>
                    <Routes>
                        <Route path="/wartung/:id" element={<WartungProtokoll />} />
                        <Route path="/wartung" element={<p>Übersicht</p>} />
                    </Routes>
                </MemoryRouter>
            </ConfirmProvider>
        </ToastProvider>,
    );
}

beforeEach(() => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    // jsdom kennt kein Scrollen
    Element.prototype.scrollIntoView = vi.fn();
});
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('WartungProtokoll', () => {
    it('zeigt Nummer, Ort, Fortschritt und die Prüfpunkte unter ihrem Abschnitt', async () => {
        stubServer(protokoll());
        zeige();
        expect(await screen.findByRole('heading', { name: 'WA-2026-1001' })).toBeVisible();
        expect(screen.getByText('Max Mustermann GmbH · Werk Nord · Tür 1.01')).toBeVisible();
        expect(screen.getByText(/1 von 2 bewertet/)).toBeVisible();
        expect(screen.getByRole('progressbar', { name: 'Fortschritt der Prüfung' })).toHaveAttribute('aria-valuenow', '50');
        expect(screen.getByRole('heading', { name: 'Prüfpunkte Brandschutztür' })).toBeVisible();
        expect(screen.getByText('Zulassungsschild lesbar')).toBeVisible();
        expect(screen.getAllByText('Brandschutztür (1-flügelig)')[0]).toBeVisible();
    });

    it('speichert eine Bewertung und zeigt sie sofort, auch bevor der Server antwortet', async () => {
        let antworten: (r: Response) => void = () => undefined;
        const fetcher = stubServer(protokoll(), (url, init) => {
            if (url.endsWith('/pruefpunkte/101') && init?.method === 'PUT') {
                return new Promise<Response>((ok) => { antworten = ok; }) as unknown as Response;
            }
            return undefined;
        });
        zeige();
        const zeile = await screen.findByTestId('pruefpunkt-101');
        await userEvent.click(within(zeile).getByRole('button', { name: /^Nicht in Ordnung/ }));

        // Der Server hat noch nicht geantwortet, die Schaltfläche ist trotzdem schon gewählt.
        expect(within(screen.getByTestId('pruefpunkt-101')).getByRole('button', { name: /^Nicht in Ordnung/ })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByText(/2 von 2 bewertet/)).toBeVisible();

        const put = fetcher.mock.calls.find(([, init]) => init?.method === 'PUT')!;
        expect(put[0]).toBe('/api/wartung/protokolle/1/pruefpunkte/101');
        expect(JSON.parse(put[1]!.body as string)).toEqual({ ergebnis: 'NIO', bemerkung: null });

        antworten(antwort(200, {
            status: 'IN_BEARBEITUNG',
            statistik: { gesamt: 2, bewertet: 2, inOrdnung: 1, nichtInOrdnung: 1, nichtAnwendbar: 0 },
            pruefpunkt: { id: 101, abschnittId: 10, modulKey: 'm_bst', label: 'Bänder auf festen Sitz prüfen', ergebnis: 'NIO', bemerkung: null, vorherigesErgebnis: 'NIO', sortierung: 1 },
            datenfeld: null,
        }));
        expect(await screen.findByText(/1 Mangel/)).toBeVisible();
    });

    it('nimmt die Bewertung zurück und zeigt die Meldung des Servers, wenn das Speichern scheitert', async () => {
        stubServer(protokoll(), (url, init) => (url.endsWith('/pruefpunkte/101') && init?.method === 'PUT'
            ? antwort(409, { message: 'Dieses Protokoll ist „abgeschlossen“ und lässt sich nicht mehr ändern.' }) : undefined));
        zeige();
        const zeile = await screen.findByTestId('pruefpunkt-101');
        await userEvent.click(within(zeile).getByRole('button', { name: /^In Ordnung/ }));

        expect(await screen.findByText('Dieses Protokoll ist „abgeschlossen“ und lässt sich nicht mehr ändern.')).toBeVisible();
        await waitFor(() => expect(within(screen.getByTestId('pruefpunkt-101')).getByRole('button', { name: /^In Ordnung/ })).toHaveAttribute('aria-pressed', 'false'));
        expect(screen.getByText(/1 von 2 bewertet/)).toBeVisible();
    });

    it('lässt ein abgeschlossenes Protokoll nicht bearbeiten und bietet Wieder öffnen an', async () => {
        stubServer(protokoll({
            status: 'ABGESCHLOSSEN', bearbeitbar: false, abgeschlossenAm: '2026-05-10T10:00:00',
            statistik: { gesamt: 2, bewertet: 2, inOrdnung: 2, nichtInOrdnung: 0, nichtAnwendbar: 0 },
        }));
        zeige();
        const zeile = await screen.findByTestId('pruefpunkt-100');
        expect(within(zeile).getByRole('button', { name: /^In Ordnung/ })).toBeDisabled();
        expect(screen.getByRole('button', { name: /Wieder öffnen/ })).toBeVisible();
        expect(screen.getByRole('button', { name: /Archivieren/ })).toBeVisible();
        expect(screen.queryByRole('button', { name: /Zum Abschluss/ })).toBeNull();
        expect(screen.getByText(/lässt sich nicht mehr ändern/)).toBeVisible();
    });

    it('sperrt das Abschließen und nennt den Grund, solange Prüfpunkte offen sind', async () => {
        stubServer(protokoll());
        zeige();
        const knopf = await screen.findByRole('button', { name: 'Feldeinsatz abschließen' });
        expect(knopf).toBeDisabled();
        expect(knopf).toHaveAttribute('title', 'Es ist noch ein Prüfpunkt nicht bewertet.');
        expect(screen.getByText('Der Mitarbeiter muss noch unterschreiben.')).toBeVisible();
    });

    it('sperrt das Abschließen ohne Unterschrift des Technikers, auch wenn alles bewertet ist', async () => {
        stubServer(protokoll({ statistik: { gesamt: 2, bewertet: 2, inOrdnung: 2, nichtInOrdnung: 0, nichtAnwendbar: 0 } }));
        zeige();
        const knopf = await screen.findByRole('button', { name: 'Feldeinsatz abschließen' });
        expect(knopf).toBeDisabled();
        expect(knopf).toHaveAttribute('title', 'Der Techniker hat noch nicht unterschrieben.');
    });

    it('listet die Mängel mit ihrer Beschreibung im Abschluss', async () => {
        stubServer(protokoll({
            pruefpunkte: [
                { id: 100, abschnittId: 10, modulKey: 'm_bst', label: 'Dichtung prüfen', ergebnis: 'NIO', bemerkung: 'Dichtung gerissen', vorherigesErgebnis: null, sortierung: 0 },
            ],
            statistik: { gesamt: 1, bewertet: 1, inOrdnung: 0, nichtInOrdnung: 1, nichtAnwendbar: 0 },
        }));
        zeige();
        const maengel = (await screen.findByRole('heading', { name: 'Festgestellte Mängel' })).parentElement!;
        // Derselbe Text steht auch im Hinweisfeld des Prüfpunkts, deshalb nur im Mängelbereich suchen
        expect(within(maengel).getByText('Dichtung gerissen')).toBeVisible();
    });

    it('fragt beim Abschließen nach und schickt erst nach der Bestätigung den neuen Status', async () => {
        const fertig = protokoll({
            statistik: { gesamt: 2, bewertet: 2, inOrdnung: 2, nichtInOrdnung: 0, nichtAnwendbar: 0 },
            unterschriftTechniker: { bild: 'data:image/png;base64,iVBORw0KGgo=', name: 'Max Mustermann' },
        });
        const fetcher = stubServer(fertig, (url, init) => (url.endsWith('/status') && init?.method === 'PUT'
            ? antwort(200, { ...fertig, status: 'ABGESCHLOSSEN', bearbeitbar: false, abgeschlossenAm: '2026-05-10T12:00:00' }) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Feldeinsatz abschließen' }));

        expect(await screen.findByText(/lässt sich das Protokoll nicht mehr ändern/)).toBeVisible();
        expect(fetcher.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
        await userEvent.click(screen.getByRole('button', { name: 'Abschließen' }));

        await waitFor(() => {
            const put = fetcher.mock.calls.find(([url, init]) => String(url).endsWith('/status') && init?.method === 'PUT');
            expect(put).toBeTruthy();
            expect(JSON.parse(put![1]!.body as string)).toEqual({ status: 'ABGESCHLOSSEN' });
        });
        expect(await screen.findByText('Status: Abgeschlossen.')).toBeVisible();
    });

    it('verschickt nichts, wenn die Rückfrage zum Abschließen abgelehnt wird', async () => {
        const fertig = protokoll({
            statistik: { gesamt: 2, bewertet: 2, inOrdnung: 2, nichtInOrdnung: 0, nichtAnwendbar: 0 },
            unterschriftTechniker: { bild: 'data:image/png;base64,iVBORw0KGgo=', name: 'Max Mustermann' },
        });
        const fetcher = stubServer(fertig);
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Feldeinsatz abschließen' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }));
        expect(fetcher.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
    });

    it('öffnet das PDF des Protokolls in einem neuen Tab', async () => {
        stubServer(protokoll());
        const oeffne = vi.spyOn(window, 'open').mockReturnValue(null);
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Protokoll als PDF öffnen' }));
        expect(oeffne).toHaveBeenCalledWith('/api/wartung/protokolle/1/pdf', '_blank', 'noopener');
    });

    it('zeigt nur offene Punkte, wenn der Filter an ist', async () => {
        stubServer(protokoll());
        zeige();
        await screen.findByText('Zulassungsschild lesbar');
        await userEvent.click(screen.getByRole('button', { name: 'Nur offene Punkte' }));
        expect(screen.queryByText('Zulassungsschild lesbar')).toBeNull();
        expect(screen.getByText('Bänder auf festen Sitz prüfen')).toBeVisible();
    });

    it('speichert ein Datenfeld beim Verlassen und nur, wenn es geändert wurde', async () => {
        const fetcher = stubServer(protokoll(), (url, init) => (url.endsWith('/datenfelder/200') && init?.method === 'PUT'
            ? antwort(200, { status: 'IN_BEARBEITUNG', statistik: protokoll().statistik, pruefpunkt: null,
                datenfeld: { id: 200, abschnittId: null, modulKey: null, label: 'Standort / Lage', feldtyp: 'TEXT', optionen: [], wert: 'Halle 3', sortierung: 0 } })
            : undefined));
        zeige();
        const feld = await screen.findByRole('textbox', { name: 'Standort / Lage' });
        await userEvent.click(feld);
        await userEvent.tab();
        expect(fetcher.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);

        await userEvent.type(feld, 'Halle 3');
        await userEvent.tab();
        await waitFor(() => {
            const put = fetcher.mock.calls.find(([url, init]) => String(url).endsWith('/datenfelder/200') && init?.method === 'PUT');
            expect(JSON.parse(put![1]!.body as string)).toEqual({ wert: 'Halle 3' });
        });
    });

    it('zeigt bei einem Ladefehler die Meldung des Servers und bietet einen neuen Versuch an', async () => {
        stubServer(protokoll(), (url) => (url === '/api/wartung/protokolle/1' ? antwort(404, { message: 'Das Protokoll wurde nicht gefunden.' }) : undefined));
        zeige();
        expect((await screen.findAllByText('Das Protokoll wurde nicht gefunden.')).length).toBeGreaterThan(0);
        expect(screen.getByRole('button', { name: 'Erneut versuchen' })).toBeVisible();
        expect(screen.getByRole('link', { name: 'Zur Übersicht' })).toBeVisible();
    });

    // ── Alles auf einer Seite ───────────────────────────────────────────────

    it('zeigt Angaben, Prüfpunkte und Abschluss auf einer Seite, ohne Reiter, Datenfelder vor den Prüfpunkten', async () => {
        stubServer(protokoll());
        zeige();
        await screen.findByRole('heading', { name: 'WA-2026-1001' });

        expect(screen.queryByRole('tab')).toBeNull();
        const angaben = screen.getByRole('heading', { name: 'Angaben' });
        const standort = screen.getByRole('textbox', { name: 'Standort / Lage' });
        const pruefpunkte = screen.getByRole('heading', { name: 'Prüfpunkte' });
        const abschluss = screen.getByRole('heading', { name: 'Unterschrift und Abschluss' });
        const vor = (a: Element, b: Element) => Boolean(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);
        expect(vor(angaben, standort)).toBe(true);
        expect(vor(standort, pruefpunkte)).toBe(true);
        expect(vor(pruefpunkte, abschluss)).toBe(true);
        expect(screen.getByText('Zulassungsschild lesbar')).toBeVisible();
        expect(screen.getByRole('button', { name: 'Feldeinsatz abschließen' })).toBeVisible();
    });

    it('springt mit dem Knopf "Zum Abschluss" und der Sprungleiste zum Abschnitt', async () => {
        stubServer(protokoll());
        zeige();
        await screen.findByRole('heading', { name: 'WA-2026-1001' });

        await userEvent.click(screen.getByRole('button', { name: /Zum Abschluss/ }));
        expect(Element.prototype.scrollIntoView).toHaveBeenCalled();
        await userEvent.click(within(screen.getByRole('navigation', { name: 'Springe zu' })).getByRole('button', { name: 'Prüfpunkte' }));
        expect(vi.mocked(Element.prototype.scrollIntoView)).toHaveBeenCalledTimes(2);
    });

    it('führt von der Prüfung zurück zur Wartung mit ihrer Historie', async () => {
        stubServer(protokoll());
        zeige();
        expect(await screen.findByRole('link', { name: /Zur Wartung und Historie/ })).toHaveAttribute('href', '/wartung/anlagen/3');
    });

    it('weist darauf hin, dass der Feldeinsatz aus einer früheren Prüfung entstand und diese unverändert bleibt', async () => {
        stubServer(protokoll({ vorlageProtokollId: 9 }));
        zeige();
        expect(await screen.findByRole('note')).toHaveTextContent('aus einer früheren Prüfung gestartet');
    });

    // ── Hinweis zu jedem Ergebnis ───────────────────────────────────────────

    it('speichert einen Hinweis bei i.O. und zeigt ihn nach dem Neuladen', async () => {
        const fetcher = stubServer(protokoll(), (url, init) => (url.endsWith('/pruefpunkte/100') && init?.method === 'PUT'
            ? antwort(200, { status: 'IN_BEARBEITUNG', statistik: protokoll().statistik, datenfeld: null,
                pruefpunkt: { id: 100, abschnittId: 10, modulKey: 'm_bst', label: 'Zulassungsschild lesbar', ergebnis: 'IO', bemerkung: 'Schild leicht verblasst', vorherigesErgebnis: null, sortierung: 0 } })
            : undefined));
        zeige();
        const zeile = await screen.findByTestId('pruefpunkt-100');
        await userEvent.click(within(zeile).getByRole('button', { name: 'Hinweis schreiben' }));
        await userEvent.type(within(zeile).getByRole('textbox'), 'Schild leicht verblasst');
        await userEvent.tab();

        await waitFor(() => {
            const put = fetcher.mock.calls.find(([url, init]) => String(url).endsWith('/pruefpunkte/100') && init?.method === 'PUT');
            expect(JSON.parse(put![1]!.body as string)).toEqual({ ergebnis: 'IO', bemerkung: 'Schild leicht verblasst' });
        });
        expect(within(screen.getByTestId('pruefpunkt-100')).getByRole('textbox')).toHaveValue('Schild leicht verblasst');
    });

    // ── Prüfpunkte ergänzen, umformulieren, löschen ─────────────────────────

    it('ergänzt einen Prüfpunkt und zeigt ihn in der Liste', async () => {
        const neu = protokoll({
            pruefpunkte: [...protokoll().pruefpunkte, { id: 102, abschnittId: 10, modulKey: null, label: 'Abgasmessung', ergebnis: null, bemerkung: null, vorherigesErgebnis: null, sortierung: 2 }],
            statistik: { gesamt: 3, bewertet: 1, inOrdnung: 1, nichtInOrdnung: 0, nichtAnwendbar: 0 },
        });
        const fetcher = stubServer(protokoll(), (url, init) => (url === '/api/wartung/protokolle/1/pruefpunkte' && init?.method === 'POST' ? antwort(201, neu) : undefined));
        zeige();
        await screen.findByText('Zulassungsschild lesbar');
        await userEvent.type(screen.getByLabelText('Was ist zusätzlich zu prüfen?'), 'Abgasmessung');
        await userEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }));

        expect(await screen.findByText('Abgasmessung')).toBeVisible();
        const post = fetcher.mock.calls.find(([url, init]) => url === '/api/wartung/protokolle/1/pruefpunkte' && init?.method === 'POST')!;
        expect(JSON.parse(post[1]!.body as string)).toEqual({ abschnittId: null, label: 'Abgasmessung' });
        expect(screen.getByLabelText('Was ist zusätzlich zu prüfen?')).toHaveValue('');
        expect(screen.getByText(/1 von 3 bewertet/)).toBeVisible();
    });

    it('ergänzt nichts ohne Text und zeigt die Meldung des Servers bei einem Fehler', async () => {
        const fetcher = stubServer(protokoll(), (url, init) => (url.endsWith('/pruefpunkte') && init?.method === 'POST'
            ? antwort(409, { message: 'Dieses Protokoll ist „abgeschlossen“ und lässt sich nicht mehr ändern.' }) : undefined));
        zeige();
        await screen.findByText('Zulassungsschild lesbar');
        expect(screen.getByRole('button', { name: 'Hinzufügen' })).toBeDisabled();
        await userEvent.type(screen.getByLabelText('Was ist zusätzlich zu prüfen?'), 'X');
        await userEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }));

        expect(await screen.findByText('Dieses Protokoll ist „abgeschlossen“ und lässt sich nicht mehr ändern.')).toBeVisible();
        expect(fetcher.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1);
        expect(screen.getByLabelText('Was ist zusätzlich zu prüfen?')).toHaveValue('X');
    });

    it('bietet das Ergänzen im abgeschlossenen Feldeinsatz gar nicht erst an', async () => {
        stubServer(protokoll({ status: 'ABGESCHLOSSEN', bearbeitbar: false, abgeschlossenAm: '2026-05-10T10:00:00' }));
        zeige();
        await screen.findByText('Zulassungsschild lesbar');
        expect(screen.queryByLabelText('Was ist zusätzlich zu prüfen?')).toBeNull();
        expect(screen.queryByRole('button', { name: /Prüfpunkt löschen/ })).toBeNull();
    });

    it('löscht einen unberührten Prüfpunkt ohne Rückfrage', async () => {
        const ohne = protokoll({ pruefpunkte: [protokoll().pruefpunkte[0]], statistik: { gesamt: 1, bewertet: 1, inOrdnung: 1, nichtInOrdnung: 0, nichtAnwendbar: 0 } });
        const fetcher = stubServer(protokoll(), (url, init) => (url.endsWith('/pruefpunkte/101') && init?.method === 'DELETE' ? antwort(200, ohne) : undefined));
        zeige();
        const zeile = await screen.findByTestId('pruefpunkt-101');
        await userEvent.click(within(zeile).getByRole('button', { name: /Prüfpunkt löschen/ }));

        await waitFor(() => expect(screen.queryByTestId('pruefpunkt-101')).toBeNull());
        expect(screen.queryByRole('dialog')).toBeNull();
        expect(fetcher.mock.calls.some(([url, init]) => String(url).endsWith('/pruefpunkte/101') && init?.method === 'DELETE')).toBe(true);
    });

    it('fragt vor dem Löschen eines bewerteten Prüfpunkts nach, und bei "Abbrechen" bleibt alles', async () => {
        const fetcher = stubServer(protokoll());
        zeige();
        const zeile = await screen.findByTestId('pruefpunkt-100');
        await userEvent.click(within(zeile).getByRole('button', { name: /Prüfpunkt löschen/ }));

        expect(await screen.findByText('Prüfpunkt löschen?')).toBeVisible();
        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(screen.getByTestId('pruefpunkt-100')).toBeVisible();
        expect(fetcher.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);
    });

    it('löscht einen bewerteten Prüfpunkt nach der Bestätigung', async () => {
        const ohne = protokoll({ pruefpunkte: [protokoll().pruefpunkte[1]], statistik: { gesamt: 1, bewertet: 0, inOrdnung: 0, nichtInOrdnung: 0, nichtAnwendbar: 0 } });
        const fetcher = stubServer(protokoll(), (url, init) => (url.endsWith('/pruefpunkte/100') && init?.method === 'DELETE' ? antwort(200, ohne) : undefined));
        zeige();
        const zeile = await screen.findByTestId('pruefpunkt-100');
        await userEvent.click(within(zeile).getByRole('button', { name: /Prüfpunkt löschen/ }));
        await userEvent.click(await screen.findByRole('button', { name: 'Löschen' }));

        await waitFor(() => expect(screen.queryByTestId('pruefpunkt-100')).toBeNull());
        expect(fetcher.mock.calls.some(([url, init]) => String(url).endsWith('/pruefpunkte/100') && init?.method === 'DELETE')).toBe(true);
    });

    it('formuliert einen Prüfpunkt um und schickt den neuen Text', async () => {
        const geaendert = protokoll({
            pruefpunkte: [{ ...protokoll().pruefpunkte[0], label: 'Zulassungsschild gut lesbar' }, protokoll().pruefpunkte[1]],
        });
        const fetcher = stubServer(protokoll(), (url, init) => (url.endsWith('/pruefpunkte/100/text') && init?.method === 'PUT' ? antwort(200, geaendert) : undefined));
        zeige();
        const zeile = await screen.findByTestId('pruefpunkt-100');
        await userEvent.click(within(zeile).getByRole('button', { name: /umformulieren/ }));
        const feld = within(zeile).getByRole('textbox');
        await userEvent.clear(feld);
        await userEvent.type(feld, 'Zulassungsschild gut lesbar{Enter}');

        expect(await screen.findByText('Zulassungsschild gut lesbar')).toBeVisible();
        const put = fetcher.mock.calls.find(([url]) => String(url).endsWith('/pruefpunkte/100/text'))!;
        expect(JSON.parse(put[1]!.body as string)).toEqual({ label: 'Zulassungsschild gut lesbar' });
    });

    // ── Projekt in den Angaben ──────────────────────────────────────────────

    it('bietet in den Angaben die Projekte des Kunden an und speichert die Wahl mit den Angaben', async () => {
        const fetcher = stubServer(protokoll(), (url, init) => (url.endsWith('/kopf') && init?.method === 'PUT'
            ? antwort(200, protokoll({ projekt: { id: 7, name: '2026-001 – Neubau Halle' } })) : undefined));
        zeige();
        await screen.findByRole('heading', { name: 'WA-2026-1001' });
        await userEvent.click(screen.getByRole('combobox', { name: /Projekt/ }));
        expect(screen.getByRole('option', { name: '2026-002 – Umbau Lager' })).toBeVisible();
        await userEvent.click(screen.getByRole('option', { name: '2026-001 – Neubau Halle' }));
        await userEvent.click(screen.getByRole('button', { name: 'Angaben speichern' }));

        await waitFor(() => {
            const put = fetcher.mock.calls.find(([url, init]) => String(url).endsWith('/kopf') && init?.method === 'PUT');
            expect(JSON.parse(put![1]!.body as string)).toMatchObject({ projektId: 7 });
        });
        expect(await screen.findByText('Angaben gespeichert.')).toBeVisible();
    });

    it('zeigt "Noch nicht gespeichert", solange Angaben geändert, aber nicht gespeichert sind', async () => {
        stubServer(protokoll());
        zeige();
        await screen.findByRole('heading', { name: 'WA-2026-1001' });
        expect(screen.queryByText('Noch nicht gespeichert')).toBeNull();
        await userEvent.type(screen.getByRole('textbox', { name: 'Ansprechpartner des Kunden' }), 'Hausmeister');
        expect(screen.getByText('Noch nicht gespeichert')).toBeVisible();
    });

    // ── Unterschrift des Mitarbeiters ───────────────────────────────────────

    const GESPEICHERT = { bild: 'data:image/png;base64,iVBORw0KGgo=', name: 'Max Mustermann', geaendertAm: '2026-04-01T08:00:00' };

    it('setzt die gespeicherte Unterschrift ein, wenn der Mitarbeiter sie verwenden möchte', async () => {
        const mit = protokoll({ unterschriftTechniker: { bild: GESPEICHERT.bild, name: 'Max Mustermann' } });
        const fetcher = stubServer(protokoll(), (url, init) => {
            if (url === '/api/wartung/unterschrift/meine') return antwort(200, GESPEICHERT);
            if (url.endsWith('/unterschrift/gespeichert') && init?.method === 'POST') return antwort(200, mit);
            return undefined;
        });
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Meine gespeicherte Unterschrift verwenden' }));

        expect(await screen.findByAltText('Unterschrift von Max Mustermann')).toBeVisible();
        expect(fetcher.mock.calls.some(([url, init]) => String(url).endsWith('/protokolle/1/unterschrift/gespeichert') && init?.method === 'POST')).toBe(true);
        expect(screen.getByText('Der Mitarbeiter hat unterschrieben.')).toBeVisible();
    });

    it('bietet die gespeicherte Unterschrift nicht an, wenn es keine gibt, und sagt es bei der Zeichenfläche mit "merken"', async () => {
        stubServer(protokoll());
        zeige();
        await screen.findByRole('heading', { name: 'WA-2026-1001' });
        expect(screen.queryByRole('button', { name: 'Meine gespeicherte Unterschrift verwenden' })).toBeNull();
        expect(screen.getByRole('checkbox', { name: /Für künftige Feldeinsätze merken/ })).not.toBeChecked();
    });

    it('löscht die gespeicherte Unterschrift nach Rückfrage, ohne bestehende Protokolle zu berühren', async () => {
        const fetcher = stubServer(protokoll(), (url, init) => {
            if (url === '/api/wartung/unterschrift/meine' && (!init || !init.method)) return antwort(200, GESPEICHERT);
            if (url === '/api/wartung/unterschrift/meine' && init?.method === 'DELETE') return antwort(204, null);
            return undefined;
        });
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: /Gespeicherte Unterschrift löschen/ }));
        expect(await screen.findByText(/Bereits unterschriebene Protokolle bleiben/)).toBeVisible();
        await userEvent.click(screen.getByRole('button', { name: 'Löschen' }));

        await waitFor(() => expect(screen.queryByRole('button', { name: /Gespeicherte Unterschrift löschen/ })).toBeNull());
        expect(fetcher.mock.calls.some(([url, init]) => url === '/api/wartung/unterschrift/meine' && init?.method === 'DELETE')).toBe(true);
        expect(fetcher.mock.calls.some(([url]) => String(url).includes('/protokolle/1/unterschrift'))).toBe(false);
    });

    // ── Bericht im Projekt ──────────────────────────────────────────────────

    const FERTIG = {
        status: 'ABGESCHLOSSEN' as const, bearbeitbar: false, abgeschlossenAm: '2026-05-10T10:00:00',
        statistik: { gesamt: 2, bewertet: 2, inOrdnung: 2, nichtInOrdnung: 0, nichtAnwendbar: 0 },
        projekt: { id: 7, name: '2026-001 – Neubau Halle' },
    };

    it('legt den Bericht im Projekt ab und zeigt den Link zum Öffnen', async () => {
        const fetcher = stubServer(protokoll(FERTIG), (url, init) => (url.endsWith('/protokolle/1/bericht') && init?.method === 'POST'
            ? antwort(201, { dokumentId: 900, dateiname: 'Wartungsprotokoll_WA-2026-1001.pdf', url: '/api/dokumente/abc.pdf', erstelltAm: '2026-10-01T10:00:00' }) : undefined));
        zeige();
        expect(await screen.findByText(/wird im Projekt/)).toHaveTextContent('2026-001 – Neubau Halle');
        await userEvent.click(screen.getByRole('button', { name: 'PDF-Bericht im Projekt speichern' }));

        expect(await screen.findByRole('link', { name: /Bericht öffnen/ })).toHaveAttribute('href', '/api/dokumente/abc.pdf');
        expect(screen.getByText('Der Bericht liegt jetzt im Projekt.')).toBeVisible();
        expect(screen.getByRole('button', { name: 'Bericht neu erstellen' })).toBeVisible();
        expect(fetcher.mock.calls.some(([url, init]) => String(url).endsWith('/bericht') && init?.method === 'POST')).toBe(true);
    });

    it('zeigt einen vorhandenen Bericht beim Öffnen der Seite und bietet "neu erstellen" an', async () => {
        stubServer(protokoll({ ...FERTIG, bericht: { dokumentId: 900, url: '/api/dokumente/abc.pdf', erstelltAm: '2026-10-01T10:00:00' } }));
        zeige();
        expect(await screen.findByRole('link', { name: /Bericht öffnen/ })).toHaveAttribute('href', '/api/dokumente/abc.pdf');
        expect(screen.getByRole('button', { name: 'Bericht neu erstellen' })).toBeEnabled();
    });

    it('sperrt den Bericht ohne Projekt und erklärt, was zu tun ist', async () => {
        const fetcher = stubServer(protokoll({ ...FERTIG, projekt: null }));
        zeige();
        const knopf = await screen.findByRole('button', { name: 'PDF-Bericht im Projekt speichern' });
        expect(knopf).toBeDisabled();
        expect(screen.getByText(/gehört zu keinem Projekt/)).toBeVisible();
        await userEvent.click(knopf);
        expect(fetcher.mock.calls.some(([url]) => String(url).endsWith('/bericht'))).toBe(false);
    });

    it('zeigt die Meldung des Servers, wenn der Bericht nicht erstellt werden kann', async () => {
        stubServer(protokoll(FERTIG), (url, init) => (url.endsWith('/bericht') && init?.method === 'POST'
            ? antwort(409, { message: 'Der Bericht lässt sich erst nach dem Abschluss des Feldeinsatzes erstellen.' }) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'PDF-Bericht im Projekt speichern' }));
        expect(await screen.findByText('Der Bericht lässt sich erst nach dem Abschluss des Feldeinsatzes erstellen.')).toBeVisible();
        expect(screen.queryByRole('link', { name: /Bericht öffnen/ })).toBeNull();
    });

    it('bietet vor dem Abschluss keinen Bericht an', async () => {
        stubServer(protokoll());
        zeige();
        await screen.findByRole('heading', { name: 'WA-2026-1001' });
        expect(screen.queryByRole('button', { name: /Bericht/ })).toBeNull();
    });
});
