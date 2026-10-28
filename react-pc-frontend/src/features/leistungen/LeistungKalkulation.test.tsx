import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { LeistungKalkulation } from './LeistungKalkulation';
import { ToastProvider } from '../../components/ui/toast';
import type { Kalkulation } from './kalkulationApi';

/** Nur Dummy-Daten (DSGVO). Die Zahlen rechnet das Backend, hier werden Anzeige, Eingabeprüfung und Aufrufe geprüft. */

const kalkulation = (aenderung: Partial<Kalkulation> = {}): Kalkulation => ({
    leistungId: 7, kalkuliert: true, zuschlagProzent: 10,
    positionen: [
        { id: 1, typ: 'ARTIKEL', artikelId: 1, arbeitsgangId: null, bezeichnung: 'Handlauf', menge: 2.5, einheit: 'Stk', einzelpreis: 12, summe: 30, hinweis: 'OK', notiz: null },
        { id: 2, typ: 'ARBEIT', artikelId: null, arbeitsgangId: 5, bezeichnung: 'Montage', menge: 1.5, einheit: 'Std.', einzelpreis: 60, summe: 90, hinweis: 'OK', notiz: null },
    ],
    materialSumme: 30, arbeitSumme: 90, zwischensumme: 120, zuschlag: 12, preisBerechnet: 132, preisGespeichert: 132, preisVeraltet: false, vollstaendig: true,
    ...aenderung,
});

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten });

function aufrufe(fetcher: ReturnType<typeof vi.fn>, methode: string, teil: string) {
    return fetcher.mock.calls.filter(([url, init]) => String(url).includes(teil) && (init?.method ?? 'GET') === methode);
}

let fetcher: ReturnType<typeof vi.fn>;
let aktuell: Kalkulation;

beforeEach(() => {
    aktuell = kalkulation();
    fetcher = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (url.endsWith('/kalkulation') && methode === 'GET') return antwort(aktuell);
        if (url.endsWith('/kalkulation/vorschau')) return antwort(aktuell);
        if (url.endsWith('/kalkulation') && methode === 'PUT') return antwort(aktuell);
        if (url.endsWith('/preis-uebernehmen')) return antwort({ ...aktuell, preisVeraltet: false, preisGespeichert: aktuell.preisBerechnet });
        return antwort([]);
    });
    vi.stubGlobal('fetch', fetcher);
});

afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (onPreis = vi.fn()) => {
    render(<ToastProvider><LeistungKalkulation leistungId={7} onPreisGeaendert={onPreis} /></ToastProvider>);
    return onPreis;
};

it('zeigt Zeilen, Hinweise und die Summe aus dem Backend', async () => {
    aktuell = kalkulation({
        positionen: [
            { ...kalkulation().positionen[0], einzelpreis: null, summe: 0, hinweis: 'KEIN_PREIS' },
            kalkulation().positionen[1],
        ],
        vollstaendig: false, preisBerechnet: 99,
    });
    zeige();

    expect(await screen.findByText('Handlauf')).toBeVisible();
    expect(screen.getByText('Montage')).toBeVisible();
    expect(screen.getByText('Kein Preis hinterlegt')).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent('Der berechnete Preis wäre zu niedrig');
    expect(screen.getByTestId('kalkulation-preis')).toHaveTextContent('99,00');
    expect(screen.getByRole('checkbox', { name: /Preis aus der Kalkulation berechnen/ })).toBeChecked();
});

it('weist auf einen veralteten Preis hin und übernimmt ihn erst auf Klick', async () => {
    const user = userEvent.setup();
    aktuell = kalkulation({ preisBerechnet: 140, preisGespeichert: 132, preisVeraltet: true });
    const onPreis = zeige();

    const hinweis = await screen.findByTestId('kalkulation-veraltet');
    expect(hinweis).toHaveTextContent('132,00');
    expect(hinweis).toHaveTextContent('140,00');
    expect(aufrufe(fetcher, 'POST', 'preis-uebernehmen')).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: 'Preis übernehmen' }));

    await waitFor(() => expect(onPreis).toHaveBeenCalledWith(140, true));
    expect(aufrufe(fetcher, 'POST', 'preis-uebernehmen')).toHaveLength(1);
    expect(screen.queryByTestId('kalkulation-veraltet')).not.toBeInTheDocument();
});

it('rechnet nach dem Entfernen einer Zeile neu und speichert die Zahlen als Zahlen', async () => {
    const user = userEvent.setup();
    const onPreis = zeige();
    await screen.findByText('Handlauf');
    expect(screen.getByRole('button', { name: 'Kalkulation speichern' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Zeile Montage entfernen' }));
    await waitFor(() => expect(aufrufe(fetcher, 'POST', '/vorschau').length).toBeGreaterThan(0));
    const vorschau = JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body);
    expect(vorschau.positionen).toHaveLength(1);
    expect(vorschau.positionen[0]).toMatchObject({ typ: 'ARTIKEL', artikelId: 1, menge: 2.5 });
    expect(vorschau.zuschlagProzent).toBe(10);

    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    const gespeichert = JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body);
    expect(gespeichert).toMatchObject({ kalkuliert: true, zuschlagProzent: 10 });
    expect(gespeichert.positionen).toHaveLength(1);
    await waitFor(() => expect(onPreis).toHaveBeenCalledWith(132, true));
});

it('nimmt Mengen mit Komma an und lehnt unvollständige oder ungültige Eingaben vor dem Speichern ab', async () => {
    const user = userEvent.setup();
    zeige();
    const menge = await screen.findByRole('textbox', { name: 'Menge Handlauf' });

    await user.clear(menge);
    await user.type(menge, '3,');
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(0);
    expect((await screen.findAllByText(/vollständige Zahl mit Dezimalkomma/)).length).toBeGreaterThan(0);

    await user.clear(menge);
    await user.type(menge, '0');
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(0);

    await user.clear(menge);
    await user.type(menge, '3,25');
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    expect(JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body).positionen[0].menge).toBe(3.25);
});

