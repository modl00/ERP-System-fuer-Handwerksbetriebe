import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DarstellungSection } from './DarstellungSection';
import { ToastProvider } from '../../ui/toast';

let puts: Record<string, unknown>[];

function stub(gespeichert: string | null, putStatus = 200) {
    const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
    vi.stubGlobal('fetch', vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'PUT') {
            const body = JSON.parse(String(init.body)) as { farbe: string | null };
            puts.push(body);
            return putStatus === 200 ? json({ farbe: body.farbe }) : json({ message: 'Bitte eine Farbe wie #e11d48 angeben.' }, putStatus);
        }
        return json({ farbe: gespeichert });
    }));
}

const rendern = () => render(<ToastProvider><DarstellungSection /></ToastProvider>);
const wert = () => document.documentElement.style.getPropertyValue('--farbe-600');

beforeEach(() => { puts = []; });
afterEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.removeAttribute('style');
    localStorage.clear();
});

describe('Darstellung: Programmfarbe', { timeout: 30000 }, () => {
    it('zeigt die gespeicherte Farbe als gewählt; Speichern geht erst nach einer Änderung', async () => {
        stub('#0f766e');
        rendern();
        await waitFor(() => expect(screen.getByRole('button', { name: /Türkis/ })).toHaveAttribute('aria-pressed', 'true'));
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    });

    it('zeigt die Farbe sofort als Vorschau und speichert sie erst auf Knopfdruck für die ganze Firma', async () => {
        stub(null);
        const user = userEvent.setup();
        rendern();
        await waitFor(() => expect(screen.getByRole('button', { name: /Rosé \(Standard\)/ })).toHaveAttribute('aria-pressed', 'true'));

        await user.click(screen.getByRole('button', { name: /Blau/ }));
        expect(wert()).toBe('29 78 216');
        expect(puts).toHaveLength(0);

        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(puts).toEqual([{ farbe: '#1d4ed8' }]));
        expect(localStorage.getItem('programmfarbe')).toBe('#1d4ed8');
        expect(wert()).toBe('29 78 216');
    });

    it('nimmt eine eigene Farbe an, auch ohne Raute und in Großbuchstaben', async () => {
        stub(null);
        const user = userEvent.setup();
        rendern();
        await waitFor(() => expect(screen.getByLabelText('Eigene Farbe')).toBeEnabled());

        await user.type(screen.getByLabelText('Eigene Farbe'), '0F766E');
        await user.click(screen.getByRole('button', { name: 'Ausprobieren' }));
        expect(wert()).toBe('15 118 110');
    });

    it('lehnt eine ungültige eigene Farbe mit einem Hinweis ab', async () => {
        stub(null);
        const user = userEvent.setup();
        rendern();
        await waitFor(() => expect(screen.getByLabelText('Eigene Farbe')).toBeEnabled());

        await user.type(screen.getByLabelText('Eigene Farbe'), 'rot');
        await user.click(screen.getByRole('button', { name: 'Ausprobieren' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine Farbe wie #0f766e angeben');
        expect(wert()).toBe('');
    });

    it('verwirft die Vorschau und stellt die gespeicherte Farbe wieder her', async () => {
        stub('#0f766e');
        const user = userEvent.setup();
        rendern();
        await waitFor(() => expect(screen.getByRole('button', { name: /Türkis/ })).toHaveAttribute('aria-pressed', 'true'));

        await user.click(screen.getByRole('button', { name: /Violett/ }));
        expect(wert()).toBe('126 34 206');
        await user.click(screen.getByRole('button', { name: 'Verwerfen' }));
        expect(wert()).toBe('15 118 110');
        expect(screen.getByRole('button', { name: /Türkis/ })).toHaveAttribute('aria-pressed', 'true');
    });

    it('stellt das Standard-Rosé wieder her', async () => {
        stub('#0f766e');
        const user = userEvent.setup();
        rendern();
        const knopf = await screen.findByRole('button', { name: /Standard-Rosé wiederherstellen/ });
        await waitFor(() => expect(knopf).toBeEnabled());

        await user.click(knopf);
        await waitFor(() => expect(puts).toEqual([{ farbe: null }]));
        expect(wert()).toBe('');
    });

    it('warnt bei einer Farbe, auf der weiße Schrift schlecht lesbar ist', async () => {
        stub(null);
        const user = userEvent.setup();
        rendern();
        await waitFor(() => expect(screen.getByLabelText('Eigene Farbe')).toBeEnabled());

        await user.type(screen.getByLabelText('Eigene Farbe'), '#ffff00');
        await user.click(screen.getByRole('button', { name: 'Ausprobieren' }));
        expect(await screen.findByRole('status')).toHaveTextContent('schwer zu lesen');
    });

    it('geht beim Verlassen ohne Speichern zurück zur gespeicherten Farbe', async () => {
        stub('#0f766e');
        const user = userEvent.setup();
        const { unmount } = rendern();
        await waitFor(() => expect(screen.getByRole('button', { name: /Türkis/ })).toHaveAttribute('aria-pressed', 'true'));
        await user.click(screen.getByRole('button', { name: /Violett/ }));
        expect(wert()).toBe('126 34 206');

        unmount();
        expect(wert()).toBe('15 118 110');
    });

    it('zeigt den Fehler des Servers beim Speichern und behält die Vorschau', async () => {
        stub(null, 400);
        const user = userEvent.setup();
        rendern();
        await waitFor(() => expect(screen.getByRole('button', { name: /Blau/ })).toBeEnabled());

        await user.click(screen.getByRole('button', { name: /Blau/ }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Bitte eine Farbe wie #e11d48 angeben.')).toBeInTheDocument();
        expect(localStorage.getItem('programmfarbe')).toBeNull();
    });
});
