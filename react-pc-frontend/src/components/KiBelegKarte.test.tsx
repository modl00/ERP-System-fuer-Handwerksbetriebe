import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KiBelegKarte } from './KiBelegKarte';

/** Nur Dummy-Daten. */
const BELEG = { dokumentTyp: 'RECHNUNG', dokumentNummer: 'R-100', dokumentDatum: '2026-10-01', betragNetto: 100, betragBrutto: 119, lieferantName: 'Muster Stahlhandel GmbH', mwstSatz: 19 };
const EINDEUTIG = { beleg: BELEG, lieferantId: 1, lieferantName: 'Muster Stahlhandel GmbH', kandidaten: [{ id: 1, name: 'Muster Stahlhandel GmbH', aehnlichkeit: 1 }], hinweise: [] };

let aufrufe: { url: string; methode: string; form?: FormData }[];
let antworten: Record<string, () => Response>;
const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
    aufrufe = [];
    antworten = {};
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        aufrufe.push({ url: String(url), methode: init?.method ?? 'GET', form: init?.body instanceof FormData ? init.body : undefined });
        const treffer = Object.entries(antworten).find(([k]) => String(url).includes(k));
        return treffer ? treffer[1]() : json({});
    }));
});
afterEach(() => { vi.unstubAllGlobals(); });

const datei = () => new File(['%PDF'], 'rechnung.pdf', { type: 'application/pdf' });
async function auswaehlen(user: ReturnType<typeof userEvent.setup>) {
    await user.upload(screen.getByLabelText('Beleg auswählen'), datei());
}

describe('Beleg im KI-Chat', { timeout: 30000 }, () => {
    it('wertet den Beleg aus und zeigt einen Vorschlag; gespeichert wird noch nichts', async () => {
        const user = userEvent.setup();
        antworten['/beleg/pruefen'] = () => json(EINDEUTIG);
        render(<KiBelegKarte />);

        await auswaehlen(user);

        const karte = await screen.findByTestId('ki-beleg-vorschlag');
        expect(karte).toHaveTextContent('Eingangsrechnung');
        expect(karte).toHaveTextContent('Muster Stahlhandel GmbH');
        expect(screen.getByLabelText('Nummer')).toHaveValue('R-100');
        expect(screen.getByRole('radio', { name: 'Muster Stahlhandel GmbH' })).toBeChecked();
        expect(karte).toHaveTextContent('Es wird nichts gespeichert, bevor Sie bestätigen.');
        expect(aufrufe.filter(a => a.url.includes('/import'))).toHaveLength(0);
    });

    it('legt nach „Ja, ablegen" über den Import des Lieferanten ab, mit den geprüften Angaben', async () => {
        const user = userEvent.setup();
        antworten['/beleg/pruefen'] = () => json(EINDEUTIG);
        antworten['/api/lieferanten/1/dokumente/import'] = () => json({ ok: true });
        render(<KiBelegKarte />);
        await auswaehlen(user);
        const nummer = await screen.findByLabelText('Nummer');

        await user.clear(nummer);
        await user.type(nummer, 'R-101');
        await user.selectOptions(screen.getByLabelText('Art'), 'LIEFERSCHEIN');
        await user.click(screen.getByRole('button', { name: /Ja, ablegen/ }));

        await waitFor(() => expect(aufrufe.some(a => a.url === '/api/lieferanten/1/dokumente/import')).toBe(true));
        const form = aufrufe.find(a => a.url.endsWith('/import'))!.form!;
        expect((form.get('datei') as File).name).toBe('rechnung.pdf');
        const meta = JSON.parse(await (form.get('metadata') as Blob).text());
        expect(meta).toMatchObject({ dokumentTyp: 'LIEFERSCHEIN', dokumentNummer: 'R-101', lieferantId: 1, betragBrutto: 119 });
        expect(meta.lieferantName).toBeUndefined();
        expect(await screen.findByRole('status')).toHaveTextContent('Abgelegt bei Muster Stahlhandel GmbH: Lieferschein R-101.');
    });

    it('ohne eindeutigen Lieferanten bleibt „Ja, ablegen" gesperrt, bis einer gewählt ist; Hinweise stehen da', async () => {
        const user = userEvent.setup();
        antworten['/beleg/pruefen'] = () => json({ beleg: BELEG, lieferantId: null, lieferantName: null,
            kandidaten: [{ id: 1, name: 'Muster Stahlhandel GmbH', aehnlichkeit: 0.8 }, { id: 2, name: 'Muster Schrauben KG', aehnlichkeit: 0.5 }], hinweise: ['Der Lieferant ist nicht eindeutig. Bitte einen der Vorschläge wählen.'] });
        render(<KiBelegKarte />);
        await auswaehlen(user);

        const ablegen = await screen.findByRole('button', { name: /Ja, ablegen/ });
        expect(ablegen).toBeDisabled();
        expect(screen.getByText(/nicht eindeutig/)).toBeVisible();
        await user.click(screen.getByRole('radio', { name: 'Muster Schrauben KG' }));
        expect(ablegen).toBeEnabled();
    });

    it('bei unbekanntem Lieferanten gibt es keinen Knopf zum Anlegen, nur den Hinweis', async () => {
        const user = userEvent.setup();
        antworten['/beleg/pruefen'] = () => json({ beleg: BELEG, lieferantId: null, lieferantName: null, kandidaten: [], hinweise: ['Den Lieferanten „X" gibt es noch nicht.'] });
        render(<KiBelegKarte />);
        await auswaehlen(user);

        expect(await screen.findByText(/Kein passender Lieferant gefunden/)).toBeVisible();
        expect(screen.getByRole('button', { name: /Ja, ablegen/ })).toBeDisabled();
    });

    it('Serverfehler beim Auswerten und beim Ablegen stehen im Klartext da; Verwerfen setzt zurück', async () => {
        const user = userEvent.setup();
        antworten['/beleg/pruefen'] = () => json({ message: 'Nach der Einstellung „Wie weit darf die KI gehen?" darf ein Beleg nicht an einen Cloud-Anbieter gehen.' }, 403);
        const { unmount } = render(<KiBelegKarte />);
        await auswaehlen(user);
        expect(await screen.findByRole('alert')).toHaveTextContent('Cloud-Anbieter');
        unmount();

        antworten['/beleg/pruefen'] = () => json(EINDEUTIG);
        antworten['/import'] = () => json({ message: "Ein Dokument mit der Nummer 'R-100' existiert bereits für diesen Lieferanten." }, 409);
        render(<KiBelegKarte />);
        await auswaehlen(user);
        await user.click(await screen.findByRole('button', { name: /Ja, ablegen/ }));
        expect(await screen.findByRole('alert')).toHaveTextContent('existiert bereits');

        await user.click(screen.getByRole('button', { name: /Verwerfen/ }));
        expect(within(screen.getByTestId('ki-beleg')).getByRole('button', { name: 'Beleg auswerten und ablegen' })).toBeVisible();
        expect(screen.queryByRole('alert')).toBeNull();
    });
});
