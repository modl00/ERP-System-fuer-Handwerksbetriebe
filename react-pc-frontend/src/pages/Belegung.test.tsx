import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Belegung } from '../features/plantafel/Belegung';
import { platziere } from '../features/plantafel/belegungLayout';
import type { Belegung as Daten, BelegungEintrag } from '../features/plantafel/plantafelApi';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

/** Nur Dummy-Daten. */
const eintrag = (id: string, titel: string, von: string, bis: string, rest: Partial<BelegungEintrag> = {}): BelegungEintrag => ({
    id, quelle: 'VORGANG', titel, untertitel: 'Geländer (A-1)', von, bis, vonZeit: null, bisZeit: null, farbe: '#be123c', projektId: 7, prioritaet: 'NORMAL', fest: false, status: 'GEPLANT', hinweis: null, ...rest,
});

const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
let fetcher: ReturnType<typeof vi.fn>;

const daten = (von: string, bis: string): Daten => ({
    von, bis, zeilen: [
        { schluessel: 'A', gruppe: 'ALLGEMEIN', name: 'Ohne Zuweisung', eintraege: [eintrag('V9', 'Planung', von, von, { projektId: 9 })] },
        {
            schluessel: 'M4', gruppe: 'MITARBEITER', name: 'Max Mustermann', eintraege: [
                eintrag('V2', 'Fertigung', von, von, { hinweis: 'Doppelt belegt mit „Tor" (Tor (A-2))', fest: true, prioritaet: 'DRINGEND' }),
                eintrag('T5', 'Aufmaß', von, von, { quelle: 'TERMIN', vonZeit: '08:30:00', bisZeit: '10:00:00', farbe: '#64748b', projektId: null, prioritaet: null, status: null }),
                eintrag('A1', 'Urlaub', von, von, { quelle: 'ABWESENHEIT', untertitel: null, farbe: '#94a3b8', projektId: null, prioritaet: null, status: null }),
            ],
        },
        { schluessel: 'R8', gruppe: 'RESSOURCE', name: 'Werkstatt', eintraege: [] },
    ],
});

const mitProvidern = (inhalt: ReactElement) => <ToastProvider><ConfirmProvider>{inhalt}</ConfirmProvider></ToastProvider>;
const zeige = (inhalt: ReactElement) => render(mitProvidern(inhalt));
const belegungsAufrufe = () => fetcher.mock.calls.filter(([u]) => String(u).includes('/api/plantafel/belegung'));
const aenderungen = () => fetcher.mock.calls.filter(([, init]) => init?.method === 'PUT');

