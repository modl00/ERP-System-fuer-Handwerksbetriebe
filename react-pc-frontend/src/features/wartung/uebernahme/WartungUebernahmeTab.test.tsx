import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WartungUebernahmeTab } from './WartungUebernahmeTab';
import { erstelleVorschau, type Ergebnisbericht, type Vorschau } from './uebernahmeApi';

/** Nur Dummy-Daten. */
const VORSCHAU: Vorschau = {
    zusammenfassung: { wartungen: 3, abgeschlossen: 2, offen: 1, modulErgaenzt: 1, modulUnklar: 1, kundenNeu: 1, objekteNeu: 2, tuerenNeu: 4, pruefpunkteGesamt: 100, pruefpunkteZugeordnet: 90,
        pruefpunkteEigene: 10, werteNeuInListe: 5, werteUngueltig: 0 },
    kunden: [{ altId: 1, name: 'Muster GmbH', erpId: 7, erpName: 'Muster GmbH', vorschlag: 'ZUORDNEN', wartungen: 2 }, { altId: 2, name: 'Neue Firma AG', erpId: null, erpName: null, vorschlag: 'NEU', wartungen: 1 }],
    protokolle: [
        { altId: 1, altNummer: 'A-1', titel: 'Jahreswartung', status: 'abgeschlossen', protokollart: 'Wartungsprotokoll', module: ['m_bst', 'm_fsa'], modulQuelle: 'PROTOKOLL', ergaenzteModule: [], bauart: '2-flügelig',
            pruefpunkte: 40, pruefpunkteZugeordnet: 38, pruefpunkteEigene: 2, fragen: [] },
        { altId: 2, altNummer: 'A-2', titel: 'Instandhaltung Tür', status: 'offen', protokollart: 'Instandhaltungsprotokoll', module: ['m_bst', 'm_boden'], modulQuelle: 'TITEL_UND_PRUEFPUNKTE',
            ergaenzteModule: [], bauart: null, pruefpunkte: 30, pruefpunkteZugeordnet: 30, pruefpunkteEigene: 0, fragen: ['Die Bauart (1-flügelig, 2-flügelig, Schiebetür) ist nicht erkennbar. Bitte wählen.'] },
        { altId: 3, altNummer: 'A-3', titel: 'Wartungsprotokoll', status: 'offen', protokollart: 'Wartungsprotokoll', module: [], modulQuelle: 'UNKLAR', ergaenzteModule: [], bauart: null,
            pruefpunkte: 0, pruefpunkteZugeordnet: 0, pruefpunkteEigene: 0, fragen: ['Modul fehlt: Weder der Titel „Wartungsprotokoll" noch die Prüfpunkte nennen eines. Bitte wählen.'] },
    ],
    werte: [
        { feld: 'Anschlag', altWert: 'DINL', neuerWert: 'DIN links', anzahl: 22, aktion: 'ZUGEORDNET' },
        { feld: 'Hersteller', altWert: 'W. Podszuck KG. Kiel', neuerWert: 'W. Podszuck KG. Kiel', anzahl: 1, aktion: 'NEU_IN_LISTE' },
        { feld: 'Baujahr', altWert: 'Keine Angabe', neuerWert: '', anzahl: 21, aktion: 'LEER' },
    ],
    eigenePruefpunkte: [{ label: 'Sonderpunkt Kunde', anzahl: 3 }],
    hinweise: ['Variante „Standard ohne Handtaster" des Moduls Feststellanlage wird im Katalog ergänzt.'],
};

let fetcher: ReturnType<typeof vi.fn>;
const datei = (inhalt: string) => new File([inhalt], 'wartung-export.json', { type: 'application/json' });
const antwort = (daten: unknown, status = 200) => ({ ok: status < 300, status, json: async () => daten }) as Response;

