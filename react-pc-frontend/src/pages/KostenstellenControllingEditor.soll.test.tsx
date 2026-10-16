import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import KostenstellenControllingEditor from './KostenstellenControllingEditor';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';

vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: { children: ReactNode; actions?: ReactNode }) => <div>{actions}{children}</div> }));

/** Geschätzter Jahresbetrag je Kostenstelle (#237): darf beim Bearbeiten nicht verloren gehen. Nur Dummy-Daten. */
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten, text: async () => JSON.stringify(daten) });
const kostenstelle = (aenderung: Record<string, unknown> = {}) => ({
    id: 5, bezeichnung: 'Miete', typ: 'GEMEINKOSTEN', beschreibung: 'Werkstatt', istFixkosten: true, istInvestition: false,
    aktiv: true, sortierung: 3, jahresSoll: 6000, summeDiesesJahr: 500, summeVorjahr: 0, anzahlDiesesJahr: 1, ...aenderung,
});

let fetcher: ReturnType<typeof vi.fn>;
let speicherAntwort: () => ReturnType<typeof antwort>;
const speichern = () => fetcher.mock.calls.filter(([u, i]) => String(u).startsWith('/api/firma/kostenstellen') && i?.method === 'PUT');

beforeEach(() => {
    speicherAntwort = () => antwort({});
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url).startsWith('/api/bestellungen-uebersicht/kostenstellen/auswertung')) return antwort([kostenstelle()]);
        if (init?.method === 'PUT') return speicherAntwort();
        return antwort([]);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(
    <MemoryRouter initialEntries={['/kostenstellen']}>
        <ToastProvider><ConfirmProvider>
            <Routes><Route path="/kostenstellen" element={<KostenstellenControllingEditor />} /></Routes>
        </ConfirmProvider></ToastProvider>
    </MemoryRouter>,
);

describe('Kostenstellen bearbeiten mit geschätztem Jahresbetrag', { timeout: 30000 }, () => {
    it('zeigt den Jahresbetrag im Formular und schickt ihn mit Aktiv und Reihenfolge unverändert zurück', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Kostenstelle bearbeiten' }));

        expect((await screen.findByLabelText('Geschätzt im Jahr (€)') as HTMLInputElement).value).toBe('6000');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));

        await waitFor(() => expect(speichern()).toHaveLength(1));
        const body = JSON.parse(speichern()[0][1].body);
        expect(body).toMatchObject({ id: 5, bezeichnung: 'Miete', aktiv: true, sortierung: 3, jahresSoll: 6000, istFixkosten: true });
        expect(body).not.toHaveProperty('jahresSollText');
    });

    it('speichert einen geänderten und einen geleerten Jahresbetrag', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Kostenstelle bearbeiten' }));
        const feld = await screen.findByLabelText('Geschätzt im Jahr (€)');

        await user.clear(feld);
        await user.type(feld, '7200,50');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        await waitFor(() => expect(speichern()).toHaveLength(1));
        expect(JSON.parse(speichern()[0][1].body).jahresSoll).toBe(7200.5);

        await user.click(await screen.findByRole('button', { name: 'Kostenstelle bearbeiten' }));
        await user.clear(await screen.findByLabelText('Geschätzt im Jahr (€)'));
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        await waitFor(() => expect(speichern()).toHaveLength(2));
        expect(JSON.parse(speichern()[1][1].body).jahresSoll).toBeNull();
    });

    it('lässt einen ungültigen Betrag nicht zum Server und zeigt die Meldung des Servers bei Ablehnung', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Kostenstelle bearbeiten' }));
        const feld = await screen.findByLabelText('Geschätzt im Jahr (€)');

        await user.clear(feld);
        await user.type(feld, 'viel');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        expect(speichern()).toHaveLength(0);

        await user.clear(feld);
        await user.type(feld, '5');
        speicherAntwort = () => antwort({ message: 'Der geschätzte Jahresbetrag ist zu hoch.' }, false, 400);
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        expect(await screen.findByText('Der geschätzte Jahresbetrag ist zu hoch.')).toBeInTheDocument();
    });

    it('andere Typen als Gemeinkosten haben kein Feld und schicken keinen Betrag', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
            if (String(url).startsWith('/api/bestellungen-uebersicht/kostenstellen/auswertung')) {
                return antwort([kostenstelle({ typ: 'LAGER', istFixkosten: false, istInvestition: true, jahresSoll: null })]);
            }
            if (init?.method === 'PUT') return antwort({});
            return antwort([]);
        });
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Kostenstelle bearbeiten' }));

        expect(screen.queryByLabelText('Geschätzt im Jahr (€)')).toBeNull();
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        await waitFor(() => expect(speichern()).toHaveLength(1));
        expect(JSON.parse(speichern()[0][1].body).jahresSoll).toBeNull();
    });
});
