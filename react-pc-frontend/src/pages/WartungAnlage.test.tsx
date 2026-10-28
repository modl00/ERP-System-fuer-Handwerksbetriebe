import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import WartungAnlage from './WartungAnlage';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import type { WartungAnlage as Anlage, WartungModul } from '../features/wartung/types';

// Der angemeldete Benutzer ist Mitarbeiter 5: Er wird als Mitarbeiter des neuen Feldeinsatzes eingetragen
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ isAdmin: false, user: { id: 1, mitarbeiter: { id: 5 } } }) }));

const pruefung = (id: number, nr: number, datum: string, extra: Partial<Anlage['historie'][number]> = {}) => ({
    id, pruefung: nr, nummer: `WA-2026-100${nr}`, datum, status: 'ABGESCHLOSSEN' as const, technikerName: 'Max Müller',
    pruefpunkteGesamt: 10, pruefpunkteBewertet: 10, maengel: 0, vorlageProtokollId: null, berichtDokumentId: null, berichtUrl: null, ...extra,
});

function anlage(extra: Partial<Anlage> = {}): Anlage {
    return {
        id: 3, bezeichnung: 'Heizungsanlage', notizen: 'Schlüssel beim Hausmeister', intervallMonate: 6, naechsteFaelligkeit: '2027-04-01', aktiv: true,
        kunde: { id: 1, name: 'Max Mustermann GmbH' },
        objekt: { id: 2, bezeichnung: 'Werk Nord', strasse: 'Industriestraße 12', plz: '97080', ort: 'Würzburg' },
        projekt: null,
        module: [{ modulId: 1, name: 'Heizungsanlage', bauart: 'Gas-Brennwert', bauartSchluessel: 'gas' }],
        offenerFeldeinsatzId: null, offenerFeldeinsatzNummer: null, letztePruefungId: 12,
        historie: [
            pruefung(10, 1, '2026-10-01', { berichtDokumentId: 900, berichtUrl: '/api/dokumente/b1.pdf', maengel: 1 }),
            pruefung(12, 2, '2027-04-01', { vorlageProtokollId: 10, technikerName: 'Erika Schmidt' }),
        ],
        ...extra,
    };
}

const istGet = (init?: RequestInit) => !init || !init.method || init.method === 'GET';

function json(status: number, body: unknown = {}): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

interface Stubs { fetcher: ReturnType<typeof vi.fn>; gesendet: (methode: string, endung: string) => unknown }

