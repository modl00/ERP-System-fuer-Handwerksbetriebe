import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NeueKategorieDialog } from './NeueKategorieDialog';
import { legeKategorieAn } from './kategorieApi';

/** Nur Dummy-Daten. */
const folders = [
    { id: '1', name: 'Metallbau', parentId: null },
    { id: '2', name: 'Geländer', parentId: '1' },
];
let fetcher: ReturnType<typeof vi.fn>;
let antwort: () => Response;
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body, text: async () => JSON.stringify(body) }) as Response;
const form = () => (fetcher.mock.calls[0][1] as RequestInit).body as FormData;

beforeEach(() => {
    antwort = () => json({ id: 9, bezeichnung: 'Treppen' }, 201);
    fetcher = vi.fn(async () => antwort());
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('NeueKategorieDialog', () => {
    it('legt die Kategorie mit Name, Elternkategorie und Einheit an und meldet sie zurück', async () => {
        const u = userEvent.setup();
        const angelegt = vi.fn();
        render(<NeueKategorieDialog open onOpenChange={vi.fn()} folders={folders} elternId="1" onAngelegt={angelegt} />);

        await u.type(screen.getByLabelText('Name'), '  Treppen ');
        await u.click(screen.getByRole('button', { name: /Anlegen/ }));

        await waitFor(() => expect(angelegt).toHaveBeenCalledWith({ id: 9, bezeichnung: 'Treppen' }));
        expect(fetcher.mock.calls[0][0]).toBe('/api/produktkategorien');
        expect((fetcher.mock.calls[0][1] as RequestInit).method).toBe('POST');
        expect(form().get('bezeichnung')).toBe('Treppen');
        expect(form().get('parentId')).toBe('1');
        expect(form().get('verrechnungseinheit')).toBe('STUECK');
    });

    it('ohne Namen wird nichts geschickt', async () => {
        const u = userEvent.setup();
        render(<NeueKategorieDialog open onOpenChange={vi.fn()} folders={folders} elternId={null} onAngelegt={vi.fn()} />);
        await u.click(screen.getByRole('button', { name: /Anlegen/ }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Bitte einen Namen eingeben.');
        expect(fetcher).not.toHaveBeenCalled();
    });

    it('zeigt den Fehler des Servers und lässt den Dialog offen', async () => {
        const u = userEvent.setup();
        antwort = () => json({ message: 'Eine Kategorie mit diesem Namen gibt es schon.' }, 400);
        const angelegt = vi.fn();
        render(<NeueKategorieDialog open onOpenChange={vi.fn()} folders={folders} elternId={null} onAngelegt={angelegt} />);

        await u.type(screen.getByLabelText('Name'), 'Treppen');
        await u.click(screen.getByRole('button', { name: /Anlegen/ }));

        expect(await screen.findByRole('alert')).toHaveTextContent('gibt es schon');
        expect(angelegt).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: /Anlegen/ })).toBeEnabled();
    });

    it('oberste Ebene schickt keine Elternkategorie mit; Abbrechen schließt', async () => {
        const u = userEvent.setup();
        const zu = vi.fn();
        render(<NeueKategorieDialog open onOpenChange={zu} folders={folders} elternId={null} onAngelegt={vi.fn()} />);
        await u.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(zu).toHaveBeenCalledWith(false);
        await legeKategorieAn('Dach', null, 'QUADRATMETER');
        expect(form().has('parentId')).toBe(false);
        expect(form().get('verrechnungseinheit')).toBe('QUADRATMETER');
    });
});