it('zeigt eine Fehlermeldung des Servers beim Speichern und ändert den Preis nicht', async () => {
    const user = userEvent.setup();
    const onPreis = zeige();
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        if ((init?.method ?? 'GET') === 'PUT') return antwort({ message: 'Für "Handlauf" fehlt ein Preis. Bitte zuerst pflegen, sonst wäre der Preis zu niedrig.' }, false, 400);
        return antwort(url.endsWith('/kalkulation/vorschau') || url.endsWith('/kalkulation') ? aktuell : []);
    });
    await screen.findByText('Handlauf');
    await user.click(screen.getByRole('checkbox', { name: /Preis aus der Kalkulation berechnen/ }));

    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));

    expect(await screen.findByText(/fehlt ein Preis/)).toBeVisible();
    expect(onPreis).not.toHaveBeenCalled();
});

it('zeigt einen Ladefehler als Meldung statt einer leeren Fläche', async () => {
    fetcher.mockImplementation(async (url: string) => (url.includes('/kalkulation')
        ? antwort({ message: 'Leistung nicht gefunden.' }, false, 404)
        : antwort([])));
    zeige();

    expect(await screen.findByRole('alert')).toHaveTextContent('Leistung nicht gefunden.');
});

const mitZeiten = (): Kalkulation => kalkulation({
    losgroesse: 50,
    positionen: [
        { id: 2, typ: 'ARBEIT', artikelId: null, arbeitsgangId: 5, bezeichnung: 'Montage', menge: 0.11, einheit: 'Std.', einzelpreis: 60, summe: 6.6, hinweis: 'OK', notiz: null,
            ruestzeitMinuten: 30, bearbeitungszeitMinuten: 6 },
    ],
    materialSumme: 0, arbeitSumme: 6.6, zwischensumme: 6.6, zuschlag: 0.66, preisBerechnet: 7.26, preisGespeichert: 7.26, zuschlagProzent: 10,
});

it('zeigt bei Arbeit mit Zeiten Rüstzeit, Bearbeitungszeit, errechnete Stunden und die Losgröße', async () => {
    aktuell = mitZeiten();
    zeige();

    expect(await screen.findByRole('textbox', { name: 'Rüstzeit Montage' })).toHaveValue('30');
    expect(screen.getByRole('textbox', { name: 'Bearbeitungszeit Montage' })).toHaveValue('6');
    expect(screen.getByTestId('stunden-0')).toHaveTextContent('= 0,11 Std. je Einheit');
    expect(screen.getByRole('textbox', { name: /Losgröße/ })).toHaveValue('50');
    expect(screen.queryByRole('textbox', { name: 'Menge Montage' })).not.toBeInTheDocument();
});

it('schickt Zeiten und Losgröße als Zahlen an Vorschau und Speichern', async () => {
    const user = userEvent.setup();
    aktuell = mitZeiten();
    zeige();
    const bearbeitung = await screen.findByRole('textbox', { name: 'Bearbeitungszeit Montage' });

    await user.clear(bearbeitung);
    await user.type(bearbeitung, '7,5');
    await waitFor(() => expect(aufrufe(fetcher, 'POST', '/vorschau').length).toBeGreaterThan(0));
    const vorschau = JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body);
    expect(vorschau.losgroesse).toBe(50);
    expect(vorschau.positionen[0]).toMatchObject({ typ: 'ARBEIT', arbeitsgangId: 5, menge: null, ruestzeitMinuten: 30, bearbeitungszeitMinuten: 7.5 });

    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    const gespeichert = JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body);
    expect(gespeichert.losgroesse).toBe(50);
    expect(gespeichert.positionen[0]).toMatchObject({ ruestzeitMinuten: 30, bearbeitungszeitMinuten: 7.5 });
});

it('verlangt bei Zeiten mindestens eine Zeit größer als 0 und eine gültige Losgröße', async () => {
    const user = userEvent.setup();
    aktuell = mitZeiten();
    zeige();
    const ruest = await screen.findByRole('textbox', { name: 'Rüstzeit Montage' });
    const bearbeitung = screen.getByRole('textbox', { name: 'Bearbeitungszeit Montage' });

    await user.clear(ruest);
    await user.clear(bearbeitung);
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    expect((await screen.findAllByText(/Rüstzeit oder eine Bearbeitungszeit größer als 0/)).length).toBeGreaterThan(0);
    expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(0);

    await user.type(bearbeitung, '5');
    const los = screen.getByRole('textbox', { name: /Losgröße/ });
    await user.clear(los);
    await user.type(los, '2000000');
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    expect((await screen.findAllByText(/Losgröße darf höchstens/)).length).toBeGreaterThan(0);
    expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(0);
});

