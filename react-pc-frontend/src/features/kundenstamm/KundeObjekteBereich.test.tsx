import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KundeObjekteBereich } from './KundeObjekteBereich';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import { setzeFeaturesStand, setzeFeaturesZurueck } from '../modulschalter/useFeatures';
import type { Ansprechpartner, KundeObjekt } from './types';

const objekt = (id: number, bezeichnung: string, extra: Partial<KundeObjekt> = {}): KundeObjekt => ({
    id, bezeichnung, strasse: null, plz: null, ort: null, ansprechpartnerName: null, ansprechpartnerTelefon: null,
    notizen: null, aktiv: true, anzahlWartungen: 0, ...extra,
});

const hans: Ansprechpartner = { id: 1, name: 'Hans Beispiel', funktion: 'Hausmeister', telefon: '0931 111', mobil: '0171 222', email: null, notizen: null, haupt: true };

const istGet = (init?: RequestInit) => !init || !init.method || init.method === 'GET';

function json(status: number, body: unknown = {}): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

interface Aufruf { methode: string; url: string; body: unknown }

function stub(aktive: KundeObjekt[], inaktive: KundeObjekt[] = [], eigene?: (url: string, init?: RequestInit) => Response | undefined) {
    const aufrufe: Aufruf[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        aufrufe.push({ methode, url, body: init?.body ? JSON.parse(init.body as string) : undefined });
        const e = eigene?.(url, init);
        if (e) return e;
        if (url === '/api/kunden/3/objekte' && methode === 'GET') return json(200, aktive);
        if (url === '/api/kunden/3/objekte?inaktive=true') return json(200, [...aktive, ...inaktive]);
        if (url === '/api/kunden/3/ansprechpartner') return json(200, [hans]);
        return json(404, { message: 'nicht gefunden: ' + url });
    }));
    return aufrufe;
}

const zeige = (onAnzahl?: (n: number) => void) => render(
    <ToastProvider><ConfirmProvider><MemoryRouter><KundeObjekteBereich kundeId={3} onAnzahl={onAnzahl} /></MemoryRouter></ConfirmProvider></ToastProvider>,
);

beforeEach(() => { setzeFeaturesZurueck(); setzeFeaturesStand({ wartung: true }); });
afterEach(() => { vi.unstubAllGlobals(); setzeFeaturesZurueck(); });

