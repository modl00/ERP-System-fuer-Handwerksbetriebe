import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { DatenfeldZeile, type DatenfeldWerte } from './DatenfeldZeile';

/** Hält den Zustand wie die Seiten, die die Zeile benutzen, und macht ihn für den Test sichtbar. */
function Rahmen({ start, melde }: { start: DatenfeldWerte; melde: (f: DatenfeldWerte) => void }) {
    const [feld, setFeld] = useState(start);
    return (
        <DatenfeldZeile
            idPrefix="t" nummer={1} feld={feld} onEntfernen={() => undefined}
            onChange={(neu) => { setFeld(neu); melde(neu); }}
        />
    );
}

async function waehleArt(name: string) {
    await userEvent.click(screen.getByRole('combobox', { name: 'Art von Datenfeld 1' }));
    await userEvent.click(await screen.findByRole('option', { name }));
}

describe('DatenfeldZeile', () => {
    it('zeigt Möglichkeiten nur bei einer Auswahl und gibt sie als Liste ohne leere Zeilen weiter', async () => {
        let letzter: DatenfeldWerte = { label: 'Zustand', feldtyp: 'AUSWAHL', optionen: [] };
        render(<Rahmen start={letzter} melde={(f) => { letzter = f; }} />);

        await userEvent.type(screen.getByLabelText('Möglichkeiten von Datenfeld 1'), 'gut{Enter}{Enter} schlecht {Enter}');
        expect(letzter.optionen).toEqual(['gut', 'schlecht']);
        // Der Zeilenumbruch am Ende bleibt im Textfeld stehen, man kann weiterschreiben
        expect((screen.getByLabelText('Möglichkeiten von Datenfeld 1') as HTMLTextAreaElement).value).toBe('gut\n\n schlecht \n');

        await waehleArt('Freitext');
        expect(screen.queryByLabelText('Möglichkeiten von Datenfeld 1')).not.toBeInTheDocument();
    });

    it('behält die Möglichkeiten, wenn man die Art wechselt und zurückwechselt, und Text und Daten bleiben gleich', async () => {
        let letzter: DatenfeldWerte = { label: 'Zustand', feldtyp: 'AUSWAHL', optionen: ['gut', 'mittel'] };
        render(<Rahmen start={letzter} melde={(f) => { letzter = f; }} />);

        await waehleArt('Freitext');
        await waehleArt('Auswahl aus Liste');

        expect((screen.getByLabelText('Möglichkeiten von Datenfeld 1') as HTMLTextAreaElement).value).toBe('gut\nmittel');
        expect(letzter).toEqual({ label: 'Zustand', feldtyp: 'AUSWAHL', optionen: ['gut', 'mittel'] });
    });

    it('benennt die Eingaben eindeutig, auch mit Abschnitt', () => {
        render(<DatenfeldZeile idPrefix="t" nummer={2} kontext="Datenfeld-Abschnitt 3" feld={{ label: '', feldtyp: 'TEXT', optionen: [] }} onChange={() => undefined} onEntfernen={() => undefined} />);
        expect(screen.getByLabelText('Bezeichnung von Datenfeld 2 in Datenfeld-Abschnitt 3')).toBeVisible();
        expect(screen.getByRole('button', { name: 'Datenfeld 2 in Datenfeld-Abschnitt 3 entfernen' })).toBeVisible();
    });
});
