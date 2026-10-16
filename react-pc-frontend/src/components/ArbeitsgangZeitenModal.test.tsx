import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ArbeitsgangZeitenModal } from './ArbeitsgangZeitenModal';
import type { Arbeitsgang } from '../types';

const gang = (z: Partial<Arbeitsgang> = {}): Arbeitsgang => ({
    id: 5, beschreibung: 'Schweißen', abteilungId: 1, abteilungName: 'Werkstatt', stundensatz: 60, stundensatzJahr: 2026, ...z,
});

const zeige = (a: Arbeitsgang, onSave = vi.fn().mockResolvedValue(undefined), onClose = vi.fn()) => {
    render(<ArbeitsgangZeitenModal arbeitsgang={a} isOpen onClose={onClose} onSave={onSave} />);
    return { onSave, onClose };
};

describe('ArbeitsgangZeitenModal', () => {
    it('zeigt die gespeicherten Zeiten und speichert geänderte Werte als Zahlen', async () => {
        const user = userEvent.setup();
        const { onSave, onClose } = zeige(gang({ ruestzeitMinuten: 30, bearbeitungszeitMinuten: 6 }));
        const ruest = screen.getByLabelText(/Rüstzeit/);
        const bearbeitung = screen.getByLabelText(/Bearbeitungszeit/);
        expect(ruest).toHaveValue('30');
        expect(bearbeitung).toHaveValue('6');

        await user.clear(bearbeitung);
        await user.type(bearbeitung, '6,5');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(onSave).toHaveBeenCalledWith(5, 30, 6.5));
        expect(onClose).toHaveBeenCalled();
    });

    it('lässt Felder leer (keine Angabe) und schickt dafür null', async () => {
        const user = userEvent.setup();
        const { onSave } = zeige(gang({ ruestzeitMinuten: 30, bearbeitungszeitMinuten: 6 }));

        await user.clear(screen.getByLabelText(/Rüstzeit/));
        await user.clear(screen.getByLabelText(/Bearbeitungszeit/));
        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(onSave).toHaveBeenCalledWith(5, null, null));
    });

    it('lehnt ungültige Zahlen ab, ohne zu speichern', async () => {
        const user = userEvent.setup();
        const { onSave } = zeige(gang());

        await user.type(screen.getByLabelText(/Rüstzeit/), '12,');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        expect(await screen.findByRole('alert')).toHaveTextContent(/Rüstzeit/);
        expect(onSave).not.toHaveBeenCalled();
    });

    it('zeigt die Meldung des Servers und bleibt offen, wenn das Speichern scheitert', async () => {
        const user = userEvent.setup();
        const { onClose } = zeige(gang(), vi.fn().mockRejectedValue(new Error('Die Rüstzeit muss zwischen 0 und 100.000 Minuten liegen.')));

        await user.type(screen.getByLabelText(/Rüstzeit/), '5');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Die Rüstzeit muss zwischen 0 und 100.000 Minuten liegen.');
        expect(onClose).not.toHaveBeenCalled();
    });
});
