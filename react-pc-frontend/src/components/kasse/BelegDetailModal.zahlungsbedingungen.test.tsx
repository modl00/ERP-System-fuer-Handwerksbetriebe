import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BelegDetailModal } from './BelegDetailModal';
import { ToastProvider } from '../ui/toast';
import type { Beleg } from '../../types';

/** Nur Dummy-Daten. */
const beleg = (extra: Partial<Beleg> = {}): Beleg => ({
    id: 5, belegKategorie: 'EINGANGSRECHNUNG' as Beleg['belegKategorie'], dokumentTyp: 'RECHNUNG', status: 'OFFEN' as Beleg['status'], kiAnalyseStatus: 'FERTIG' as Beleg['kiAnalyseStatus'],
    belegDatum: '2026-10-01', betragBrutto: 119, betragNetto: 100, mwstSatz: 19, zahlungsart: 'UEBERWEISUNG', lieferantId: 3, lieferantName: 'Muster Stahl GmbH',
    eingangsrechnungId: 90, eingangsrechnungBezahlt: false, kostenstellenSplits: [], ...extra,
}) as Beleg;

let fetcher: ReturnType<typeof vi.fn>;
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;

function stub(detail: Beleg) {
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url === `/api/buchhaltung/belege/${detail.id}` && init?.method === 'PUT') return json(detail);
        if (url === `/api/buchhaltung/belege/${detail.id}`) return json(detail);
        return json([]);
    });
    vi.stubGlobal('fetch', fetcher);
}

const zeige = (b: Beleg) => render(
    <ToastProvider>
        <BelegDetailModal beleg={b} sachkonten={[]} zahlungsarten={[{ id: 1, bezeichnung: 'UEBERWEISUNG', aktiv: true, sortierung: 1 }]} onClose={vi.fn()} onSaved={vi.fn()} onDeleted={vi.fn()} />
    </ToastProvider>,
);
const putBody = () => {
    const aufruf = fetcher.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'PUT');
    return aufruf ? JSON.parse((aufruf[1] as RequestInit).body as string) : null;
};

beforeEach(() => { vi.stubGlobal('scrollTo', vi.fn()); });
afterEach(() => { vi.unstubAllGlobals(); });

describe('BelegDetailModal: Zahlungsbedingungen (#191)', () => {
    it('zeigt unter „Mehr Details“ Nummern, Zahlungsziel und Skonto der Eingangsrechnung', async () => {
        const u = userEvent.setup();
        const b = beleg({ eingangsrechnungBestellnummer: 'B-100', eingangsrechnungReferenzNummer: 'AB-7', eingangsrechnungSkontoProzent: 2, eingangsrechnungSkontoTage: 14, eingangsrechnungNettoTage: 30, eingangsrechnungTatsaechlichGezahlt: 116.62, eingangsrechnungMitSkonto: true });
        stub(b);
        zeige(b);

        await u.click(await screen.findByRole('button', { name: /Mehr Details/ }));

        expect(screen.getByTestId('zahlungsbedingungen')).toBeInTheDocument();
        expect(screen.getByLabelText('Bestellnummer')).toHaveValue('B-100');
        expect(screen.getByLabelText('Referenznummer')).toHaveValue('AB-7');
        expect(screen.getByLabelText('Skonto-Tage')).toHaveValue('14');
        expect(screen.getByLabelText('Netto-Tage')).toHaveValue('30');
        expect(screen.getByRole('checkbox', { name: /Mit Skonto bezahlt/ })).toBeChecked();
    });

    it('schickt geänderte Zahlungsbedingungen beim Speichern mit', async () => {
        const u = userEvent.setup();
        const b = beleg();
        stub(b);
        zeige(b);

        await u.click(await screen.findByRole('button', { name: /Mehr Details/ }));
        await u.type(screen.getByLabelText('Bestellnummer'), 'B-200');
        await u.type(screen.getByLabelText('Skonto-Tage'), '10');
        await u.click(screen.getByRole('button', { name: /Zwischenspeichern/ }));

        await waitFor(() => expect(putBody()).not.toBeNull());
        expect(putBody()).toMatchObject({ bestellnummer: 'B-200', skontoTage: 10, mitSkonto: false });
    });

    it('weist unsinnige Tage ab, ohne zu speichern', async () => {
        const u = userEvent.setup();
        const b = beleg();
        stub(b);
        zeige(b);

        await u.click(await screen.findByRole('button', { name: /Mehr Details/ }));
        await u.type(screen.getByLabelText('Skonto-Tage'), '1x');
        await u.click(screen.getByRole('button', { name: /Zwischenspeichern/ }));

        expect(await screen.findAllByText(/Skonto-Tage: Bitte eine ganze Zahl/)).not.toHaveLength(0);
        expect(putBody()).toBeNull();
    });

    it('zeigt den Abschnitt nicht, wenn zum Beleg keine Eingangsrechnung besteht, und schickt nichts davon', async () => {
        const u = userEvent.setup();
        const b = beleg({ eingangsrechnungId: null, dokumentTyp: 'KASSENBON' });
        stub(b);
        zeige(b);

        await u.click(await screen.findByRole('button', { name: /Mehr Details/ }));
        expect(screen.queryByTestId('zahlungsbedingungen')).toBeNull();
        await u.click(screen.getByRole('button', { name: /Zwischenspeichern/ }));

        await waitFor(() => expect(putBody()).not.toBeNull());
        expect(putBody()).not.toHaveProperty('bestellnummer');
        expect(putBody()).not.toHaveProperty('skontoTage');
    });
});
