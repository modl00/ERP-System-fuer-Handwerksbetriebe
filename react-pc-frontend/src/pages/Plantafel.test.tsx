import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Plantafel from './Plantafel';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import type { Auftrag, Vorgang } from '../features/plantafel/plantafelApi';

/** Nur Dummy-Daten. */
const typen = [
    { id: 1, name: 'Planung', farbe: '#6366f1', sortierung: 10, standardDauerTage: 2, aktiv: true, abteilungId: null, stundenProTag: null, wartet: false, buero: true },
    { id: 2, name: 'Fertigung', farbe: '#be123c', sortierung: 30, standardDauerTage: 5, aktiv: true, abteilungId: null, stundenProTag: null, wartet: false, buero: false },
];

const v = (id: number, bezeichnung: string, start: string, ende: string, vorgaenger: Vorgang['vorgaenger'] = []): Vorgang => ({
    id, typId: id, typName: bezeichnung, farbe: '#be123c', bezeichnung, dauerTage: 2, dauerHerkunft: 'GESCHAETZT', festerStart: null, start, ende, konflikt: false, status: 'GEPLANT', notiz: null, vorgaenger,
    mitarbeiterIds: [], ressourceId: null, prioritaet: 'NORMAL', aufwandStunden: 16, aufwandHerkunft: 'GESCHAETZT', fruehestensAb: null, rang: null, hinweis: null, wartet: false, buero: false,
    ohneZuweisungGeschaetzt: false, stand: 3, lieferantId: null, lieferantName: null, bestelltAm: null, lieferterminLieferant: null, fremdleistungId: null, menge: null, mengenEinheit: null, istStunden: null,
});

const auftrag = (): Auftrag => ({
    projektId: 7, bezeichnung: 'Geländer Musterstraße', auftragsnummer: 'A-1', startDatum: null, zielDatum: null, liefertermin: '2026-10-13', ampel: 'OHNE_ZIEL',
    vorgaenge: [v(1, 'Planung', '2026-10-06', '2026-10-07'), v(2, 'Fertigung', '2026-10-08', '2026-10-13', [{ vorgaengerId: 1, versatzTage: 0 }])],
});

const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;

let fetcher: ReturnType<typeof vi.fn>;
let liste: Auftrag[];
let kiFehler = false;
let aenderungen: unknown[] = [];
let offene: unknown[] = [];

const FERTIGUNG = /^Fertigung, Geländer Musterstraße/;
/** Schaltet „Bearbeiten" ein und öffnet den Vorgang „Fertigung" (der erste Treffer steht in der Zeile des Auftrags). */
const oeffneVorgang = async (user: ReturnType<typeof userEvent.setup>) => {
    const treffer = await screen.findAllByRole('button', { name: FERTIGUNG });
    const schalter = screen.getByRole('button', { name: /^Bearbeiten( ist an)?$/ });
    if (schalter.getAttribute('aria-pressed') !== 'true') await user.click(schalter);
    await user.click(treffer[0]);
};
const zeige = () => render(<ToastProvider><ConfirmProvider><Plantafel /></ConfirmProvider></ToastProvider>);
const aufrufe = (methode: string, teil: string) => fetcher.mock.calls.filter(([u, i]) => String(u).includes(teil) && ((i as RequestInit | undefined)?.method ?? 'GET') === methode);