it('schaltet eine Stundenzeile auf Zeiten um und füllt sie mit den Normzeiten des Arbeitsgangs vor', async () => {
    const user = userEvent.setup();
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (url === '/api/arbeitsgaenge') return antwort([{ id: 5, beschreibung: 'Montage', abteilungId: 1, abteilungName: 'Werkstatt', stundensatz: 60, stundensatzJahr: 2026, ruestzeitMinuten: 30, bearbeitungszeitMinuten: 6.5 }]);
        if (url.includes('/kalkulation') && methode !== 'POST') return antwort(aktuell);
        if (url.endsWith('/vorschau')) return antwort(aktuell);
        return antwort([]);
    });
    zeige();
    await screen.findByText('Montage');
    expect(screen.queryByRole('textbox', { name: 'Rüstzeit Montage' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /Losgröße/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Mit Rüstzeit und Bearbeitungszeit rechnen' }));

    expect(screen.getByRole('textbox', { name: 'Rüstzeit Montage' })).toHaveValue('30');
    expect(screen.getByRole('textbox', { name: 'Bearbeitungszeit Montage' })).toHaveValue('6,5');
    expect(screen.getByRole('textbox', { name: /Losgröße/ })).toHaveValue('1');

    await user.click(screen.getByRole('button', { name: 'Lieber in Stunden angeben' }));
    expect(screen.getByRole('textbox', { name: 'Menge Montage' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Rüstzeit Montage' })).not.toBeInTheDocument();
});

// ---------------------------------------------------------------------- Arbeitsgang mit Formel (#216)

const BOHREN = {
    id: 6, beschreibung: 'Bohren', abteilungId: 1, abteilungName: 'Werkstatt', stundensatz: 60, stundensatzJahr: 2026,
    formelBearbeitung: 'D*Tiefe/100', formelRuest: '5',
    formelFelder: [{ name: 'D', bezeichnung: 'Bohrerdurchmesser', einheit: 'mm', standard: 8 }, { name: 'Tiefe', bezeichnung: 'Bohrtiefe', einheit: 'mm', standard: 20 }],
};

const mitFormel = (): Kalkulation => kalkulation({
    positionen: [
        { id: 4, typ: 'ARBEIT', artikelId: null, arbeitsgangId: 6, bezeichnung: 'Bohren', menge: 0.027, einheit: 'Std.', einzelpreis: 60, summe: 1.6, hinweis: 'OK', notiz: null,
            ruestzeitMinuten: 5, bearbeitungszeitMinuten: 1.6, anzahlVorgaenge: 1, variablen: { d: 8, tiefe: 20 } },
    ],
    materialSumme: 0, arbeitSumme: 1.6, zwischensumme: 1.6, zuschlag: 0, preisBerechnet: 1.6, preisGespeichert: 1.6, losgroesse: 1,
});

function mitArbeitsgangFormel() {
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (url === '/api/arbeitsgaenge') return antwort([BOHREN]);
        if (url.includes('/kalkulation') && methode !== 'POST' && methode !== 'PUT') return antwort(aktuell);
        if (url.endsWith('/vorschau') || (url.endsWith('/kalkulation') && methode === 'PUT')) return antwort(aktuell);
        return antwort([]);
    });
}

it('zeigt bei einem Arbeitsgang mit Formel die Eingabemaske mit den gespeicherten Werten und dem errechneten Ergebnis', async () => {
    aktuell = mitFormel();
    mitArbeitsgangFormel();
    zeige();

    expect(await screen.findByTestId('formel-eingabe')).toBeVisible();
    expect(screen.getByRole('textbox', { name: 'Bohrerdurchmesser Bohren' })).toHaveValue('8');
    expect(screen.getByRole('textbox', { name: 'Bohrtiefe Bohren' })).toHaveValue('20');
    expect(screen.getByTestId('formel-ergebnis')).toHaveTextContent('= 1,6 min je Einheit, 5 min rüsten');
    expect(screen.queryByRole('textbox', { name: 'Bearbeitungszeit Bohren' })).not.toBeInTheDocument();
});

it('schickt die eingegebenen Werte als Zahlen mit, ohne Zeiten, und der Server rechnet', async () => {
    const user = userEvent.setup();
    aktuell = mitFormel();
    mitArbeitsgangFormel();
    zeige();
    const durchmesser = await screen.findByRole('textbox', { name: 'Bohrerdurchmesser Bohren' });

    await user.clear(durchmesser);
    await user.type(durchmesser, '35');
    await waitFor(() => expect(aufrufe(fetcher, 'POST', '/vorschau').length).toBeGreaterThan(0));
    const vorschau = JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body);
    expect(vorschau.positionen[0]).toMatchObject({ typ: 'ARBEIT', arbeitsgangId: 6, variablen: { d: 35, tiefe: 20 } });

    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    expect(JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body).positionen[0].variablen).toEqual({ d: 35, tiefe: 20 });
});

