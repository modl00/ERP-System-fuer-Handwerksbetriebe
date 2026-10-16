import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtikelFormelModal, type ArtikelFormel } from './ArtikelFormelModal';

/** Nur Dummy-Daten. */
const leer: ArtikelFormel = { felder: [], gewicht: '', flaeche: '' };
const blech: ArtikelFormel = {
    felder: [{ name: 'L', bezeichnung: 'Länge', einheit: 'mm', standard: 1000 }, { name: 'B', bezeichnung: 'Breite', einheit: 'mm', standard: 500 }],
    gewicht: 'L*B/1000000*10*dichte', flaeche: 'L*B/1000000*2',
};

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });
let fetcher: ReturnType<typeof vi.fn>;
let rechnenAntwort: () => ReturnType<typeof antwort>;
const rechenAufrufe = () => fetcher.mock.calls.filter(([u]) => String(u).endsWith('/profile/formel'));

beforeEach(() => {
    rechnenAntwort = () => antwort({ kg: 39.25, m2: 1 });
    fetcher = vi.fn(async () => rechnenAntwort());
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (formel: ArtikelFormel, onUebernehmen = vi.fn(), onClose = vi.fn()) => {
    render(<ArtikelFormelModal isOpen onClose={onClose} formel={formel} werkstoffId={4} kgJeMeter="2,4" m2JeMeter="0,16" onUebernehmen={onUebernehmen} />);
    return { onUebernehmen, onClose };
};

describe('ArtikelFormelModal', { timeout: 30000 }, () => {
    it('übernimmt eine Vorlage mit Feldern und beiden Formeln', async () => {
        const user = userEvent.setup();
        zeige(leer);
        await user.click(await screen.findByRole('button', { name: 'Blechzuschnitt' }));

        expect(screen.getByLabelText('Name von Feld 1')).toHaveValue('L');
        expect(screen.getByLabelText('Bezeichnung von Feld 3')).toHaveValue('Dicke');
        expect(screen.getByLabelText(/Formel für das Gewicht/)).toHaveValue('(L/1000) * (B/1000) * T * dichte');
        expect(screen.getByLabelText(/Formel für die Außenfläche/)).toHaveValue('(L/1000) * (B/1000) * 2');
    });

    it('rechnet die Testrechnung auf dem Server und zeigt Gewicht und Fläche je Stück', async () => {
        zeige(blech);
        expect(await screen.findByTestId('artikel-formel-ergebnis')).toHaveTextContent('39,25 kg');
        expect(screen.getByTestId('artikel-formel-ergebnis')).toHaveTextContent('1 m²');
        const body = JSON.parse(rechenAufrufe().at(-1)![1].body as string);
        expect(body).toMatchObject({ gewicht: 'L*B/1000000*10*dichte', flaeche: 'L*B/1000000*2', werkstoffId: 4, kgJeMeter: 2.4, m2JeMeter: 0.16 });
        expect(body.felder).toHaveLength(2);
    });

    it('schickt eingetragene Testwerte mit', async () => {
        const user = userEvent.setup();
        zeige(blech);
        await screen.findByTestId('artikel-formel-ergebnis');
        await user.type(screen.getByLabelText('Testwert Länge'), '2000');

        await waitFor(() => expect(JSON.parse(rechenAufrufe().at(-1)![1].body as string).werte).toEqual({ l: 2000 }));
    });

    it('zeigt einen Rechenfehler des Servers in Klartext', async () => {
        rechnenAntwort = () => antwort({ message: 'Die Formel enthält „Q“, das es nicht gibt.' }, false, 400);
        zeige(blech);
        expect(await screen.findByTestId('artikel-formel-fehler')).toHaveTextContent('Q');
        expect(screen.queryByTestId('artikel-formel-ergebnis')).toBeNull();
    });

    it('meldet unbekannte Werte, wenn nur das Gewicht eine Formel hat', async () => {
        rechnenAntwort = () => antwort({ kg: 5, m2: null });
        zeige({ ...blech, flaeche: '' });
        expect(await screen.findByTestId('artikel-formel-ergebnis')).toHaveTextContent('Fläche unbekannt');
    });

    it('übernimmt Felder und Formeln ins Formular und schließt', async () => {
        const user = userEvent.setup();
        const { onUebernehmen, onClose } = zeige(blech);
        await user.click(await screen.findByRole('button', { name: /Übernehmen/ }));

        expect(onUebernehmen).toHaveBeenCalledWith({
            felder: [{ name: 'L', bezeichnung: 'Länge', einheit: 'mm', standard: 1000 }, { name: 'B', bezeichnung: 'Breite', einheit: 'mm', standard: 500 }],
            gewicht: 'L*B/1000000*10*dichte', flaeche: 'L*B/1000000*2',
        });
        expect(onClose).toHaveBeenCalled();
    });

    it('ohne eine der beiden Formeln lässt sich nichts übernehmen', async () => {
        const user = userEvent.setup();
        const { onUebernehmen } = zeige(leer);
        await user.click(await screen.findByRole('button', { name: /Übernehmen/ }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Formel für das Gewicht oder die Fläche');
        expect(onUebernehmen).not.toHaveBeenCalled();
    });

    it('„Formel entfernen“ übernimmt eine leere Formel und gibt es nur, wenn eine da ist', async () => {
        const user = userEvent.setup();
        const { onUebernehmen } = zeige(blech);
        await user.click(await screen.findByRole('button', { name: /Formel entfernen/ }));
        expect(onUebernehmen).toHaveBeenCalledWith({ felder: [], gewicht: '', flaeche: '' });
    });

    it('ohne bestehende Formel gibt es kein „Formel entfernen“', async () => {
        zeige(leer);
        await screen.findByRole('button', { name: /Übernehmen/ });
        expect(screen.queryByRole('button', { name: /Formel entfernen/ })).toBeNull();
    });

    it('ein Feld lässt sich anlegen und entfernen, höchstens acht', async () => {
        const user = userEvent.setup();
        zeige(leer);
        for (let i = 0; i < 9; i++) await user.click(screen.getByRole('button', { name: /Feld hinzufügen/ }));
        expect(screen.getAllByTestId(/artikel-formel-feld-/)).toHaveLength(8);
        expect(screen.getByRole('button', { name: /Feld hinzufügen/ })).toBeDisabled();
        await user.click(screen.getByRole('button', { name: 'Feld 1 entfernen' }));
        expect(screen.getAllByTestId(/artikel-formel-feld-/)).toHaveLength(7);
    });

    it('lehnt einen ungültigen Standardwert ab', async () => {
        const user = userEvent.setup();
        const { onUebernehmen } = zeige({ ...blech, felder: [{ name: 'L', bezeichnung: 'Länge', einheit: 'mm', standard: null }] });
        await user.type(await screen.findByLabelText('Standardwert von Feld 1'), '12,x');
        await user.click(screen.getByRole('button', { name: /Übernehmen/ }));
        const meldungen = (await screen.findAllByRole('alert')).map(m => m.textContent ?? '').join(' | ');
        expect(meldungen).toContain('Standardwert');
        expect(onUebernehmen).not.toHaveBeenCalled();
    });
});
