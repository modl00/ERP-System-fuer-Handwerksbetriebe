import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import ArbeitsgangEditor from './ArbeitsgangEditor';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';

vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: { children: ReactNode; actions?: ReactNode }) => <div>{actions}{children}</div> }));

/** Arbeitsgang ausblenden statt löschen (#235). Nur Dummy-Daten. */
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten, text: async () => JSON.stringify(daten) });
const gang = (id: number, name: string, archiviert = false) => ({ id, beschreibung: name, abteilungId: 1, abteilungName: 'Werkstatt', stundensatz: 60, stundensatzJahr: 2026, archiviert });

let fetcher: ReturnType<typeof vi.fn>;
let gaenge: ReturnType<typeof gang>[];
let loeschenAntwort: () => ReturnType<typeof antwort>;
const aufrufe = (methode: string, teil: string) => fetcher.mock.calls.filter(([u, i]) => String(u).includes(teil) && (i?.method ?? 'GET') === methode);

beforeEach(() => {
    gaenge = [gang(10, 'Sägen'), gang(11, 'Bohren', true)];
    loeschenAntwort = () => antwort({}, true, 204);
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/abteilungen') return antwort([{ id: 1, name: 'Werkstatt' }]);
        if (url === '/api/arbeitsgaenge') return antwort(gaenge);
        if (url.endsWith('/archiviert') && init?.method === 'PUT') {
            const id = Number(url.split('/').at(-2));
            const neu = JSON.parse(String(init.body)).archiviert as boolean;
            gaenge = gaenge.map(g => (g.id === id ? { ...g, archiviert: neu } : g));
            return antwort(gaenge.find(g => g.id === id));
        }
        if (url.startsWith('/api/arbeitsgaenge/') && init?.method === 'DELETE') return loeschenAntwort();
        return antwort([]);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(
    <MemoryRouter initialEntries={['/arbeitsgaenge']}>
        <ToastProvider><ConfirmProvider>
            <Routes><Route path="/arbeitsgaenge" element={<ArbeitsgangEditor />} /></Routes>
        </ConfirmProvider></ToastProvider>
    </MemoryRouter>,
);

describe('Arbeitsgang ausblenden statt löschen', { timeout: 30000 }, () => {
    it('zeigt ausgeblendete Arbeitsgänge nicht, bietet sie aber auf Wunsch mit Kennzeichen an', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Werkstatt'));

        expect(await screen.findByText('Sägen')).toBeVisible();
        expect(screen.queryByText('Bohren')).toBeNull();

        await user.click(screen.getByRole('checkbox', { name: /Ausgeblendete zeigen \(1\)/ }));
        expect(await screen.findByText('Bohren')).toBeVisible();
        expect(screen.getByTestId('ausgeblendet-11')).toHaveTextContent('ausgeblendet');
    });

    it('blendet einen Arbeitsgang mit einem Klick aus und sagt, dass nichts gelöscht wird', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Werkstatt'));
        await user.click(await screen.findByRole('button', { name: 'Sägen ausblenden' }));

        await waitFor(() => expect(aufrufe('PUT', '/api/arbeitsgaenge/10/archiviert')).toHaveLength(1));
        expect(JSON.parse(aufrufe('PUT', '/archiviert')[0][1].body as string)).toEqual({ archiviert: true });
        expect(await screen.findByText(/ausgeblendet\. Er steht nicht mehr in der Auswahl, alles Bestehende bleibt/)).toBeVisible();
        await waitFor(() => expect(screen.queryByText('Sägen')).toBeNull());
    });

    it('ein benutzter Arbeitsgang lässt sich nicht löschen: Der Grund steht da und das Ausblenden wird angeboten', async () => {
        const user = userEvent.setup();
        loeschenAntwort = () => antwort({ message: 'Der Arbeitsgang „Sägen“ wird noch benutzt (3 Zeitbuchungen und 2 Zeilen in Leistungen).' }, false, 409);
        zeige();
        await user.click(await screen.findByText('Werkstatt'));
        await user.click(await screen.findByRole('button', { name: 'Sägen löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));

        expect(await screen.findByText(/wird noch benutzt \(3 Zeitbuchungen und 2 Zeilen in Leistungen\)/)).toBeVisible();
        await user.click(screen.getByRole('button', { name: 'Ausblenden' }));
        await waitFor(() => expect(aufrufe('PUT', '/api/arbeitsgaenge/10/archiviert')).toHaveLength(1));
    });

    it('wer das Ausblenden ablehnt, behält den Arbeitsgang unverändert', async () => {
        const user = userEvent.setup();
        loeschenAntwort = () => antwort({ message: 'Der Arbeitsgang „Sägen“ wird noch benutzt (1 Zeitbuchung).' }, false, 409);
        zeige();
        await user.click(await screen.findByText('Werkstatt'));
        await user.click(await screen.findByRole('button', { name: 'Sägen löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        await screen.findByText(/wird noch benutzt \(1 Zeitbuchung\)/);
        await user.click(screen.getAllByRole('button', { name: 'Abbrechen' }).at(-1)!);

        expect(aufrufe('PUT', '/archiviert')).toHaveLength(0);
        expect(screen.getByText('Sägen')).toBeVisible();
    });

    it('blendet einen ausgeblendeten Arbeitsgang wieder ein', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Werkstatt'));
        await user.click(await screen.findByRole('checkbox', { name: /Ausgeblendete zeigen/ }));
        await user.click(await screen.findByRole('button', { name: 'Bohren wieder einblenden' }));

        await waitFor(() => expect(JSON.parse(aufrufe('PUT', '/api/arbeitsgaenge/11/archiviert')[0][1].body as string)).toEqual({ archiviert: false }));
        expect(await screen.findByText('Der Arbeitsgang steht wieder in der Auswahl.')).toBeVisible();
    });

    it('zeigt andere Löschfehler mit dem Grund des Servers', async () => {
        const user = userEvent.setup();
        loeschenAntwort = () => antwort({ message: 'Etwas anderes ist schiefgegangen.' }, false, 500);
        zeige();
        await user.click(await screen.findByText('Werkstatt'));
        await user.click(await screen.findByRole('button', { name: 'Sägen löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));

        expect(await screen.findByText('Etwas anderes ist schiefgegangen.')).toBeVisible();
    });
});
