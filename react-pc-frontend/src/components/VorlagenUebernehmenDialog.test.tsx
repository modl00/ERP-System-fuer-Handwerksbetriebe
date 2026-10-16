import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ToastProvider } from './ui/toast';
import { VorlagenUebernehmenDialog } from './VorlagenUebernehmenDialog';

const antwort = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;

const arbeitsgaenge = {
    hinweis: 'Keine Stundensätze enthalten.',
    abteilungen: [{ id: 1, name: 'Werkstatt' }],
    zeilen: [
        { name: 'Bohren', abteilung: 'Werkstatt', einheit: 'je Bohrung', erklaerung: 'Formel', vorhanden: false, felder: 5, beispielMinuten: 0.5 },
        { name: 'Richten', abteilung: 'Werkstatt', einheit: 'je Baugruppe', erklaerung: 'Formel', vorhanden: true, felder: 3, beispielMinuten: 15 },
    ],
};

function zeige(art: 'arbeitsgaenge' | 'profile' = 'arbeitsgaenge') {
    return render(<ToastProvider><VorlagenUebernehmenDialog art={art} offen onOpenChange={() => {}} onFertig={() => {}} /></ToastProvider>);
}

afterEach(() => { vi.unstubAllGlobals(); });

describe('VorlagenUebernehmenDialog', () => {
    it('wählt nur neue vor und sperrt vorhandene', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort(arbeitsgaenge)));
        zeige();
        const bohren = await screen.findByRole('checkbox', { name: 'Bohren übernehmen' });
        expect(bohren).toBeChecked();
        expect(screen.getByRole('checkbox', { name: 'Richten übernehmen' })).toBeDisabled();
        expect(screen.getByText('1 neu, 1 schon vorhanden.')).toBeInTheDocument();
    });

    it('schickt die Auswahl beim Übernehmen an den Server', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(antwort(arbeitsgaenge))
            .mockResolvedValueOnce(antwort({ angelegt: 1, uebersprungen: 0 }));
        vi.stubGlobal('fetch', fetchMock);
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Arbeitsgänge übernehmen (1)' }));
        await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
        expect(fetchMock.mock.calls[1][0]).toBe('/api/vorlagen/metallbau/arbeitsgaenge/uebernehmen');
        expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ namen: ['Bohren'], abteilungId: null });
    });

    it('zeigt Profile ohne Vorauswahl und filtert nach dem Suchtext', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort({
            werkstoffGefunden: false,
            zeilen: [
                { nummer: 'VL-RR-48.3x3.2', name: 'Rundrohr 48,3 x 3,2', form: 'Rundrohr', vorhanden: false, kg: 3.559, flaeche: 0.152, einheit: 'je Meter', herkunft: 'BERECHNET' },
                { nummer: 'VL-BL-2', name: 'Blech 2 mm', form: 'Blech', vorhanden: false, kg: 15.7, flaeche: 1, einheit: 'je m²', herkunft: 'BERECHNET' },
            ],
        })));
        zeige('profile');
        expect(await screen.findByRole('checkbox', { name: 'Blech 2 mm übernehmen' })).not.toBeChecked();
        expect(screen.getByText(/Werkstoff S235JR fehlt/)).toBeInTheDocument();
        await userEvent.type(screen.getByRole('searchbox', { name: 'Vorlagen durchsuchen' }), 'blech');
        expect(screen.queryByRole('checkbox', { name: /Rundrohr/ })).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Alle neuen auswählen' }));
        expect(screen.getByRole('button', { name: 'Artikel übernehmen (1)' })).toBeEnabled();
    });

    it('meldet fehlende Rechte verständlich', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort({}, 403)));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('nur Administratoren');
    });
});