describe('KundeObjekteBereich', () => {
    it('zeigt Objekte mit Adresse, Ansprechpartner vor Ort, Notiz und der Zahl der Wartungen und meldet die Anzahl', async () => {
        stub([objekt(5, 'Werk Nord', { strasse: 'Industriestraße 12', plz: '97080', ort: 'Würzburg', ansprechpartnerName: 'Hans Beispiel', ansprechpartnerTelefon: '0931 111', notizen: 'Schlüssel beim Hausmeister', anzahlWartungen: 2 }),
            objekt(6, 'Büro')]);
        const onAnzahl = vi.fn();
        zeige(onAnzahl);

        const karte = await screen.findByTestId('objekt-5');
        expect(karte).toHaveTextContent('Werk Nord');
        expect(karte).toHaveTextContent('Industriestraße 12, 97080 Würzburg');
        expect(karte).toHaveTextContent('Hans Beispiel · 0931 111');
        expect(karte).toHaveTextContent('Schlüssel beim Hausmeister');
        expect(within(karte).getByRole('link', { name: /2 Wartungen ansehen/ })).toHaveAttribute('href', '/wartung?tab=objekte');
        expect(within(screen.getByTestId('objekt-6')).queryByRole('link')).toBeNull();
        expect(onAnzahl).toHaveBeenLastCalledWith(2);
    });

    it('blendet den Link zur Wartung aus, wenn das Wartungsmodul ausgeschaltet ist, die Objekte bleiben', async () => {
        setzeFeaturesStand({ wartung: false });
        stub([objekt(5, 'Werk Nord', { anzahlWartungen: 2 })]);
        zeige();

        const karte = await screen.findByTestId('objekt-5');
        expect(karte).toHaveTextContent('Werk Nord');
        expect(within(karte).queryByRole('link')).toBeNull();
        expect(screen.queryByText(/Dieselben Objekte nutzt die Wartung/)).toBeNull();
    });

    it('sagt bei einem Kunden ohne Objekt, dass noch keins eingetragen ist', async () => {
        stub([]);
        zeige();
        expect(await screen.findByText('Für diesen Kunden ist noch kein Objekt eingetragen.')).toBeVisible();
    });

    it('zeigt bei einem Ladefehler die Meldung und bietet einen neuen Versuch an', async () => {
        let n = 0;
        stub([objekt(5, 'Werk Nord')], [], (url, init) => (url === '/api/kunden/3/objekte' && istGet(init) && ++n === 1 ? json(500, { message: 'Die Datenbank antwortet nicht.' }) : undefined));
        zeige();

        expect(await screen.findByRole('alert')).toHaveTextContent('Die Datenbank antwortet nicht.');
        await userEvent.click(screen.getByRole('button', { name: 'Noch einmal versuchen' }));
        expect(await screen.findByTestId('objekt-5')).toBeVisible();
    });

    it('legt ein Objekt an und übernimmt einen Ansprechpartner des Kunden samt Telefon (Mobil bevorzugt)', async () => {
        const aufrufe = stub([], [], (url, init) => (url === '/api/kunden/3/objekte' && init?.method === 'POST'
            ? json(201, objekt(9, 'Mehrfamilienhaus')) : undefined));
        zeige();
        await screen.findByText(/noch kein Objekt/);
        await userEvent.click(screen.getByRole('button', { name: /Objekt anlegen/ }));

        const dialog = await screen.findByRole('dialog');
        await userEvent.type(within(dialog).getByLabelText('Bezeichnung'), 'Mehrfamilienhaus');
        await userEvent.type(within(dialog).getByLabelText('Straße'), 'Gartenstraße 4');
        await userEvent.type(within(dialog).getByLabelText('PLZ'), '97070');
        await userEvent.type(within(dialog).getByLabelText('Ort'), 'Würzburg');
        await userEvent.click(within(dialog).getByRole('combobox', { name: /Ansprechpartner des Kunden übernehmen/ }));
        await userEvent.click(await screen.findByRole('option', { name: /Hans Beispiel/ }));
        expect(within(dialog).getByLabelText('Ansprechpartner vor Ort')).toHaveValue('Hans Beispiel');
        expect(within(dialog).getByLabelText('Telefon')).toHaveValue('0171 222');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
        const post = aufrufe.find((a) => a.methode === 'POST')!;
        expect(post.body).toEqual({
            bezeichnung: 'Mehrfamilienhaus', strasse: 'Gartenstraße 4', plz: '97070', ort: 'Würzburg',
            ansprechpartnerName: 'Hans Beispiel', ansprechpartnerTelefon: '0171 222', notizen: null,
        });
    });

    it('verlangt eine Bezeichnung und schickt ohne sie nichts ab', async () => {
        const aufrufe = stub([]);
        zeige();
        await screen.findByText(/noch kein Objekt/);
        await userEvent.click(screen.getByRole('button', { name: /Objekt anlegen/ }));
        const dialog = await screen.findByRole('dialog');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        expect(await within(dialog).findByText(/Bitte eine Bezeichnung eintragen/)).toBeVisible();
        expect(aufrufe.some((a) => a.methode === 'POST')).toBe(false);
    });

    it('bearbeitet ein Objekt mit seinen bisherigen Werten', async () => {
        const aufrufe = stub([objekt(5, 'Werk Nord', { strasse: 'Industriestraße 12', plz: '97080', ort: 'Würzburg' })], [], (url, init) => (
            url === '/api/kunden/3/objekte/5' && init?.method === 'PUT' ? json(200, objekt(5, 'Werk Süd')) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Werk Nord bearbeiten' }));

        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByLabelText('Bezeichnung')).toHaveValue('Werk Nord');
        expect(within(dialog).getByLabelText('Straße')).toHaveValue('Industriestraße 12');
        await userEvent.clear(within(dialog).getByLabelText('Bezeichnung'));
        await userEvent.type(within(dialog).getByLabelText('Bezeichnung'), 'Werk Süd');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
        expect(aufrufe.find((a) => a.methode === 'PUT')!.body).toMatchObject({ bezeichnung: 'Werk Süd', strasse: 'Industriestraße 12' });
    });

    it('deaktiviert ein Objekt erst nach Rückfrage und sagt, dass die Wartungen erhalten bleiben', async () => {
        let aus = false;
        const aufrufe = stub([objekt(5, 'Werk Nord')], [], (url, init) => {
            if (url === '/api/kunden/3/objekte/5/aktiv' && init?.method === 'PUT') { aus = true; return json(200, objekt(5, 'Werk Nord', { aktiv: false })); }
            if (url === '/api/kunden/3/objekte' && istGet(init)) return json(200, aus ? [] : [objekt(5, 'Werk Nord')]);
            return undefined;
        });
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Werk Nord deaktivieren' }));
        expect(await screen.findByText(/Bisherige Protokolle und Wartungen bleiben erhalten/)).toBeVisible();
        expect(aufrufe.some((a) => a.methode === 'PUT')).toBe(false);
        await userEvent.click(screen.getByRole('button', { name: 'Deaktivieren' }));

        await waitFor(() => expect(screen.queryByTestId('objekt-5')).toBeNull());
        expect(aufrufe.find((a) => a.methode === 'PUT')!.body).toEqual({ aktiv: false });
    });

    it('zeigt Inaktive auf Wunsch gekennzeichnet und aktiviert sie ohne Rückfrage wieder', async () => {
        const aufrufe = stub([objekt(5, 'Werk Nord')], [objekt(6, 'Altes Lager', { aktiv: false })], (url, init) => (
            url === '/api/kunden/3/objekte/6/aktiv' && init?.method === 'PUT' ? json(200, objekt(6, 'Altes Lager')) : undefined));
        zeige();
        await screen.findByTestId('objekt-5');
        expect(screen.queryByTestId('objekt-6')).toBeNull();

        await userEvent.click(screen.getByRole('button', { name: 'Inaktive anzeigen' }));
        const karte = await screen.findByTestId('objekt-6');
        expect(karte).toHaveTextContent('Inaktiv');
        await userEvent.click(within(karte).getByRole('button', { name: 'Altes Lager wieder aktivieren' }));

        await waitFor(() => expect(aufrufe.some((a) => a.methode === 'PUT' && a.url.endsWith('/6/aktiv'))).toBe(true));
        expect(screen.queryByText(/Objekt deaktivieren\?/)).toBeNull();
    });

    it('zeigt die Meldung des Servers, wenn das Speichern scheitert, und lässt den Dialog offen', async () => {
        stub([], [], (url, init) => (init?.method === 'POST' ? json(404, { message: 'Der Kunde wurde nicht gefunden.' }) : undefined));
        zeige();
        await screen.findByText(/noch kein Objekt/);
        await userEvent.click(screen.getByRole('button', { name: /Objekt anlegen/ }));
        const dialog = await screen.findByRole('dialog');
        await userEvent.type(within(dialog).getByLabelText('Bezeichnung'), 'Werk Nord');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText('Der Kunde wurde nicht gefunden.')).toBeVisible();
        expect(screen.getByRole('dialog')).toBeVisible();
    });
});
