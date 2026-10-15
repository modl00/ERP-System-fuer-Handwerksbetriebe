import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { DatenfeldEingabe } from './DatenfeldEingabe';
import type { ProtokollDatenfeld } from './types';

const feld = (extra: Partial<ProtokollDatenfeld> = {}): ProtokollDatenfeld => ({
    id: 7, abschnittId: null, modulKey: null, label: 'Baujahr', feldtyp: 'JAHR', optionen: [], wert: null, sortierung: 1, ...extra,
});

describe('Datenfeld „Jahreszahl“', () => {
    it('ist ein Zahlenfeld mit höchstens vier Stellen und speichert beim Verlassen', async () => {
        const user = userEvent.setup();
        const onSetzen = vi.fn();
        render(<DatenfeldEingabe feld={feld()} bearbeitbar onSetzen={onSetzen} />);

        const eingabe = screen.getByLabelText('Baujahr');
        expect(eingabe).toHaveAttribute('inputmode', 'numeric');
        expect(eingabe).toHaveAttribute('maxlength', '4');
        expect(eingabe).toHaveAttribute('placeholder', 'JJJJ');
        await user.type(eingabe, '20145');
        await user.tab();

        expect(onSetzen).toHaveBeenCalledTimes(1);
        expect(onSetzen).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }), '2014');
    });

    it('zeigt den gespeicherten Wert und sperrt bei abgeschlossenem Protokoll', () => {
        render(<DatenfeldEingabe feld={feld({ wert: '2006' })} bearbeitbar={false} onSetzen={vi.fn()} />);

        expect(screen.getByLabelText('Baujahr')).toHaveValue('2006');
        expect(screen.getByLabelText('Baujahr')).toBeDisabled();
    });

    it('Freitext behält seine Länge und hat keinen Jahres-Hinweis', () => {
        render(<DatenfeldEingabe feld={feld({ feldtyp: 'TEXT', label: 'Standort' })} bearbeitbar onSetzen={vi.fn()} />);

        expect(screen.getByLabelText('Standort')).toHaveAttribute('maxlength', '5000');
        expect(screen.getByLabelText('Standort')).not.toHaveAttribute('inputmode');
    });
});
