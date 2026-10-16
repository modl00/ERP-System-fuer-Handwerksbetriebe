import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import ArbeitsgangEditor from './ArbeitsgangEditor';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';

vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: { children: ReactNode; actions?: ReactNode }) => <div>{actions}{children}</div> }));

/** Nur Dummy-Daten. */
const antwort = (daten: unknown) => ({ ok: true, status: 200, json: async () => daten, text: async () => JSON.stringify(daten) });

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
        if (url === '/api/abteilungen') return antwort([{ id: 1, name: 'Werkstatt' }]);
        if (url === '/api/arbeitsgaenge') return antwort([
            { id: 10, beschreibung: 'Sägen', abteilungId: 1, abteilungName: 'Werkstatt', stundensatz: 60, stundensatzJahr: 2026, maschinenStundensatz: 40, maschinenBezeichnung: 'Kappsäge', maschineId: 5 },
            { id: 11, beschreibung: 'Bohren', abteilungId: 1, abteilungName: 'Werkstatt', stundensatz: 55, stundensatzJahr: 2026, maschinenStundensatz: 22.5, maschinenBezeichnung: 'Ständerbohrmaschine', maschineId: null },
        ]);
        return antwort([]);
    }));
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(
    <MemoryRouter initialEntries={['/arbeitsgaenge']}>
        <ToastProvider><ConfirmProvider>
            <Routes>
                <Route path="/arbeitsgaenge" element={<ArbeitsgangEditor />} />
                <Route path="/kostenrechnung" element={<p>Hier ist die Kostenrechnung</p>} />
            </Routes>
        </ConfirmProvider></ToastProvider>
    </MemoryRouter>,
);

describe('ArbeitsgangEditor und die Maschinenliste', () => {
    it('kennzeichnet eine Maschine aus der Maschinenliste und führt zu ihrer Pflege in der Kostenrechnung, statt sie am Arbeitsgang zu ändern', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Werkstatt'));

        expect(await screen.findByTestId('maschine-10')).toHaveTextContent('Maschine aus der Maschinenliste: Kappsäge');
        expect(screen.getByTestId('maschine-11')).toHaveTextContent('Maschine: Ständerbohrmaschine');
        expect(screen.getByTestId('maschine-11')).not.toHaveTextContent('Maschinenliste');

        await user.click(screen.getByRole('button', { name: 'Maschine von Sägen bearbeiten' }));
        expect(await screen.findByText('Hier ist die Kostenrechnung')).toBeVisible();
    });

    it('ein Arbeitsgang ohne Maschine aus der Liste öffnet weiter das Fenster am Arbeitsgang', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Werkstatt'));
        await user.click(await screen.findByRole('button', { name: 'Maschine von Bohren bearbeiten' }));
        expect(screen.queryByText('Hier ist die Kostenrechnung')).toBeNull();
        expect(await screen.findByRole('dialog')).toBeVisible();
    });
});
