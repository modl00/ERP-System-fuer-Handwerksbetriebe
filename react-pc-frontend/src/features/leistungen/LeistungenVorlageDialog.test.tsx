import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { LeistungenVorlageDialog, ergebnisText } from './LeistungenVorlageDialog';

/** Nur Dummy-Daten. */
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body, text: async () => JSON.stringify(body) }) as Response;
const vorschau = {
    hinweis: 'Alle Zahlen sind Beispielwerte.',
    leistungen: [
        { name: 'Geländer, Stahl, gestrichen', einheit: 'Laufende Meter', kategorie: 'Geländer', baugruppe: false, vorhanden: false, beschreibung: 'Geländer mit Montage.', positionen: 14, enthaelt: ['Geländerpfosten', 'Handlauf'] },
        { name: 'Aufmaß vor Ort', einheit: 'Stück', kategorie: 'Stahlbau und Konstruktion', baugruppe: false, vorhanden: true, beschreibung: 'Aufmaß.', positionen: 2, enthaelt: [] },
        { name: 'Geländerpfosten', einheit: 'Stück', kategorie: 'Baugruppen (Bauteile)', baugruppe: true, vorhanden: false, beschreibung: 'Pfosten.', positionen: 8, enthaelt: [] },
    ],
    fremdleistungen: [{ bezeichnung: 'Feuerverzinken', lieferant: 'Feuerverzinkerei (Beispiel)', einheit: 'kg', preis: 0.62, mindestpreis: 95, erklaerung: 'Verzinkt wird in Ladungen (Chargen).' }],
    maschinen: 7, kategorien: 7,
};
let fetcher: ReturnType<typeof vi.fn>;
let gesendet: { namen: string[] } | null;
beforeEach(() => {
    gesendet = null;
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url).endsWith('/uebernehmen') && init?.method === 'POST') {
            gesendet = JSON.parse(String(init.body));
            return json({ leistungen: 3, uebersprungen: 0, arbeitsgaenge: 5, artikel: 4, maschinen: 2, kategorien: 2, lieferanten: 1, fremdleistungen: 1 });
        }
        return json(vorschau);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (onFertig = vi.fn()) => { render(<ToastProvider><LeistungenVorlageDialog offen onOpenChange={() => undefined} onFertig={onFertig} /></ToastProvider>); return onFertig; };

describe('LeistungenVorlageDialog', { timeout: 30000 }, () => {
    it('zeigt die Leistungen mit ihren Baugruppen; Baugruppen stehen nicht einzeln zur Auswahl', async () => {
        zeige();

        expect(await screen.findByTestId('vorlage-Geländer, Stahl, gestrichen')).toHaveTextContent('Mit Baugruppen: Geländerpfosten; Handlauf');
        expect(screen.queryByLabelText('Geländerpfosten übernehmen')).not.toBeInTheDocument();
        expect(screen.getByLabelText('Aufmaß vor Ort übernehmen')).toBeDisabled();
        expect(screen.getByText(/Dazu kommen 1 Baugruppen, 7 Maschinen und 7 Kategorien/)).toBeInTheDocument();
    });

    it('wählt die neuen Leistungen vor und übernimmt sie', async () => {
        const user = userEvent.setup();
        const fertig = zeige();
        const knopf = await screen.findByRole('button', { name: /Leistungen übernehmen \(1\)/ });
        await user.click(knopf);

        await waitFor(() => expect(gesendet).toEqual({ namen: ['Geländer, Stahl, gestrichen'] }));
        await waitFor(() => expect(fertig).toHaveBeenCalled());
    });

    it('erklärt Fremdleistungen und Chargen in einfachen Worten', async () => {
        zeige();
        const erklaerung = await screen.findByTestId('charge-erklaerung');

        expect(erklaerung).toHaveTextContent('Eine Charge ist eine Ladung');
        expect(erklaerung).toHaveTextContent('Feuerverzinken bei Feuerverzinkerei (Beispiel)');
        expect(erklaerung).toHaveTextContent('mindestens 95 Euro');
    });

    it('ohne Auswahl ist das Übernehmen gesperrt, und die Suche filtert', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('checkbox', { name: 'Geländer, Stahl, gestrichen übernehmen' }));
        expect(screen.getByRole('button', { name: /Leistungen übernehmen \(0\)/ })).toBeDisabled();
        await user.type(screen.getByLabelText('Leistungen durchsuchen'), 'Tor');
        expect(screen.getByText('Keine Leistung gefunden.')).toBeInTheDocument();
    });

    it('meldet einen Ladefehler im Klartext', async () => {
        fetcher.mockImplementation(async () => json({ message: 'Datenbank nicht erreichbar.' }, 500));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('Datenbank nicht erreichbar.');
    });
});

describe('ergebnisText', () => {
    it('nennt nur, was angelegt wurde', () => {
        expect(ergebnisText({ leistungen: 1, uebersprungen: 0, arbeitsgaenge: 0, artikel: 4, maschinen: 0, kategorien: 2, lieferanten: 1, fremdleistungen: 0 })).toBe('Angelegt: 1 Leistung, 4 Artikel, 2 Kategorien, 1 Beispiel-Lieferant.');
        expect(ergebnisText({ leistungen: 0, uebersprungen: 3, arbeitsgaenge: 0, artikel: 0, maschinen: 0, kategorien: 0, lieferanten: 0, fremdleistungen: 0 })).toBe('Es war schon alles vorhanden, nichts wurde angelegt.');
    });
});
