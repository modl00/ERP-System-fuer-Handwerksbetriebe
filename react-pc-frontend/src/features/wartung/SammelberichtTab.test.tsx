import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SammelberichtTab } from './SammelberichtTab';
import { ToastProvider } from '../../components/ui/toast';
import type { WartungListeneintrag } from './types';

const eintrag = (extra: Partial<WartungListeneintrag> = {}): WartungListeneintrag => ({
    id: 1, nummer: 'WA-2026-1001', titel: null, protokollTyp: 'Wartungsprotokoll', datum: '2026-05-10', naechsteWartung: null,
    status: 'ABGESCHLOSSEN', kundeId: 1, kundeName: 'Max Mustermann GmbH', objektId: 2, objektBezeichnung: 'Werk Nord', tuerId: 3, tuerBezeichnung: 'Tür 1.01',
    pruefpunkteGesamt: 10, pruefpunkteBewertet: 10, maengel: 0, ...extra,
});

const seite = (content: WartungListeneintrag[], extra: Record<string, unknown> = {}) => ({
    content, totalElements: content.length, totalPages: 1, number: 0, size: 100, ...extra,
});

function json(status: number, body: unknown): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

function pdfAntwort(): Response {
    return {
        ok: true, status: 200, blob: async () => new Blob(['%PDF-1.4'], { type: 'application/pdf' }),
        headers: new Headers({ 'Content-Disposition': "inline; filename*=UTF-8''Bericht_M%C3%BCller.pdf" }),
    } as Response;
}

let fetcher: ReturnType<typeof vi.fn>;
let fenster: { closed: boolean; location: { href: string }; document: { title: string; body: { style: { cssText: string }; textContent: string } }; close: ReturnType<typeof vi.fn>; opener: unknown };

function stub(liste: WartungListeneintragListe = () => json(200, seite([eintrag(), eintrag({ id: 2, nummer: 'WA-2026-1002', maengel: 1 })])), pdf: () => Response = pdfAntwort) {
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url.startsWith('/api/wartung/protokolle?')) return liste();
        if (url === '/api/wartung/sammelbericht/pdf' && init?.method === 'POST') return pdf();
        return json(404, {});
    });
    vi.stubGlobal('fetch', fetcher);
}
type WartungListeneintragListe = () => Response;

const zeige = () => render(<ToastProvider><MemoryRouter><SammelberichtTab /></MemoryRouter></ToastProvider>);

beforeEach(() => {
    fenster = { closed: false, location: { href: '' }, document: { title: '', body: { style: { cssText: '' }, textContent: '' } }, close: vi.fn(), opener: 'x' };
    vi.stubGlobal('open', vi.fn(() => fenster));
    URL.createObjectURL = vi.fn(() => 'blob:test-pdf');
    URL.revokeObjectURL = vi.fn();
});
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

function postBody() {
    const aufruf = fetcher.mock.calls.find(([url, init]) => url === '/api/wartung/sammelbericht/pdf' && (init as RequestInit)?.method === 'POST');
    return aufruf ? JSON.parse((aufruf[1] as RequestInit).body as string) : null;
}

