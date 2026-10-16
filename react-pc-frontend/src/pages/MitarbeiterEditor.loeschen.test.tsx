import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import MitarbeiterEditor from './MitarbeiterEditor';

const auth = vi.hoisted(() => ({ isAdmin: true, user: null as null | { mitarbeiter?: { id: number } } }));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => auth }));

/** Mitarbeiter löschen: Der Grund des Servers erscheint in der Meldung (#241). Nur Dummy-Daten. */
const MITARBEITER = {
    id: 42, vorname: 'Max', nachname: 'Mustermann', strasse: null, plz: null, ort: null,
    email: 'max.mustermann@example.invalid', telefon: null, festnetz: null, qualifikation: null,
    stundenlohn: null, geburtstag: null, eintrittsdatum: '2026-01-01', aktiv: true,
    fuehrtZeitkonto: true, abteilungIds: [], abteilungNames: null, loginToken: null, jahresUrlaub: 30,
};

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let loeschenAntwort: () => ReturnType<typeof antwort>;
const loeschAufrufe = () => fetcher.mock.calls.filter(([u, i]) => String(u).endsWith('/api/mitarbeiter/42') && i?.method === 'DELETE');

beforeEach(() => {
    loeschenAntwort = () => antwort({}, true, 204);
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/mitarbeiter/42' && init?.method === 'DELETE') return loeschenAntwort();
        if (url === '/api/mitarbeiter') return antwort([MITARBEITER]);
        if (url === '/api/abteilungen') return antwort([]);
        if (url.includes('/dokumente') || url.includes('/notizen') || url.includes('/lohnabrechnungen')) return antwort([]);
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

async function zumLoeschenKnopf(user: ReturnType<typeof userEvent.setup>) {
    render(<MemoryRouter><ConfirmProvider><ToastProvider><MitarbeiterEditor /></ToastProvider></ConfirmProvider></MemoryRouter>);
    await user.click(await screen.findByText('Mustermann', { exact: true }));
    await user.click(await screen.findByRole('button', { name: 'Mitarbeiter löschen' }));
    await user.click(await screen.findByRole('button', { name: 'Löschen' }));
}

describe('Mitarbeiter löschen', { timeout: 30000 }, () => {
    it('nennt den Grund des Servers, zum Beispiel einen zugeordneten Benutzer', async () => {
        const user = userEvent.setup();
        loeschenAntwort = () => antwort({ message: 'Dieser Mitarbeiter gehört zum Benutzer „Max Mustermann“. Bitte zuerst in der Benutzerverwaltung die Zuordnung lösen.' }, false, 409);
        await zumLoeschenKnopf(user);

        expect(await screen.findByText(/gehört zum Benutzer „Max Mustermann“/)).toBeVisible();
        expect(loeschAufrufe()).toHaveLength(1);
    });

    it('fällt ohne Meldung des Servers auf einen klaren Standardtext zurück', async () => {
        const user = userEvent.setup();
        loeschenAntwort = () => ({ ok: false, status: 404, text: async () => '', json: async () => ({}) });
        await zumLoeschenKnopf(user);

        expect(await screen.findByText('Mitarbeiter konnte nicht gelöscht werden.')).toBeVisible();
    });

    it('ein Mitarbeiter ohne Hindernis wird gelöscht', async () => {
        const user = userEvent.setup();
        await zumLoeschenKnopf(user);
        await waitFor(() => expect(loeschAufrufe()).toHaveLength(1));
        expect(screen.queryByText(/konnte nicht gelöscht werden/)).toBeNull();
    });
});
