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
        kunde: { id: 1, name: 'Max Mustermann GmbH' }, objekt: null, tuer: null,
        projekt: null, techniker: { id: 5, name: 'Max Mustermann' },
        titel: 'Jahreswartung', protokollTyp: 'Wartungsprotokoll', datum: '2026-05-10', intervallMonate: 12, naechsteWartung: '2027-05-10',
        status: 'IN_BEARBEITUNG', bearbeitbar: true, moeglicheStatus: ['ABGESCHLOSSEN', 'ARCHIVIERT', 'OFFEN'],
        bemerkungen: null, ansprechpartnerName: null,
        unterschriftTechniker: { bild: null, name: null }, unterschriftKunde: { bild: null, name: null },
        abgeschlossenAm: null, vorlageProtokollId: null, bericht: null, angelegtAm: '2026-05-10T08:00:00', geaendertAm: '2026-05-10T08:00:00',
        module: [{ modulId: 1, modulKey: 'm_bst', name: 'Brandschutztür', untertypSchluessel: 'einflg', untertypLabel: '1-flügelig' }],
        abschnitte: [{ id: 10, bereich: 'PRUEFPUNKTE', label: 'Prüfpunkte Brandschutztür', sortierung: 0 }],
        pruefpunkte: [{ id: 100, abschnittId: 10, modulKey: 'm_bst', label: 'Zulassungsschild lesbar', ergebnis: 'IO', bemerkung: null, vorherigesErgebnis: null, sortierung: 0 }],
        datenfelder: [],
        statistik: { gesamt: 1, bewertet: 1, inOrdnung: 1, nichtInOrdnung: 0, nichtAnwendbar: 0 },
        ...extra,
    };
}

const OBJEKTE = [
    { id: 5, bezeichnung: 'Werk Nord', strasse: null, plz: null, ort: null, ansprechpartnerName: null, ansprechpartnerTelefon: null, notizen: null, aktiv: true, anzahlWartungen: 0 },
    { id: 6, bezeichnung: 'Werk Süd', strasse: null, plz: null, ort: null, ansprechpartnerName: null, ansprechpartnerTelefon: null, notizen: null, aktiv: true, anzahlWartungen: 0 },
];

function antwort(status: number, body: unknown): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

interface Aufruf { methode: string; url: string; body: Record<string, unknown> | undefined }

function stubServer(start: Protokoll, eigene?: (url: string, methode: string, body: Record<string, unknown> | undefined) => Response | undefined) {
    const aufrufe: Aufruf[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        const body = init?.body ? JSON.parse(init.body as string) : undefined;
        aufrufe.push({ methode, url, body });
        const e = eigene?.(url, methode, body);
        if (e) return e;
        if (url === '/api/kunden/1/objekte') return antwort(200, OBJEKTE);
        if (url === '/api/wartung/kunden/1/projekte') return antwort(200, []);
        if (url === '/api/wartung/unterschrift/meine') return antwort(204, null);
        if (url === '/api/mitarbeiter') return antwort(200, [{ id: 5, vorname: 'Max', nachname: 'Mustermann', aktiv: true }]);
        if (url === '/api/wartung/protokolle/1' && methode === 'GET') return antwort(200, start);
        return antwort(404, { message: 'nicht gefunden: ' + url });
    }));
    return aufrufe;
}

const zeige = () => render(
    <ToastProvider><ConfirmProvider><MemoryRouter initialEntries={['/wartung/1']}>
        <Routes><Route path="/wartung/:id" element={<WartungProtokoll />} /></Routes>
    </MemoryRouter></ConfirmProvider></ToastProvider>,
);

const kopfPut = (aufrufe: Aufruf[]) => aufrufe.find((a) => a.methode === 'PUT' && a.url === '/api/wartung/protokolle/1/kopf');

