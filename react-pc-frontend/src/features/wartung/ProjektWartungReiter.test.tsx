import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjektWartungReiter } from './ProjektWartungReiter';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { setzeFeaturesStand, setzeFeaturesZurueck } from '../modulschalter/useFeatures';
import type { AusgangsGeschaeftsDokument } from '../../types';

const dok = (id: number, typ: string, nummer: string, datum: string, netto: number, extra: Partial<AusgangsGeschaeftsDokument> = {}) =>
    ({ id, typ, dokumentNummer: nummer, datum, betragNetto: netto, gebucht: true, storniert: false, bearbeitbar: false, ...extra }) as AusgangsGeschaeftsDokument;

const wartung = {
    id: 3, bezeichnung: 'Heizungsanlage', objektBezeichnung: 'Werk Nord', kundeName: 'Max Mustermann GmbH', module: 'Heizung', naechsteFaelligkeit: '2999-04-01',
    letztePruefungDatum: '2026-10-01', anzahlPruefungen: 2, offenerFeldeinsatzId: null, offenerFeldeinsatzNummer: null,
    berichte: [{ protokollId: 11, nummer: 'WA-2026-1002', datum: '2027-04-01', url: '/api/dokumente/b2.pdf', dateiname: 'B2.pdf' },
        { protokollId: 10, nummer: 'WA-2026-1001', datum: '2026-10-01', url: '/api/dokumente/b1.pdf', dateiname: 'B1.pdf' }],
};

beforeEach(() => {
    setzeFeaturesZurueck();
    setzeFeaturesStand({ wartung: true });
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
        if (url.endsWith('/vertrag')) return { ok: true, status: 204, json: async () => ({}) } as Response;
        if (url.endsWith('/wartungen')) return { ok: true, status: 200, json: async () => [wartung] } as Response;
        return { ok: true, status: 200, json: async () => [] } as Response;
    }));
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (dokumente: AusgangsGeschaeftsDokument[], dateien: number, onOeffne = vi.fn()) => {
    render(<MemoryRouter><ToastProvider><ConfirmProvider>
        <ProjektWartungReiter projektId={3} kundeId={1} dokumente={dokumente} dateienAnzahl={dateien} onOeffneReiter={onOeffne} />
    </ConfirmProvider></ToastProvider></MemoryRouter>);
    return onOeffne;
};

describe('ProjektWartungReiter', () => {
    it('zeigt Vertrag, Objekte und Wartungen und die Projektmappe', async () => {
        zeige([dok(1, 'ANGEBOT', 'AN-1', '2026-09-01', 1000), dok(2, 'RECHNUNG', 'RE-1', '2026-10-02', 500, { storniert: true })], 4);

        expect(await screen.findByTestId('wartungsvertrag')).toBeVisible();
        expect(await screen.findByText('Heizungsanlage')).toBeVisible();
        expect(screen.getByText('Objekte und Wartungen')).toBeVisible();

        const mappe = screen.getByTestId('projektmappe');
        await waitFor(() => expect(within(mappe).getByText('Prüfberichte (2)')).toBeVisible());
        const berichte = within(mappe).getAllByRole('link');
        expect(berichte[0]).toHaveTextContent('01.04.2027');       // neuester Bericht zuerst
        expect(berichte[0]).toHaveAttribute('href', '/api/dokumente/b2.pdf');
        expect(within(mappe).getByText('Angebote und Rechnungen (2)')).toBeVisible();
        expect(mappe).toHaveTextContent('Rechnung RE-1');
        expect(mappe).toHaveTextContent('(storniert)');
        expect(within(mappe).getByText('Dokumente (4)')).toBeVisible();
        expect(mappe).toHaveTextContent('4 Dateien im Projekt');
    });

    it('führt über die Knöpfe in die Reiter Geschäftsdokumente und Dokumente', async () => {
        const user = userEvent.setup();
        const onOeffne = zeige([], 0);
        await screen.findByTestId('projektmappe');

        await user.click(screen.getByRole('button', { name: 'Alle Geschäftsdokumente öffnen' }));
        await user.click(screen.getByRole('button', { name: 'Dokumente öffnen' }));

        expect(onOeffne).toHaveBeenNthCalledWith(1, 'geschaeftsdokumente');
        expect(onOeffne).toHaveBeenNthCalledWith(2, 'dokumente');
        expect(screen.getByText('Noch keine Geschäftsdokumente.')).toBeVisible();
        expect(screen.getByText('Noch keine Dateien abgelegt.')).toBeVisible();
    });

    it('verweist für neue Objekte auf den Kunden', async () => {
        zeige([], 0);
        const link = await screen.findByRole('link', { name: /Zum Kunden/ });
        expect(link).toHaveAttribute('href', '/kunden?kundeId=1');
    });
});
