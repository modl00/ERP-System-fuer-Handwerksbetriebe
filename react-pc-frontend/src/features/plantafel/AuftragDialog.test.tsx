import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { AuftragDialog } from './AuftragDialog';
import type { Auftrag, Vorgang } from './plantafelApi';

/** Nur Dummy-Daten. */
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
const vorgang = (id: number, extra: Partial<Vorgang> = {}): Vorgang => ({
    id, typId: id, typName: 'Fertigung', farbe: '#be123c', bezeichnung: `Vorgang ${id}`, dauerTage: 2, dauerHerkunft: 'GESCHAETZT', festerStart: null, start: '2026-10-12', ende: '2026-10-13', konflikt: false,
    status: 'GEPLANT', notiz: null, vorgaenger: [], mitarbeiterIds: [], ressourceId: null, prioritaet: 'NORMAL', aufwandStunden: 16, aufwandHerkunft: 'GESCHAETZT', fruehestensAb: null, rang: null, hinweis: null,
    wartet: false, buero: false, ohneZuweisungGeschaetzt: false, stand: 0, lieferantId: null, lieferantName: null, bestelltAm: null, lieferterminLieferant: null, fremdleistungId: null, menge: null, mengenEinheit: null, istStunden: null, ...extra,
});
const auftrag: Auftrag = { projektId: 7, bezeichnung: 'Geländer Musterstraße', auftragsnummer: 'A-1', startDatum: null, zielDatum: '2026-11-30', liefertermin: '2026-11-20', ampel: 'OK', vorgaenge: [vorgang(1), vorgang(2, { wartet: true, aufwandStunden: null, dauerTage: 5 })] };

let fetcher: ReturnType<typeof vi.fn>;
let aufrufe: { url: string; methode: string; body: unknown }[];
beforeEach(() => {
    aufrufe = [];
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        aufrufe.push({ url: String(url), methode: init?.method ?? 'GET', body: init?.body ? JSON.parse(String(init.body)) : null });
        if (String(url).endsWith('/api/plantafel/auftraege/7') && init?.method === 'DELETE') return json({ ok: true });
        return json(auftrag);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const props = () => ({ onSchliessen: vi.fn(), onAuftrag: vi.fn(), onEntfernt: vi.fn(), onVorgang: vi.fn(), onAktion: vi.fn() });
const zeige = (p = props()) => { render(<ToastProvider><ConfirmProvider><AuftragDialog offen auftrag={auftrag} {...p} /></ConfirmProvider></ToastProvider>); return p; };

describe('AuftragDialog', { timeout: 30000 }, () => {
    it('zeigt den ganzen Auftrag mit Lieferzeit, Ampel und allen Vorgängen', async () => {
        zeige();

        expect(await screen.findByText('Geländer Musterstraße')).toBeInTheDocument();
        expect(screen.getByText(/Lieferzeit 20\.11\.2026 · im Plan/)).toBeInTheDocument();
        expect(screen.getByTestId('auftrag-vorgang-1')).toHaveTextContent('16 Std.');
        expect(screen.getByTestId('auftrag-vorgang-2')).toHaveTextContent('5 Arbeitstage');
    });

    it('speichert die Termine', async () => {
        const user = userEvent.setup();
        const p = zeige();
        await user.click(await screen.findByRole('button', { name: 'Termine speichern' }));

        await waitFor(() => expect(aufrufe.some(a => a.url.endsWith('/auftraege/7/termine') && a.methode === 'PUT')).toBe(true));
        expect(aufrufe.find(a => a.url.endsWith('/termine'))?.body).toEqual({ start: null, ziel: '2026-11-30' });
        await waitFor(() => expect(p.onAuftrag).toHaveBeenCalled());
    });

    it('übernimmt die Wichtigkeit für alle Vorgänge', async () => {
        const user = userEvent.setup();
        zeige();
        await user.selectOptions(await screen.findByLabelText('Wichtigkeit des Auftrags'), 'DRINGEND');
        await user.click(screen.getByRole('button', { name: 'Für alle Vorgänge übernehmen' }));

        await waitFor(() => expect(aufrufe.find(a => a.url.endsWith('/prioritaet'))?.body).toEqual({ prioritaet: 'DRINGEND' }));
    });

    it('öffnet einen Vorgang zum Bearbeiten und legt einen neuen an', async () => {
        const user = userEvent.setup();
        const p = zeige();
        await user.click(await screen.findByRole('button', { name: 'Vorgang 2 bearbeiten' }));
        expect(p.onVorgang).toHaveBeenCalledWith(7, 2);
        await user.click(screen.getByRole('button', { name: /Vorgang hinzufügen/ }));
        expect(p.onVorgang).toHaveBeenCalledWith(7, null);
        await user.click(screen.getByRole('button', { name: 'Termin erklären' }));
        expect(p.onAktion).toHaveBeenCalledWith('erklaeren');
    });

    it('entfernt den Auftrag erst nach der Rückfrage aus der Plantafel', async () => {
        const user = userEvent.setup();
        const p = zeige();
        await user.click(await screen.findByRole('button', { name: /Aus der Plantafel entfernen/ }));
        expect(await screen.findByText(/Sollen alle 2 Vorgänge von „Geländer Musterstraße" aus der Plantafel entfernt werden\?/)).toBeInTheDocument();
        expect(aufrufe.some(a => a.methode === 'DELETE')).toBe(false);
        await user.click(screen.getByRole('button', { name: 'Entfernen' }));

        await waitFor(() => expect(p.onEntfernt).toHaveBeenCalledWith(7));
        expect(aufrufe.some(a => a.methode === 'DELETE' && a.url.endsWith('/api/plantafel/auftraege/7'))).toBe(true);
    });

    it('bei „Abbrechen" bleibt der Auftrag in der Plantafel', async () => {
        const user = userEvent.setup();
        const p = zeige();
        await user.click(await screen.findByRole('button', { name: /Aus der Plantafel entfernen/ }));
        await user.click(await screen.findByRole('button', { name: /Abbrechen|Nein/ }));

        expect(p.onEntfernt).not.toHaveBeenCalled();
        expect(aufrufe.some(a => a.methode === 'DELETE')).toBe(false);
    });
});