beforeEach(() => {
    liste = [auftrag()];
    offene = [];
    kiFehler = false;
    aenderungen = [{ id: 5, zeitpunkt: '2026-10-12T08:30:00', benutzer: 'anna.buero', beschreibung: '„Fertigung" (Geländer Musterstraße): Aufwand von 16 Stunden auf 32 Stunden', vorgangId: 2, projektId: 7, zurueckgenommen: false, rueckgaengigMoeglich: true }];
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const u = String(url);
        if (u.endsWith('/api/plantafel/auftraege') && !init?.method) return json(liste);
        if (u.endsWith('/api/plantafel/vorgangstypen') && !init?.method) return json(typen);
        if (u.includes('/api/plantafel/belegung')) {
            return json({ von: '2026-10-05', bis: '2026-10-11', zeilen: [{ schluessel: 'M4', gruppe: 'MITARBEITER', name: 'Max Mustermann', eintraege: [{
                id: 'V2', quelle: 'VORGANG', titel: 'Fertigung', untertitel: 'Geländer Musterstraße (A-1)', von: '2026-10-08', bis: '2026-10-09', vonZeit: null, bisZeit: null, farbe: '#be123c',
                projektId: 7, prioritaet: 'NORMAL', fest: false, status: 'GEPLANT', hinweis: null }] }] });
        }
        if (u.endsWith('/api/plantafel/offene-planung')) return json(offene);
        if (u.includes('/api/lieferanten')) return json({ lieferanten: [{ id: 21, lieferantenname: 'Stahlhandel Muster', standardLieferzeitTage: 8 }] });
        if (u.includes('/api/plantafel/vorgaenge/2/lieferung') && init?.method === 'PUT') return json(auftrag());
        if (u.includes('/api/plantafel/aenderungen/5/rueckgaengig') && init?.method === 'POST') return json(auftrag());
        if (u.includes('/api/plantafel/aenderungen')) return json(aenderungen);
        if (u.endsWith('/api/plantafel/personen')) return json([{ id: 3, name: 'Erika Musterfrau' }, { id: 4, name: 'Max Mustermann' }]);
        if (u.endsWith('/api/plantafel/ressourcen')) return json([{ id: 8, name: 'Werkstatt', art: 'ORT', sortierung: 20, aktiv: true }]);
        if (u.endsWith('/api/plantafel/vorgaenge/2/zuweisung') && init?.method === 'PUT') {
            const neu = auftrag();
            neu.vorgaenge[1] = { ...neu.vorgaenge[1], ...JSON.parse(init.body as string) };
            return json(neu);
        }
        if (u.includes('/api/plantafel/vorgaenge/2') && init?.method === 'PUT' && !u.includes('vorgaenger')) {
            const neu = auftrag();
            neu.vorgaenge[1] = { ...neu.vorgaenge[1], dauerTage: 4, dauerHerkunft: 'VON_HAND', ende: '2026-10-15' };
            neu.liefertermin = '2026-10-15';
            return json(neu);
        }
        if (u.endsWith('/api/abteilungen')) return json([{ id: 5, name: 'Werkstatt' }]);
        if (u.match(/\/api\/plantafel\/vorgangstypen\/\d+$/) && init?.method === 'PUT') return json({ ...typen[1], ...JSON.parse(init.body as string), id: 2 });
        if (u.endsWith('/api/plantafel/auftraege/7/vorschlag')) {
            return json({
                quelle: 'ANGEBOT', hinweise: [],
                zeilen: [
                    { vorgangId: 1, bezeichnung: 'Planung', art: 'Planung', aktuelleDauer: 2, aktuelleHerkunft: 'GESCHAETZT', vorgeschlageneDauer: null, stunden: null, stundenProTag: null, abteilung: null, hinweis: 'Für diese Art ist keine Abteilung eingestellt.' },
                    { vorgangId: 2, bezeichnung: 'Fertigung', art: 'Fertigung', aktuelleDauer: 2, aktuelleHerkunft: 'GESCHAETZT', vorgeschlageneDauer: 3, stunden: 50, stundenProTag: 24, abteilung: 'Werkstatt', hinweis: null },
                ],
            });
        }
        if (u.endsWith('/api/plantafel/auftraege/7/vorschlag/uebernehmen')) { const neu = auftrag(); neu.liefertermin = '2026-10-14'; return json(neu); }
        if (u.includes('/api/plantafel/ki/auftraege/9/vorgaenge/uebernehmen')) return json({ ...auftrag(), projektId: 9, bezeichnung: 'Tor Hofeinfahrt' });
        if (u.includes('/api/plantafel/ki/auftraege/9/vorgaenge')) {
            return json({
                quelle: 'ANGEBOT', hinweise: ['Die KI nannte „Lackieren". Diese Vorgangsart gibt es nicht und sie wurde nicht übernommen.'],
                schritte: [{ typId: 1, typName: 'Planung', farbe: '#6366f1', dauerTage: 1, begruendung: 'Zeichnung prüfen' }, { typId: 2, typName: 'Fertigung', farbe: '#be123c', dauerTage: 6, begruendung: 'Schweißen und Schleifen' }],
            });
        }
        if (u.includes('/api/plantafel/ki/auftraege/7/erklaerung')) return json(kiFehler ? { message: 'Für diese Funktion ist kein Schlüssel hinterlegt.' } : { text: 'Die Fertigung bestimmt den Termin.' }, kiFehler ? 400 : 200);
        if (u.includes('/standardkette')) return json({ ...auftrag(), projektId: 9, bezeichnung: 'Tor Hofeinfahrt' });
        if (u.startsWith('/api/projekte/simple')) return json([{ id: 9, bauvorhaben: 'Tor Hofeinfahrt', auftragsnummer: 'A-9' }]);
        return json({}, 404);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('Plantafel', { timeout: 30000 }, () => {
    it('es gibt nur eine Ansicht, die Belegung der Mitarbeiter und Ressourcen; die Lieferzeit steht im Vorgang', async () => {
        const user = userEvent.setup();
        zeige();
        expect((await screen.findAllByRole('button', { name: FERTIGUNG })).length).toBeGreaterThan(0);
        expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
        expect(screen.queryByTestId('gantt')).not.toBeInTheDocument();
        expect(screen.getByRole('region', { name: 'Belegung nach Mitarbeitern und Ressourcen' })).toBeInTheDocument();

        await user.click(screen.getAllByRole('button', { name: FERTIGUNG })[0]);
        expect(await screen.findByTestId('auftrag-aktionen')).toHaveTextContent('Lieferzeit 13.10.2026');
    });

    it('jeder Auftrag hat eine Zeile mit Ampel, Lieferzeit und Zieltermin', async () => {
        liste[0] = { ...liste[0], ampel: 'VERSPAETET', verspaetungTage: 3, zielDatum: '2026-10-10', liefertermin: '2026-10-13' };
        zeige();

        const info = await screen.findByTestId('auftrag-info-7');
        expect(info).toHaveTextContent('Verspätet +3 Tg.');
        expect(info).toHaveTextContent('Lieferzeit 13.10.26');
        expect(screen.getByTestId('zeile-P7')).toHaveTextContent('Geländer Musterstraße');
    });

    it('im Ansehen-Modus sind die Felder gesperrt; „Bearbeiten" schaltet den Modus ein', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click((await screen.findAllByRole('button', { name: FERTIGUNG }))[0]);

        expect(await screen.findByTestId('vorgang-ansehen')).toBeInTheDocument();
        expect(screen.getByLabelText('Aufwand in Stunden')).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument();

        await user.click(screen.getAllByRole('button', { name: 'Bearbeiten' }).at(-1) as HTMLElement);
        await waitFor(() => expect(screen.getByLabelText('Aufwand in Stunden')).toBeEnabled());
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeInTheDocument();
    });

    it('Aufträge, die mit „Später" übersprungen wurden, stehen als „neu, noch nicht eingeplant" da und lassen sich planen', async () => {
        offene = [{ projektId: 9, bezeichnung: 'Tor Hofeinfahrt', auftragsnummer: 'A-2' }];
        const user = userEvent.setup();
        zeige();

        const hinweis = await screen.findByTestId('offene-auftraege');
        expect(hinweis).toHaveTextContent('Neu, noch nicht eingeplant (1)');
        await user.click(screen.getByRole('button', { name: /Jetzt planen: Tor Hofeinfahrt \(A-2\)/ }));
        expect(await screen.findByRole('heading', { name: 'Auftrag planen' })).toBeInTheDocument();
    });

    it('ohne übersprungene Aufträge gibt es den Hinweis nicht', async () => {
        zeige();
        await screen.findAllByRole('button', { name: FERTIGUNG });
        expect(screen.queryByTestId('offene-auftraege')).not.toBeInTheDocument();
    });

    it('ein Klick auf den Auftrag öffnet ihn komplett; von dort geht es zum Vorgang', async () => {
        const user = userEvent.setup();
        liste[0] = { ...liste[0], kundeId: 12, kundeName: 'Muster GmbH' };
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Auftrag Geländer Musterstraße öffnen' }));

        expect(await screen.findByTestId('auftrag-dialog')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /Kunde: Muster GmbH/ })).toHaveAttribute('href', '/kunden?kundeId=12');
        expect(screen.getByRole('link', { name: /Projekt: Geländer Musterstraße/ })).toHaveAttribute('href', '/projekte?projektId=7');
        await user.click(screen.getByRole('button', { name: 'Fertigung bearbeiten' }));
        expect(await screen.findByTestId('verknuepfungen')).toBeInTheDocument();
        expect(await screen.findByLabelText('Aufwand in Stunden')).toBeInTheDocument();
        expect(screen.queryByTestId('auftrag-dialog')).not.toBeInTheDocument();
    });

    it('„Rückgängig" nimmt die letzte Änderung zurück', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findAllByRole('button', { name: FERTIGUNG });
        const knopf = await screen.findByRole('button', { name: /Rückgängig/ });
        await waitFor(() => expect(knopf).toBeEnabled());
        expect(knopf).toHaveAttribute('title', expect.stringContaining('Aufwand von 16 Stunden auf 32 Stunden'));
        await user.click(knopf);

        await waitFor(() => expect(aufrufe('POST', '/api/plantafel/aenderungen/5/rueckgaengig')).toHaveLength(1));
    });

    it('ohne Änderung gibt es nichts zurückzunehmen', async () => {
        aenderungen = [];
        zeige();
        await screen.findAllByRole('button', { name: FERTIGUNG });
        expect(await screen.findByRole('button', { name: /Rückgängig/ })).toBeDisabled();
    });

    it('die Liste „Änderungen" zeigt die letzten Änderungen mit Benutzer', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findAllByRole('button', { name: FERTIGUNG });
        await user.click(screen.getByRole('button', { name: /Änderungen/ }));

        const liste = await screen.findByTestId('aenderungsliste');
        expect(liste).toHaveTextContent('Aufwand von 16 Stunden auf 32 Stunden');
        expect(liste).toHaveTextContent('anna.buero');
    });

    it('beim Speichern geht der Stand des Vorgangs mit, damit eine fremde Änderung nicht überschrieben wird', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);
        const aufwand = await screen.findByLabelText('Aufwand in Stunden');
        await user.clear(aufwand);
        await user.type(aufwand, '20');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/api/plantafel/vorgaenge/2')).toHaveLength(1));
        expect(JSON.parse((aufrufe('PUT', '/api/plantafel/vorgaenge/2')[0][1] as RequestInit).body as string).stand).toBe(3);
    });

    it('meldet der Server einen Konflikt (409), lädt die Plantafel neu und die Meldung steht im Klartext da', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);
        const vorher = aufrufe('GET', '/api/plantafel/auftraege').length;
        const bisher = fetcher.getMockImplementation() as (u: string, i?: RequestInit) => Promise<Response>;
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => (String(url).includes('/api/plantafel/vorgaenge/2') && init?.method === 'PUT'
            ? json({ message: 'Der Vorgang „Fertigung" wurde inzwischen von jemand anderem geändert.' }, 409)
            : bisher(url, init)));
        const aufwand = await screen.findByLabelText('Aufwand in Stunden');
        await user.clear(aufwand);
        await user.type(aufwand, '20');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText(/inzwischen von jemand anderem geändert/)).toBeInTheDocument();
        await waitFor(() => expect(aufrufe('GET', '/api/plantafel/auftraege').length).toBeGreaterThan(vorher));
    });

    it('die Zeitstufen reichen bis zwei Jahre', async () => {
        zeige();
        await screen.findAllByRole('button', { name: FERTIGUNG });
        const optionen = Array.from((screen.getByLabelText('Zeitraum') as HTMLSelectElement).options).map(o => o.text);
        expect(optionen).toEqual(['7 Tage', '14 Tage', '4 Wochen', '3 Monate', '6 Monate', '1 Jahr', '2 Jahre']);
    });

    it('ohne Aufträge erklärt eine Leermeldung, was zu tun ist', async () => {
        liste = [];
        zeige();
        expect(await screen.findByTestId('plantafel-leer')).toHaveTextContent('Auftrag aufnehmen');
    });

    it('ein Ladefehler steht im Klartext da', async () => {
        fetcher.mockImplementation(async () => json({ message: 'Datenbank nicht erreichbar.' }, 500));
        zeige();
        const meldungen = await screen.findAllByRole('alert');
        expect(meldungen.some(m => m.textContent?.includes('Datenbank nicht erreichbar.'))).toBe(true);
    });

    it('ein Klick auf einen Balken öffnet den Vorgang; die geänderte Dauer wird gespeichert und die Lieferzeit neu angezeigt', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);

        const aufwand = await screen.findByLabelText('Aufwand in Stunden');
        await user.clear(aufwand);
        await user.type(aufwand, '7,5');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/api/plantafel/vorgaenge/2')).toHaveLength(1));
        const body = JSON.parse((aufrufe('PUT', '/api/plantafel/vorgaenge/2')[0][1] as RequestInit).body as string);
        expect(body).toMatchObject({ aufwandStunden: 7.5, dauerTage: null, festerStart: null });
    });

    it('im Vorgang lassen sich Mitarbeiter, Ressource und Wichtigkeit zuweisen; gespeichert wird die Zuweisung', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);

        await user.click(await screen.findByRole('checkbox', { name: 'Max Mustermann' }));
        await user.selectOptions(screen.getByLabelText('Ressource'), 'Werkstatt');
        await user.selectOptions(screen.getByLabelText('Wie wichtig'), 'DRINGEND');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/api/plantafel/vorgaenge/2/zuweisung')).toHaveLength(1));
        expect(JSON.parse((aufrufe('PUT', '/api/plantafel/vorgaenge/2/zuweisung')[0][1] as RequestInit).body as string)).toEqual({ mitarbeiterIds: [4], ressourceId: 8, prioritaet: 'DRINGEND', stand: 3 });
    });

    it('ohne Änderung an der Zuweisung wird keine Zuweisung geschickt', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);
        await screen.findByRole('checkbox', { name: 'Max Mustermann' });
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/api/plantafel/vorgaenge/2')).toHaveLength(1));
        expect(aufrufe('PUT', '/zuweisung')).toHaveLength(0);
    });

    it('ein Vorgang in der Belegung lässt sich von dort neu hinzufügen', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);
        await user.click(await screen.findByRole('button', { name: 'Vorgang hinzufügen' }));

        expect(await screen.findByRole('heading', { name: 'Vorgang hinzufügen' })).toBeInTheDocument();
    });

    it('ein unveränderter Aufwand wird nicht als „von Hand" zurückgeschickt', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);
        await screen.findByLabelText('Aufwand in Stunden');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/api/plantafel/vorgaenge/2')).toHaveLength(1));
        const body = JSON.parse((aufrufe('PUT', '/api/plantafel/vorgaenge/2')[0][1] as RequestInit).body as string);
        expect(body.dauerTage).toBeNull();
        expect(body.aufwandStunden).toBeNull();
    });

    it('ein Wartevorgang hat eine Dauer in Arbeitstagen statt eines Aufwands', async () => {
        const user = userEvent.setup();
        typen[1].wartet = true;
        try {
            zeige();
            await oeffneVorgang(user);
            expect(await screen.findByLabelText('Dauer in Arbeitstagen')).toBeInTheDocument();
            expect(screen.queryByLabelText('Aufwand in Stunden')).not.toBeInTheDocument();
        } finally {
            typen[1].wartet = false;
        }
    });

    it('bei einem Wartevorgang lassen sich Lieferant, Bestelldatum und Liefertermin eintragen; gespeichert wird die Lieferung mit dem Stand', async () => {
        const user = userEvent.setup();
        typen[1].wartet = true;
        liste[0].vorgaenge[1] = { ...liste[0].vorgaenge[1], wartet: true, hinweis: 'übliche Lieferzeit von Stahlhandel Muster: 8 Arbeitstage' };
        try {
            zeige();
            await oeffneVorgang(user);
            expect(await screen.findByTestId('lieferhinweis')).toHaveTextContent('übliche Lieferzeit von Stahlhandel Muster: 8 Arbeitstage');
            await user.selectOptions(await screen.findByLabelText('Lieferant'), 'Stahlhandel Muster (üblich 8 Arbeitstage)');
            await user.click(screen.getByRole('button', { name: 'Speichern' }));

            await waitFor(() => expect(aufrufe('PUT', '/api/plantafel/vorgaenge/2/lieferung')).toHaveLength(1));
            expect(JSON.parse((aufrufe('PUT', '/vorgaenge/2/lieferung')[0][1] as RequestInit).body as string)).toEqual({ lieferantId: 21, bestelltAm: null, lieferterminLieferant: null, stand: 3 });
        } finally {
            typen[1].wartet = false;
        }
    });

    it('ein Arbeitsvorgang hat keinen Abschnitt „Lieferung"', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);
        await screen.findByLabelText('Aufwand in Stunden');
        expect(screen.queryByTestId('lieferung')).not.toBeInTheDocument();
    });

    it('der Vorgang zeigt, wie viel schon gestempelt ist, und der Balken zeigt den Fortschritt', async () => {
        const user = userEvent.setup();
        liste[0].vorgaenge[1] = { ...liste[0].vorgaenge[1], aufwandStunden: 40, istStunden: 10 };
        zeige();
        await oeffneVorgang(user);

        expect(await screen.findByTestId('gestempelt')).toHaveTextContent('Schon gestempelt: 10 von 40 Stunden (noch 30 offen). Nur der Rest wird verplant.');
        expect(screen.getAllByTestId('fortschritt-V2')[0]).toHaveStyle({ width: '25%' });
    });

    it('der Vorgang zeigt, warum er später endet und dass er geschätzt ist', async () => {
        const user = userEvent.setup();
        liste[0].vorgaenge[1] = { ...liste[0].vorgaenge[1], hinweis: 'Max Mustermann: Eilauftrag (dringend) am 13.10.', ohneZuweisungGeschaetzt: true };
        zeige();
        await oeffneVorgang(user);

        expect(await screen.findByTestId('vorgang-hinweis')).toHaveTextContent('Max Mustermann: Eilauftrag (dringend) am 13.10.');
        expect(screen.getByTestId('geschaetzt')).toHaveTextContent('Noch niemand zugewiesen');
    });

    it('beim Öffnen rechnet das Programm die Plantafel neu', async () => {
        zeige();
        await screen.findAllByRole('button', { name: FERTIGUNG });
        expect(aufrufe('POST', '/api/plantafel/neu-berechnen')).toHaveLength(1);
    });

    it('„Frühestens ab" und die Reihenfolge werden gespeichert, wenn sie sich ändern', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);
        await user.type(await screen.findByLabelText('Reihenfolge bei gleicher Wichtigkeit'), '2');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/api/plantafel/vorgaenge/2/rang')).toHaveLength(1));
        expect(JSON.parse((aufrufe('PUT', '/vorgaenge/2/rang')[0][1] as RequestInit).body as string)).toEqual({ rang: 2, stand: 3 });
        expect(aufrufe('PUT', '/vorgaenge/2/fruehestens')).toHaveLength(0);
    });

    it('„Auftrag aufnehmen" legt die Standardkette an und zeigt den neuen Auftrag', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findAllByRole('button', { name: FERTIGUNG });
        const vorher = aufrufe('GET', '/api/plantafel/belegung').length;
        await user.click(screen.getByRole('button', { name: /Auftrag aufnehmen/ }));

        await user.click(await screen.findByRole('button', { name: /Auftrag suchen/ }));
        await user.click(await screen.findByText('Tor Hofeinfahrt'));
        await user.click(screen.getByRole('button', { name: 'Aufnehmen' }));

        await waitFor(() => expect(aufrufe('POST', '/api/plantafel/auftraege/9/standardkette')).toHaveLength(1));
        // die Belegung lädt neu und zeigt den neuen Auftrag
        await waitFor(() => expect(aufrufe('GET', '/api/plantafel/belegung').length).toBeGreaterThan(vorher));
    });

    it('„Mit KI vorschlagen" zeigt die Vorschläge mit Begründung; übernommen wird nur, was angehakt ist, mit der geänderten Dauer', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findAllByRole('button', { name: FERTIGUNG });
        const vorher = aufrufe('GET', '/api/plantafel/belegung').length;
        await user.click(screen.getByRole('button', { name: /Auftrag aufnehmen/ }));
        await user.click(await screen.findByRole('button', { name: /Auftrag suchen/ }));
        await user.click(await screen.findByText('Tor Hofeinfahrt'));
        await user.click(screen.getByRole('button', { name: 'Mit KI vorschlagen' }));

        expect(await screen.findByText('Zeichnung prüfen')).toBeInTheDocument();
        expect(screen.getByText(/Vorgangsart gibt es nicht/)).toBeInTheDocument();
        await user.click(screen.getByRole('checkbox', { name: 'Planung übernehmen' }));
        const dauer = screen.getByLabelText('Dauer von Fertigung in Arbeitstagen');
        await user.clear(dauer);
        await user.type(dauer, '8');
        await user.click(screen.getByRole('button', { name: 'Auswahl übernehmen' }));

        await waitFor(() => expect(aufrufe('POST', '/api/plantafel/ki/auftraege/9/vorgaenge/uebernehmen')).toHaveLength(1));
        expect(JSON.parse((aufrufe('POST', '/vorgaenge/uebernehmen')[0][1] as RequestInit).body as string)).toEqual({ schritte: [{ typId: 2, dauerTage: 8 }] });
        await waitFor(() => expect(aufrufe('GET', '/api/plantafel/belegung').length).toBeGreaterThan(vorher));
    });

    it('die KI erklärt den Termin eines Auftrags', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);
        await user.click(await screen.findByRole('button', { name: 'Termin erklären' }));
        expect(await screen.findByTestId('erklaerung-text')).toHaveTextContent('Die Fertigung bestimmt den Termin.');
    });

    it('ein Fehler der KI steht im Klartext da', async () => {
        kiFehler = true;
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);
        await user.click(await screen.findByRole('button', { name: 'Termin erklären' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Für diese Funktion ist kein Schlüssel hinterlegt.');
    });

    it('„Dauern aus dem Angebot vorschlagen" zeigt Stunden und Rechnung; übernommen wird nur das Gewählte', async () => {
        const user = userEvent.setup();
        zeige();
        await oeffneVorgang(user);
        await user.click(await screen.findByRole('button', { name: 'Dauern aus dem Angebot' }));

        const fertigung = await screen.findByTestId('dauer-2');
        expect(fertigung).toHaveTextContent('3 Tg.');
        expect(fertigung).toHaveTextContent('50 Std. Werkstatt ÷ 24 Std. pro Tag');
        expect(screen.getByTestId('dauer-1')).toHaveTextContent('keine Abteilung eingestellt');
        expect(screen.getByRole('checkbox', { name: 'Planung übernehmen' })).toBeDisabled();
        expect(screen.getByRole('checkbox', { name: 'Fertigung übernehmen' })).toBeChecked();

        await user.click(screen.getByRole('button', { name: 'Auswahl übernehmen' }));

        await waitFor(() => expect(aufrufe('POST', '/api/plantafel/auftraege/7/vorschlag/uebernehmen')).toHaveLength(1));
        expect(JSON.parse((aufrufe('POST', '/vorschlag/uebernehmen')[0][1] as RequestInit).body as string)).toEqual({ vorgangIds: [2] });
        await oeffneVorgang(user);
        expect(await screen.findByTestId('auftrag-aktionen')).toHaveTextContent('Lieferzeit 14.10.2026');
    });

    it('in den Vorgangsarten lassen sich Abteilung und Stunden pro Arbeitstag einstellen', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findAllByRole('button', { name: FERTIGUNG });
        await user.click(screen.getByRole('button', { name: /Vorgangsarten/ }));

        await user.selectOptions(await screen.findByLabelText('Abteilung für Fertigung'), 'Werkstatt');
        await user.click(screen.getByRole('button', { name: 'Fertigung speichern' }));
        await waitFor(() => expect(aufrufe('PUT', '/api/plantafel/vorgangstypen/2')).toHaveLength(1));
        expect(JSON.parse((aufrufe('PUT', '/api/plantafel/vorgangstypen/2')[0][1] as RequestInit).body as string)).toMatchObject({ abteilungId: 5, stundenProTag: null, wartet: false, buero: false });

        await user.type(screen.getByLabelText('Stunden pro Arbeitstag für Fertigung'), '24');
        await user.click(screen.getByLabelText('Fertigung ist ein Wartevorgang'));
        await user.click(screen.getByRole('button', { name: 'Fertigung speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/api/plantafel/vorgangstypen/2')).toHaveLength(2));
        expect(JSON.parse((aufrufe('PUT', '/api/plantafel/vorgangstypen/2')[1][1] as RequestInit).body as string)).toMatchObject({ abteilungId: 5, stundenProTag: 24, wartet: true });
    });
});
