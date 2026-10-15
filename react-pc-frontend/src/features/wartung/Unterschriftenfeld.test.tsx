import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { Unterschriftenfeld } from './Unterschriftenfeld';

const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ';

function zeige(props: Partial<React.ComponentProps<typeof Unterschriftenfeld>> = {}) {
    const onSpeichern = vi.fn().mockResolvedValue(undefined);
    const onLoeschen = vi.fn().mockResolvedValue(undefined);
    render(
        <ConfirmProvider>
            <Unterschriftenfeld titel="Techniker" bild={null} name={null} bearbeitbar onSpeichern={onSpeichern} onLoeschen={onLoeschen} {...props} />
        </ConfirmProvider>,
    );
    return { onSpeichern, onLoeschen };
}

describe('Unterschriftenfeld', () => {
    // jsdom kann nicht zeichnen. Ohne Zeichenkontext bleibt die Fläche leer, das genügt für diese Tests.
    beforeEach(() => { vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null); });

    it('zeigt eine vorhandene Unterschrift als Bild mit Namen', () => {
        zeige({ bild: PNG, name: 'Max Mustermann' });
        expect(screen.getByRole('img', { name: 'Unterschrift von Max Mustermann' })).toHaveAttribute('src', PNG);
        expect(screen.getByText('Max Mustermann')).toBeVisible();
    });

    it('zeigt ein Bild, das kein PNG ist, nicht an, sondern bietet die Zeichenfläche an', () => {
        zeige({ bild: 'data:image/svg+xml;base64,PHN2Zy8+', name: 'Max' });
        expect(screen.queryByRole('img', { name: /Unterschrift von/ })).toBeNull();
        expect(screen.getByRole('img', { name: /Zeichenfläche/ })).toBeVisible();
    });

    it('schlägt bei einer Skript-URL als Bild nicht an', () => {
        zeige({ bild: 'javascript:alert(1)', name: 'Max' });
        expect(document.querySelector('img[src^="javascript"]')).toBeNull();
    });

    it('verlangt eine Unterschrift, bevor sie übernommen wird', async () => {
        const { onSpeichern } = zeige({ vorschlagName: 'Max Mustermann' });
        await userEvent.click(screen.getByRole('button', { name: 'Unterschrift übernehmen' }));
        expect(await screen.findByText('Bitte zuerst in das Feld unterschreiben.')).toBeVisible();
        expect(onSpeichern).not.toHaveBeenCalled();
    });

    it('schlägt den Namen vor und lässt ihn ändern', async () => {
        zeige({ vorschlagName: 'Max Mustermann' });
        const name = screen.getByRole('textbox', { name: 'Name' });
        expect(name).toHaveValue('Max Mustermann');
        await userEvent.clear(name);
        await userEvent.type(name, 'Erika Beispiel');
        expect(name).toHaveValue('Erika Beispiel');
    });

    it('bietet "Neu beginnen" erst an, wenn etwas gezeichnet ist', () => {
        zeige();
        const neu = screen.getByRole('button', { name: /Neu beginnen/ });
        expect(neu).toBeDisabled();
        expect(neu).toHaveAttribute('title', 'Es ist noch nichts gezeichnet');
    });

    it('lässt in einem abgeschlossenen Protokoll nichts zeichnen oder löschen', () => {
        zeige({ bild: PNG, name: 'Max Mustermann', bearbeitbar: false });
        expect(screen.queryByRole('button', { name: /Neu unterschreiben/ })).toBeNull();
        expect(screen.queryByRole('button', { name: /Löschen/ })).toBeNull();
    });

    it('sagt, dass noch nicht unterschrieben wurde, wenn nichts da ist und nichts geändert werden darf', () => {
        zeige({ bearbeitbar: false });
        expect(screen.getByText('Noch nicht unterschrieben.')).toBeVisible();
    });

    it('fragt vor dem Löschen einer Unterschrift nach', async () => {
        const { onLoeschen } = zeige({ bild: PNG, name: 'Max Mustermann' });
        await userEvent.click(screen.getByRole('button', { name: /Löschen/ }));
        expect(await screen.findByText(/wird entfernt/)).toBeVisible();
        expect(onLoeschen).not.toHaveBeenCalled();
        await userEvent.click(screen.getAllByRole('button', { name: 'Löschen' }).at(-1)!);
        await vi.waitFor(() => expect(onLoeschen).toHaveBeenCalledTimes(1));
    });

    it('löscht nicht, wenn die Rückfrage abgelehnt wird', async () => {
        const { onLoeschen } = zeige({ bild: PNG, name: 'Max Mustermann' });
        await userEvent.click(screen.getByRole('button', { name: /Löschen/ }));
        await userEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }));
        expect(onLoeschen).not.toHaveBeenCalled();
    });
});
