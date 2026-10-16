import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EigenePositionUebernehmen } from './EigenePositionUebernehmen';
import { ToastProvider } from '../ui/toast';
import type { DocBlock } from './types';

/** Nur Dummy-Daten. */
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten, text: async () => JSON.stringify(daten) });
const kategorien = [{ id: 1, bezeichnung: 'Geländer', parentId: null }, { id: 2, bezeichnung: 'Pfosten', parentId: 1 }];
const block = (teil: Partial<DocBlock> = {}): DocBlock => ({ id: 'b1', type: 'SERVICE', title: 'Sonderpfosten verzinkt', description: '<p>Nach Zeichnung gefertigt</p>', quantity: 4, unit: 'Stk', price: 89.5, eigenePosition: true, ...teil });

let fetcher: ReturnType<typeof vi.fn>;
let leistungAntwort: () => ReturnType<typeof antwort>;
const posts = () => fetcher.mock.calls.filter(([u, i]) => String(u) === '/api/leistungen' && i?.method === 'POST');

beforeEach(() => {
    leistungAntwort = () => antwort({ id: 77, name: 'Sonderpfosten verzinkt' });
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url).startsWith('/api/produktkategorien')) return antwort(kategorien);
        if (String(url) === '/api/leistungen' && init?.method === 'POST') return leistungAntwort();
        if (String(url).endsWith('/uebernehmen') && init?.method === 'POST') return antwort({ id: 77, name: 'Sonderpfosten verzinkt' });
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (b: DocBlock = block(), onUebernommen = vi.fn()) => ({ onUebernommen, ...render(<ToastProvider><EigenePositionUebernehmen block={b} onUebernommen={onUebernommen} /></ToastProvider>) });

describe('Eigene Position in den Leistungsstamm übernehmen', () => {
    it('ohne Titel ist der Knopf gesperrt und erklärt warum', () => {
        zeige(block({ title: '' }));
        expect(screen.getByRole('button', { name: /In den Leistungsstamm übernehmen/ })).toBeDisabled();
    });

    it('übernimmt Name, Text, Preis, Einheit und Kategorie; die Position verweist danach auf die Leistung', async () => {
        const user = userEvent.setup();
        const { onUebernommen } = zeige();
        await user.click(screen.getByRole('button', { name: /In den Leistungsstamm übernehmen/ }));

        expect(await screen.findByLabelText('Name der Leistung')).toHaveValue('Sonderpfosten verzinkt');
        expect(screen.getByLabelText('Abgerechnet wird')).toHaveValue('STUECK');
        expect(screen.getByText(/89,50/)).toBeVisible();

        await user.click(screen.getByText('Kategorie wählen'));
        await user.click(await screen.findByText('Geländer / Pfosten'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(posts()).toHaveLength(1));
        expect(JSON.parse(posts()[0][1].body as string)).toEqual({ name: 'Sonderpfosten verzinkt', description: '<p>Nach Zeichnung gefertigt</p>', price: 89.5, unit: 'STUECK', folderId: 2 });
        await waitFor(() => expect(onUebernommen).toHaveBeenCalledWith(77, 2));
    });

    it('wurde die Position schon kalkuliert, wird die temporäre Leistung samt Kalkulation übernommen, statt eine zweite anzulegen', async () => {
        const user = userEvent.setup();
        const { onUebernommen } = zeige(block({ leistungId: 77, temporaereLeistung: true }));
        await user.click(screen.getByRole('button', { name: /In den Leistungsstamm übernehmen/ }));
        await screen.findByLabelText('Name der Leistung');
        await user.click(screen.getByText('Kategorie wählen'));
        await user.click(await screen.findByText('Geländer / Pfosten'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(fetcher.mock.calls.filter(([u, i]) => String(u) === '/api/leistungen/77/uebernehmen' && i?.method === 'POST')).toHaveLength(1));
        expect(posts()).toHaveLength(0);
        await waitFor(() => expect(onUebernommen).toHaveBeenCalledWith(77, 2));
    });

    it('das Kürzel der Position bestimmt die vorgewählte Einheit (lfm, m², kg), Unbekanntes wird zu Stück', async () => {
        const user = userEvent.setup();
        zeige(block({ unit: 'lfm' }));
        await user.click(screen.getByRole('button', { name: /In den Leistungsstamm übernehmen/ }));
        expect(await screen.findByLabelText('Abgerechnet wird')).toHaveValue('LAUFENDE_METER');
    });

    it('ohne Kategorie oder bei einem Fehler des Servers bleibt der Dialog offen und nennt den Grund', async () => {
        const user = userEvent.setup();
        const { onUebernommen } = zeige();
        await user.click(screen.getByRole('button', { name: /In den Leistungsstamm übernehmen/ }));
        await screen.findByLabelText('Name der Leistung');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine Kategorie wählen');
        expect(posts()).toHaveLength(0);

        leistungAntwort = () => antwort({ message: 'Eine Leistung mit diesem Namen gibt es schon.' }, false, 409);
        await user.click(screen.getByText('Kategorie wählen'));
        await user.click(await screen.findByText('Geländer'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Eine Leistung mit diesem Namen gibt es schon.')).toBeVisible();
        expect(onUebernommen).not.toHaveBeenCalled();
    });

    it('der Name lässt sich vor dem Übernehmen ändern und darf nicht leer sein', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(screen.getByRole('button', { name: /In den Leistungsstamm übernehmen/ }));
        const name = await screen.findByLabelText('Name der Leistung');
        await user.clear(name);
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Bitte einen Namen');
    });
});
