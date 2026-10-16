import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EigenePositionAlsArtikel } from './EigenePositionAlsArtikel';
import { aufschlagFuer } from './helpers';
import { ToastProvider } from '../ui/toast';
import type { DocBlock } from './types';

vi.mock('../CategoryTreeModal', () => ({
    CategoryTreeModal: ({ onSelect, onClose }: { onSelect?: (id: number, name: string) => void; onClose: () => void }) => (
        <div role="dialog" aria-label="Kategorie-Auswahl">
            <button onClick={() => onSelect?.(12, 'Befestigung > Schrauben')}>Schrauben wählen</button>
            <button onClick={onClose}>Schließen</button>
        </div>
    ),
}));

/** Nur Dummy-Daten. */
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten, text: async () => JSON.stringify(daten) });
const block = (teil: Partial<DocBlock> = {}): DocBlock => ({ id: 'b1', type: 'SERVICE', title: 'Sonderschraube M12', description: '<p>Edelstahl A4, nach Zeichnung</p>', quantity: 20, unit: 'Stk', price: 12, eigenePosition: true, ...teil });

let fetcher: ReturnType<typeof vi.fn>;
let artikelAntwort: () => ReturnType<typeof antwort>;
const aufrufe = (methode: string, teil: string) => fetcher.mock.calls.filter(([u, i]) => (i?.method ?? 'GET') === methode && String(u).includes(teil));