it('ein neu hinzugefügter Arbeitsgang mit Formel startet mit der Eingabemaske statt mit Stunden', async () => {
    const user = userEvent.setup();
    aktuell = kalkulation({ positionen: [] , materialSumme: 0, arbeitSumme: 0, zwischensumme: 0, zuschlag: 0, preisBerechnet: 0, preisGespeichert: 0 });
    mitArbeitsgangFormel();
    zeige();
    await user.click(await screen.findByText('+ Arbeitszeit hinzufügen'));
    await user.click(await screen.findByText('Bohren'));

    expect(await screen.findByTestId('formel-eingabe')).toBeVisible();
    expect(screen.getByRole('textbox', { name: 'Bohrerdurchmesser Bohren' })).toHaveAttribute('placeholder', '8');
    expect(screen.queryByRole('textbox', { name: 'Menge Bohren' })).not.toBeInTheDocument();
});

it('eine Zeile mit Handzeiten lässt sich auf die Formel umstellen und zurück', async () => {
    const user = userEvent.setup();
    aktuell = kalkulation({ positionen: [{ ...mitFormel().positionen[0], variablen: null }] });
    mitArbeitsgangFormel();
    zeige();
    await screen.findByRole('textbox', { name: 'Bearbeitungszeit Bohren' });
    expect(screen.queryByTestId('formel-eingabe')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Mit der Formel des Arbeitsgangs berechnen' }));
    expect(screen.getByTestId('formel-eingabe')).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Zeiten lieber von Hand eintragen' }));
    expect(screen.getByRole('textbox', { name: 'Bearbeitungszeit Bohren' })).toHaveValue('1,6');
    expect(screen.queryByTestId('formel-eingabe')).not.toBeInTheDocument();
});

// ---------------------------------------------------------------------- Baugruppen und Anzahl der Vorgänge (#135)

const mitBaugruppe = (): Kalkulation => kalkulation({
    zuschlagProzent: 10,
    positionen: [
        { id: 3, typ: 'BAUGRUPPE', artikelId: null, arbeitsgangId: null, baugruppeId: 12, bezeichnung: 'Feld', menge: 0.5, einheit: 'Stk', einzelpreis: 92, summe: 46, hinweis: 'OK', notiz: null },
    ],
    materialSumme: 16, arbeitSumme: 30, zwischensumme: 46, zuschlag: 4.6, preisBerechnet: 50.6, preisGespeichert: 50.6,
});

it('zeigt eine Baugruppe mit Menge je Einheit und dem Preis einer Einheit der Baugruppe', async () => {
    aktuell = mitBaugruppe();
    zeige();

    const feld = await screen.findByText('Feld');
    expect(feld).toBeVisible();
    expect(screen.getByLabelText('Baugruppe')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Menge Feld' })).toHaveValue('0,5');
    expect(screen.getByTestId('kalkulation-preis')).toHaveTextContent('50,60');
    expect(screen.getByText('92,00 €')).toBeVisible();
});

it('schickt eine Baugruppe mit ihrer Nummer und Menge als Zahl an Vorschau und Speichern', async () => {
    const user = userEvent.setup();
    aktuell = mitBaugruppe();
    zeige();
    const menge = await screen.findByRole('textbox', { name: 'Menge Feld' });

    await user.clear(menge);
    await user.type(menge, '2,5');
    await waitFor(() => expect(aufrufe(fetcher, 'POST', '/vorschau').length).toBeGreaterThan(0));
    const vorschau = JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body);
    expect(vorschau.positionen[0]).toMatchObject({ typ: 'BAUGRUPPE', baugruppeId: 12, menge: 2.5 });

    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    expect(JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body).positionen[0]).toMatchObject({ typ: 'BAUGRUPPE', baugruppeId: 12, menge: 2.5 });
});

it('bietet nur Leistungen als Baugruppe an, die dafür freigegeben sind, und nicht die Leistung selbst', async () => {
    const user = userEvent.setup();
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (url === '/api/leistungen') {
            return antwort([{ id: 11, name: 'Pfosten', baugruppe: true }, { id: 12, name: 'Montage vor Ort', baugruppe: false }, { id: 7, name: 'Diese Leistung', baugruppe: true }]);
        }
        if (url.endsWith('/kalkulation') && methode === 'GET') return antwort(kalkulation());
        if (url.endsWith('/kalkulation/vorschau')) return antwort(kalkulation());
        return antwort([]);
    });
    zeige();
    await screen.findByText('Handlauf');

    await user.click(screen.getByRole('button', { name: '+ Baugruppe hinzufügen' }));

    expect(await screen.findByText('Pfosten')).toBeVisible();
    expect(screen.queryByText('Montage vor Ort')).not.toBeInTheDocument();
    expect(screen.queryByText('Diese Leistung')).not.toBeInTheDocument();
    await user.click(screen.getByText('Pfosten'));
    expect(await screen.findByRole('textbox', { name: 'Menge Pfosten' })).toHaveValue('1');
    await waitFor(() => expect(aufrufe(fetcher, 'POST', '/vorschau').length).toBeGreaterThan(0));
    expect(JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body).positionen.at(-1)).toMatchObject({ typ: 'BAUGRUPPE', baugruppeId: 11, menge: 1 });
});

it('gibt ohne freigegebene Baugruppen einen Tipp, wie man welche anlegt', async () => {
    zeige();

    expect(await screen.findByText(/Als Baugruppe nutzbar/)).toBeVisible();
});