beforeEach(() => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    Element.prototype.scrollIntoView = vi.fn();
});
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('Objekt im Feldeinsatz zuordnen', () => {
    it('ordnet einen Feldeinsatz ohne Wartung einem Objekt des Kunden zu und speichert es mit den Angaben', async () => {
        const aufrufe = stubServer(protokoll(), (url, methode) => (url === '/api/wartung/protokolle/1/kopf' && methode === 'PUT'
            ? antwort(200, protokoll({ objekt: { id: 6, name: 'Werk Süd' } })) : undefined));
        zeige();
        const feld = await screen.findByRole('combobox', { name: 'Objekt' });
        expect(feld).toBeEnabled();
        expect(feld).toHaveTextContent('Kein Objekt');

        await userEvent.click(feld);
        await userEvent.click(await screen.findByRole('option', { name: 'Werk Süd' }));
        expect(await screen.findByText('Noch nicht gespeichert')).toBeVisible();
        await userEvent.click(screen.getByRole('button', { name: 'Angaben speichern' }));

        await waitFor(() => expect(kopfPut(aufrufe)).toBeDefined());
        expect(kopfPut(aufrufe)!.body).toMatchObject({ objektId: 6 });
        expect(await screen.findByText('Angaben gespeichert.')).toBeVisible();
    });

    it('löst die Zuordnung mit "Kein Objekt" (objektId null)', async () => {
        const aufrufe = stubServer(protokoll({ objekt: { id: 5, name: 'Werk Nord' } }), (url, methode) => (
            url === '/api/wartung/protokolle/1/kopf' && methode === 'PUT' ? antwort(200, protokoll()) : undefined));
        zeige();
        const feld = await screen.findByRole('combobox', { name: 'Objekt' });
        await waitFor(() => expect(feld).toHaveTextContent('Werk Nord'));

        await userEvent.click(feld);
        await userEvent.click(await screen.findByRole('option', { name: 'Kein Objekt' }));
        await userEvent.click(screen.getByRole('button', { name: 'Angaben speichern' }));

        await waitFor(() => expect(kopfPut(aufrufe)).toBeDefined());
        expect(kopfPut(aufrufe)!.body).toMatchObject({ objektId: null });
    });

    it('zeigt ein inzwischen deaktiviertes Objekt des Feldeinsatzes weiter an (es steht nicht mehr in der Auswahl der aktiven)', async () => {
        stubServer(protokoll({ objekt: { id: 9, name: 'Altes Lager' } }));
        zeige();
        const feld = await screen.findByRole('combobox', { name: 'Objekt' });

        await waitFor(() => expect(feld).toHaveTextContent('Altes Lager'));
    });

    it('bei einer Wartung ergibt sich das Objekt aus ihr: Auswahl gesperrt, mit Hinweis, und die Angaben schicken dasselbe Objekt', async () => {
        const aufrufe = stubServer(protokoll({ objekt: { id: 5, name: 'Werk Nord' }, tuer: { id: 3, name: 'Tür 1.01' }, bemerkungen: null }), (url, methode) => (
            url === '/api/wartung/protokolle/1/kopf' && methode === 'PUT' ? antwort(200, protokoll({ objekt: { id: 5, name: 'Werk Nord' }, tuer: { id: 3, name: 'Tür 1.01' }, bemerkungen: 'neu' })) : undefined));
        zeige();
        const feld = await screen.findByRole('combobox', { name: 'Objekt' });

        expect(feld).toBeDisabled();
        expect(screen.getByText(/ergibt sich aus der Wartung „Tür 1.01“/)).toBeVisible();
        expect(screen.queryByRole('button', { name: /Neues Objekt/ })).toBeNull();

        await userEvent.type(screen.getByLabelText('Bemerkungen'), 'neu');
        await userEvent.click(screen.getByRole('button', { name: 'Angaben speichern' }));
        await waitFor(() => expect(kopfPut(aufrufe)).toBeDefined());
        expect(kopfPut(aufrufe)!.body).toMatchObject({ objektId: 5 });
    });

    it('legt über "Neues Objekt" ein Objekt an und wählt es gleich aus', async () => {
        const aufrufe = stubServer(protokoll(), (url, methode) => (url === '/api/wartung/kunden/1/objekte' && methode === 'POST'
            ? antwort(201, { id: 12, kundeId: 1, kundeName: 'Max Mustermann GmbH', bezeichnung: 'Lager Ost', strasse: null, plz: null, ort: null, ansprechpartnerName: null, ansprechpartnerTelefon: null, notizen: null, aktiv: true, tueren: [] }) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: /Neues Objekt/ }));
        const dialog = await screen.findByRole('dialog');
        await userEvent.type(within(dialog).getByLabelText('Bezeichnung'), 'Lager Ost');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
        expect(screen.getByRole('combobox', { name: 'Objekt' })).toHaveTextContent('Lager Ost');
        expect(screen.getByText('Noch nicht gespeichert')).toBeVisible();
        expect(aufrufe.some((a) => a.methode === 'POST' && a.url === '/api/wartung/kunden/1/objekte')).toBe(true);
    });

    it('zeigt die Meldung des Servers, wenn das Objekt nicht gespeichert werden kann, und behält die Auswahl', async () => {
        stubServer(protokoll(), (url, methode) => (url === '/api/wartung/protokolle/1/kopf' && methode === 'PUT'
            ? antwort(400, { message: 'Das Objekt gehört nicht zu diesem Kunden.' }) : undefined));
        zeige();
        await userEvent.click(await screen.findByRole('combobox', { name: 'Objekt' }));
        await userEvent.click(await screen.findByRole('option', { name: 'Werk Nord' }));
        await userEvent.click(screen.getByRole('button', { name: 'Angaben speichern' }));

        expect(await screen.findByText('Das Objekt gehört nicht zu diesem Kunden.')).toBeVisible();
        expect(screen.getByRole('combobox', { name: 'Objekt' })).toHaveTextContent('Werk Nord');
    });

    it('bei einem abgeschlossenen Protokoll ist das Objekt gesperrt und es gibt kein "Neues Objekt"', async () => {
        stubServer(protokoll({ status: 'ABGESCHLOSSEN', bearbeitbar: false, abgeschlossenAm: '2026-05-10T10:00:00', objekt: { id: 5, name: 'Werk Nord' } }));
        zeige();
        const feld = await screen.findByRole('combobox', { name: 'Objekt' });

        expect(feld).toBeDisabled();
        expect(screen.queryByRole('button', { name: /Neues Objekt/ })).toBeNull();
    });
});
