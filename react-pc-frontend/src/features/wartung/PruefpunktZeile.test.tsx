import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PruefpunktZeile } from './PruefpunktZeile';
import type { ProtokollPruefpunkt } from './types';

const punkt = (extra: Partial<ProtokollPruefpunkt> = {}): ProtokollPruefpunkt => ({
    id: 7, abschnittId: 1, modulKey: 'm_bst', label: 'Zarge auf Beschädigungen prüfen',
    ergebnis: null, bemerkung: null, vorherigesErgebnis: null, sortierung: 0, ...extra,
});

describe('PruefpunktZeile', () => {
    it('zeigt den Prüfpunkt mit drei Schaltflächen, von denen keine gewählt ist', () => {
        render(<PruefpunktZeile punkt={punkt()} bearbeitbar onBewerten={vi.fn()} />);
        expect(screen.getByText('Zarge auf Beschädigungen prüfen')).toBeVisible();
        for (const name of [/In Ordnung/, /Nicht in Ordnung/, /Nicht anwendbar/]) {
            expect(screen.getByRole('button', { name })).toHaveAttribute('aria-pressed', 'false');
        }
    });

    it('meldet die Bewertung mit dem Prüfpunkt und der bisherigen Bemerkung', async () => {
        const onBewerten = vi.fn();
        render(<PruefpunktZeile punkt={punkt()} bearbeitbar onBewerten={onBewerten} />);
        await userEvent.click(screen.getByRole('button', { name: /^In Ordnung/ }));
        expect(onBewerten).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }), 'IO', '');
    });

    it('nimmt die Bewertung zurück, wenn die gewählte Schaltfläche noch einmal gedrückt wird', async () => {
        const onBewerten = vi.fn();
        render(<PruefpunktZeile punkt={punkt({ ergebnis: 'IO' })} bearbeitbar onBewerten={onBewerten} />);
        const io = screen.getByRole('button', { name: /^In Ordnung/ });
        expect(io).toHaveAttribute('aria-pressed', 'true');
        await userEvent.click(io);
        expect(onBewerten).toHaveBeenCalledWith(expect.anything(), null, '');
    });

    it('wechselt von einer Bewertung zu einer anderen', async () => {
        const onBewerten = vi.fn();
        render(<PruefpunktZeile punkt={punkt({ ergebnis: 'IO' })} bearbeitbar onBewerten={onBewerten} />);
        await userEvent.click(screen.getByRole('button', { name: /^Nicht in Ordnung/ }));
        expect(onBewerten).toHaveBeenCalledWith(expect.anything(), 'NIO', '');
    });

    it('zeigt bei "nicht in Ordnung" ein Feld für den Mangel und speichert es beim Verlassen', async () => {
        const onBewerten = vi.fn();
        render(<PruefpunktZeile punkt={punkt({ ergebnis: 'NIO' })} bearbeitbar onBewerten={onBewerten} />);
        const feld = screen.getByRole('textbox', { name: 'Was ist nicht in Ordnung?' });
        await userEvent.type(feld, 'Dichtung gerissen');
        expect(onBewerten).not.toHaveBeenCalled();
        await userEvent.tab();
        expect(onBewerten).toHaveBeenCalledWith(expect.anything(), 'NIO', 'Dichtung gerissen');
    });

    it('speichert eine unveränderte Bemerkung nicht noch einmal', async () => {
        const onBewerten = vi.fn();
        render(<PruefpunktZeile punkt={punkt({ ergebnis: 'NIO', bemerkung: 'Dichtung gerissen' })} bearbeitbar onBewerten={onBewerten} />);
        await userEvent.click(screen.getByRole('textbox'));
        await userEvent.tab();
        expect(onBewerten).not.toHaveBeenCalled();
    });

    it('bietet bei jedem Ergebnis ein optionales Hinweisfeld an, das auf Wunsch aufklappt', async () => {
        const onBewerten = vi.fn();
        for (const ergebnis of ['IO', 'NA', null] as const) {
            const { unmount } = render(<PruefpunktZeile punkt={punkt({ ergebnis })} bearbeitbar onBewerten={onBewerten} />);
            expect(screen.queryByRole('textbox')).toBeNull();
            await userEvent.click(screen.getByRole('button', { name: 'Hinweis schreiben' }));
            expect(screen.getByRole('textbox')).toHaveValue('');
            unmount();
        }
    });

    it('nennt das Feld je nach Ergebnis Bemerkung, Fehlerbeschreibung oder Begründung', async () => {
        const { rerender } = render(<PruefpunktZeile punkt={punkt({ ergebnis: 'IO' })} bearbeitbar onBewerten={vi.fn()} />);
        await userEvent.click(screen.getByRole('button', { name: 'Hinweis schreiben' }));
        expect(screen.getByRole('textbox', { name: 'Hinweis / Bemerkung (optional)' })).toBeVisible();
        rerender(<PruefpunktZeile punkt={punkt({ ergebnis: 'NA' })} bearbeitbar onBewerten={vi.fn()} />);
        expect(screen.getByRole('textbox', { name: 'Begründung (optional)' })).toBeVisible();
        rerender(<PruefpunktZeile punkt={punkt({ ergebnis: 'NIO' })} bearbeitbar onBewerten={vi.fn()} />);
        expect(screen.getByRole('textbox', { name: 'Was ist nicht in Ordnung?' })).toBeVisible();
    });

    it('speichert bei i.O. eine Bemerkung mit dem unveränderten Ergebnis', async () => {
        const onBewerten = vi.fn();
        render(<PruefpunktZeile punkt={punkt({ ergebnis: 'IO' })} bearbeitbar onBewerten={onBewerten} />);
        await userEvent.click(screen.getByRole('button', { name: 'Hinweis schreiben' }));
        await userEvent.type(screen.getByRole('textbox'), 'Schild leicht verblasst');
        await userEvent.tab();
        expect(onBewerten).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }), 'IO', 'Schild leicht verblasst');
    });

    it('speichert bei n.a. eine Begründung und lässt die Bemerkung auch ganz weg', async () => {
        const onBewerten = vi.fn();
        render(<PruefpunktZeile punkt={punkt({ ergebnis: 'NA' })} bearbeitbar onBewerten={onBewerten} />);
        await userEvent.click(screen.getByRole('button', { name: 'Hinweis schreiben' }));
        await userEvent.type(screen.getByRole('textbox'), 'Gerät nicht vorhanden');
        await userEvent.tab();
        expect(onBewerten).toHaveBeenCalledWith(expect.anything(), 'NA', 'Gerät nicht vorhanden');

        onBewerten.mockClear();
        // Ohne jeden Text wird beim Verlassen nichts gesendet
        const { unmount } = render(<PruefpunktZeile punkt={punkt({ ergebnis: 'IO' })} bearbeitbar onBewerten={onBewerten} />);
        unmount();
        render(<PruefpunktZeile punkt={punkt({ ergebnis: 'IO' })} bearbeitbar onBewerten={onBewerten} />);
        expect(screen.queryAllByRole('textbox').length).toBeLessThanOrEqual(1);
        expect(onBewerten).not.toHaveBeenCalled();
    });

    it('lässt ein vorhandenes Hinweisfeld offen: Zuklappen würde Eingegebenes verstecken', () => {
        render(<PruefpunktZeile punkt={punkt({ ergebnis: 'IO', bemerkung: 'Schild leicht verblasst' })} bearbeitbar onBewerten={vi.fn()} />);
        expect(screen.getByRole('textbox')).toHaveValue('Schild leicht verblasst');
        expect(screen.getByRole('button', { name: 'Hinweis' })).toBeDisabled();
    });

    it('sperrt das Hinweisfeld im abgeschlossenen Protokoll, zeigt aber vorhandene Hinweise', () => {
        render(<PruefpunktZeile punkt={punkt({ ergebnis: 'IO', bemerkung: 'ok' })} bearbeitbar={false} onBewerten={vi.fn()} />);
        expect(screen.getByRole('textbox')).toBeDisabled();
        expect(screen.queryByRole('button', { name: /umformulieren/ })).toBeNull();
        expect(screen.queryByRole('button', { name: /löschen/ })).toBeNull();
    });

    it('ändert den Wortlaut mit Stift und Haken, Escape bricht ab, ein leerer Text wird nicht übernommen', async () => {
        const onTextAendern = vi.fn().mockResolvedValue(undefined);
        render(<PruefpunktZeile punkt={punkt({ label: 'Sicherheitsventil' })} bearbeitbar onBewerten={vi.fn()} onTextAendern={onTextAendern} />);
        await userEvent.click(screen.getByRole('button', { name: 'Prüfpunkt umformulieren: Sicherheitsventil' }));
        const feld = screen.getByRole('textbox', { name: 'Prüfpunkt umformulieren: Sicherheitsventil' });
        await userEvent.clear(feld);
        expect(screen.getByRole('button', { name: 'Neuen Wortlaut übernehmen' })).toBeDisabled();
        await userEvent.type(feld, 'Sicherheitsventil (Heizkreis 2)');
        await userEvent.click(screen.getByRole('button', { name: 'Neuen Wortlaut übernehmen' }));
        expect(onTextAendern).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }), 'Sicherheitsventil (Heizkreis 2)');
    });

    it('bricht das Umformulieren mit Escape ab und sendet nichts', async () => {
        const onTextAendern = vi.fn();
        render(<PruefpunktZeile punkt={punkt({ label: 'Sicherheitsventil' })} bearbeitbar onBewerten={vi.fn()} onTextAendern={onTextAendern} />);
        await userEvent.click(screen.getByRole('button', { name: 'Prüfpunkt umformulieren: Sicherheitsventil' }));
        await userEvent.type(screen.getByRole('textbox'), 'xyz{Escape}');
        expect(onTextAendern).not.toHaveBeenCalled();
        expect(screen.getByText('Sicherheitsventil')).toBeVisible();
    });

    it('meldet das Löschen mit dem Prüfpunkt, ohne selbst zu fragen', async () => {
        const onLoeschen = vi.fn();
        render(<PruefpunktZeile punkt={punkt({ label: 'Sicherheitsventil' })} bearbeitbar onBewerten={vi.fn()} onLoeschen={onLoeschen} />);
        await userEvent.click(screen.getByRole('button', { name: 'Prüfpunkt löschen: Sicherheitsventil' }));
        expect(onLoeschen).toHaveBeenCalledWith(expect.objectContaining({ id: 7, label: 'Sicherheitsventil' }));
    });

    it('übernimmt eine Bemerkung, die der Server geändert hat', () => {
        const { rerender } = render(<PruefpunktZeile punkt={punkt({ ergebnis: 'NIO', bemerkung: 'alt' })} bearbeitbar onBewerten={vi.fn()} />);
        rerender(<PruefpunktZeile punkt={punkt({ ergebnis: 'NIO', bemerkung: 'neu' })} bearbeitbar onBewerten={vi.fn()} />);
        expect(screen.getByRole('textbox')).toHaveValue('neu');
    });

    it('zeigt das Ergebnis vom letzten Mal', () => {
        render(<PruefpunktZeile punkt={punkt({ vorherigesErgebnis: 'NIO' })} bearbeitbar onBewerten={vi.fn()} />);
        expect(screen.getByText(/Letztes Mal/)).toHaveTextContent('Letztes Mal: n.i.O.');
    });

    it('lässt in einem abgeschlossenen Protokoll nichts mehr zu und erklärt warum', async () => {
        const onBewerten = vi.fn();
        render(<PruefpunktZeile punkt={punkt({ ergebnis: 'IO' })} bearbeitbar={false} onBewerten={onBewerten} />);
        const io = screen.getByRole('button', { name: /^In Ordnung/ });
        expect(io).toBeDisabled();
        await userEvent.click(io);
        expect(onBewerten).not.toHaveBeenCalled();
        expect(screen.getByRole('group')).toHaveAttribute('title', expect.stringMatching(/wieder öffnen/));
    });
});