it('zeigt eine unvollständige Baugruppe mit Hinweis, ohne Einzelpreis', async () => {
    aktuell = kalkulation({
        positionen: [{ id: 3, typ: 'BAUGRUPPE', artikelId: null, arbeitsgangId: null, baugruppeId: 11, bezeichnung: 'Pfosten', menge: 2, einheit: 'Stk', einzelpreis: null, summe: 0, hinweis: 'BAUGRUPPE_UNVOLLSTAENDIG', notiz: null }],
        vollstaendig: false, preisBerechnet: 0,
    });
    zeige();

    expect(await screen.findByText('Baugruppe unvollständig (leer, oder es fehlt ein Preis)')).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent('Der berechnete Preis wäre zu niedrig');
});

it('zeigt bei Arbeit mit Zeiten die Anzahl der Vorgänge und rechnet die Bearbeitungszeit je Vorgang', async () => {
    const user = userEvent.setup();
    aktuell = kalkulation({
        positionen: [{ id: 2, typ: 'ARBEIT', artikelId: null, arbeitsgangId: 5, bezeichnung: 'Bohren', menge: 0.133, einheit: 'Std.', einzelpreis: 60, summe: 8, hinweis: 'OK', notiz: null,
            ruestzeitMinuten: 0, bearbeitungszeitMinuten: 2, anzahlVorgaenge: 4 }],
        materialSumme: 0, arbeitSumme: 8, zwischensumme: 8, zuschlag: 0.8, preisBerechnet: 8.8,
    });
    zeige();

    const vorgaenge = await screen.findByRole('textbox', { name: 'Anzahl der Vorgänge Bohren' });
    expect(vorgaenge).toHaveValue('4');
    expect(screen.getByText('min je Vorgang')).toBeVisible();

    await user.clear(vorgaenge);
    await user.type(vorgaenge, '6');
    await waitFor(() => expect(aufrufe(fetcher, 'POST', '/vorschau').length).toBeGreaterThan(0));
    expect(JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body).positionen[0]).toMatchObject({ typ: 'ARBEIT', bearbeitungszeitMinuten: 2, anzahlVorgaenge: 6 });

    await user.clear(vorgaenge);
    await waitFor(() => expect(screen.getByText('min je Einheit')).toBeVisible());
    await waitFor(() => expect(JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body).positionen[0].anzahlVorgaenge).toBe(1));
});

// ---------------------------------------------------------------------- Verschnitt (#135)

it('zeigt bei Material den Verschnitt und die Menge einschließlich Verschnitt', async () => {
    aktuell = kalkulation({
        positionen: [{ ...kalkulation().positionen[0], menge: 8, einheit: 'lfm', einzelpreis: 12, summe: 103.68, verschnittProzent: 8, mengeMitVerschnitt: 8.64 }, kalkulation().positionen[1]],
    });
    zeige();

    expect(await screen.findByRole('textbox', { name: 'Verschnitt Handlauf' })).toHaveValue('8');
    expect(screen.getByTestId('verschnitt-0')).toHaveTextContent('= 8,64 lfm mit Verschnitt');
    expect(screen.queryByRole('textbox', { name: 'Verschnitt Montage' })).not.toBeInTheDocument();
});

it('schickt den Verschnitt als Zahl; leer heißt keiner; über 100 wird vor dem Senden abgelehnt', async () => {
    const user = userEvent.setup();
    zeige();
    const verschnitt = await screen.findByRole('textbox', { name: 'Verschnitt Handlauf' });
    expect(verschnitt).toHaveValue('');

    await user.type(verschnitt, '12,5');
    await waitFor(() => expect(aufrufe(fetcher, 'POST', '/vorschau').length).toBeGreaterThan(0));
    expect(JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body).positionen[0]).toMatchObject({ typ: 'ARTIKEL', verschnittProzent: 12.5, menge: 2.5 });

    await user.clear(verschnitt);
    await waitFor(() => expect(JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body).positionen[0].verschnittProzent).toBeNull());

    const vorher = aufrufe(fetcher, 'POST', '/vorschau').length;
    await user.type(verschnitt, '150');
    expect(await screen.findByRole('alert')).toHaveTextContent(/Verschnitt bei "Handlauf"/);
    expect(aufrufe(fetcher, 'POST', '/vorschau').length).toBe(vorher);
});

// ---------------------------------------------------------------------- Mengenstaffel (#135 Stufe 2)

it('zeigt den Preis je Einheit für mehrere Mengen aus der gespeicherten Kalkulation', async () => {
    const user = userEvent.setup();
    const alt = fetcher.getMockImplementation()!;
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => (url.endsWith('/staffel')
        ? antwort([{ menge: 10, materialJeEinheit: 5, arbeitJeEinheit: 7, preisJeEinheit: 13.2, gesamtpreis: 132, vollstaendig: true },
            { menge: 50, materialJeEinheit: 5, arbeitJeEinheit: 2, preisJeEinheit: 7.7, gesamtpreis: 385, vollstaendig: false }])
        : alt(url, init)));
    zeige();
    await screen.findByText('Handlauf');

    await user.click(screen.getByRole('button', { name: 'Preise je Menge zeigen' }));

    const tabelle = await screen.findByRole('table', { name: 'Preis nach Menge' });
    expect(tabelle).toHaveTextContent('13,20 €');
    expect(tabelle).toHaveTextContent('385,00 €');
    expect(tabelle).toHaveTextContent('(unvollständig)');
    expect(JSON.parse(aufrufe(fetcher, 'POST', '/staffel')[0][1].body)).toEqual({ mengen: [10, 25, 50] });
});

