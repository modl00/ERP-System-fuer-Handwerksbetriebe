import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ArbeitsgangMaschineModal } from './ArbeitsgangMaschineModal';
import type { Arbeitsgang } from '../types';

/** Nur Dummy-Daten. */
const ag = (a: Partial<Arbeitsgang> = {}): Arbeitsgang => ({ id: 5, beschreibung: 'Sägen', abteilungId: 1, abteilungName: 'Werkstatt', stundensatz: 60, stundensatzJahr: 2026, ...a });
const zeige = (arbeitsgang: Arbeitsgang, onSave = vi.fn(async () => undefined)) => {
    render(<ArbeitsgangMaschineModal arbeitsgang={arbeitsgang} isOpen onClose={vi.fn()} onSave={onSave} />);
    return onSave;
};

describe('Maschine am Arbeitsgang', { timeout: 30000 }, () => {
    it('zeigt die bisherige Maschine und speichert Name und Satz als Zahl', async () => {
        const user = userEvent.setup();
        const onSave = zeige(ag({ maschinenBezeichnung: 'Kappsäge', maschinenStundensatz: 38.5 }));

        expect(screen.getByLabelText('Name der Maschine')).toHaveValue('Kappsäge');
        const satz = screen.getByLabelText('Kosten je Maschinenstunde (€)');
        expect(satz).toHaveValue('38,5');
        await user.clear(satz);
        await user.type(satz, '42,25');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(onSave).toHaveBeenCalledWith(5, 'Kappsäge', 42.25));
    });

    it('ein leerer Satz entfernt die Maschine', async () => {
        const user = userEvent.setup();
        const onSave = zeige(ag({ maschinenBezeichnung: 'Kappsäge', maschinenStundensatz: 38.5 }));

        await user.clear(screen.getByLabelText('Kosten je Maschinenstunde (€)'));
        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(onSave).toHaveBeenCalledWith(5, 'Kappsäge', null));
    });

    it('der Rechner schlägt einen Satz vor und übernimmt ihn ins Feld', async () => {
        const user = userEvent.setup();
        zeige(ag());
        await user.click(screen.getByRole('button', { name: /Satz berechnen lassen/ }));
        expect(screen.getByTestId('maschine-vorschlag')).toHaveTextContent('bitte Nutzungsdauer und Stunden');

        await user.type(screen.getByLabelText('Anschaffung (€)'), '24000');
        await user.type(screen.getByLabelText('Nutzungsdauer (Jahre)'), '8');
        await user.type(screen.getByLabelText('Läuft im Jahr (Stunden)'), '1200');
        await user.type(screen.getByLabelText('Wartung je Jahr (€)'), '1200');
        await user.type(screen.getByLabelText('Energie je Jahr (€)'), '1800');
        await user.type(screen.getByLabelText('Raum je Jahr (€)'), '600');

        expect(screen.getByTestId('maschine-vorschlag')).toHaveTextContent('Vorschlag: 5,5 € je Stunde');
        await user.click(screen.getByRole('button', { name: 'Übernehmen' }));
        expect(screen.getByLabelText('Kosten je Maschinenstunde (€)')).toHaveValue('5,5');
    });

    it('ein unmöglicher Satz wird nicht gesendet; Fehler des Servers stehen im Dialog', async () => {
        const user = userEvent.setup();
        const onSave = zeige(ag(), vi.fn(async () => { throw new Error('Der Maschinenstundensatz muss zwischen 0 und 1.000 Euro liegen.'); }));

        await user.type(screen.getByLabelText('Kosten je Maschinenstunde (€)'), '5000');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        expect(onSave).not.toHaveBeenCalled();
        expect(await screen.findByRole('alert')).toBeVisible();

        const satz = screen.getByLabelText('Kosten je Maschinenstunde (€)');
        await user.clear(satz);
        await user.type(satz, '50');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        expect(await screen.findByText(/zwischen 0 und 1.000 Euro/)).toBeVisible();
    });
});