beforeEach(() => {
    fetcher = vi.fn(async (url: string) => {
        const u = new URL(String(url), 'http://x');
        if (u.pathname === '/api/plantafel/belegung') return json(daten(u.searchParams.get('von') ?? '', u.searchParams.get('bis') ?? ''));
        return json({ id: 1 });
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('Belegung', { timeout: 30000 }, () => {
    it('zeigt Mitarbeiter und Ressourcen als Zeilen mit Vorgang, Termin und Abwesenheit', async () => {
        zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);

        const zeile = await screen.findByTestId('zeile-M4');
        expect(within(zeile).getByText('Max Mustermann')).toBeInTheDocument();
        expect(within(zeile).getByRole('button', { name: /^Fertigung, Geländer \(A-1\)/ })).toBeInTheDocument();
        expect(within(zeile).getByRole('button', { name: /^Aufmaß.*08:30 bis 10:00/ })).toBeInTheDocument();
        expect(within(zeile).getByRole('img', { name: /^Urlaub/ })).toBeInTheDocument();
        expect(screen.getByTestId('zeile-R8')).toHaveTextContent('Werkstatt');
        expect(screen.getByTestId('zeile-A')).toHaveTextContent('Ohne Zuweisung');
        expect(screen.getByRole('button', { name: 'Allgemein' })).toBeInTheDocument();
    });

    it('die Kopfzeile zeigt Wochentag, Datum, Kalenderwoche und die Stunden', async () => {
        zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);

        const gitter = await screen.findByTestId('belegung-gitter');
        expect(gitter).toHaveTextContent(/Mo\., \d+\.\d+\. KW \d+/);
        expect(within(gitter).getAllByText('08')).toHaveLength(84);
        expect(within(gitter).getAllByText('14')).toHaveLength(84);
    });

    it('eine Gruppe lässt sich zu- und aufklappen', async () => {
        const user = userEvent.setup();
        zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);
        await screen.findByTestId('zeile-M4');

        await user.click(screen.getByRole('button', { name: 'Mitarbeiter' }));
        expect(screen.queryByTestId('zeile-M4')).toBeNull();
        expect(screen.getByRole('button', { name: 'Mitarbeiter' })).toHaveAttribute('aria-expanded', 'false');

        await user.click(screen.getByRole('button', { name: 'Mitarbeiter' }));
        expect(screen.getByTestId('zeile-M4')).toBeInTheDocument();
    });

    it('der Hinweis „doppelt belegt" steht am Vorgang', async () => {
        zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);

        const balken = await screen.findByRole('button', { name: /Achtung: Doppelt belegt mit „Tor"/ });
        expect(balken).toBeInTheDocument();
    });

    it('ein Klick auf einen Vorgang meldet Auftrag und Vorgang', async () => {
        const onVorgang = vi.fn();
        const user = userEvent.setup();
        zeige(<Belegung aktualisierung={0} onVorgang={onVorgang} />);

        await user.click(await screen.findByRole('button', { name: /^Fertigung/ }));

        expect(onVorgang).toHaveBeenCalledWith(7, 2);
        expect(aenderungen()).toHaveLength(0);
    });

    it('lädt ein Fenster von 12 Wochen, das mit einem Montag beginnt und mit einem Sonntag endet; die Pfeile und der Zoom laden nichts neu', async () => {
        const user = userEvent.setup();
        zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);
        await screen.findByTestId('belegung');
        const erster = new URL(String(fetcher.mock.calls[0][0]), 'http://x').searchParams;
        expect(new Date(erster.get('von') + 'T00:00:00').getDay()).toBe(1);
        expect(new Date(erster.get('bis') + 'T00:00:00').getDay()).toBe(0);
        expect(Math.round((new Date(erster.get('bis') + 'T00:00:00').getTime() - new Date(erster.get('von') + 'T00:00:00').getTime()) / 86_400_000)).toBe(83);

        await user.click(screen.getByRole('button', { name: 'Zeitraum vor' }));
        await user.selectOptions(screen.getByRole('combobox', { name: 'Zeitraum' }), '4 Wochen');

        expect(belegungsAufrufe()).toHaveLength(1);
        expect(screen.getByTestId('belegung-gitter')).toHaveStyle({ width: '5376px' }); // 84 Tage zu je 64 Pixel
    });

    it('am linken Rand lädt die Ansicht vier Wochen davor nach, am rechten vier Wochen danach', async () => {
        zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);
        const tafel = await screen.findByTestId('belegung');
        const erster = new URL(String(fetcher.mock.calls[0][0]), 'http://x').searchParams;
        const tag = (iso: string, n: number) => { const d = new Date(iso + 'T00:00:00'); d.setDate(d.getDate() + n); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); };
        const mass = (links: number) => {
            Object.defineProperty(tafel, 'scrollLeft', { configurable: true, writable: true, value: links });
            Object.defineProperty(tafel, 'scrollWidth', { configurable: true, value: 13000 });
            Object.defineProperty(tafel, 'clientWidth', { configurable: true, value: 1000 });
        };

        mass(100);
        fireEvent.scroll(tafel);
        await waitFor(() => expect(belegungsAufrufe()).toHaveLength(2));
        const links = new URL(String(belegungsAufrufe()[1][0]), 'http://x').searchParams;
        expect(links.get('von')).toBe(tag(erster.get('von') as string, -28));
        expect(links.get('bis')).toBe(erster.get('bis'));

        mass(13000 - 1000 - 100);
        fireEvent.scroll(tafel);
        await waitFor(() => expect(belegungsAufrufe()).toHaveLength(3));
        const rechts = new URL(String(belegungsAufrufe()[2][0]), 'http://x').searchParams;
        expect(rechts.get('von')).toBe(links.get('von'));
        expect(rechts.get('bis')).toBe(tag(links.get('bis') as string, 28));
    });

    it('mitten in der Ansicht wird nichts nachgeladen', async () => {
        zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);
        const tafel = await screen.findByTestId('belegung');
        Object.defineProperty(tafel, 'scrollLeft', { configurable: true, writable: true, value: 5000 });
        Object.defineProperty(tafel, 'scrollWidth', { configurable: true, value: 13000 });
        Object.defineProperty(tafel, 'clientWidth', { configurable: true, value: 1000 });

        fireEvent.scroll(tafel);

        expect(belegungsAufrufe()).toHaveLength(1);
    });

    it('eine Änderung (aktualisierung) lädt neu', async () => {
        const { rerender } = zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);
        await screen.findByTestId('belegung');

        rerender(mitProvidern(<Belegung aktualisierung={1} onVorgang={() => undefined} />));

        await waitFor(() => expect(belegungsAufrufe()).toHaveLength(2));
    });

    it('Fehler vom Server stehen als Klartext da', async () => {
        fetcher.mockImplementation(async () => json({ message: 'Der Zeitraum darf höchstens 62 Tage lang sein.' }, 400));
        zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);

        expect(await screen.findByRole('alert')).toHaveTextContent('Der Zeitraum darf höchstens 62 Tage lang sein.');
    });
});