it('lehnt eine ungültige Menge in der Staffel vor dem Senden ab', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByText('Handlauf');

    const feld = screen.getByLabelText(/Mengen \(mit Leerzeichen/);
    await user.clear(feld);
    await user.type(feld, '10 abc');
    await user.click(screen.getByRole('button', { name: 'Preise je Menge zeigen' }));

    expect(await screen.findByText(/Menge/, { selector: 'p[role="alert"]' })).toBeVisible();
    expect(aufrufe(fetcher, 'POST', '/staffel')).toHaveLength(0);
});

// ---------------------------------------------------------------------- Maschinen (#63)

const mitMaschine = (anteil = 100): Kalkulation => kalkulation({
    positionen: [{ id: 2, typ: 'ARBEIT', artikelId: null, arbeitsgangId: 5, bezeichnung: 'Sägen', menge: 0.5, einheit: 'Std.', einzelpreis: 60, summe: 50, hinweis: 'OK', notiz: null,
        maschinenSatz: 40, maschinenSumme: 20, maschinenanteilProzent: anteil }],
    materialSumme: 0, arbeitSumme: 30, maschineSumme: 20, zwischensumme: 50, zuschlag: 5, preisBerechnet: 55, preisGespeichert: 55, zuschlagProzent: 10,
});

it('zeigt bei einem Arbeitsgang mit Maschine die Maschinenkosten und eine eigene Zeile „Maschine" in der Zusammenfassung', async () => {
    aktuell = mitMaschine()
    zeige();

    expect(await screen.findByTestId('maschine-0')).toHaveTextContent('Maschine: 20,00 € (40,00 € je Std.)');
    expect(screen.getByRole('textbox', { name: 'Maschinenanteil Sägen' })).toHaveValue('');
    expect(screen.getByTestId('kalkulation-maschine')).toHaveTextContent('20,00');
    expect(screen.getByText('Arbeit (Lohn)')).toBeVisible();
    expect(screen.getByTestId('kalkulation-preis')).toHaveTextContent('55,00');
});

it('der Maschinenanteil geht als Zahl an Vorschau und Speichern; leer heißt 100 Prozent', async () => {
    const user = userEvent.setup();
    aktuell = mitMaschine();
    zeige();
    const feld = await screen.findByRole('textbox', { name: 'Maschinenanteil Sägen' });
    expect(feld).toHaveValue('');

    await user.type(feld, '50');
    await waitFor(() => expect(aufrufe(fetcher, 'POST', '/vorschau').length).toBeGreaterThan(0));
    expect(JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body).positionen[0]).toMatchObject({ typ: 'ARBEIT', menge: 0.5, maschinenanteilProzent: 50 });

    await user.clear(feld);
    await waitFor(() => expect(JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body).positionen[0].maschinenanteilProzent).toBeNull());
});

it('weicht der gespeicherte Anteil von 100 ab, steht er im Feld; ohne Maschine am Arbeitsgang gibt es das Feld nicht', async () => {
    aktuell = mitMaschine(50);
    const { unmount } = render(<ToastProvider><LeistungKalkulation leistungId={7} onPreisGeaendert={vi.fn()} /></ToastProvider>);
    expect(await screen.findByRole('textbox', { name: 'Maschinenanteil Sägen' })).toHaveValue('50');
    unmount();

    aktuell = kalkulation();
    zeige();
    await screen.findByText('Montage');
    expect(screen.queryByRole('textbox', { name: /Maschinenanteil/ })).toBeNull();
    expect(screen.queryByTestId('kalkulation-maschine')).toBeNull();
});

it('fehlt der Maschinenstundensatz am Arbeitsgang, steht der Hinweis an der Zeile und das Feld ist da', async () => {
    aktuell = kalkulation({
        positionen: [{ id: 2, typ: 'ARBEIT', artikelId: null, arbeitsgangId: 5, bezeichnung: 'Sägen', menge: 0.5, einheit: 'Std.', einzelpreis: 60, summe: 30, hinweis: 'KEIN_MASCHINENSATZ', notiz: null }],
        vollstaendig: false,
    });
    zeige();

    expect(await screen.findByText('Am Arbeitsgang fehlt der Maschinenstundensatz')).toBeVisible();
    expect(screen.getByRole('textbox', { name: 'Maschinenanteil Sägen' })).toBeVisible();
});

// ── Fremdleistung (Stufe 4 von #135) ──

const fremdZeile = (aenderung: Record<string, unknown> = {}) => ({
    id: 3, typ: 'FREMDLEISTUNG' as const, artikelId: null, arbeitsgangId: null, fremdleistungId: 31, veredler: 'Verzinkerei Muster A', bezeichnung: 'Feuerverzinken', menge: 8, einheit: 'kg',
    einzelpreis: 1.4, summe: 11.2, hinweis: 'OK' as const, notiz: null,
    kostenarten: [{ art: 'EINHEITSPREIS', text: '160 kg × 1 €', betrag: 160 }, { art: 'ANTEILIG', text: '80 € je Charge à 200 kg, verteilt auf 160 kg', betrag: 64 }],
    ...aenderung,
});

