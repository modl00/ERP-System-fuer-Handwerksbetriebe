import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import LagerEditor from './LagerEditor';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import type { LagerBestand, Lagerort } from '../features/lager/lagerApi';

/** Nur Dummy-Daten (DSGVO). Gerechnet wird im Backend, hier zählen Anzeige, Eingabeprüfung und die richtigen Aufrufe. */

const ORTE: Lagerort[] = [
    { id: 1, name: 'Hauptlager', beschreibung: 'Halle', aktiv: true, sortOrder: 0 },
    { id: 2, name: 'Montagefahrzeug', beschreibung: null, aktiv: true, sortOrder: 1 },
];

const ZEILEN: LagerBestand[] = [
    { artikelId: 5, bezeichnung: 'Pfosten verzinkt', artikelnummer: 'A-100', einheit: 'Stk', bestand: 4, mindestbestand: 10, unterMindestbestand: true, negativ: false,
        orte: [{ lagerortId: 1, lagerortName: 'Hauptlager', bestand: 4 }] },
    { artikelId: 6, bezeichnung: 'Rundrohr 42,4', artikelnummer: null, einheit: 'm', bestand: -2.5, mindestbestand: null, unterMindestbestand: false, negativ: true,
        orte: [{ lagerortId: 2, lagerortName: 'Montagefahrzeug', bestand: -2.5 }] },
    { artikelId: 7, bezeichnung: 'Schrauben M8', artikelnummer: 'S-8', einheit: 'Stk', bestand: 500, mindestbestand: 100, unterMindestbestand: false, negativ: false,
        orte: [{ lagerortId: 1, lagerortName: 'Hauptlager', bestand: 500 }] },
];

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let zeilen: LagerBestand[];

const aufrufe = (methode: string, teil: string) =>
    fetcher.mock.calls.filter(([url, init]) => String(url).includes(teil) && (init?.method ?? 'GET') === methode);

beforeEach(() => {
    zeilen = ZEILEN;
    fetcher = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (url.startsWith('/api/lager/bestand')) {
            const nurBedarf = url.includes('nurHandlungsbedarf=true');
            return antwort(nurBedarf ? zeilen.filter(z => z.unterMindestbestand || z.negativ) : zeilen);
        }
        if (url === '/api/lager/zusammenfassung') return antwort({ anzahlArtikel: zeilen.length, unterMindestbestand: 1, negativ: 1 });
        if (url === '/api/lager/orte' && methode === 'GET') return antwort(ORTE);
        if (url === '/api/lager/orte' && methode === 'POST') return antwort({ id: 3, name: 'Regal 7', beschreibung: null, aktiv: true, sortOrder: 2 });
        if (url.endsWith('/aktiv')) return antwort({ ...ORTE[1], aktiv: false });
        if (url === '/api/lager/buchungen' && methode === 'POST') return antwort({ ...ZEILEN[0], bestand: 9.5 });
        if (url === '/api/lager/umbuchungen' && methode === 'POST') return antwort({ ...ZEILEN[0] });
        if (url === '/api/lager/artikel/5' && methode === 'PUT') return antwort({ ...ZEILEN[0], bezeichnung: 'Pfosten 60x60', einheit: 'm' });
        if (url.includes('/buchungen') && methode === 'GET') return antwort([]);
        return antwort([]);
    });
    vi.stubGlobal('fetch', fetcher);
});

afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<MemoryRouter><ToastProvider><ConfirmProvider><LagerEditor /></ConfirmProvider></ToastProvider></MemoryRouter>);

