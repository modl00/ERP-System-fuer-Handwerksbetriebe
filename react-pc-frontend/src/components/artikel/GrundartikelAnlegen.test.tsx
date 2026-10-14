import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GrundartikelAnlegen } from './GrundartikelAnlegen';
import { ToastProvider } from '../ui/toast';

vi.mock('../CategoryTreeModal', () => ({
    CategoryTreeModal: ({ onSelect }: { onSelect: (id: number, name: string) => void }) => (
        <button type="button" onClick={() => onSelect(5, 'Hohlprofile > Quadratrohr')}>Kategorie 5 wählen</button>
    ),
}));

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
let fetcher: ReturnType<typeof vi.fn>;
const posts = () => fetcher.mock.calls.filter(([u, i]) => String(u).endsWith('/grundartikel') && i?.method === 'POST');

beforeEach(() => {
    fetcher = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        const u = String(url);
        if (u.endsWith('/grundartikel/formen')) return json([{ profilform: 'QUADRATROHR', form: 'Quadratrohr' }, { profilform: 'RUNDROHR', form: 'Rundrohr' }]);
        if (u.endsWith('/werkstoffe/details')) return json([{ id: 4, name: 'Baustahl' }]);
        if (init?.method === 'POST') return json({ id: 77, produktname: 'Quadratrohr', kurzbezeichnung: 'QH' }, 201);
        return json({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const rendern = () => {
    const onAngelegt = vi.fn();
    const onAbbrechen = vi.fn();
    render(<ToastProvider><GrundartikelAnlegen onAngelegt={onAngelegt} onAbbrechen={onAbbrechen} /></ToastProvider>);
    return { onAngelegt, onAbbrechen };
};

describe('GrundartikelAnlegen', () => {
    it('verlangt Kurzbezeichnung, Name und Form, bevor etwas gesendet wird', async () => {
        const user = userEvent.setup();
        rendern();
        await screen.findByLabelText('Kurzbezeichnung *');

        await user.click(screen.getByRole('button', { name: 'Grundartikel anlegen' }));
        expect((await screen.findAllByText(/Kurzbezeichnung eingeben/)).length).toBeGreaterThan(0);

        await user.type(screen.getByLabelText('Kurzbezeichnung *'), 'QH');
        await user.click(screen.getByRole('button', { name: 'Grundartikel anlegen' }));
        expect((await screen.findAllByText(/Namen eingeben/)).length).toBeGreaterThan(0);

        await user.type(screen.getByLabelText('Name *'), 'Quadratrohr');
        await user.click(screen.getByRole('button', { name: 'Grundartikel anlegen' }));
        expect((await screen.findAllByText(/Form wählen/)).length).toBeGreaterThan(0);
        expect(posts()).toHaveLength(0);
    });

    it('prüft den Aufschlag vor dem Senden', async () => {
        const user = userEvent.setup();
        rendern();
        await user.type(await screen.findByLabelText('Kurzbezeichnung *'), 'QH');
        await user.type(screen.getByLabelText('Name *'), 'Quadratrohr');
        await user.click(screen.getByRole('combobox', { name: 'Form' }));
        await user.click(await screen.findByRole('option', { name: 'Quadratrohr' }));
        await user.type(screen.getByLabelText('Aufschlag in Prozent'), '1000');

        await user.click(screen.getByRole('button', { name: 'Grundartikel anlegen' }));

        expect((await screen.findAllByText(/999,99/)).length).toBeGreaterThan(0);
        expect(posts()).toHaveLength(0);
    });

    it('legt den Grundartikel an und meldet die neue ID', async () => {
        const user = userEvent.setup();
        const { onAngelegt } = rendern();
        await user.type(await screen.findByLabelText('Kurzbezeichnung *'), ' QH ');
        await user.type(screen.getByLabelText('Name *'), 'Quadratrohr');
        await user.click(screen.getByRole('combobox', { name: 'Form' }));
        await user.click(await screen.findByRole('option', { name: 'Quadratrohr' }));
        await user.click(screen.getByRole('combobox', { name: 'Werkstoff' }));
        await user.click(await screen.findByRole('option', { name: 'Baustahl' }));
        await user.click(screen.getByText('Kategorie wählen'));
        await user.click(await screen.findByRole('button', { name: 'Kategorie 5 wählen' }));
        await user.type(screen.getByLabelText('Aufschlag in Prozent'), '25');

        await user.click(screen.getByRole('button', { name: 'Grundartikel anlegen' }));

        await waitFor(() => expect(onAngelegt).toHaveBeenCalledWith(77));
        expect(JSON.parse(posts()[0][1].body as string)).toEqual({
            kurzbezeichnung: 'QH', produktname: 'Quadratrohr', profilform: 'QUADRATROHR', werkstoffId: 4, kategorieId: 5,
            verrechnungseinheit: 'LAUFENDE_METER', verkaufsaufschlagProzent: 25,
        });
    });

    it('zeigt die Meldung des Servers, zum Beispiel bei doppelter Kurzbezeichnung', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (url: RequestInfo | URL, init?: RequestInit) => {
            const u = String(url);
            if (u.endsWith('/grundartikel/formen')) return json([{ profilform: 'QUADRATROHR', form: 'Quadratrohr' }]);
            if (u.endsWith('/werkstoffe/details')) return json([]);
            if (init?.method === 'POST') return json({ message: 'Es gibt schon einen Grundartikel mit der Kurzbezeichnung QH.' }, 409);
            return json({});
        });
        const { onAngelegt } = rendern();
        await user.type(await screen.findByLabelText('Kurzbezeichnung *'), 'QH');
        await user.type(screen.getByLabelText('Name *'), 'Quadratrohr');
        await user.click(screen.getByRole('combobox', { name: 'Form' }));
        await user.click(await screen.findByRole('option', { name: 'Quadratrohr' }));

        await user.click(screen.getByRole('button', { name: 'Grundartikel anlegen' }));

        expect((await screen.findAllByText('Es gibt schon einen Grundartikel mit der Kurzbezeichnung QH.')).length).toBeGreaterThan(0);
        expect(onAngelegt).not.toHaveBeenCalled();
    });

    it('bricht ab', async () => {
        const user = userEvent.setup();
        const { onAbbrechen } = rendern();
        await screen.findByLabelText('Kurzbezeichnung *');

        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));

        expect(onAbbrechen).toHaveBeenCalled();
    });
});
