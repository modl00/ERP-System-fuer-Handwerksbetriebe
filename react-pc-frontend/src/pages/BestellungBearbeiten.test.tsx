import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BestellungBearbeiten from './BestellungBearbeiten';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import type { Bestellung, BestellPosition } from '../features/bestellwesen/bestellwesenApi';

/** Nur Dummy-Daten. Gerechnet und geprüft wird im Backend; hier zählen Bedienung, Eingabeprüfung und die richtigen Aufrufe. */
let rechte = { ANLEGEN: true, AENDERN: true, LOESCHEN: true };
vi.mock('../features/funktionsrechte/useFunktionsrechte', () => ({ useDarf: (_f: string, a: 'ANLEGEN' | 'AENDERN' | 'LOESCHEN') => rechte[a] }));
vi.mock('../features/bestellwesen/LieferantWahl', () => ({
    LieferantWahl: (p: { lieferantName: string; onWaehlen: (l: { id: number; name: string }) => void; disabled?: boolean }) =>
        p.disabled ? <p>{p.lieferantName}</p> : <button onClick={() => p.onWaehlen({ id: 3, name: 'Stahlhandel Muster' })}>Lieferant wählen (Test){p.lieferantName ? `: ${p.lieferantName}` : ''}</button>,
}));
vi.mock('../features/lager/LagerArtikelWahl', () => ({
    LagerArtikelWahl: (p: { onWaehlen: (a: unknown) => void }) =>
        <button onClick={() => p.onWaehlen({ id: 11, titel: 'Schraube M8', nummer: 'S-M8', einheit: { kurz: 'Stk' } })}>Artikel wählen (Test)</button>,
}));
vi.mock('../components/ProjectSelectModal', () => ({
    ProjectSelectModal: (p: { onSelect: (x: { id: number; bauvorhaben: string }) => void }) =>
        <button onClick={() => p.onSelect({ id: 7, bauvorhaben: 'Anbau Halle' })}>Projekt 7 wählen (Test)</button>,
}));

const pos = (id: number, extra: Partial<BestellPosition> = {}): BestellPosition => ({
    id, artikelId: 10 + id, artikelnummer: `A-${id}`, bezeichnung: `Artikel ${id}`, menge: 5, einheit: 'Stk', einzelpreis: null, summe: null, herkunft: 'FREI', projektId: null,
    projektName: null, artikelInProjektId: null, kommentar: null, geliefertAm: null, ...extra,
});

const bestellung = (extra: Partial<Bestellung> = {}): Bestellung => ({
    id: 9, bestellnummer: null, status: 'ENTWURF', lieferstand: 'ENTWURF', lieferantId: 3, lieferantName: 'Stahlhandel Muster', lieferantEmails: ['bestellung@stahl.example.org'], notiz: null,
    erstelltAm: '2026-10-05T08:00:00', erstelltVon: 'admin', bestelltAm: null, bestelltVon: null, bestelltPer: null, storniertAm: null, stornoGrund: null, summe: 0,
    anzahlPositionen: 2, anzahlGeliefert: 0, tageSeitBestellung: null, ueberfaellig: false, ueberfaelligNachTagen: 7, projekte: [], positionen: [pos(1), pos(2)], ...extra,
});

const antwort = (daten: unknown, status = 200) => ({ ok: status < 300, status, json: async () => daten, blob: async () => new Blob(['%PDF']) });
let fetcher: ReturnType<typeof vi.fn>;
let aktuelle: Bestellung;
const aufrufe = (methode: string, teil: string) => fetcher.mock.calls.filter(([u, i]) => String(u).includes(teil) && ((i as RequestInit | undefined)?.method ?? 'GET') === methode);
const body = (c: unknown[]) => JSON.parse((c[1] as RequestInit).body as string);

function Ort() { return <p data-testid="ort">{useLocation().pathname}</p>; }
const zeige = (pfad: string) => render(
    <MemoryRouter initialEntries={[pfad]}>
        <ToastProvider><ConfirmProvider>
            <Routes>
                <Route path="/bestellungen/bestellung/:id" element={<BestellungBearbeiten />} />
                <Route path="*" element={<Ort />} />
            </Routes>
        </ConfirmProvider></ToastProvider>
    </MemoryRouter>);

