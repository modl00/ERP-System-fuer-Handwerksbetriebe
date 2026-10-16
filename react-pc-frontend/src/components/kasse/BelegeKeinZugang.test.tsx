import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../ui/toast';
import { BelegeKeinZugang } from './BelegeKeinZugang';

const antwort = (daten: unknown, ok = true) =>
    Promise.resolve({ ok, status: ok ? 200 : 403, json: () => Promise.resolve(daten) });

function zeige(onFreigeschaltet = vi.fn()) {
    render(
        <MemoryRouter>
            <ToastProvider>
                <BelegeKeinZugang onFreigeschaltet={onFreigeschaltet} />
            </ToastProvider>
        </MemoryRouter>,
    );
    return onFreigeschaltet;
}

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('BelegeKeinZugang', () => {
    it('Administrator ohne Mitarbeiter: erklärt den Grund und bietet das Anlegen an', async () => {
        vi.stubGlobal('fetch', vi.fn(() => antwort({ darfSehen: false, darfScannen: false, grund: 'KEIN_MITARBEITER', kannMitarbeiterAnlegen: true })));
        zeige();

        expect(await screen.findByText(/keinem Mitarbeiter zugeordnet/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mich als Mitarbeiter anlegen' })).toBeInTheDocument();
    });

    it('normaler Benutzer ohne Mitarbeiter: verweist auf die Seite Benutzer, ohne Knopf', async () => {
        vi.stubGlobal('fetch', vi.fn(() => antwort({ darfSehen: false, darfScannen: false, grund: 'KEIN_MITARBEITER', kannMitarbeiterAnlegen: false })));
        zeige();

        expect(await screen.findByRole('link', { name: 'Benutzer' })).toHaveAttribute('href', '/benutzer');
        expect(screen.queryByRole('button', { name: 'Mich als Mitarbeiter anlegen' })).not.toBeInTheDocument();
    });

    it('ohne Abteilung: verweist auf die Mitarbeiter', async () => {
        vi.stubGlobal('fetch', vi.fn(() => antwort({ darfSehen: false, darfScannen: false, grund: 'KEINE_ABTEILUNG' })));
        zeige();

        expect(await screen.findByText(/keiner Abteilung/)).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Mitarbeiter' })).toHaveAttribute('href', '/mitarbeiter');
    });

    it('ohne Recht in den Abteilungen: verweist auf die Rechte der Abteilungen mit dem lesbaren Namen der Zeile', async () => {
        vi.stubGlobal('fetch', vi.fn(() => antwort({ darfSehen: false, darfScannen: false, grund: 'KEIN_RECHT' })));
        zeige();

        expect(await screen.findByText(/Keine Ihrer Abteilungen darf Belege sehen/)).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Rechte der Abteilungen' })).toHaveAttribute('href', '/abteilung-berechtigungen');
        expect(screen.getByText(/Belege & Kasse/)).toBeInTheDocument();
    });

    it('ohne Antwort des Servers: allgemeine Erklärung statt leerer Seite', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('offline'))));
        zeige();

        expect(await screen.findByText(/braucht einen Mitarbeiter mit einer Abteilung/)).toBeInTheDocument();
    });

    it('Anlegen schaltet frei und meldet es der Seite', async () => {
        const fetchMock = vi.fn((url: string, init?: RequestInit) => {
            if (init?.method === 'POST') return antwort({ darfSehen: true, darfScannen: true });
            return antwort({ darfSehen: false, darfScannen: false, grund: 'KEIN_MITARBEITER', kannMitarbeiterAnlegen: true });
        });
        vi.stubGlobal('fetch', fetchMock);
        const frei = zeige();

        await userEvent.click(await screen.findByRole('button', { name: 'Mich als Mitarbeiter anlegen' }));

        await waitFor(() => expect(frei).toHaveBeenCalledTimes(1));
        expect(fetchMock).toHaveBeenCalledWith('/api/buchhaltung/me/mitarbeiter-anlegen', { method: 'POST' });
    });

    it('Ablehnung durch den Server zeigt die Meldung und bleibt auf der Seite', async () => {
        vi.stubGlobal('fetch', vi.fn((_url: string, init?: RequestInit) => init?.method === 'POST'
            ? antwort({ message: 'Nur Administratoren können sich selbst als Mitarbeiter anlegen.' }, false)
            : antwort({ darfSehen: false, darfScannen: false, grund: 'KEIN_MITARBEITER', kannMitarbeiterAnlegen: true })));
        const frei = zeige();

        await userEvent.click(await screen.findByRole('button', { name: 'Mich als Mitarbeiter anlegen' }));

        expect(await screen.findByText('Nur Administratoren können sich selbst als Mitarbeiter anlegen.')).toBeInTheDocument();
        expect(frei).not.toHaveBeenCalled();
    });
});
