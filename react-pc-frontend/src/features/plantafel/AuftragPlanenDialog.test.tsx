import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { AuftragPlanenDialog } from './AuftragPlanenDialog';
import { AuftragPlanenHost } from './AuftragPlanenHost';
import { meldeAuftragsbestaetigung, type Aufnahme } from './plantafelApi';

/** Nur Dummy-Daten. */
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
const aufnahmeDaten = (extra: Partial<Aufnahme> = {}): Aufnahme => ({
    projektId: 7, bezeichnung: 'Geländer Musterstraße', auftragsnummer: 'A-1', bereitsGeplant: false, offen: false, quelle: 'AUFTRAGSBESTAETIGUNG',
    fremdleistungen: [{ fremdleistungId: 3, bezeichnung: 'Feuerverzinken', lieferantId: 21, lieferantName: 'Verzinkerei Muster', einheit: 'kg', menge: 250.5, lieferzeitTage: 6 }],
    arten: [], hinweise: [], arbeitstagStunden: 8, hatArbeitszeit: true, ...extra,
});

let fetcher: ReturnType<typeof vi.fn>;
let aufnahme: Aufnahme;
let fensterAktiv: boolean;
let gesendet: Record<string, unknown> | null;
let fensterGesetzt: boolean | null;

beforeEach(() => {
    aufnahme = aufnahmeDaten();
    fensterAktiv = true;
    gesendet = null;
    fensterGesetzt = null;
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const u = String(url);
        if (u.endsWith('/aufnahme')) return json(aufnahme);
        if (u.endsWith('/api/plantafel/personen')) return json([{ id: 4, name: 'Max Mustermann' }, { id: 5, name: 'Erika Musterfrau' }]);
        if (u.endsWith('/planen') && init?.method === 'POST') {
            gesendet = JSON.parse(String(init.body));
            return json({ projektId: 7, bezeichnung: 'Geländer Musterstraße', auftragsnummer: 'A-1', startDatum: null, zielDatum: null, liefertermin: null, ampel: 'OHNE_ZIEL', vorgaenge: [] });
        }
        if (u.endsWith('/api/plantafel/ab-fenster') && init?.method === 'PUT') {
            fensterGesetzt = JSON.parse(String(init.body)).aktiv;
            return json({ aktiv: fensterGesetzt });
        }
        if (u.endsWith('/api/plantafel/ab-fenster')) return json({ aktiv: fensterAktiv });
        return json({}, 404);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (props: Partial<React.ComponentProps<typeof AuftragPlanenDialog>> = {}) =>
    render(<ToastProvider><AuftragPlanenDialog offen projektId={7} onSchliessen={() => undefined} {...props} /></ToastProvider>);

describe('AuftragPlanenDialog', { timeout: 30000 }, () => {
    it('zeigt den Auftrag mit der erkannten Fremdleistung, vorausgewählt', async () => {
        zeige();

        expect(await screen.findByText(/Geländer Musterstraße \(A-1\)/)).toBeInTheDocument();
        const haken = await screen.findByRole('checkbox', { name: /Feuerverzinken/ });
        expect(haken).toBeChecked();
        expect(screen.getByText(/bei Verzinkerei Muster, 250,5 kg, übliche Lieferzeit 6 Arbeitstage/)).toBeInTheDocument();
        expect(screen.getByText(/Der Aufwand je Abteilung kommt aus der Kalkulation/)).toBeInTheDocument();
    });

    it('ohne erkannte Fremdleistung steht „Keine Fremdleistung erkannt“', async () => {
        aufnahme = aufnahmeDaten({ fremdleistungen: [], hatArbeitszeit: false });
        zeige();

        expect(await screen.findByTestId('keine-fremdleistung')).toHaveTextContent('Keine Fremdleistung erkannt');
        expect(screen.getByText(/keine berechnete Arbeitszeit/)).toBeInTheDocument();
    });

    it('„Übernehmen“ schickt Wichtigkeit, Fremdleistung, Montage und Wunschpersonen', async () => {
        const user = userEvent.setup();
        const geplant = vi.fn();
        zeige({ onGeplant: geplant });
        await user.selectOptions(await screen.findByLabelText('Wie wichtig'), 'DRINGEND');
        await user.click(screen.getByLabelText('Ja'));
        const tage = screen.getByLabelText('Montage in Tagen');
        await user.clear(tage);
        await user.type(tage, '3');
        await user.click(screen.getByRole('checkbox', { name: 'Max Mustermann' }));
        await user.click(screen.getByRole('button', { name: /Übernehmen/ }));

        await waitFor(() => expect(gesendet).not.toBeNull());
        expect(gesendet).toMatchObject({
            spaeter: false, prioritaet: 'DRINGEND', zielDatum: null, montageVorOrt: true, montageTage: 3, mitarbeiterIds: [4],
            fremdleistungen: [{ fremdleistungId: 3, bezeichnung: 'Feuerverzinken', lieferantId: 21, menge: 250.5, einheit: 'kg' }],
        });
        await waitFor(() => expect(geplant).toHaveBeenCalled());
    });

    it('eine abgehakte Fremdleistung wird nicht geschickt', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('checkbox', { name: /Feuerverzinken/ }));
        await user.click(screen.getByRole('button', { name: /Übernehmen/ }));

        await waitFor(() => expect(gesendet).not.toBeNull());
        expect((gesendet as unknown as { fremdleistungen: unknown[] }).fremdleistungen).toEqual([]);
    });

    it('„Später“ überspringt alles', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Später' }));

        await waitFor(() => expect(gesendet).not.toBeNull());
        expect(gesendet).toMatchObject({ spaeter: true });
    });

    it('eine unsinnige Montagedauer wird vor dem Senden abgelehnt', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByLabelText('Ja'));
        const tage = screen.getByLabelText('Montage in Tagen');
        await user.clear(tage);
        await user.type(tage, 'x');
        await user.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Die Montage dauert eine ganze Zahl von Tagen');
        expect(gesendet).toBeNull();
    });

    it('ein schon geplanter Auftrag wird nicht noch einmal gefragt', async () => {
        aufnahme = aufnahmeDaten({ bereitsGeplant: true });
        zeige();

        expect(await screen.findByTestId('schon-geplant')).toHaveTextContent('Dieser Auftrag ist schon in der Plantafel');
        expect(screen.queryByRole('button', { name: /Übernehmen/ })).not.toBeInTheDocument();
    });

    it('kommt das Fenster von selbst, lässt es sich abschalten', async () => {
        const user = userEvent.setup();
        const zu = vi.fn();
        zeige({ vonSelbst: true, onSchliessen: zu });
        await user.click(await screen.findByRole('checkbox', { name: /nicht mehr zeigen/ }));
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));

        await waitFor(() => expect(fensterGesetzt).toBe(false));
        expect(zu).toHaveBeenCalled();
    });

    it('über den Knopf im Projekt gibt es die Abschalt-Möglichkeit nicht', async () => {
        zeige();
        await screen.findByRole('button', { name: 'Später' });
        expect(screen.queryByRole('checkbox', { name: /nicht mehr zeigen/ })).not.toBeInTheDocument();
    });
});