/** Termine anlegen: Ein Klick auf einen Tag, ein Aufziehen über mehrere Tage, ein Klick auf einen Termin. Ohne Messung ist ein Tag 150 Pixel breit. */
describe('Belegung – Termine', { timeout: 30000 }, () => {
    const TAG = 150;
    const ersterTag = () => new URL(String(fetcher.mock.calls[0][0]), 'http://x').searchParams.get('von') as string;
    const tagN = (n: number) => { const d = new Date(ersterTag() + 'T00:00:00'); d.setDate(d.getDate() + n); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); };
    const kalender = () => fetcher.mock.calls.filter(([u, i]) => String(u).startsWith('/api/kalender') && i?.method);

    beforeEach(() => {
        const basis = fetcher.getMockImplementation() as (url: string, init?: RequestInit) => Promise<Response>;
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
            const u = String(url);
            if (u === '/api/plantafel/personen') return json([{ id: 3, name: 'Erika Musterfrau' }, { id: 4, name: 'Max Mustermann' }]);
            if (u === '/api/kalender/5' && !init?.method) {
                return json({ id: 5, titel: 'Aufmaß', beschreibung: null, datum: ersterTag(), datumBis: null, startZeit: '08:30:00', endeZeit: '10:00:00', ganztaegig: false, farbe: null,
                    projektId: 7, kundeId: null, lieferantId: null, anfrageId: null, teilnehmer: [{ id: 4, name: 'Max Mustermann' }] });
            }
            if (u.startsWith('/api/kalender') && init?.method) return json({ id: 99 });
            return basis(url, init);
        });
    });

    const druecke = (el: HTMLElement, vonTag: number, bisTag: number) => {
        fireEvent.pointerDown(el, { pointerId: 1, button: 0, clientX: vonTag * TAG + 10, clientY: 10 });
        fireEvent.pointerMove(el, { pointerId: 1, clientX: bisTag * TAG + 10, clientY: 10 });
        fireEvent.pointerUp(el, { pointerId: 1, clientX: bisTag * TAG + 10, clientY: 10 });
    };

    it('ein Klick auf einen Tag in der Zeile eines Mitarbeiters legt einen Termin für diesen Tag und diesen Mitarbeiter an', async () => {
        const user = userEvent.setup();
        zeige(<Belegung bearbeiten aktualisierung={0} onVorgang={() => undefined} />);
        const tage = await screen.findByTestId('tage-M4');

        druecke(tage, 2, 2);

        expect(await screen.findByRole('heading', { name: 'Termin anlegen' })).toBeInTheDocument();
        expect(await screen.findByRole('checkbox', { name: 'Max Mustermann' })).toBeChecked();
        expect(screen.getByRole('checkbox', { name: 'Erika Musterfrau' })).not.toBeChecked();
        await user.type(screen.getByLabelText('Titel'), 'Aufmaß Familie Muster');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(kalender()).toHaveLength(1));
        expect(kalender()[0][1]?.method).toBe('POST');
        expect(JSON.parse(String(kalender()[0][1]?.body))).toMatchObject({
            titel: 'Aufmaß Familie Muster', datum: tagN(2), datumBis: tagN(2), ganztaegig: false, startZeit: '08:00', endeZeit: '09:00', teilnehmerIds: [4],
        });
        await waitFor(() => expect(belegungsAufrufe()).toHaveLength(2));
    });

    it('über mehrere Tage aufgezogen entsteht ein durchgehender, ganztägiger Termin über alle gewählten Tage', async () => {
        const user = userEvent.setup();
        zeige(<Belegung bearbeiten aktualisierung={0} onVorgang={() => undefined} />);
        const tage = await screen.findByTestId('tage-M4');

        druecke(tage, 3, 5);

        expect(await screen.findByRole('heading', { name: 'Termin anlegen' })).toBeInTheDocument();
        expect(screen.getByLabelText('Ganztägig (ein Termin über mehrere Tage ist immer ganztägig)')).toBeChecked();
        expect(screen.queryByLabelText('Beginn')).toBeNull();
        await user.type(screen.getByLabelText('Titel'), 'Messe');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(kalender()).toHaveLength(1));
        expect(JSON.parse(String(kalender()[0][1]?.body))).toMatchObject({ datum: tagN(3), datumBis: tagN(5), ganztaegig: true, startZeit: null, endeZeit: null, teilnehmerIds: [4] });
    });

    it('von rechts nach links aufgezogen gilt dieselbe Spanne', async () => {
        const user = userEvent.setup();
        zeige(<Belegung bearbeiten aktualisierung={0} onVorgang={() => undefined} />);
        const tage = await screen.findByTestId('tage-M4');

        druecke(tage, 5, 3);

        await user.type(await screen.findByLabelText('Titel'), 'Messe');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(kalender()).toHaveLength(1));
        expect(JSON.parse(String(kalender()[0][1]?.body))).toMatchObject({ datum: tagN(3), datumBis: tagN(5) });
    });

    it('beim Aufziehen zeigt eine Markierung die gewählten Tage; ein Termin ohne Titel wird nicht gespeichert', async () => {
        const user = userEvent.setup();
        zeige(<Belegung bearbeiten aktualisierung={0} onVorgang={() => undefined} />);
        const tage = await screen.findByTestId('tage-M4');

        fireEvent.pointerDown(tage, { pointerId: 1, button: 0, clientX: 3 * TAG + 10, clientY: 10 });
        fireEvent.pointerMove(tage, { pointerId: 1, clientX: 5 * TAG + 10, clientY: 10 });
        expect(screen.getByTestId('tage-auswahl')).toBeInTheDocument();
        fireEvent.pointerUp(tage, { pointerId: 1, clientX: 5 * TAG + 10, clientY: 10 });
        expect(screen.queryByTestId('tage-auswahl')).toBeNull();

        await user.click(await screen.findByRole('button', { name: 'Speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Bitte einen Titel eingeben.');
        expect(kalender()).toHaveLength(0);
    });

    it('in der Zeile einer Ressource lässt sich kein Termin anlegen', async () => {
        zeige(<Belegung bearbeiten aktualisierung={0} onVorgang={() => undefined} />);
        await screen.findByTestId('zeile-R8');

        expect(screen.queryByTestId('tage-R8')?.className).not.toContain('cursor-cell');
        fireEvent.pointerDown(screen.getByTestId('tage-R8'), { pointerId: 1, button: 0, clientX: 10, clientY: 10 });
        fireEvent.pointerUp(screen.getByTestId('tage-R8'), { pointerId: 1, clientX: 10, clientY: 10 });

        expect(screen.queryByRole('heading', { name: 'Termin anlegen' })).toBeNull();
    });

    it('ein Klick auf einen Termin öffnet ihn zum Ändern; Löschen fragt nach', async () => {
        const user = userEvent.setup();
        zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);

        await user.click(await screen.findByRole('button', { name: /^Aufmaß/ }));

        expect(await screen.findByRole('heading', { name: 'Termin bearbeiten' })).toBeInTheDocument();
        expect(await screen.findByLabelText('Titel')).toHaveValue('Aufmaß');
        await user.click(screen.getByRole('button', { name: /Löschen/ }));
        await user.click(await screen.findByRole('button', { name: 'Termin löschen' }));
        await waitFor(() => expect(kalender().some(([, i]) => i?.method === 'DELETE')).toBe(true));
    });

    it('der Knopf „Termin" legt einen Termin für heute ohne Mitarbeiter an', async () => {
        const user = userEvent.setup();
        zeige(<Belegung aktualisierung={0} onVorgang={() => undefined} />);
        await screen.findByTestId('belegung');

        await user.click(screen.getByRole('button', { name: 'Termin' }));

        expect(await screen.findByRole('heading', { name: 'Termin anlegen' })).toBeInTheDocument();
        expect(await screen.findByText('Ohne Auswahl ist es ein Termin der Firma.')).toBeInTheDocument();
    });
});