it('zeigt Bestand, Mindestbestand, Lagerorte und markiert Handlungsbedarf', async () => {
    zeige();

    const pfosten = await screen.findByTestId('lager-zeile-5');
    expect(pfosten).toHaveTextContent('Pfosten verzinkt');
    expect(pfosten).toHaveTextContent('4 Stk');
    expect(pfosten).toHaveTextContent('10 Stk');
    expect(pfosten).toHaveTextContent('unter Mindestbestand: es fehlen 6 Stk');
    expect(pfosten).toHaveTextContent('Hauptlager: 4 Stk');

    const rohr = screen.getByTestId('lager-zeile-6');
    expect(rohr).toHaveTextContent('-2,5 m');
    expect(rohr).toHaveTextContent('Bestand fehlt: Zugang oder Inventur buchen');

    expect(screen.getByTestId('lager-zeile-7')).not.toHaveTextContent('unter Mindestbestand');
    expect(screen.getByTestId('filter-bedarf')).toHaveTextContent('(2)');
});

it('filtert auf Handlungsbedarf und sucht nach einer kurzen Pause', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('lager-zeile-7');

    await user.click(screen.getByTestId('filter-bedarf'));
    await waitFor(() => expect(screen.queryByTestId('lager-zeile-7')).not.toBeInTheDocument());
    expect(screen.getByTestId('lager-zeile-5')).toBeVisible();
    expect(aufrufe('GET', 'nurHandlungsbedarf=true').length).toBeGreaterThan(0);

    await user.type(screen.getByRole('textbox', { name: 'Lagerbestand durchsuchen' }), 'pfost');
    await waitFor(() => expect(aufrufe('GET', 'q=pfost').length).toBeGreaterThan(0));
});

it('bucht einen Zugang aus der Zeile mit Komma-Menge und lädt die Liste neu', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('lager-zeile-5');
    const vorher = aufrufe('GET', '/api/lager/bestand').length;

    await user.click(screen.getByRole('button', { name: 'Zugang für Pfosten verzinkt buchen' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByTestId('lager-artikel')).toHaveTextContent('Pfosten verzinkt');
    await user.type(within(dialog).getByRole('textbox', { name: /Menge \(eingegangen\)/ }), '5,5');
    await user.type(within(dialog).getByPlaceholderText('z. B. Lieferschein-Nr.'), 'Lieferschein 4711');
    await user.click(within(dialog).getByRole('button', { name: 'Zugang buchen' }));

    await waitFor(() => expect(aufrufe('POST', '/api/lager/buchungen')).toHaveLength(1));
    expect(JSON.parse(aufrufe('POST', '/api/lager/buchungen')[0][1].body)).toEqual({
        art: 'ZUGANG', artikelId: 5, lagerortId: 1, menge: 5.5, projektId: null, notiz: 'Lieferschein 4711',
    });
    expect(await screen.findByText(/Zugang gebucht\. Bestand: 9,5 Stk/)).toBeVisible();
    await waitFor(() => expect(aufrufe('GET', '/api/lager/bestand').length).toBeGreaterThan(vorher));
});

it('lehnt leere, unvollständige und nicht positive Mengen vor dem Buchen ab', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('lager-zeile-5');
    await user.click(screen.getByRole('button', { name: 'Entnahme für Pfosten verzinkt buchen' }));
    const dialog = await screen.findByRole('dialog');
    const menge = within(dialog).getByRole('textbox', { name: /Menge \(entnommen\)/ });
    const buchen = within(dialog).getByRole('button', { name: 'Entnahme buchen' });

    await user.click(buchen);
    expect(await within(dialog).findByText(/Bitte Menge \(entnommen\) eingeben/)).toBeVisible();

    await user.type(menge, '3,');
    await user.click(buchen);
    expect(await within(dialog).findByText(/vollständige Zahl mit Dezimalkomma/)).toBeVisible();

    await user.clear(menge);
    await user.type(menge, '0');
    await user.click(buchen);
    expect(await within(dialog).findByText(/mindestens/)).toBeVisible();

    expect(aufrufe('POST', '/api/lager/buchungen')).toHaveLength(0);
});

