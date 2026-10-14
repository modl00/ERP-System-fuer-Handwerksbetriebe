import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtikelLoeschen } from './ArtikelLoeschen';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';

/** Nur Dummy-Daten. */
const MIT_PROJEKTEN = {
    id: 5, name: 'Rundrohr 42,4 x 2', archiviert: true, lieferantenpreise: 4, dokumente: 1, varianten: 0, grundartikelName: null,
    projekte: [{ projektId: 9, bauvorhaben: 'Geländer Musterstraße', menge: '3 Stück, 12,5 m' }, { projektId: 10, bauvorhaben: 'Treppe Beispielweg', menge: '1 Stück' }],
};
const OHNE_PROJEKTE = { ...MIT_PROJEKTEN, projekte: [], lieferantenpreise: 1, dokumente: 0 };

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
let verwendungen: unknown;
let loeschAntwort: () => Response;
let aufrufe: { url: string; methode: string }[];

beforeEach(() => {
    aufrufe = [];
    verwendungen = MIT_PROJEKTEN;
    loeschAntwort = () => json({ alsPlatzhalter: true, projekte: 2 });
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        aufrufe.push({ url: String(url), methode: init?.method ?? 'GET' });
        if (init?.method === 'DELETE') return loeschAntwort();
        return json(verwendungen);
    }));
});
afterEach(() => { vi.unstubAllGlobals(); });

const rendern = (onGeloescht = vi.fn(), onAbbrechen = vi.fn()) => {
    render(<ToastProvider><ConfirmProvider><ArtikelLoeschen artikelId={5} name="Rundrohr 42,4 x 2" onGeloescht={onGeloescht} onAbbrechen={onAbbrechen} /></ConfirmProvider></ToastProvider>);
    return { onGeloescht, onAbbrechen };
};

describe('ArtikelLoeschen', () => {
    it('zeigt zuerst, wo der Artikel verwendet wird', async () => {
        rendern();

        expect(await screen.findByText('Geländer Musterstraße')).toBeVisible();
        expect(screen.getByText('3 Stück, 12,5 m')).toBeVisible();
        expect(screen.getByText('Treppe Beispielweg')).toBeVisible();
        expect(screen.getByText(/4 Lieferantenpreise und 1 Dokument, die mit gelöscht werden/)).toBeVisible();
        expect(aufrufe.filter(a => a.methode === 'DELETE')).toHaveLength(0);
    });

    it('löscht erst nach der Bestätigung und weist auf den Text mit Preis in den Projekten hin', async () => {
        const user = userEvent.setup();
        const { onGeloescht } = rendern();
        await screen.findByText('Geländer Musterstraße');

        await user.click(screen.getByRole('button', { name: 'Endgültig löschen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Artikel endgültig löschen?' });
        expect(dialog).toHaveTextContent('steckt in 2 Projekten');
        expect(dialog).toHaveTextContent('als Text mit Preis');
        expect(aufrufe.filter(a => a.methode === 'DELETE')).toHaveLength(0);

        await user.click(screen.getAllByRole('button', { name: 'Endgültig löschen' }).at(-1)!);

        await waitFor(() => expect(onGeloescht).toHaveBeenCalled());
        expect(aufrufe.filter(a => a.methode === 'DELETE')).toEqual([{ url: '/api/artikel/5', methode: 'DELETE' }]);
    });

    it('sagt ohne Projektverwendung, dass der Artikel samt Preisen entfernt wird', async () => {
        verwendungen = OHNE_PROJEKTE;
        const user = userEvent.setup();
        rendern();
        expect(await screen.findByText('In keinem Projekt.')).toBeVisible();

        await user.click(screen.getByRole('button', { name: 'Endgültig löschen' }));

        expect(await screen.findByRole('dialog', { name: 'Artikel endgültig löschen?' })).toHaveTextContent('samt seinen Preisen und Dokumenten wird entfernt');
    });

    it('bricht bei „Abbrechen“ in der Rückfrage ab, ohne zu löschen', async () => {
        const user = userEvent.setup();
        const { onGeloescht } = rendern();
        await screen.findByText('Geländer Musterstraße');
        await user.click(screen.getByRole('button', { name: 'Endgültig löschen' }));
        await screen.findByRole('dialog', { name: 'Artikel endgültig löschen?' });

        await user.click(screen.getAllByRole('button', { name: 'Abbrechen' }).at(-1)!);

        expect(onGeloescht).not.toHaveBeenCalled();
        expect(aufrufe.filter(a => a.methode === 'DELETE')).toHaveLength(0);
    });

    it('sperrt das Löschen eines Grundartikels, der noch Varianten hat', async () => {
        verwendungen = { ...MIT_PROJEKTEN, projekte: [], varianten: 3 };
        rendern();

        expect(await screen.findByText(/noch 3 Varianten/)).toBeVisible();
        expect(screen.getByRole('button', { name: 'Endgültig löschen' })).toBeDisabled();
    });

    it('zeigt die Meldung des Servers, wenn das Löschen scheitert', async () => {
        loeschAntwort = () => json({ message: 'Bitte den Artikel zuerst archivieren. Endgültig gelöscht wird nur aus dem Archiv.' }, 409);
        const user = userEvent.setup();
        const { onGeloescht } = rendern();
        await screen.findByText('Geländer Musterstraße');
        await user.click(screen.getByRole('button', { name: 'Endgültig löschen' }));
        await screen.findByRole('dialog', { name: 'Artikel endgültig löschen?' });
        await user.click(screen.getAllByRole('button', { name: 'Endgültig löschen' }).at(-1)!);

        expect((await screen.findAllByText(/zuerst archivieren/)).length).toBeGreaterThan(0);
        expect(onGeloescht).not.toHaveBeenCalled();
    });

    it('meldet, wenn die Verwendung nicht geladen werden kann, und sperrt das Löschen', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'Den Artikel gibt es nicht.' }, 404)));
        rendern();

        expect((await screen.findAllByText('Den Artikel gibt es nicht.')).length).toBeGreaterThan(0);
        expect(screen.getByRole('button', { name: 'Endgültig löschen' })).toBeDisabled();
    });
});