beforeEach(() => {
    fetcher = vi.fn(async () => antwort(VORSCHAU));
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('Übernahme aus HandwerkPro: Vorschau', { timeout: 30000 }, () => {
    it('sagt klar, dass nichts importiert wird, und verlangt zuerst eine Datei', () => {
        render(<WartungUebernahmeTab />);

        expect(screen.getByText(/ändert nichts/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Vorschau erstellen/ })).toBeDisabled();
    });

    it('schickt die Datei als JSON an den Server und zeigt Kennzahlen, Fragen, Hinweise und Kunden', async () => {
        const user = userEvent.setup();
        render(<WartungUebernahmeTab />);

        await user.upload(screen.getByLabelText('Exportdatei wählen'), datei('{"quelle":"HandwerkPro","wartungen":[]}'));
        await user.click(screen.getByRole('button', { name: /Vorschau erstellen/ }));

        const kennzahlen = await screen.findByTestId('kennzahlen');
        expect(within(kennzahlen).getByText('Module nachgereicht')).toBeInTheDocument();
        expect(within(kennzahlen).getByText('von 100 Prüfpunkten zugeordnet')).toBeInTheDocument();
        expect(fetcher).toHaveBeenCalledWith('/api/admin/wartung-import/vorschau', expect.objectContaining({ method: 'POST', body: '{"quelle":"HandwerkPro","wartungen":[]}' }));
        expect(screen.getByText('2 Protokolle brauchen eine Entscheidung')).toBeInTheDocument();
        expect(screen.getByText(/Modul fehlt: Weder der Titel/)).toBeInTheDocument();
        expect(screen.getByText(/Variante „Standard ohne Handtaster"/)).toBeInTheDocument();
        expect(screen.getByText('wird neu angelegt')).toBeInTheDocument();
        expect(screen.getByText('Sonderpunkt Kunde')).toBeInTheDocument();
        expect(screen.getByText('aus Titel und Prüfpunkten')).toBeInTheDocument();
    });

    it('die Abbildungstabelle zeigt zuerst nur, was eine Entscheidung braucht, mit Schalter für alle Werte', async () => {
        const user = userEvent.setup();
        render(<WartungUebernahmeTab />);
        await user.upload(screen.getByLabelText('Exportdatei wählen'), datei('{"quelle":"HandwerkPro","wartungen":[]}'));
        await user.click(screen.getByRole('button', { name: /Vorschau erstellen/ }));
        await screen.findByTestId('kennzahlen');

        expect(screen.getAllByText('W. Podszuck KG. Kiel', { selector: 'td' })).toHaveLength(2);
        expect(screen.queryByText('DINL')).not.toBeInTheDocument();
        await user.click(screen.getByLabelText(/Nur Werte, die eine Entscheidung brauchen/));
        expect(screen.getByText('DINL')).toBeInTheDocument();
        expect(screen.getByText('DIN links')).toBeInTheDocument();
        expect(screen.getAllByText('(leer)').length).toBeGreaterThan(0);
        expect(screen.getByText('neu in die Liste')).toBeInTheDocument();
    });

    it('keine gültige JSON-Datei: Meldung, es geht nichts an den Server', async () => {
        const user = userEvent.setup();
        render(<WartungUebernahmeTab />);

        await user.upload(screen.getByLabelText('Exportdatei wählen'), datei('das ist kein json'));
        await user.click(screen.getByRole('button', { name: /Vorschau erstellen/ }));

        expect(await screen.findByRole('alert')).toHaveTextContent('keine gültige JSON-Datei');
        expect(fetcher).not.toHaveBeenCalled();
    });

    it('Fehler des Servers (fremde Datei, kein Administrator) steht im Klartext da', async () => {
        fetcher.mockImplementation(async () => antwort({ message: 'Das ist keine Exportdatei aus HandwerkPro (tools/wartung-export-handwerkpro.mjs).' }, 400));
        const user = userEvent.setup();
        render(<WartungUebernahmeTab />);

        await user.upload(screen.getByLabelText('Exportdatei wählen'), datei('{"x":1}'));
        await user.click(screen.getByRole('button', { name: /Vorschau erstellen/ }));

        expect(await screen.findByRole('alert')).toHaveTextContent('keine Exportdatei aus HandwerkPro');
        await waitFor(() => expect(screen.queryByTestId('kennzahlen')).not.toBeInTheDocument());
        fetcher.mockImplementation(async () => antwort({}, 403));
        await user.click(screen.getByRole('button', { name: /Vorschau erstellen/ }));
        expect(await screen.findByRole('alert')).toHaveTextContent('nur für Administratoren');
    });


    it('Übernehmen: erst eine Rückfrage, dann geht die Datei an den Server; danach steht der Bericht mit ausgelassenen Protokollen da', async () => {
        const bericht: Ergebnisbericht = { protokolleNeu: 105, protokolleSchonDa: 0, kundenNeu: 6, objekteNeu: 8, tuerenNeu: 43, pruefpunkteEigene: 227, werteVerworfen: 0, katalogErgaenzt: 71,
            uebersprungen: ['A-3: Modul fehlt.'], hinweise: ['Der Techniker „Max Mustermann" ist im ERP kein Mitarbeiter.'] };
        fetcher.mockImplementation(async (url: string) => (url.includes('/ausfuehren') ? antwort(bericht) : antwort(VORSCHAU)));
        const user = userEvent.setup();
        render(<WartungUebernahmeTab />);
        await user.upload(screen.getByLabelText('Exportdatei wählen'), datei('{"quelle":"HandwerkPro","wartungen":[]}'));
        await user.click(screen.getByRole('button', { name: /Vorschau erstellen/ }));
        await screen.findByTestId('kennzahlen');

        await user.click(screen.getByLabelText(/Neue Werte \(zum Beispiel neue Hersteller\)/));
        await user.click(screen.getByRole('button', { name: /Protokolle übernehmen/ }));
        expect(fetcher).toHaveBeenCalledTimes(1);
        expect(screen.getByText(/Geht etwas schief, wird nichts übernommen/)).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: /Ja, jetzt übernehmen/ }));

        expect(await screen.findByText('Die Übernahme ist fertig.')).toBeInTheDocument();
        expect(fetcher).toHaveBeenLastCalledWith('/api/admin/wartung-import/ausfuehren?neueWerteInKatalog=false', expect.objectContaining({ method: 'POST' }));
        expect(screen.getByText(/105 Protokolle übernommen/)).toBeInTheDocument();
        expect(screen.getByText(/1 Protokoll wurde ausgelassen/)).toBeInTheDocument();
        expect(screen.getByText(/A-3: Modul fehlt/)).toBeInTheDocument();
        expect(screen.getByText(/kein Mitarbeiter/)).toBeInTheDocument();
    });

    it('Übernehmen abbrechen: es geht nichts an den Server; ein Fehler sagt, dass nichts übernommen wurde', async () => {
        fetcher.mockImplementation(async (url: string) => (url.includes('/ausfuehren') ? antwort({}, 500) : antwort(VORSCHAU)));
        const user = userEvent.setup();
        render(<WartungUebernahmeTab />);
        await user.upload(screen.getByLabelText('Exportdatei wählen'), datei('{"quelle":"HandwerkPro","wartungen":[]}'));
        await user.click(screen.getByRole('button', { name: /Vorschau erstellen/ }));
        await screen.findByTestId('kennzahlen');

        await user.click(screen.getByRole('button', { name: /Protokolle übernehmen/ }));
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(fetcher).toHaveBeenCalledTimes(1);

        await user.click(screen.getByRole('button', { name: /Protokolle übernehmen/ }));
        await user.click(screen.getByRole('button', { name: /Ja, jetzt übernehmen/ }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Es wurde nichts übernommen');
    });

    it('erstelleVorschau: ohne Netz ein Satz zur Verbindung', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));

        await expect(erstelleVorschau('{"quelle":"HandwerkPro"}')).rejects.toThrow('Verbindung');
    });
});