describe('AuftragPlanenHost', { timeout: 30000 }, () => {
    const zeigeHost = () => render(<ToastProvider><AuftragPlanenHost /></ToastProvider>);

    it('fragt nach, wenn eine Auftragsbestätigung erstellt wurde', async () => {
        zeigeHost();
        await act(async () => { meldeAuftragsbestaetigung(7); });

        expect(await screen.findByRole('heading', { name: 'Auftrag planen' })).toBeInTheDocument();
        expect(await screen.findByText(/Die Auftragsbestätigung ist erstellt/)).toBeInTheDocument();
    });

    it('bleibt still, wenn der Benutzer das Fenster abgeschaltet hat', async () => {
        fensterAktiv = false;
        zeigeHost();
        await act(async () => { meldeAuftragsbestaetigung(7); });

        await waitFor(() => expect(fetcher.mock.calls.some(([u]) => String(u).endsWith('/ab-fenster'))).toBe(true));
        expect(screen.queryByRole('heading', { name: 'Auftrag planen' })).not.toBeInTheDocument();
    });

    it('bleibt still, wenn der Auftrag schon in der Plantafel steht', async () => {
        aufnahme = aufnahmeDaten({ bereitsGeplant: true });
        zeigeHost();
        await act(async () => { meldeAuftragsbestaetigung(7); });

        await waitFor(() => expect(fetcher.mock.calls.some(([u]) => String(u).endsWith('/aufnahme'))).toBe(true));
        expect(screen.queryByRole('heading', { name: 'Auftrag planen' })).not.toBeInTheDocument();
    });
});
