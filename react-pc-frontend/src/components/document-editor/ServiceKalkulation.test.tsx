import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ServiceKalkulation } from './ServiceKalkulation';

/** Nur Dummy-Daten. Die Zahlen rechnet das Backend, hier werden Anzeige, Aufruf und Übernahme geprüft. */
const kalk = (a: Record<string, unknown> = {}) => ({
    leistungId: 7, kalkuliert: true, zuschlagProzent: 10, losgroesse: 12,
    positionen: [
        { id: 1, typ: 'ARTIKEL', artikelId: 1, arbeitsgangId: null, bezeichnung: 'Handlauf', menge: 2.5, einheit: 'lfm', einzelpreis: 12, summe: 30, hinweis: 'OK', notiz: null },
        { id: 2, typ: 'ARBEIT', artikelId: null, arbeitsgangId: 5, bezeichnung: 'Montage', menge: 1.5, einheit: 'Std.', einzelpreis: 60, summe: 90, hinweis: 'OK', notiz: null },
        { id: 3, typ: 'FREMDLEISTUNG', artikelId: null, arbeitsgangId: null, bezeichnung: 'Feuerverzinken', veredler: 'Verzinkerei Muster', menge: 6, einheit: 'kg', einzelpreis: 1.4, summe: 8.4, hinweis: 'OK', notiz: null },
    ],
    materialSumme: 30, arbeitSumme: 90, maschineSumme: 0, fremdleistungSumme: 8.4, zwischensumme: 128.4, zuschlag: 12.84, preisBerechnet: 141.24, preisGespeichert: 132, preisVeraltet: true, vollstaendig: true, ...a,
});
const antwort = (daten: unknown, ok = true) => ({ ok, status: ok ? 200 : 400, json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let stand: ReturnType<typeof kalk>;

beforeEach(() => {
    stand = kalk();
    fetcher = vi.fn(async () => antwort(stand));
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (props: Partial<React.ComponentProps<typeof ServiceKalkulation>> = {}) => {
    const onPreis = vi.fn();
    render(<ServiceKalkulation leistungId={7} menge={12} preis={132} gesperrt={false} onPreisUebernehmen={onPreis} {...props} />);
    return onPreis;
};

describe('ServiceKalkulation', () => {
    it('ist zu, bis man sie öffnet, und lädt dann die Aufstellung für die Menge der Position', async () => {
        const user = userEvent.setup();
        zeige({ menge: 12.5 });
        expect(fetcher).not.toHaveBeenCalled();
        expect(screen.getByText('(nur intern)')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: /Kalkulation ansehen/ }));

        await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
        expect(String(fetcher.mock.calls[0][0])).toBe('/api/leistungen/7/kalkulation/angebot?menge=12.5');
        const liste = await screen.findByRole('list', { name: 'Zeilen der Kalkulation' });
        expect(liste).toHaveTextContent('Handlauf');
        expect(liste).toHaveTextContent('Montage');
        expect(liste).toHaveTextContent('Feuerverzinken bei Verzinkerei Muster');
        expect(screen.getByTestId('selbstkosten')).toHaveTextContent('128,40');
        expect(screen.getByTestId('kalkuliert')).toHaveTextContent('141,24');
    });

    it('zeigt die Abweichung zum Preis im Angebot und übernimmt den kalkulierten Preis nur auf Klick', async () => {
        const user = userEvent.setup();
        const onPreis = zeige({ preis: 132 });
        await user.click(screen.getByRole('button', { name: /Kalkulation ansehen/ }));

        const hinweis = await screen.findByTestId('abweichung');
        expect(hinweis).toHaveTextContent('Im Angebot steht 132,00');
        expect(hinweis).toHaveTextContent('kalkuliert sind 141,24');
        expect(onPreis).not.toHaveBeenCalled();

        await user.click(screen.getByRole('button', { name: 'Kalkulierten Preis übernehmen' }));
        expect(onPreis).toHaveBeenCalledWith(141.24);
    });

    it('bei gleichem Preis steht nur die Bestätigung; gesperrte Dokumente bieten keine Übernahme an', async () => {
        const user = userEvent.setup();
        zeige({ preis: 141.24 });
        await user.click(screen.getByRole('button', { name: /Kalkulation ansehen/ }));
        expect(await screen.findByText(/entspricht der Kalkulation/)).toBeInTheDocument();
    });

    it('gesperrt: kein Knopf zum Übernehmen', async () => {
        const user = userEvent.setup();
        zeige({ gesperrt: true });
        await user.click(screen.getByRole('button', { name: /Kalkulation ansehen/ }));
        await screen.findByTestId('abweichung');
        expect(screen.queryByRole('button', { name: 'Kalkulierten Preis übernehmen' })).toBeNull();
    });

    it('unvollständige Kalkulation: Warnung, keine Übernahme', async () => {
        const user = userEvent.setup();
        stand = kalk({ vollstaendig: false });
        zeige();
        await user.click(screen.getByRole('button', { name: /Kalkulation ansehen/ }));
        expect(await screen.findByRole('alert')).toHaveTextContent('wäre zu niedrig');
        expect(screen.queryByRole('button', { name: 'Kalkulierten Preis übernehmen' })).toBeNull();
    });

    it('ohne Menge keine Anfrage; ohne Kalkulation ein Hinweis; Fehler des Servers im Klartext', async () => {
        const user = userEvent.setup();
        const { unmount } = render(<ServiceKalkulation leistungId={7} menge={0} preis={0} gesperrt={false} onPreisUebernehmen={vi.fn()} />);
        await user.click(screen.getByRole('button', { name: /Kalkulation ansehen/ }));
        expect(screen.getByText('Bitte zuerst eine Menge eintragen.')).toBeInTheDocument();
        expect(fetcher).not.toHaveBeenCalled();
        unmount();

        stand = kalk({ positionen: [] });
        const r = render(<ServiceKalkulation leistungId={7} menge={5} preis={0} gesperrt={false} onPreisUebernehmen={vi.fn()} />);
        await user.click(screen.getByRole('button', { name: /Kalkulation ansehen/ }));
        expect(await screen.findByText(/noch keine Kalkulation/)).toBeInTheDocument();
        r.unmount();

        fetcher.mockImplementation(async () => antwort({ message: 'Leistung nicht gefunden.' }, false));
        render(<ServiceKalkulation leistungId={9} menge={5} preis={0} gesperrt={false} onPreisUebernehmen={vi.fn()} />);
        await user.click(screen.getByRole('button', { name: /Kalkulation ansehen/ }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Leistung nicht gefunden.');
    });
});