it('zeigt bei der Inventur den aktuell gebuchten Bestand und erlaubt 0', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('lager-zeile-5');

    await user.click(screen.getByRole('button', { name: 'Inventur für Pfosten verzinkt buchen' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByText(/Aktuell gebucht: 4 Stk/)).toBeVisible();
    await user.type(within(dialog).getByRole('textbox', { name: /Gezählter Bestand/ }), '0');
    await user.click(within(dialog).getByRole('button', { name: 'Inventur buchen' }));
    await waitFor(() => expect(aufrufe('POST', '/api/lager/buchungen')).toHaveLength(1));
    expect(JSON.parse(aufrufe('POST', '/api/lager/buchungen')[0][1].body)).toMatchObject({ art: 'KORREKTUR', menge: 0 });
});

it('zeigt eine Fehlermeldung des Servers im Dialog und schließt ihn nicht', async () => {
    const user = userEvent.setup();
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        if (url === '/api/lager/buchungen' && init?.method === 'POST') return antwort({ message: 'Der Lagerort "Hauptlager" ist nicht mehr aktiv.' }, false, 400);
        if (url.startsWith('/api/lager/bestand')) return antwort(ZEILEN);
        if (url === '/api/lager/orte') return antwort(ORTE);
        return antwort({ anzahlArtikel: 3, unterMindestbestand: 1, negativ: 1 });
    });
    zeige();
    await screen.findByTestId('lager-zeile-5');
    await user.click(screen.getByRole('button', { name: 'Zugang für Pfosten verzinkt buchen' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByRole('textbox', { name: /Menge \(eingegangen\)/ }), '2');

    await user.click(within(dialog).getByRole('button', { name: 'Zugang buchen' }));

    expect((await within(dialog).findAllByText(/nicht mehr aktiv/)).length).toBeGreaterThan(0);
    expect(screen.getByRole('dialog')).toBeVisible();
});

it('setzt den Mindestbestand mit Komma und entfernt ihn mit leerem Feld', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('lager-zeile-7');

    await user.click(screen.getByRole('button', { name: 'Mindestbestand für Schrauben M8 festlegen' }));
    const dialog = await screen.findByRole('dialog');
    const feld = within(dialog).getByRole('textbox', { name: /Mindestbestand in Stk/ });
    expect(feld).toHaveValue('100');
    await user.clear(feld);
    await user.type(feld, '250,5');
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

    await waitFor(() => expect(aufrufe('PUT', '/mindestbestand')).toHaveLength(1));
    expect(aufrufe('PUT', '/artikel/7/mindestbestand')).toHaveLength(1);
    expect(JSON.parse(aufrufe('PUT', '/mindestbestand')[0][1].body)).toEqual({ mindestbestand: 250.5 });
});

it('beginnt die Lagerort-Auswahl dort, wo der Artikel liegt', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('lager-zeile-6');

    await user.click(screen.getByRole('button', { name: 'Inventur für Rundrohr 42,4 buchen' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByText(/Aktuell gebucht: -2,5 m/)).toBeVisible();
    await user.type(within(dialog).getByRole('textbox', { name: /Gezählter Bestand/ }), '12,5');
    await user.click(within(dialog).getByRole('button', { name: 'Inventur buchen' }));
    await waitFor(() => expect(aufrufe('POST', '/api/lager/buchungen')).toHaveLength(1));
    expect(JSON.parse(aufrufe('POST', '/api/lager/buchungen')[0][1].body)).toMatchObject({ artikelId: 6, lagerortId: 2, menge: 12.5 });
});

it('zeigt ohne Bestand einen verständlichen Leerzustand mit dem ersten Zugang', async () => {
    zeilen = [];
    zeige();

    expect(await screen.findByText('Noch nichts im Lager.')).toBeVisible();
    expect(screen.getByRole('button', { name: /Ersten Zugang buchen/ })).toBeVisible();
});

it('legt einen Lagerort an und legt einen Lagerort still', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('lager-zeile-5');

    await user.click(screen.getByRole('tab', { name: 'Lagerorte' }));
    expect(await screen.findByTestId('lagerort-1')).toHaveTextContent('Hauptlager');

    await user.click(screen.getByRole('button', { name: /Lagerort anlegen/ }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByPlaceholderText('z. B. Montagefahrzeug'), 'Regal 7');
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(aufrufe('POST', '/api/lager/orte')).toHaveLength(1));
    expect(JSON.parse(aufrufe('POST', '/api/lager/orte')[0][1].body)).toMatchObject({ name: 'Regal 7' });

    await user.click(screen.getByRole('button', { name: 'Lagerort Montagefahrzeug stilllegen' }));
    await waitFor(() => expect(aufrufe('PUT', '/orte/2/aktiv')).toHaveLength(1));
    expect(JSON.parse(aufrufe('PUT', '/orte/2/aktiv')[0][1].body)).toEqual({ aktiv: false });
});