it('zeigt eine Fremdleistung mit Veredler, Aufstellung der Kostenarten und Summe', async () => {
    aktuell = kalkulation({ positionen: [fremdZeile()], materialSumme: 0, arbeitSumme: 0, fremdleistungSumme: 11.2, zwischensumme: 11.2, zuschlag: 0, preisBerechnet: 11.2, preisGespeichert: 11.2 });
    zeige();

    const zeile = await screen.findByTestId('fremd-0');
    expect(zeile).toHaveTextContent('bei Verzinkerei Muster A');
    expect(zeile).toHaveTextContent('160 kg × 1 €');
    expect(zeile).toHaveTextContent('64,00');
    expect(screen.getByTestId('kalkulation-fremd')).toHaveTextContent('11,20');
    expect(screen.getByLabelText('Losgröße (Einheiten)')).toBeVisible();
});

it('speichert die Fremdleistungs-Zeile mit Angebot, Menge und Gewicht', async () => {
    const user = userEvent.setup();
    aktuell = kalkulation({ positionen: [fremdZeile({ hinweis: 'KEIN_GEWICHT', einzelpreis: null })], vollstaendig: false });
    zeige();
    const gewicht = await screen.findByRole('textbox', { name: 'Gewicht Feuerverzinken' });
    expect(screen.getByText('Gewicht fehlt')).toBeVisible();

    await user.type(gewicht, '8,5');
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));

    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    const gespeichert = JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body);
    expect(gespeichert.positionen[0]).toMatchObject({ typ: 'FREMDLEISTUNG', fremdleistungId: 31, gewichtKgJeEinheit: 8.5, menge: 8 });
});

it('„Günstigsten Veredler wählen" vergleicht für die Auftragsmenge und trägt den günstigsten ein', async () => {
    const user = userEvent.setup();
    aktuell = kalkulation({ positionen: [fremdZeile()] });
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (String(url).includes('/api/fremdleistungen/vergleich')) {
            return antwort([
                { fremdleistungId: 32, lieferantId: 2, lieferantName: 'Verzinkerei Muster B', bezeichnung: 'Feuerverzinken', einheit: 'KG', summe: 140, summeJeEinheit: 1.4, gewichtFehlt: false, guenstigster: true, kostenarten: [] },
                { fremdleistungId: 31, lieferantId: 1, lieferantName: 'Verzinkerei Muster A', bezeichnung: 'Feuerverzinken', einheit: 'KG', summe: 198, summeJeEinheit: 1.98, gewichtFehlt: false, guenstigster: false, kostenarten: [] },
            ]);
        }
        if (String(url).endsWith('/kalkulation') && methode === 'GET') return antwort(aktuell);
        if (String(url).endsWith('/kalkulation/vorschau')) return antwort(aktuell);
        if (String(url).endsWith('/kalkulation') && methode === 'PUT') return antwort(aktuell);
        return antwort([]);
    });
    zeige();
    await screen.findByTestId('fremd-0');

    await user.click(screen.getByRole('button', { name: /Günstigsten Veredler wählen/ }));

    await waitFor(() => expect(fetcher.mock.calls.some(([u]) => String(u).includes('/api/fremdleistungen/vergleich'))).toBe(true));
    const vergleich = String(fetcher.mock.calls.find(([u]) => String(u).includes('/api/fremdleistungen/vergleich'))![0]);
    expect(vergleich).toContain('bezeichnung=Feuerverzinken');
    expect(vergleich).toContain('menge=8');
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    expect(JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body).positionen[0].fremdleistungId).toBe(32);
});

// ── Gewicht und Oberfläche, Menge aus dem Gewicht ──

it('zeigt Gewicht und Oberfläche je Material-Zeile und in der Zusammenfassung', async () => {
    aktuell = kalkulation({
        positionen: [{ ...kalkulation().positionen[0], einheit: 'lfm', menge: 3, gewichtKg: 5.67, oberflaecheM2: 0.377 }],
        gewichtKg: 5.67, oberflaecheM2: 0.377, gewichtVollstaendig: true,
    });
    zeige();

    expect(await screen.findByTestId('gewicht-0')).toHaveTextContent('5,67 kg, 0,377 m² (ohne Verschnitt)');
    expect(screen.getByTestId('kalkulation-gewicht')).toHaveTextContent('5,67 kg, 0,377 m²');
    expect(screen.getByTestId('kalkulation-gewicht')).not.toHaveTextContent('unvollständig');
});

it('meldet unvollständiges Gewicht in der Zusammenfassung', async () => {
    aktuell = kalkulation({ gewichtKg: 2, oberflaecheM2: 0, gewichtVollstaendig: false });
    zeige();
    expect(await screen.findByTestId('kalkulation-gewicht')).toHaveTextContent('(unvollständig)');
});

it('Fremdleistung: „Menge aus dem Gewicht" zeigt den berechneten Wert und speichert ohne Menge', async () => {
    const user = userEvent.setup();
    aktuell = kalkulation({ positionen: [fremdZeile({ menge: 6, mengeAus: 'GEWICHT' })] });
    zeige();
    const zeile = await screen.findByTestId('fremd-0');
    expect(screen.getByLabelText('Woher die Menge kommt, Feuerverzinken')).toHaveValue('GEWICHT');
    expect(screen.getByTestId('menge-berechnet-0')).toHaveTextContent('= 6 kg Gewicht je Einheit (berechnet)');
    expect(screen.queryByRole('textbox', { name: 'Menge Feuerverzinken' })).toBeNull();
    expect(zeile).toBeVisible();

    await user.selectOptions(screen.getByLabelText('Woher die Menge kommt, Feuerverzinken'), 'OBERFLAECHE');
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));

    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    expect(JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body).positionen[0]).toMatchObject({ typ: 'FREMDLEISTUNG', fremdleistungId: 31, mengeAus: 'OBERFLAECHE', menge: null });
});