function stub(start: Anlage, eigene?: (url: string, init?: RequestInit) => Response | undefined): Stubs {
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const e = eigene?.(url, init);
        if (e) return e;
        if (url === '/api/wartung/tueren/3' && istGet(init)) return json(200, start);
        if (url === '/api/wartung/kunden/1/projekte') return json(200, [{ id: 7, name: '2026-001 – Neubau Halle' }, { id: 8, name: '2026-002 – Umbau Lager' }]);
        if (url === '/api/wartung/module') return json(200, [] as WartungModul[]);
        return json(404, { message: 'nicht gefunden: ' + url });
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

const zeige = () => render(
    <ToastProvider><ConfirmProvider><MemoryRouter initialEntries={['/wartung/anlagen/3']}>
        <Routes>
            <Route path="/wartung/anlagen/:tuerId" element={<WartungAnlage />} />
            <Route path="/wartung/:id" element={<p>Feldeinsatz geöffnet</p>} />
            <Route path="/wartung" element={<p>Übersicht</p>} />
        </Routes>
    </MemoryRouter></ConfirmProvider></ToastProvider>,
);

afterEach(() => { vi.unstubAllGlobals(); });

describe('WartungAnlage: Angaben und Historie', () => {
    it('zeigt Kunde, Objekt, Module, Intervall und nächste Prüfung', async () => {
        stub(anlage());
        zeige();
        expect(await screen.findByRole('heading', { name: 'Heizungsanlage' })).toBeVisible();
        expect(screen.getByText('Max Mustermann GmbH · Werk Nord')).toBeVisible();
        expect(screen.getByText('Heizungsanlage (Gas-Brennwert)')).toBeVisible();
        expect(screen.getByText('6 Monate')).toBeVisible();
        const angaben = screen.getByRole('heading', { name: 'Angaben' }).parentElement!;
        expect(within(angaben).getByText('01.04.2027')).toBeVisible();
        expect(screen.getByText('Schlüssel beim Hausmeister')).toBeVisible();
        expect(screen.getByText('Industriestraße 12, 97080 Würzburg')).toBeVisible();
    });

    it('zeigt die Historie mit laufender Nummer (neueste zuerst), Datum, Mitarbeiter, Mängeln, Vorlage und Bericht', async () => {
        stub(anlage());
        zeige();
        await screen.findByRole('heading', { name: 'Historie der Prüfungen' });

        const eintraege = screen.getAllByTestId(/^pruefung-/);
        expect(eintraege.map((e) => e.getAttribute('data-testid'))).toEqual(['pruefung-12', 'pruefung-10']);
        expect(eintraege[0]).toHaveTextContent('Prüfung 02');
        expect(eintraege[0]).toHaveTextContent('01.04.2027');
        expect(eintraege[0]).toHaveTextContent('Erika Schmidt');
        expect(eintraege[0]).toHaveTextContent('gestartet aus Prüfung 01');
        expect(eintraege[1]).toHaveTextContent('Prüfung 01');
        expect(eintraege[1]).toHaveTextContent('01.10.2026');
        expect(eintraege[1]).toHaveTextContent('Max Müller');
        expect(eintraege[1]).toHaveTextContent('1 Mangel');
        expect(eintraege[1]).toHaveTextContent('WA-2026-1001 · 10/10 Punkte bewertet');
        expect(within(eintraege[1]).getByRole('link', { name: 'Bericht zu Prüfung 01 öffnen' })).toHaveAttribute('href', '/api/dokumente/b1.pdf');
        expect(within(eintraege[0]).queryByRole('link', { name: /Bericht/ })).toBeNull();
        expect(within(eintraege[0]).getByRole('link', { name: 'Prüfung 02 öffnen' })).toHaveAttribute('href', '/wartung/12');
    });

    it('sagt bei einer Wartung ohne Prüfung, dass es noch keine gibt', async () => {
        stub(anlage({ historie: [], letztePruefungId: null }));
        zeige();
        expect(await screen.findByText('Noch keine Prüfung durchgeführt.')).toBeVisible();
    });

    it('meldet einen Ladefehler und bietet einen neuen Versuch an', async () => {
        let aufrufe = 0;
        stub(anlage(), (url) => (url === '/api/wartung/tueren/3' && ++aufrufe === 1 ? json(404, { message: 'Die Wartung wurde nicht gefunden.' }) : undefined));
        zeige();
        expect((await screen.findAllByText('Die Wartung wurde nicht gefunden.')).length).toBeGreaterThan(0);
        await userEvent.click(screen.getByRole('button', { name: 'Noch einmal versuchen' }));
        expect(await screen.findByRole('heading', { name: 'Heizungsanlage' })).toBeVisible();
    });
});

describe('WartungAnlage: Feldeinsatz starten', () => {
    const protokollAntwort = { id: 77, nummer: 'WA-2026-2001' };

    it('startet aus der letzten abgeschlossenen Prüfung als Vorlage, mit Mitarbeiter und übernommenen Werten', async () => {
        const s = stub(anlage(), (url, init) => (url === '/api/wartung/protokolle' && init?.method === 'POST' ? json(201, protokollAntwort) : undefined));
        zeige();
        expect(await screen.findByText(/dient als Vorlage/)).toHaveTextContent('Prüfung 02');
        await userEvent.click(screen.getByRole('button', { name: 'Feldeinsatz starten' }));

        expect(await screen.findByText('Feldeinsatz geöffnet')).toBeVisible();
        expect(s.gesendet('POST', '/api/wartung/protokolle')).toMatchObject({
            kundeId: 1, objektId: 2, tuerId: 3, technikerId: 5, vorlageProtokollId: 12, werteUebernehmen: true, module: [],
        });
    });

    it('übernimmt keine Werte, wenn der Haken weg ist, behält aber die Vorlage (Letztes Mal)', async () => {
        const s = stub(anlage(), (url, init) => (url === '/api/wartung/protokolle' && init?.method === 'POST' ? json(201, protokollAntwort) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('checkbox', { name: /Bisherige Ergebnisse, Bemerkungen und Werte übernehmen/ }));
        await userEvent.click(screen.getByRole('button', { name: 'Feldeinsatz starten' }));

        await screen.findByText('Feldeinsatz geöffnet');
        expect(s.gesendet('POST', '/api/wartung/protokolle')).toMatchObject({ vorlageProtokollId: 12, werteUebernehmen: false });
    });

    it('startet auf Wunsch ohne Vorlage neu aus dem Katalog mit den Modulen der Wartung', async () => {
        const s = stub(anlage(), (url, init) => (url === '/api/wartung/protokolle' && init?.method === 'POST' ? json(201, protokollAntwort) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: /Neu aus dem Katalog starten/ }));

        await screen.findByText('Feldeinsatz geöffnet');
        expect(s.gesendet('POST', '/api/wartung/protokolle')).toMatchObject({
            vorlageProtokollId: null, module: [{ modulId: 1, untertypSchluessel: 'gas' }],
        });
    });

    it('startet aus einer früheren Prüfung der Historie, nicht nur aus der letzten', async () => {
        const s = stub(anlage(), (url, init) => (url === '/api/wartung/protokolle' && init?.method === 'POST' ? json(201, protokollAntwort) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Neue Prüfung aus Prüfung 01 starten' }));

        await screen.findByText('Feldeinsatz geöffnet');
        expect(s.gesendet('POST', '/api/wartung/protokolle')).toMatchObject({ vorlageProtokollId: 10, werteUebernehmen: true });
    });

    it('startet die erste Prüfung aus dem Katalog, wenn es noch keine gibt', async () => {
        const s = stub(anlage({ historie: [], letztePruefungId: null }), (url, init) => (url === '/api/wartung/protokolle' && init?.method === 'POST' ? json(201, protokollAntwort) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Feldeinsatz starten' }));

        await screen.findByText('Feldeinsatz geöffnet');
        expect(s.gesendet('POST', '/api/wartung/protokolle')).toMatchObject({ vorlageProtokollId: null, module: [{ modulId: 1, untertypSchluessel: 'gas' }] });
    });

    it('sperrt den Start ohne Module und sagt, was zu tun ist', async () => {
        stub(anlage({ historie: [], letztePruefungId: null, module: [] }));
        zeige();
        expect(await screen.findByRole('button', { name: 'Feldeinsatz starten' })).toBeDisabled();
        expect(screen.getByText(/zuerst unter „Bearbeiten" ein Modul wählen/)).toBeVisible();
    });

    it('bietet bei einem offenen Feldeinsatz nur "fortsetzen" an: kein zweiter Start nebenbei', async () => {
        stub(anlage({ offenerFeldeinsatzId: 60, offenerFeldeinsatzNummer: 'WA-2026-1999' }));
        zeige();
        expect(await screen.findByText(/Es läuft schon ein Feldeinsatz/)).toHaveTextContent('WA-2026-1999');
        expect(screen.queryByRole('button', { name: 'Feldeinsatz starten' })).toBeNull();
        expect(screen.queryByRole('button', { name: /Neu aus dem Katalog/ })).toBeNull();
        expect(screen.queryByRole('button', { name: /Neue Prüfung aus Prüfung/ })).toBeNull();
        await userEvent.click(screen.getByRole('button', { name: 'Feldeinsatz fortsetzen' }));
        expect(await screen.findByText('Feldeinsatz geöffnet')).toBeVisible();
    });

    it('zeigt die Meldung des Servers, wenn der Start scheitert, und lädt die Seite neu', async () => {
        let geladen = 0;
        stub(anlage(), (url, init) => {
            if (url === '/api/wartung/tueren/3' && istGet(init)) {
                return ++geladen === 1 ? undefined : json(200, anlage({ offenerFeldeinsatzId: 61, offenerFeldeinsatzNummer: 'WA-2026-2000' }));
            }
            if (url === '/api/wartung/protokolle' && init?.method === 'POST') return json(409, { message: 'Für diese Wartung läuft schon ein Feldeinsatz (WA-2026-2000). Bitte den öffnen oder abschließen.' });
            return undefined;
        });
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Feldeinsatz starten' }));

        expect((await screen.findAllByText(/läuft schon ein Feldeinsatz \(WA-2026-2000\)/)).length).toBeGreaterThan(0);
        expect(await screen.findByRole('button', { name: 'Feldeinsatz fortsetzen' })).toBeVisible();
    });
});

describe('WartungAnlage: Projekt', () => {
    it('ordnet die Wartung einem Projekt des Kunden zu', async () => {
        const s = stub(anlage(), (url, init) => (url.endsWith('/tueren/3/projekt') && init?.method === 'PUT'
            ? json(200, anlage({ projekt: { id: 7, name: '2026-001 – Neubau Halle' } })) : undefined));
        zeige();
        await screen.findByText('Diese Wartung gehört noch zu keinem Projekt.');
        expect(screen.getByRole('button', { name: 'Zuordnen' })).toBeDisabled();
        await userEvent.click(screen.getByRole('combobox', { name: /Einem Projekt zuordnen/ }));
        await userEvent.click(await screen.findByRole('option', { name: '2026-001 – Neubau Halle' }));
        await userEvent.click(screen.getByRole('button', { name: 'Zuordnen' }));

        expect(await screen.findByRole('link', { name: '2026-001 – Neubau Halle' })).toHaveAttribute('href', expect.stringContaining('/projekte?projektId=7'));
        expect(s.gesendet('PUT', '/tueren/3/projekt')).toEqual({ projektId: 7 });
        expect(screen.getByText('Die Wartung ist dem Projekt zugeordnet.')).toBeVisible();
    });

    it('wechselt das Projekt und bietet dabei das aktuelle nicht noch einmal an', async () => {
        const s = stub(anlage({ projekt: { id: 7, name: '2026-001 – Neubau Halle' } }), (url, init) => (url.endsWith('/tueren/3/projekt') && init?.method === 'PUT'
            ? json(200, anlage({ projekt: { id: 8, name: '2026-002 – Umbau Lager' } })) : undefined));
        zeige();
        await screen.findByRole('link', { name: '2026-001 – Neubau Halle' });
        await userEvent.click(screen.getByRole('combobox', { name: /Anderem Projekt zuordnen/ }));
        expect(screen.queryByRole('option', { name: '2026-001 – Neubau Halle' })).toBeNull();
        await userEvent.click(await screen.findByRole('option', { name: '2026-002 – Umbau Lager' }));
        await userEvent.click(screen.getByRole('button', { name: 'Zuordnen' }));

        expect(await screen.findByRole('link', { name: '2026-002 – Umbau Lager' })).toBeVisible();
        expect(s.gesendet('PUT', '/tueren/3/projekt')).toEqual({ projektId: 8 });
    });

    it('trennt die Wartung nach Rückfrage vom Projekt, ohne Prüfungen zu verändern', async () => {
        const s = stub(anlage({ projekt: { id: 7, name: '2026-001 – Neubau Halle' } }), (url, init) => (url.endsWith('/tueren/3/projekt') && init?.method === 'PUT'
            ? json(200, anlage({ projekt: null })) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Wartung vom Projekt trennen' }));
        expect(await screen.findByText('Projekt entfernen?')).toBeVisible();
        expect(screen.getByText(/Bestehende Prüfungen und ihre Berichte bleiben/)).toBeVisible();
        await userEvent.click(screen.getByRole('button', { name: 'Vom Projekt trennen' }));

        expect(await screen.findByText('Diese Wartung gehört noch zu keinem Projekt.')).toBeVisible();
        expect(s.gesendet('PUT', '/tueren/3/projekt')).toEqual({ projektId: null });
    });

    it('trennt nichts, wenn die Rückfrage abgebrochen wird', async () => {
        const s = stub(anlage({ projekt: { id: 7, name: '2026-001 – Neubau Halle' } }));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Wartung vom Projekt trennen' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }));

        expect(screen.getByRole('link', { name: '2026-001 – Neubau Halle' })).toBeVisible();
        expect(s.gesendet('PUT', '/tueren/3/projekt')).toBeNull();
    });

    it('zeigt die Meldung des Servers, wenn das Projekt nicht zugeordnet werden kann, und behält den Stand', async () => {
        stub(anlage(), (url, init) => (url.endsWith('/tueren/3/projekt') && init?.method === 'PUT' ? json(400, { message: 'Das Projekt gehört zu einem anderen Kunden.' }) : undefined));
        zeige();
        await screen.findByText('Diese Wartung gehört noch zu keinem Projekt.');
        await userEvent.click(screen.getByRole('combobox', { name: /Einem Projekt zuordnen/ }));
        await userEvent.click(await screen.findByRole('option', { name: '2026-001 – Neubau Halle' }));
        await userEvent.click(screen.getByRole('button', { name: 'Zuordnen' }));

        expect(await screen.findByText('Das Projekt gehört zu einem anderen Kunden.')).toBeVisible();
        expect(screen.getByText('Diese Wartung gehört noch zu keinem Projekt.')).toBeVisible();
    });

    it('lässt die Auswahl leer und gesperrt, wenn der Kunde keine weiteren Projekte hat', async () => {
        stub(anlage(), (url) => (url === '/api/wartung/kunden/1/projekte' ? json(200, []) : undefined));
        zeige();
        await screen.findByText('Diese Wartung gehört noch zu keinem Projekt.');
        await waitFor(() => expect(screen.getByRole('combobox', { name: /Einem Projekt zuordnen/ })).toBeDisabled());
    });
});

describe('WartungAnlage: Bearbeiten', () => {
    it('lädt für das Bearbeiten den Katalog und öffnet den Dialog', async () => {
        const s = stub(anlage());
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: /Bearbeiten/ }));
        expect(await screen.findByRole('dialog')).toBeVisible();
        expect(s.fetcher.mock.calls.some(([u]) => u === '/api/wartung/module')).toBe(true);
    });
});