/** Ziehen mit der Maus: Die Tafel ist 8400 Pixel breit (84 Tage zu 100 Pixel), die Zeilen sind je 100 Pixel hoch. */
describe('Belegung – Ziehen', { timeout: 30000 }, () => {
    const ZEILEN = ['A', 'M4', 'R8'];

    beforeEach(() => {
        vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
            if (this.dataset.testid === 'belegung-gitter') return { width: 8400, height: 40, top: 0, left: 200, right: 8600, bottom: 40, x: 200, y: 0, toJSON: () => ({}) };
            const nr = this.dataset.zeile ? ZEILEN.indexOf(this.dataset.zeile) : -1;
            if (nr >= 0) return { width: 900, height: 100, top: 100 + nr * 100, left: 0, right: 900, bottom: 200 + nr * 100, x: 0, y: 100 + nr * 100, toJSON: () => ({}) };
            return { width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0, x: 0, y: 0, toJSON: () => ({}) };
        });
    });

    const ziehe = (el: HTMLElement, dx: number, y: number) => {
        fireEvent.pointerDown(el, { pointerId: 1, button: 0, clientX: 300, clientY: 250 });
        fireEvent.pointerMove(el, { pointerId: 1, clientX: 300 + dx / 2, clientY: 250 });
        fireEvent.pointerMove(el, { pointerId: 1, clientX: 300 + dx, clientY: y });
        fireEvent.pointerUp(el, { pointerId: 1, clientX: 300 + dx, clientY: y });
        fireEvent.click(el);
    };
    const montag = () => new URL(String(fetcher.mock.calls[0][0]), 'http://x').searchParams.get('von') as string;
    const naechsterTag = (iso: string, n = 1) => {
        const d = new Date(iso + 'T00:00:00');
        d.setDate(d.getDate() + n);
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    };
    const koerper = (nr: number) => JSON.parse(String(aenderungen()[nr][1]?.body));

    it('ein Vorgang auf den nächsten Tag gezogen bekommt einen frühesten Start und lädt neu', async () => {
        const onVorgang = vi.fn();
        zeige(<Belegung bearbeiten aktualisierung={0} onVorgang={onVorgang} />);
        const balken = await screen.findByRole('button', { name: /^Planung/ });

        ziehe(balken, 100, 150);

        await waitFor(() => expect(aenderungen()).toHaveLength(1));
        expect(String(aenderungen()[0][0])).toBe('/api/plantafel/vorgaenge/9/fruehestens');
        expect(koerper(0)).toEqual({ datum: naechsterTag(montag()), stand: null });
        await waitFor(() => expect(belegungsAufrufe()).toHaveLength(2));
        expect(onVorgang).not.toHaveBeenCalled();
    });

    it('ein Fixtermin wird erst nach Rückfrage verschoben, bei „Abbrechen" passiert nichts', async () => {
        const user = userEvent.setup();
        zeige(<Belegung bearbeiten aktualisierung={0} onVorgang={() => undefined} />);
        const balken = await screen.findByRole('button', { name: /^Fertigung/ });

        ziehe(balken, 200, 250);
        expect(await screen.findByText(/hat einen festen Start/)).toBeInTheDocument();
        expect(aenderungen()).toHaveLength(0);
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(screen.queryByText(/hat einen festen Start/)).toBeNull());
        expect(aenderungen()).toHaveLength(0);

        ziehe(screen.getByRole('button', { name: /^Fertigung/ }), 200, 250);
        await user.click(await screen.findByRole('button', { name: 'Verschieben' }));
        await waitFor(() => expect(aenderungen()).toHaveLength(1));
        expect(koerper(0)).toEqual({ festerStart: naechsterTag(montag(), 2) });
    });

    it('in die Zeile einer Ressource gezogen wird sie zugewiesen, die Person bleibt, der Tag bleibt', async () => {
        zeige(<Belegung bearbeiten aktualisierung={0} onVorgang={() => undefined} />);
        const balken = await screen.findByRole('button', { name: /^Fertigung/ });

        ziehe(balken, 0, 350);

        await waitFor(() => expect(aenderungen()).toHaveLength(1));
        expect(String(aenderungen()[0][0])).toBe('/api/plantafel/vorgaenge/2/zuweisung');
        expect(koerper(0)).toEqual({ mitarbeiterIds: [4], ressourceId: 8, prioritaet: null, stand: null });
    });

    it('ein Termin mit Uhrzeit rastet auf halbe Stunden ein und ändert nichts anderes', async () => {
        zeige(<Belegung bearbeiten aktualisierung={0} onVorgang={() => undefined} />);
        const termin = await screen.findByRole('button', { name: /^Aufmaß/ });

        ziehe(termin, 100 + 100 / 18, 250); // ein Tag und eine halbe Stunde

        await waitFor(() => expect(aenderungen()).toHaveLength(1));
        expect(String(aenderungen()[0][0])).toBe('/api/plantafel/termine/5/verschieben');
        expect(koerper(0)).toEqual({ datum: naechsterTag(montag()), von: '09:00', bis: '10:30', vonMitarbeiterId: null, nachMitarbeiterId: null });
    });

    it('eine kleine Bewegung bleibt ein Klick, eine Abwesenheit lässt sich nicht ziehen', async () => {
        const onVorgang = vi.fn();
        zeige(<Belegung bearbeiten aktualisierung={0} onVorgang={onVorgang} />);
        const balken = await screen.findByRole('button', { name: /^Fertigung/ });

        fireEvent.pointerDown(balken, { pointerId: 1, button: 0, clientX: 300, clientY: 250 });
        fireEvent.pointerMove(balken, { pointerId: 1, clientX: 302, clientY: 251 });
        fireEvent.pointerUp(balken, { pointerId: 1, clientX: 302, clientY: 251 });
        fireEvent.click(balken);

        expect(onVorgang).toHaveBeenCalledWith(7, 2);
        expect(aenderungen()).toHaveLength(0);
        expect(screen.getByRole('img', { name: /^Urlaub/ })).not.toHaveAttribute('draggable', 'true');
    });
});