beforeEach(() => {
    artikelAntwort = () => antwort({ id: 501 });
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const u = String(url);
        if (u.startsWith('/api/lieferanten')) return antwort({ lieferanten: [{ id: 3, lieferantenname: 'Muster Schraubenhandel' }] });
        if (u === '/api/artikel' && init?.method === 'POST') return artikelAntwort();
        if (u.endsWith('/dokumenttexte')) return antwort({ id: 501 });
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (b: DocBlock = block(), onUebernommen = vi.fn()) => ({ onUebernommen, ...render(<ToastProvider><EigenePositionAlsArtikel block={b} onUebernommen={onUebernommen} /></ToastProvider>) });
const oeffnen = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.click(screen.getByRole('button', { name: /Als Artikel übernehmen/ }));
    await screen.findByLabelText('Bezeichnung des Artikels');
};

describe('aufschlagFuer', () => {
    it('rechnet den Aufschlag so, dass Einkauf mal (1 + Aufschlag) den Verkaufspreis ergibt', () => {
        expect(aufschlagFuer(12, 8)).toBe(50);
        expect(aufschlagFuer(10, 10)).toBe(0);
        expect(aufschlagFuer(8, 10)).toBe(0);
        expect(aufschlagFuer(12.34, 7)).toBe(76.29);
        expect(aufschlagFuer(1000000, 1)).toBe(999.99);
        expect(aufschlagFuer(12, null)).toBeNull();
        expect(aufschlagFuer(12, 0)).toBeNull();
    });
});

describe('Eigene Position als Artikel übernehmen', () => {
    it('ohne Titel ist der Knopf gesperrt', () => {
        zeige(block({ title: '' }));
        expect(screen.getByRole('button', { name: /Als Artikel übernehmen/ })).toBeDisabled();
    });

    it('legt den Artikel mit Kategorie und Einheit an, setzt Text und Aufschlag aus dem Einkaufspreis, und die Position verweist danach darauf', async () => {
        const user = userEvent.setup();
        const { onUebernommen } = zeige();
        await oeffnen(user);
        expect(screen.getByLabelText('Bezeichnung des Artikels')).toHaveValue('Sonderschraube M12');
        expect(screen.getByLabelText('Abgerechnet wird')).toHaveValue('STUECK');

        await user.click(screen.getByRole('button', { name: 'Kategorie wählen' }));
        await user.click(await screen.findByRole('button', { name: 'Schrauben wählen' }));
        await user.click(screen.getByText('Lieferant wählen'));
        await user.click(await screen.findByText('Muster Schraubenhandel'));
        await user.type(screen.getByLabelText(/Einkaufspreis netto/), '8');
        expect(screen.getByTestId('aufschlag-hinweis')).toHaveTextContent('Aufschlag von 50 %');

        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('POST', '/api/artikel')).toHaveLength(1));
        expect(JSON.parse(aufrufe('POST', '/api/artikel')[0][1].body as string)).toEqual({ produktname: 'Sonderschraube M12', verrechnungseinheit: 'STUECK', kategorieId: 12, lieferantId: 3, preis: 8 });
        await waitFor(() => expect(aufrufe('PATCH', '/api/artikel/501/dokumenttexte')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PATCH', '/dokumenttexte')[0][1].body as string)).toEqual({ kurzbeschreibung: 'Sonderschraube M12', beschreibung: '<p>Edelstahl A4, nach Zeichnung</p>', verkaufsaufschlagProzent: 50 });
        await waitFor(() => expect(onUebernommen).toHaveBeenCalledWith(501));
    });

    it('ohne Einkaufspreis wird nur der Artikel angelegt, ohne Lieferant und ohne Aufschlag', async () => {
        const user = userEvent.setup();
        const { onUebernommen } = zeige();
        await oeffnen(user);
        await user.click(screen.getByRole('button', { name: 'Kategorie wählen' }));
        await user.click(await screen.findByRole('button', { name: 'Schrauben wählen' }));
        expect(screen.getByTestId('aufschlag-hinweis')).toHaveTextContent('Ohne Einkaufspreis bleibt der Aufschlag leer');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('POST', '/api/artikel')).toHaveLength(1));
        expect(JSON.parse(aufrufe('POST', '/api/artikel')[0][1].body as string)).toEqual({ produktname: 'Sonderschraube M12', verrechnungseinheit: 'STUECK', kategorieId: 12 });
        await waitFor(() => expect(onUebernommen).toHaveBeenCalledWith(501));
        expect(JSON.parse(aufrufe('PATCH', '/dokumenttexte')[0][1].body as string)).not.toHaveProperty('verkaufsaufschlagProzent');
    });

    it('fehlen Kategorie oder der Lieferant zum Einkaufspreis, bleibt der Dialog offen und nennt den Grund; nichts wird gesendet', async () => {
        const user = userEvent.setup();
        const { onUebernommen } = zeige();
        await oeffnen(user);
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine Kategorie wählen');

        await user.click(screen.getByRole('button', { name: 'Kategorie wählen' }));
        await user.click(await screen.findByRole('button', { name: 'Schrauben wählen' }));
        await user.type(screen.getByLabelText(/Einkaufspreis netto/), '8');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Zum Einkaufspreis gehört der Lieferant');
        expect(aufrufe('POST', '/api/artikel')).toHaveLength(0);
        expect(onUebernommen).not.toHaveBeenCalled();
    });

    it('eine Ablehnung des Servers steht im Dialog, und die Position bleibt unverändert', async () => {
        artikelAntwort = () => antwort({ message: 'Die EAN gehört schon zu einem anderen Artikel.' }, false, 409);
        const user = userEvent.setup();
        const { onUebernommen } = zeige();
        await oeffnen(user);
        await user.click(screen.getByRole('button', { name: 'Kategorie wählen' }));
        await user.click(await screen.findByRole('button', { name: 'Schrauben wählen' }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Die EAN gehört schon zu einem anderen Artikel.')).toBeVisible();
        expect(onUebernommen).not.toHaveBeenCalled();
    });

    it('liegt der Verkaufspreis unter dem Einkaufspreis, warnt der Dialog und setzt den Aufschlag auf 0', async () => {
        const user = userEvent.setup();
        zeige(block({ price: 5 }));
        await oeffnen(user);
        await user.type(screen.getByLabelText(/Einkaufspreis netto/), '8');
        expect(screen.getByTestId('aufschlag-hinweis')).toHaveTextContent('unter dem Einkaufspreis');
        expect(screen.getByTestId('aufschlag-hinweis')).toHaveTextContent('0 %');
    });
});