it('zeigt einen Ladefehler als Meldung statt einer leeren Fläche', async () => {
    fetcher.mockImplementation(async (url: string) => (url.startsWith('/api/lager/bestand') ? antwort({ message: 'Der Bestand konnte nicht geladen werden.' }, false, 500) : antwort(url === '/api/lager/orte' ? ORTE : {})));
    zeige();

    expect((await screen.findAllByText('Der Bestand konnte nicht geladen werden.')).length).toBeGreaterThan(0);
    expect(screen.queryByTestId('lager-tabelle')).not.toBeInTheDocument();
});

it('bucht eine Menge an einen anderen Lagerort um und prüft vorher Ziel und vorhandene Menge', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('lager-zeile-5');

    await user.click(screen.getByRole('button', { name: 'Pfosten verzinkt umbuchen' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Umbuchen' }));
    expect(await within(dialog).findByText('Bitte wählen, wohin die Ware soll.')).toBeVisible();

    await user.click(within(dialog).getByText('Lagerort wählen'));
    await user.click(await screen.findByRole('option', { name: 'Montagefahrzeug' }));
    await user.type(within(dialog).getByLabelText(/Menge in Stk/), '9');
    await user.click(within(dialog).getByRole('button', { name: 'Umbuchen' }));
    expect(await within(dialog).findByText(/nur 4 Stk vorhanden/)).toBeVisible();
    expect(aufrufe('POST', '/api/lager/umbuchungen')).toHaveLength(0);

    await user.clear(within(dialog).getByLabelText(/Menge in Stk/));
    await user.type(within(dialog).getByLabelText(/Menge in Stk/), '3');
    await user.click(within(dialog).getByRole('button', { name: 'Umbuchen' }));
    await waitFor(() => expect(aufrufe('POST', '/api/lager/umbuchungen')).toHaveLength(1));
    expect(JSON.parse(aufrufe('POST', '/api/lager/umbuchungen')[0][1].body)).toMatchObject({ artikelId: 5, vonLagerortId: 1, nachLagerortId: 2, menge: 3 });
});

it('ändert Bezeichnung und Einheit eines Artikels und warnt vor der Einheitenänderung', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByTestId('lager-zeile-5');

    await user.click(screen.getByRole('button', { name: 'Pfosten verzinkt bearbeiten' }));
    const dialog = await screen.findByRole('dialog');
    const feld = within(dialog).getByLabelText('Bezeichnung');
    expect(feld).toHaveValue('Pfosten verzinkt');
    expect(within(dialog).queryByText(/nicht umgerechnet/)).toBeNull();
    await user.clear(feld);
    await user.type(feld, 'Pfosten 60x60');
    await user.click(within(dialog).getByText('Stück (Stk)'));
    await user.click(await screen.findByRole('option', { name: 'Meter (m)' }));
    expect(within(dialog).getByText(/nicht umgerechnet/)).toBeVisible();
    expect(within(dialog).getByRole('link', { name: /im Artikel öffnen/ })).toHaveAttribute('href', '/artikel/5');
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

    await waitFor(() => expect(aufrufe('PUT', '/api/lager/artikel/5')).toHaveLength(1));
    expect(JSON.parse(aufrufe('PUT', '/api/lager/artikel/5')[0][1].body)).toEqual({ bezeichnung: 'Pfosten 60x60', einheit: 'LAUFENDE_METER' });
});