describe('platziere', () => {
    const start = new Date(2026, 9, 12); // Montag

    it('Einträge, die sich berühren, kommen in verschiedene Bahnen', () => {
        const { platziert, bahnen } = platziere([eintrag('V1', 'A', '2026-10-12', '2026-10-14'), eintrag('V2', 'B', '2026-10-13', '2026-10-13'), eintrag('V3', 'C', '2026-10-15', '2026-10-16')], start, 7);

        expect(bahnen).toBe(2);
        expect(platziert.find(p => p.eintrag.id === 'V1')?.bahn).toBe(0);
        expect(platziert.find(p => p.eintrag.id === 'V2')?.bahn).toBe(1);
        expect(platziert.find(p => p.eintrag.id === 'V3')?.bahn).toBe(0);
    });

    it('schneidet am sichtbaren Zeitraum ab und lässt Einträge außerhalb weg', () => {
        const { platziert } = platziere([eintrag('V1', 'A', '2026-10-08', '2026-10-13'), eintrag('V2', 'B', '2026-10-30', '2026-11-02'), eintrag('V3', 'C', '2026-10-17', '2026-10-25')], start, 7);

        expect(platziert.map(p => [p.eintrag.id, p.erster, p.letzter])).toEqual([['V1', 0, 1], ['V3', 5, 6]]);
        expect(platziert.map(p => [p.von, p.bis])).toEqual([[0, 2], [5, 7]]);
    });

    it('Termine mit Uhrzeit stehen genau im Tag, und zwei zu verschiedenen Zeiten teilen sich eine Bahn', () => {
        const termin = (id: string, von: string, bis: string | null) => eintrag(id, id, '2026-10-13', '2026-10-13', { quelle: 'TERMIN', vonZeit: von, bisZeit: bis });
        const { platziert, bahnen } = platziere([termin('T1', '08:00:00', '10:00:00'), termin('T2', '13:00:00', null), termin('T3', '09:00:00', '12:00:00')], start, 7);

        expect(bahnen).toBe(2);
        const t1 = platziert.find(p => p.eintrag.id === 'T1');
        expect(t1?.von).toBeCloseTo(1, 5);
        expect(t1?.bis).toBeCloseTo(1 + 2 / 9, 5);
        expect(platziert.find(p => p.eintrag.id === 'T2')?.bahn).toBe(0);
        expect(platziert.find(p => p.eintrag.id === 'T3')?.bahn).toBe(1);
    });
});
