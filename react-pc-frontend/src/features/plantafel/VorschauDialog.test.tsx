import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { auftragZeile, braucht, tageText, useVorschauFrage } from './VorschauDialog';
import type { AuftragAenderung, Vorschau } from './plantafelApi';

/** Nur Dummy-Daten. */
const aenderung = (extra: Partial<AuftragAenderung> = {}): AuftragAenderung => ({
    projektId: 7, bezeichnung: 'Geländer Musterstraße', altLiefertermin: '2026-11-20', neuLiefertermin: '2026-12-04', tage: 14, altAmpel: 'OK', neuAmpel: 'VERSPAETET', neuVerspaetungTage: 3, eigener: true, ...extra,
});
const vorschau = (extra: Partial<Vorschau> = {}): Vorschau => ({ auftraege: [aenderung()], vorgaenge: [], warnungen: [], ...extra });

describe('Vorschau-Texte', () => {
    it('nennt alten und neuen Plantermin mit den Tagen', () => {
        expect(auftragZeile(aenderung())).toBe('Plantermin rückt vom 20.11.26 auf den 04.12.26 (+14 Tage), verspätet +3 Arbeitstage');
        expect(auftragZeile(aenderung({ neuLiefertermin: '2026-11-19', tage: -1, neuAmpel: 'OK', neuVerspaetungTage: null }))).toBe('Plantermin rückt vom 20.11.26 auf den 19.11.26 (−1 Tag)');
        expect(auftragZeile(aenderung({ neuLiefertermin: '2026-11-20', tage: 0, neuAmpel: 'OK' }))).toBe('Plantermin bleibt am 20.11.26');
        expect(auftragZeile(aenderung({ altLiefertermin: null, tage: null, neuAmpel: 'OK' }))).toBe('Plantermin 20.11.26'.replace('20.11.26', '04.12.26'));
        expect(tageText(0)).toBe('gleich');
        expect(tageText(null)).toBe('neu');
    });

    it('fragt nur nach, wenn sich etwas Wesentliches ändert', () => {
        expect(braucht(vorschau())).toBe(true);
        expect(braucht(vorschau({ auftraege: [aenderung({ tage: 0, altAmpel: 'OK', neuAmpel: 'OK' })] }))).toBe(false);
        expect(braucht(vorschau({ auftraege: [], warnungen: ['Konflikt'] }))).toBe(true);
        expect(braucht(vorschau({ auftraege: [] }))).toBe(false);
    });
});

function Probe({ onErgebnis }: { onErgebnis: (ja: boolean) => void }) {
    const { frage, dialog } = useVorschauFrage();
    return (
        <>
            <button type="button" onClick={() => void frage(2, { aufwandStunden: 32 }, '„Fertigung": Aufwand von 24 auf 32 Stunden.').then(onErgebnis)}>Fragen</button>
            {dialog}
        </>
    );
}

let antwort: { status: number; body: unknown };
beforeEach(() => {
    antwort = { status: 200, body: vorschau({ vorgaenge: [{ vorgangId: 2, projektId: 7, bezeichnung: 'Fertigung', auftrag: 'Geländer', altStart: '2026-11-02', altEnde: '2026-11-06', neuStart: '2026-11-02', neuEnde: '2026-11-13' }], warnungen: ['Achtung'] }) };
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: antwort.status < 300, status: antwort.status, json: async () => antwort.body }) as Response));
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('useVorschauFrage', () => {
    it('zeigt, was sich ändert, und gibt „ja" zurück, wenn der Anwender übernimmt', async () => {
        const user = userEvent.setup();
        const ergebnis = vi.fn();
        render(<Probe onErgebnis={ergebnis} />);
        await user.click(screen.getByRole('button', { name: 'Fragen' }));

        expect(await screen.findByText('Das ändert sich')).toBeInTheDocument();
        expect(screen.getByTestId('vorschau-auftrag-7')).toHaveTextContent('Plantermin rückt vom 20.11.26 auf den 04.12.26 (+14 Tage)');
        expect(screen.getByTestId('vorschau')).toHaveTextContent('Achtung');
        expect(screen.getByTestId('vorschau')).toHaveTextContent('Fertigung');
        await user.click(screen.getByRole('button', { name: 'Übernehmen' }));
        await waitFor(() => expect(ergebnis).toHaveBeenCalledWith(true));
    });

    it('gibt „nein" zurück bei Abbrechen', async () => {
        const user = userEvent.setup();
        const ergebnis = vi.fn();
        render(<Probe onErgebnis={ergebnis} />);
        await user.click(screen.getByRole('button', { name: 'Fragen' }));
        await user.click(await screen.findByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(ergebnis).toHaveBeenCalledWith(false));
    });

    it('fragt nicht, wenn sich nichts Wesentliches ändert oder die Vorschau fehlt', async () => {
        const user = userEvent.setup();
        const ergebnis = vi.fn();
        render(<Probe onErgebnis={ergebnis} />);
        antwort = { status: 200, body: vorschau({ auftraege: [aenderung({ tage: 0, altAmpel: 'OK', neuAmpel: 'OK' })] }) };
        await user.click(screen.getByRole('button', { name: 'Fragen' }));
        await waitFor(() => expect(ergebnis).toHaveBeenLastCalledWith(true));
        expect(screen.queryByText('Das ändert sich')).not.toBeInTheDocument();

        antwort = { status: 500, body: { message: 'kaputt' } };
        await user.click(screen.getByRole('button', { name: 'Fragen' }));
        await waitFor(() => expect(ergebnis).toHaveBeenCalledTimes(2));
        expect(ergebnis).toHaveBeenLastCalledWith(true);
    });
});