it('Fremdleistung: „Menge von Hand" schaltet das Mengenfeld wieder frei', async () => {
    const user = userEvent.setup();
    aktuell = kalkulation({ positionen: [fremdZeile({ menge: 6, mengeAus: 'GEWICHT' })] });
    zeige();
    await screen.findByTestId('fremd-0');
    await user.selectOptions(screen.getByLabelText('Woher die Menge kommt, Feuerverzinken'), 'HAND');
    const menge = await screen.findByRole('textbox', { name: 'Menge Feuerverzinken' });
    await user.clear(menge);
    await user.type(menge, '7,5');
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    const z = JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body).positionen[0];
    expect(z.menge).toBe(7.5);
    expect(z.mengeAus ?? null).toBeNull();
});

// ---------------------------------------------------------------------- Material mit Zuschnitt-Formel (#228)

const blechZeile = (): Kalkulation['positionen'][number] => ({
    id: 1, typ: 'ARTIKEL', artikelId: 3, arbeitsgangId: null, bezeichnung: 'Blechzuschnitt', menge: 2, einheit: 'Stk', einzelpreis: 196.25, summe: 392.5, hinweis: 'OK', notiz: null,
    gewichtKg: 78.5, oberflaecheM2: 2, variablen: { l: 1000, b: 500, t: 10 },
    formelFelder: [
        { name: 'L', bezeichnung: 'Länge', einheit: 'mm', standard: 1000 },
        { name: 'B', bezeichnung: 'Breite', einheit: 'mm', standard: 500 },
        { name: 'T', bezeichnung: 'Dicke', einheit: 'mm', standard: 10 },
    ],
});

it('zeigt bei Material mit Zuschnitt-Formel die Maße mit den gespeicherten Werten und das berechnete Gewicht', async () => {
    aktuell = kalkulation({ positionen: [blechZeile()] });
    zeige();

    expect(await screen.findByTestId('mass-eingabe')).toBeVisible();
    expect(screen.getByRole('textbox', { name: 'Länge Blechzuschnitt' })).toHaveValue('1000');
    expect(screen.getByRole('textbox', { name: 'Breite Blechzuschnitt' })).toHaveValue('500');
    expect(screen.getByRole('textbox', { name: 'Dicke Blechzuschnitt' })).toHaveValue('10');
    expect(screen.getByTestId('gewicht-0')).toHaveTextContent('78,5 kg');
});

it('schickt die geänderten Maße als Zahlen mit, und der Server rechnet Gewicht, Fläche und Preis', async () => {
    const user = userEvent.setup();
    aktuell = kalkulation({ positionen: [blechZeile()] });
    zeige();
    const laenge = await screen.findByRole('textbox', { name: 'Länge Blechzuschnitt' });

    await user.clear(laenge);
    await user.type(laenge, '2000');
    await waitFor(() => expect(aufrufe(fetcher, 'POST', '/vorschau').length).toBeGreaterThan(0));
    const vorschau = JSON.parse(aufrufe(fetcher, 'POST', '/vorschau').at(-1)![1].body);
    expect(vorschau.positionen[0]).toMatchObject({ typ: 'ARTIKEL', artikelId: 3, variablen: { l: 2000, b: 500, t: 10 } });
    expect(vorschau.positionen[0]).not.toHaveProperty('gewichtKg');

    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    expect(JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body).positionen[0].variablen).toEqual({ l: 2000, b: 500, t: 10 });
});

it('Material ohne Zuschnitt-Formel bekommt keine Maßfelder und schickt keine Maße', async () => {
    const user = userEvent.setup();
    zeige();
    await screen.findByText('Handlauf');
    expect(screen.queryByTestId('mass-eingabe')).not.toBeInTheDocument();

    await user.type(screen.getByRole('textbox', { name: 'Verschnitt Handlauf' }), '5');
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));
    await waitFor(() => expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(1));
    expect(JSON.parse(aufrufe(fetcher, 'PUT', '/kalkulation')[0][1].body).positionen[0]).not.toHaveProperty('variablen');
});

it('lehnt ein ungültiges Maß ab, bevor etwas gesendet wird', async () => {
    const user = userEvent.setup();
    aktuell = kalkulation({ positionen: [blechZeile()] });
    zeige();
    const dicke = await screen.findByRole('textbox', { name: 'Dicke Blechzuschnitt' });

    await user.clear(dicke);
    await user.type(dicke, '12,x');
    await user.click(screen.getByRole('button', { name: 'Kalkulation speichern' }));

    const meldungen = (await screen.findAllByRole('alert')).map(m => m.textContent ?? '').join(' | ');
    expect(meldungen).toContain('bei "Blechzuschnitt"');
    expect(aufrufe(fetcher, 'PUT', '/kalkulation')).toHaveLength(0);
});