beforeEach(() => {
    rechte = { ANLEGEN: true, AENDERN: true, LOESCHEN: true };
    aktuelle = bestellung();
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const m = init?.method ?? 'GET';
        if (url.includes('/vorschlaege/mindestbestand')) {
            return antwort([{ artikelId: 11, artikelnummer: 'S-M8', bezeichnung: 'Schraube M8', einheit: 'Stk', menge: 80, herkunft: 'LAGER', projektId: null, projektName: null, artikelInProjektId: null,
                lieferantId: 3, lieferantName: 'Stahlhandel Muster', einzelpreis: 0.15, kommentar: null, hinweis: 'Bestand 20 Stk, Mindestbestand 100 Stk' },
            { artikelId: 12, artikelnummer: null, bezeichnung: 'Dübel 8', einheit: 'Stk', menge: 30, herkunft: 'LAGER', projektId: null, projektName: null, artikelInProjektId: null,
                lieferantId: 4, lieferantName: 'Dübel Muster', einzelpreis: null, kommentar: null, hinweis: null }]);
        }
        if (url.includes('/vorschlaege/projekt/7')) {
            return antwort([{ artikelId: 10, artikelnummer: 'R-1', bezeichnung: 'Rohr 40x40', einheit: 'm', menge: 6.5, herkunft: 'PROJEKT', projektId: 7, projektName: '2026/014 Anbau Halle',
                artikelInProjektId: 55, lieferantId: null, lieferantName: null, einzelpreis: null, kommentar: 'Zuschnitt 3 m', hinweis: null }]);
        }
        if (url === '/api/emails/send') return antwort({});
        if (url.endsWith('/pdf')) return antwort({});
        if (url === '/api/lieferanten-bestellungen' && m === 'POST') return antwort({ ...aktuelle, id: 9 }, 201);
        if (m === 'PUT') return antwort(aktuelle);
        if (url.endsWith('/bestellen')) return antwort({ ...aktuelle, status: 'BESTELLT', lieferstand: 'BESTELLT', bestellnummer: 'B-2026-10-0007', bestelltAm: '2026-10-05T09:00:00', bestelltPer: JSON.parse(init?.body as string).per });
        if (url.endsWith('/geliefert')) return antwort({ ...aktuelle, lieferstand: 'TEILWEISE', anzahlGeliefert: 1, positionen: [pos(1, { geliefertAm: '2026-10-05' }), pos(2)] });
        if (url.endsWith('/alles-geliefert')) return antwort({ ...aktuelle, lieferstand: 'GELIEFERT', anzahlGeliefert: 2, positionen: [pos(1, { geliefertAm: '2026-10-05' }), pos(2, { geliefertAm: '2026-10-05' })] });
        if (url.endsWith('/stornieren')) return antwort({ ...aktuelle, status: 'STORNIERT', lieferstand: 'STORNIERT', stornoGrund: JSON.parse(init?.body as string).grund });
        if (m === 'DELETE') return { ok: true, status: 204, json: async () => undefined };
        if (/\/api\/lieferanten-bestellungen\/\d+$/.test(url)) return antwort(aktuelle);
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('Bestellung bearbeiten', { timeout: 30000 }, () => {
    it('neue Bestellung: ohne Lieferant und ohne Positionen wird beim Speichern nachgefragt, nichts geht raus', async () => {
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/neu');

        await user.click(await screen.findByRole('button', { name: 'Entwurf speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Bitte einen Lieferanten wählen.');
        await user.click(screen.getByRole('button', { name: /Lieferant wählen/ }));
        await user.click(screen.getByRole('button', { name: 'Entwurf speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('mindestens eine Position');
        expect(aufrufe('POST', '/api/lieferanten-bestellungen')).toHaveLength(0);
    });

    it('Lager auffüllen: Vorschlag übernehmen (anderer Lieferant ist nicht vorgewählt), Menge ändern, Entwurf speichern → POST mit Herkunft und Einheit', async () => {
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/neu');
        await user.click(await screen.findByRole('button', { name: /Lieferant wählen/ }));

        await user.click(screen.getByRole('button', { name: /Lager auffüllen/ }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByRole('checkbox', { name: 'Schraube M8 übernehmen' })).toBeChecked();
        expect(within(dialog).getByRole('checkbox', { name: 'Dübel 8 übernehmen' })).not.toBeChecked();
        expect(within(dialog).getByText('anderer Lieferant als diese Bestellung')).toBeInTheDocument();
        await user.click(within(dialog).getByRole('button', { name: '1 Position übernehmen' }));
        expect(await screen.findByText('Schraube M8')).toBeInTheDocument();

        const menge = screen.getByLabelText('Menge Schraube M8');
        expect(menge).toHaveValue('80');
        await user.clear(menge);
        await user.type(menge, '100');
        await user.click(screen.getByRole('button', { name: 'Entwurf speichern' }));

        await waitFor(() => expect(aufrufe('POST', '/api/lieferanten-bestellungen')).toHaveLength(1));
        expect(body(aufrufe('POST', '/api/lieferanten-bestellungen')[0])).toEqual({ lieferantId: 3, notiz: null,
            positionen: [{ artikelId: 11, menge: 100, einheit: 'Stk', einzelpreis: 0.15, herkunft: 'LAGER', projektId: null, artikelInProjektId: null, kommentar: null }] });
        expect(await screen.findByText('BESTELLUNG (ENTWURF)')).toBeInTheDocument();
    });

    it('Material eines Projekts: Projekt wählen, Vorschlag übernehmen, Projekt und Materialposition werden mitgeschickt; ohne Lieferant wird keiner geraten', async () => {
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/neu');

        await user.click(await screen.findByRole('button', { name: /Material eines Projekts/ }));
        await user.click(screen.getByRole('button', { name: /Projekt 7 wählen/ }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText(/2026\/014 Anbau Halle/)).toBeInTheDocument();
        expect(within(dialog).getByText('Kein Lieferant hinterlegt')).toBeInTheDocument();
        await user.click(within(dialog).getByRole('button', { name: '1 Position übernehmen' }));

        expect(await screen.findByText('Rohr 40x40')).toBeInTheDocument();
        expect(screen.getAllByText('2026/014 Anbau Halle').length).toBeGreaterThan(0);
        await user.click(screen.getByRole('button', { name: 'Entwurf speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Bitte einen Lieferanten wählen.');
        expect(aufrufe('POST', '/api/lieferanten-bestellungen')).toHaveLength(0);
    });

    it('dieselbe Projektposition lässt sich nicht zweimal übernehmen', async () => {
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/neu');
        for (let i = 0; i < 2; i++) {
            await user.click(await screen.findByRole('button', { name: /Material eines Projekts/ }));
            await user.click(screen.getByRole('button', { name: /Projekt 7 wählen/ }));
            await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '1 Position übernehmen' }));
        }

        expect(screen.getAllByLabelText('Menge Rohr 40x40')).toHaveLength(1);
    });

    it('freier Artikel: Menge wird geprüft, dann steht die Zeile da', async () => {
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/neu');
        await screen.findByRole('button', { name: /Lieferant wählen/ });

        await user.clear(screen.getByLabelText('Menge'));
        await user.type(screen.getByLabelText('Menge'), '0');
        await user.click(screen.getByRole('button', { name: 'Artikel wählen (Test)' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Menge größer als 0');
        await user.clear(screen.getByLabelText('Menge'));
        await user.type(screen.getByLabelText('Menge'), '12,5');
        await user.click(screen.getByRole('button', { name: 'Artikel wählen (Test)' }));

        expect(screen.getByLabelText('Menge Schraube M8')).toHaveValue('12,5');
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('Entwurf: Bestellen per E-Mail speichert, vergibt die Nummer und öffnet die E-Mail mit Bestellnummer im Betreff; Senden schickt das PDF mit', async () => {
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/9');
        await screen.findByLabelText('Menge Artikel 1');

        await user.click(screen.getByRole('button', { name: /Bestellen …/ }));
        await user.click(await screen.findByRole('button', { name: /Bestellen und per E-Mail senden/ }));

        const mail = await screen.findByRole('dialog', { name: /Bestellung B-2026-10-0007 per E-Mail senden/ });
        expect(aufrufe('PUT', '/api/lieferanten-bestellungen/9')).toHaveLength(1);
        expect(body(aufrufe('POST', '/bestellen')[0])).toEqual({ per: 'EMAIL' });
        expect(within(mail).getByLabelText('An')).toHaveValue('bestellung@stahl.example.org');
        expect(within(mail).getByLabelText('Betreff')).toHaveValue('Bestellung B-2026-10-0007');

        await user.click(within(mail).getByRole('button', { name: /E-Mail senden/ }));

        await waitFor(() => expect(aufrufe('POST', '/api/emails/send')).toHaveLength(1));
        const form = (aufrufe('POST', '/api/emails/send')[0][1] as RequestInit).body as FormData;
        const dto = JSON.parse(await (form.get('dto') as Blob).text());
        expect(dto).toMatchObject({ recipients: ['bestellung@stahl.example.org'], subject: 'Bestellung B-2026-10-0007', lieferantId: 3, direction: 'OUT' });
        expect(dto.body).toContain('B-2026-10-0007');
        expect((form.get('attachments') as File).name).toBe('Bestellung_B-2026-10-0007.pdf');
        await waitFor(() => expect(screen.queryByRole('dialog', { name: /per E-Mail senden/ })).not.toBeInTheDocument());
    });

    it('E-Mail: ungültige Adresse sperrt das Senden; Fehler beim Senden steht im Dialog', async () => {
        aktuelle = bestellung({ status: 'BESTELLT', lieferstand: 'BESTELLT', bestellnummer: 'B-2026-10-0007', bestelltAm: '2026-10-05T09:00:00', bestelltPer: 'EMAIL', lieferantEmails: [] });
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/9');
        await user.click(await screen.findByRole('button', { name: /E-Mail erneut senden/ }));

        const mail = await screen.findByRole('dialog');
        expect(within(mail).getByText(/noch keine E-Mail-Adresse hinterlegt/)).toBeInTheDocument();
        expect(within(mail).getByRole('button', { name: /E-Mail senden/ })).toBeDisabled();
        await user.type(within(mail).getByLabelText('An'), 'kein-at-zeichen');
        expect(within(mail).getByRole('button', { name: /E-Mail senden/ })).toBeDisabled();
        await user.clear(within(mail).getByLabelText('An'));
        await user.type(within(mail).getByLabelText('An'), 'bestellung@stahl.example.org');
        fetcher.mockImplementation(async (url: string) => (url === '/api/emails/send' ? antwort({}, 500) : antwort({})));
        await user.click(within(mail).getByRole('button', { name: /E-Mail senden/ }));

        expect(await within(mail).findByRole('alert')).toHaveTextContent('E-Mail-Einstellungen');
    });

    it('„Schon bestellt" (telefonisch) bestellt ohne E-Mail', async () => {
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/9');
        await screen.findByLabelText('Menge Artikel 1');

        await user.click(screen.getByRole('button', { name: /Bestellen …/ }));
        await user.click(await screen.findByRole('button', { name: /Schon bestellt/ }));

        await waitFor(() => expect(aufrufe('POST', '/bestellen')).toHaveLength(1));
        expect(body(aufrufe('POST', '/bestellen')[0])).toEqual({ per: 'ANDERS' });
        expect(await screen.findByText(/BESTELLUNG B-2026-10-0007/)).toBeInTheDocument();
        expect(screen.queryByRole('dialog', { name: /per E-Mail senden/ })).not.toBeInTheDocument();
        expect(screen.getByText(/telefonisch oder auf anderem Weg/)).toBeInTheDocument();
    });

    it('bestellte Bestellung: nicht mehr änderbar, Lieferung abhaken und „Alles geliefert"', async () => {
        aktuelle = bestellung({ status: 'BESTELLT', lieferstand: 'BESTELLT', bestellnummer: 'B-2026-10-0007', bestelltAm: '2026-10-05T09:00:00', bestelltPer: 'EMAIL' });
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/9');

        await screen.findByText('Artikel 1');
        expect(screen.queryByLabelText('Menge Artikel 1')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Entwurf speichern' })).not.toBeInTheDocument();
        await user.click(screen.getByRole('checkbox', { name: 'Artikel 1 geliefert' }));
        await waitFor(() => expect(aufrufe('POST', '/positionen/1/geliefert')).toHaveLength(1));
        expect(body(aufrufe('POST', '/positionen/1/geliefert')[0])).toEqual({ geliefert: true });
        await user.click(await screen.findByRole('button', { name: /Alles geliefert/ }));
        await waitFor(() => expect(aufrufe('POST', '/alles-geliefert')).toHaveLength(1));
        expect(screen.getAllByRole('checkbox').every((c) => (c as HTMLInputElement).checked)).toBe(true);
        expect(screen.queryByRole('button', { name: /Alles geliefert/ })).not.toBeInTheDocument();
    });

    it('Stornieren verlangt einen Grund; mit Grund geht der Aufruf raus', async () => {
        aktuelle = bestellung({ status: 'BESTELLT', lieferstand: 'BESTELLT', bestellnummer: 'B-2026-10-0007', bestelltAm: '2026-10-05T09:00:00', bestelltPer: 'EMAIL' });
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/9');

        await user.click(await screen.findByRole('button', { name: /Stornieren …/ }));
        const dialog = await screen.findByRole('dialog', { name: 'Bestellung stornieren?' });
        await user.click(within(dialog).getByRole('button', { name: 'Stornieren' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('warum');
        expect(aufrufe('POST', '/stornieren')).toHaveLength(0);

        await user.click(await screen.findByRole('button', { name: /Stornieren …/ }));
        await user.type(await screen.findByLabelText('Warum wird storniert?'), 'Lieferant liefert nicht mehr');
        await user.click(within(screen.getByRole('dialog', { name: 'Bestellung stornieren?' })).getByRole('button', { name: 'Stornieren' }));

        await waitFor(() => expect(aufrufe('POST', '/stornieren')).toHaveLength(1));
        expect(body(aufrufe('POST', '/stornieren')[0])).toEqual({ grund: 'Lieferant liefert nicht mehr' });
        expect(await screen.findByText(/Storniert am .*: Lieferant liefert nicht mehr/)).toBeInTheDocument();
    });

    it('Entwurf löschen fragt nach und geht zurück zur Übersicht', async () => {
        const user = userEvent.setup();
        zeige('/bestellungen/bestellung/9');

        await user.click(await screen.findByRole('button', { name: /Entwurf löschen/ }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));

        await waitFor(() => expect(aufrufe('DELETE', '/api/lieferanten-bestellungen/9')).toHaveLength(1));
        await waitFor(() => expect(screen.getByTestId('ort')).toHaveTextContent('/bestellungen/liste'));
    });

    it('Rechte: nur ansehen heißt kein Bestellen, kein Löschen, kein Speichern', async () => {
        rechte = { ANLEGEN: false, AENDERN: false, LOESCHEN: false };
        zeige('/bestellungen/bestellung/9');

        await screen.findByLabelText('Menge Artikel 1');
        expect(screen.getByRole('button', { name: 'Entwurf speichern' })).toBeDisabled();
        expect(screen.getByRole('button', { name: /Bestellen …/ })).toBeDisabled();
        expect(screen.queryByRole('button', { name: /Entwurf löschen/ })).not.toBeInTheDocument();
    });

    it('unbekannte Bestellung: Meldung des Servers und Weg zurück', async () => {
        fetcher.mockImplementation(async () => antwort({ message: 'Diese Bestellung gibt es nicht.' }, 404));
        zeige('/bestellungen/bestellung/99');

        expect(await screen.findByRole('alert')).toHaveTextContent('Diese Bestellung gibt es nicht.');
        expect(screen.getByRole('link', { name: /Zur Übersicht/ })).toHaveAttribute('href', '/bestellungen/liste');
    });
});