describe('SammelberichtTab', () => {
    it('lädt zuerst nur abgeschlossene Wartungen und zeigt Nummer, Ort und Mängel', async () => {
        stub();
        zeige();
        expect(await screen.findByText('WA-2026-1001')).toBeVisible();
        expect(screen.getByText('1 Mangel')).toBeVisible();
        expect(screen.getAllByText('Max Mustermann GmbH · Werk Nord · Tür 1.01')).toHaveLength(2);
        const erste = fetcher.mock.calls.find(([url]) => String(url).startsWith('/api/wartung/protokolle?'))![0] as string;
        expect(erste).toContain('status=ABGESCHLOSSEN');
        expect(erste).toContain('groesse=100');
        expect(screen.getByRole('button', { name: /PDF öffnen/ })).toBeDisabled();
        expect(screen.getByRole('button', { name: /Herunterladen/ })).toBeDisabled();
        expect(screen.getByText('Noch nichts ausgewählt.')).toBeVisible();
    });

    it('wählt einzeln oder alle an, zählt mit und leert die Auswahl', async () => {
        stub();
        zeige();
        await screen.findByText('WA-2026-1001');
        await userEvent.click(screen.getByLabelText('WA-2026-1001 in den Bericht aufnehmen'));
        expect(screen.getByText(/1 Wartung ausgewählt/)).toBeVisible();

        await userEvent.click(screen.getByLabelText('Alle 2 angezeigten auswählen'));
        expect(screen.getByText(/2 Wartungen ausgewählt/)).toBeVisible();
        expect(screen.getByRole('button', { name: /PDF öffnen/ })).toBeEnabled();

        await userEvent.click(screen.getByLabelText('Alle 2 angezeigten auswählen'));
        expect(screen.getByText('Noch nichts ausgewählt.')).toBeVisible();

        await userEvent.click(screen.getByLabelText('WA-2026-1002 in den Bericht aufnehmen'));
        await userEvent.click(screen.getByRole('button', { name: 'Auswahl leeren' }));
        expect(screen.getByText('Noch nichts ausgewählt.')).toBeVisible();
    });

    it('schickt die gewählten Nummern mit Titel und Deckblatt-Wunsch und zeigt das PDF im neuen Fenster', async () => {
        stub();
        zeige();
        await screen.findByText('WA-2026-1001');
        await userEvent.click(screen.getByLabelText('Alle 2 angezeigten auswählen'));
        await userEvent.type(screen.getByLabelText('Titel (optional)'), 'Jahreswartung Werk Nord');
        await userEvent.click(screen.getByRole('button', { name: /PDF öffnen/ }));

        await waitFor(() => expect(fenster.location.href).toBe('blob:test-pdf'));
        expect(postBody()).toEqual({ protokollIds: [1, 2], titel: 'Jahreswartung Werk Nord', mitDeckblatt: true });
        expect(window.open).toHaveBeenCalledWith('', '_blank');
        expect(fenster.opener).toBeNull();
    });

    it('lässt das Deckblatt auf Wunsch weg', async () => {
        stub();
        zeige();
        await screen.findByText('WA-2026-1001');
        await userEvent.click(screen.getByLabelText('WA-2026-1001 in den Bericht aufnehmen'));
        await userEvent.click(screen.getByRole('checkbox', { name: /Deckblatt mit Übersicht/ }));
        await userEvent.click(screen.getByRole('button', { name: /PDF öffnen/ }));

        await waitFor(() => expect(postBody()).toEqual({ protokollIds: [1], titel: null, mitDeckblatt: false }));
    });

    it('speichert beim Herunterladen die Datei mit dem Namen vom Server, ohne ein Fenster zu öffnen', async () => {
        stub();
        const klick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
        zeige();
        await screen.findByText('WA-2026-1001');
        await userEvent.click(screen.getByLabelText('WA-2026-1001 in den Bericht aufnehmen'));
        await userEvent.click(screen.getByRole('button', { name: /Herunterladen/ }));

        await waitFor(() => expect(klick).toHaveBeenCalledTimes(1));
        expect(window.open).not.toHaveBeenCalled();
        expect(URL.createObjectURL).toHaveBeenCalled();
    });

    it('lädt das PDF herunter und sagt es, wenn der Browser das neue Fenster blockiert', async () => {
        stub();
        vi.stubGlobal('open', vi.fn(() => null));
        const klick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
        zeige();
        await screen.findByText('WA-2026-1001');
        await userEvent.click(screen.getByLabelText('WA-2026-1001 in den Bericht aufnehmen'));
        await userEvent.click(screen.getByRole('button', { name: /PDF öffnen/ }));

        await waitFor(() => expect(klick).toHaveBeenCalled());
        expect(await screen.findByText(/Der Browser hat das neue Fenster blockiert/)).toBeVisible();
    });

    it('schließt das Wartefenster und zeigt die Meldung des Servers, wenn der Bericht nicht erstellt werden kann', async () => {
        stub(undefined, () => json(404, { message: 'Mindestens ein gewähltes Protokoll wurde nicht gefunden.' }));
        zeige();
        await screen.findByText('WA-2026-1001');
        await userEvent.click(screen.getByLabelText('WA-2026-1001 in den Bericht aufnehmen'));
        await userEvent.click(screen.getByRole('button', { name: /PDF öffnen/ }));

        expect(await screen.findByText('Mindestens ein gewähltes Protokoll wurde nicht gefunden.')).toBeVisible();
        expect(fenster.close).toHaveBeenCalled();
        expect(screen.getByRole('button', { name: /PDF öffnen/ })).toBeEnabled();
    });

    it('weist auf Entwürfe und offene Prüfpunkte hin, ohne zu sperren', async () => {
        stub(() => json(200, seite([
            eintrag(),
            eintrag({ id: 2, nummer: 'WA-2026-1002', status: 'IN_BEARBEITUNG', pruefpunkteBewertet: 4 }),
        ])));
        zeige();
        await screen.findByText('WA-2026-1002');
        await userEvent.click(screen.getByLabelText('Alle 2 angezeigten auswählen'));

        expect(screen.getByText('1 Wartung ist noch nicht abgeschlossen und erscheint als Entwurf.')).toBeVisible();
        expect(screen.getByText('1 Wartung hat noch offene Prüfpunkte.')).toBeVisible();
        expect(screen.getByRole('button', { name: /PDF öffnen/ })).toBeEnabled();
    });

    it('sagt, wenn es mehr passende Wartungen gibt, als angezeigt werden', async () => {
        stub(() => json(200, seite([eintrag()], { totalElements: 340 })));
        zeige();
        expect(await screen.findByText(/Es gibt 340 passende Wartungen, angezeigt werden die ersten 1/)).toBeVisible();
    });

    it('zeigt bei einer leeren Liste einen Hinweis', async () => {
        stub(() => json(200, seite([])));
        zeige();
        expect(await screen.findByText('Keine passenden Wartungen')).toBeVisible();
    });

    it('zeigt nach einem Ladefehler nicht mehr die Treffer der vorherigen Filter', async () => {
        let aufrufe = 0;
        stub(() => (++aufrufe === 1 ? json(200, seite([eintrag()])) : json(500, { message: 'kaputt' })));
        zeige();
        await screen.findByText('WA-2026-1001');

        await userEvent.type(screen.getByLabelText('Suche'), 'Werk');

        // Der Suchtext wird erst nach 350 ms abgeschickt; die Abfrage nach Rolle ist in jsdom langsam, deshalb eine großzügige Frist
        expect(await screen.findByRole('button', { name: 'Noch einmal versuchen' }, { timeout: 4000 })).toBeVisible();
        expect(screen.queryByText('WA-2026-1001')).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/Alle .* angezeigten auswählen/)).not.toBeInTheDocument();
    });

    it('aktualisiert die gemerkte Auswahl, wenn eine neue Liste den Stand eines gewählten Protokolls ändert', async () => {
        let stand: WartungListeneintrag = eintrag({ status: 'IN_BEARBEITUNG', pruefpunkteBewertet: 3 });
        stub(() => json(200, seite([stand])));
        zeige();
        await userEvent.click(await screen.findByLabelText('WA-2026-1001 in den Bericht aufnehmen'));
        expect(screen.getByText('1 Wartung ist noch nicht abgeschlossen und erscheint als Entwurf.')).toBeVisible();

        stand = eintrag({ status: 'ABGESCHLOSSEN', pruefpunkteBewertet: 10 });
        await userEvent.type(screen.getByLabelText('Suche'), 'a');

        await waitFor(() => expect(screen.queryByText(/noch nicht abgeschlossen und erscheint als Entwurf/)).not.toBeInTheDocument());
        expect(screen.getByText(/1 Wartung ausgewählt/)).toBeVisible();
    });

    it('bietet bei einem Ladefehler einen neuen Versuch an', async () => {
        stub(() => json(500, { message: 'kaputt' }));
        zeige();
        const nochmal = await screen.findByRole('button', { name: 'Noch einmal versuchen' });
        stub();
        await userEvent.click(nochmal);
        expect(await screen.findByText('WA-2026-1001')).toBeVisible();
    });
});
