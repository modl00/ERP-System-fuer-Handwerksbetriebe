import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import WartungUebersicht from './WartungUebersicht';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import type { WartungListeneintrag } from '../features/wartung/types';

/** Nur Dummy-Daten. */
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ isAdmin: false }) }));

const eintrag = (id: number, extra: Partial<WartungListeneintrag> = {}): WartungListeneintrag => ({
    id, nummer: `WA-2026-100${id}`, titel: null, protokollTyp: 'Wartungsprotokoll', datum: '2026-05-10', naechsteWartung: null, status: 'OFFEN',
    kundeId: 1, kundeName: 'Max Mustermann GmbH', objektId: 2, objektBezeichnung: 'Werk Nord', tuerId: 3, tuerBezeichnung: `Tür ${id}`,
    pruefpunkteGesamt: 10, pruefpunkteBewertet: 0, maengel: 0, ...extra,
});
const antwort = (status: number, body: unknown) => ({ ok: status < 300, status, json: async () => body }) as Response;
let fetcher: ReturnType<typeof vi.fn>;
let ergebnis: { geaendert: number; uebersprungen: { id: number | null; nummer: string | null; grund: string }[] };

beforeEach(() => {
    ergebnis = { geaendert: 2, uebersprungen: [] };
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url.startsWith('/api/wartung/protokolle?')) return antwort(200, { content: [eintrag(1), eintrag(2), eintrag(3)], totalElements: 3, totalPages: 1, number: 0, size: 20 });
        if (url === '/api/wartung/protokolle/protokollart' && init?.method === 'POST') return antwort(200, ergebnis);
        if (url === '/api/wartung/module') return antwort(200, []);
        return antwort(404, {});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><ConfirmProvider><MemoryRouter initialEntries={['/wartung']}><Routes><Route path="/wartung" element={<WartungUebersicht />} /></Routes></MemoryRouter></ConfirmProvider></ToastProvider>);
const aufrufe = () => fetcher.mock.calls.filter(([u, i]) => u === '/api/wartung/protokolle/protokollart' && (i as RequestInit)?.method === 'POST');

describe('Wartungen: Protokollart mehrerer auf einmal ändern', { timeout: 30000 }, () => {
    it('ohne Auswahl gibt es keinen Änderungsknopf; mit Auswahl erscheinen Anzahl, Protokollart und Knopf', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByLabelText('Wartung WA-2026-1001 auswählen');
        expect(screen.queryByText(/ausgewählt/)).not.toBeInTheDocument();

        await user.click(screen.getByLabelText('Wartung WA-2026-1001 auswählen'));
        await user.click(screen.getByLabelText('Wartung WA-2026-1003 auswählen'));

        expect(screen.getByText('2 ausgewählt')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Protokollart ändern' })).toBeDisabled();
    });

    it('„Alle auf dieser Seite“ wählt und hebt auf; „Auswahl aufheben“ leert', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByLabelText('Wartung WA-2026-1001 auswählen');

        await user.click(screen.getByLabelText('Alle Wartungen dieser Seite auswählen'));
        expect(screen.getByText('3 ausgewählt')).toBeInTheDocument();
        await user.click(screen.getByLabelText('Alle Wartungen dieser Seite auswählen'));
        expect(screen.queryByText(/ausgewählt/)).not.toBeInTheDocument();
        await user.click(screen.getByLabelText('Wartung WA-2026-1002 auswählen'));
        await user.click(screen.getByRole('button', { name: 'Auswahl aufheben' }));
        expect(screen.queryByText(/ausgewählt/)).not.toBeInTheDocument();
    });

    it('ändert die gewählten: schickt Nummern und Protokollart, meldet die Zahl, lädt neu und leert die Auswahl', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByLabelText('Wartung WA-2026-1001 auswählen'));
        await user.click(screen.getByLabelText('Wartung WA-2026-1002 auswählen'));
        const geladenVorher = fetcher.mock.calls.filter(([u]) => String(u).startsWith('/api/wartung/protokolle?')).length;

        await user.click(screen.getByRole('combobox', { name: 'Neue Protokollart' }));
        await user.click(await screen.findByRole('option', { name: 'Instandhaltungsprotokoll' }));
        await user.click(screen.getByRole('button', { name: 'Protokollart ändern' }));

        await waitFor(() => expect(aufrufe()).toHaveLength(1));
        expect(JSON.parse((aufrufe()[0][1] as RequestInit).body as string)).toEqual({ ids: [1, 2], protokollTyp: 'Instandhaltungsprotokoll' });
        expect(await screen.findByText('2 Wartungen sind jetzt als „Instandhaltungsprotokoll“ eingetragen.')).toBeInTheDocument();
        await waitFor(() => expect(fetcher.mock.calls.filter(([u]) => String(u).startsWith('/api/wartung/protokolle?')).length).toBeGreaterThan(geladenVorher));
        expect(screen.queryByText(/ausgewählt/)).not.toBeInTheDocument();
    });

    it('nicht geänderte (zum Beispiel abgeschlossene) werden mit Nummer und Grund genannt', async () => {
        ergebnis = { geaendert: 1, uebersprungen: [{ id: 3, nummer: 'WA-2026-1003', grund: 'Ist „abgeschlossen“ und lässt sich nicht ändern. Bitte zuerst wieder öffnen.' }] };
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByLabelText('Wartung WA-2026-1001 auswählen'));
        await user.click(screen.getByLabelText('Wartung WA-2026-1003 auswählen'));

        await user.click(screen.getByRole('combobox', { name: 'Neue Protokollart' }));
        await user.click(await screen.findByRole('option', { name: 'Prüfprotokoll' }));
        await user.click(screen.getByRole('button', { name: 'Protokollart ändern' }));

        expect(await screen.findByText('1 Wartung ist jetzt als „Prüfprotokoll“ eingetragen.')).toBeInTheDocument();
        expect(await screen.findByText(/Nicht geändert: WA-2026-1003 \(Ist „abgeschlossen“ und lässt sich nicht ändern/)).toBeInTheDocument();
    });

    it('Serverfehler: Meldung, die Auswahl bleibt erhalten', async () => {
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
            if (url.startsWith('/api/wartung/protokolle?')) return antwort(200, { content: [eintrag(1)], totalElements: 1, totalPages: 1, number: 0, size: 20 });
            if (init?.method === 'POST') return antwort(400, { message: 'Diese Protokollart gibt es nicht.' });
            return antwort(200, []);
        });
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByLabelText('Wartung WA-2026-1001 auswählen'));
        await user.click(screen.getByRole('combobox', { name: 'Neue Protokollart' }));
        await user.click(await screen.findByRole('option', { name: 'Prüfprotokoll' }));
        await user.click(screen.getByRole('button', { name: 'Protokollart ändern' }));

        expect(await screen.findByText('Diese Protokollart gibt es nicht.')).toBeInTheDocument();
        expect(within(screen.getByTestId('sammelaenderung')).getByText('1 ausgewählt')).toBeInTheDocument();
    });
});
