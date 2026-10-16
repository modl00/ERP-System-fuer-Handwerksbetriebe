import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../ui/toast';
import { KontenDialog } from './KontenDialog';
import type { Sachkonto } from '../../types';

const konto = (a: Partial<Sachkonto>): Sachkonto => ({
    id: 1, nummer: '4920', bezeichnung: 'Telefon', kontoTyp: 'AUFWAND', beschreibung: 'Mobilfunk und Festnetz', aktiv: true, sortierung: 10, systemkonto: false, ...a,
});
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten });

let konten: Sachkonto[];
let fetcher: ReturnType<typeof vi.fn>;
const aufrufe = (methode: string) => fetcher.mock.calls.filter(([, i]) => (i?.method ?? 'GET') === methode);

beforeEach(() => {
    konten = [
        konto({ id: 1 }),
        konto({ id: 2, nummer: '4120', bezeichnung: 'Loehne & Gehaelter', beschreibung: null, systemkonto: true }),
        konto({ id: 3, nummer: '8400', bezeichnung: 'Erloese 19%', kontoTyp: 'ERTRAG', beschreibung: 'Einnahmen mit 19% USt', systemkonto: true }),
        konto({ id: 4, nummer: null, bezeichnung: 'Privatanteil KFZ (alt)', kontoTyp: 'PRIVAT', aktiv: false, beschreibung: null }),
    ];
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === 'POST') return antwort({ id: 9, ...JSON.parse(init.body as string) });
        if (init?.method === 'PUT') return antwort({ id: 1 });
        return antwort(konten);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (onChanged = vi.fn()) => {
    render(<ToastProvider><KontenDialog onClose={vi.fn()} onChanged={onChanged} /></ToastProvider>);
    return onChanged;
};

describe('KontenDialog', () => {
    it('zeigt die Konten nach Art gruppiert mit Nummer, Name und Beschreibung, ausgeblendete erst auf Wunsch', async () => {
        zeige();

        expect(await screen.findByText('Telefon')).toBeInTheDocument();
        expect(screen.getByText('Mobilfunk und Festnetz')).toBeInTheDocument();
        expect(screen.getByText('4920')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: /Aufwand/ })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: /Ertrag/ })).toBeInTheDocument();
        expect(screen.queryByText('Privatanteil KFZ (alt)')).not.toBeInTheDocument();

        await userEvent.click(screen.getByLabelText(/Ausgeblendete zeigen \(1\)/));
        expect(screen.getByText('Privatanteil KFZ (alt)')).toBeInTheDocument();
    });

    it('die Suche findet nach Nummer, Name und Beschreibung', async () => {
        zeige();
        await screen.findByText('Telefon');

        await userEvent.type(screen.getByLabelText('Konten suchen'), 'mobilfunk');
        expect(screen.getByText('Telefon')).toBeInTheDocument();
        expect(screen.queryByText('Erloese 19%')).not.toBeInTheDocument();

        await userEvent.clear(screen.getByLabelText('Konten suchen'));
        await userEvent.type(screen.getByLabelText('Konten suchen'), 'gibtesnicht');
        expect(screen.getByText('Kein Konto passt zur Suche.')).toBeInTheDocument();
    });

    it('legt ein neues Konto an und meldet die Änderung der Seite', async () => {
        const geaendert = zeige();
        await userEvent.click(await screen.findByRole('button', { name: /Neues Konto/ }));

        await userEvent.type(screen.getByLabelText('Nummer'), '4970');
        await userEvent.type(screen.getByLabelText('Name'), 'Bankgebuehren');
        await userEvent.type(screen.getByLabelText('Beschreibung'), 'Kontofuehrung');
        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('POST')).toHaveLength(1));
        expect(JSON.parse(aufrufe('POST')[0][1].body)).toEqual({ nummer: '4970', bezeichnung: 'Bankgebuehren', kontoTyp: 'AUFWAND', beschreibung: 'Kontofuehrung' });
        await waitFor(() => expect(geaendert).toHaveBeenCalled());
        await waitFor(() => expect(screen.queryByRole('form', { name: 'Neues Konto' })).not.toBeInTheDocument());
    });

    it('ein Fehler des Servers bleibt im Formular stehen, in Klartext', async () => {
        fetcher.mockImplementation(async (_u: string, init?: RequestInit) => init?.method === 'POST'
            ? antwort({ message: 'Die Nummer 4920 ist schon vergeben (Telefon).' }, false, 400) : antwort(konten));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: /Neues Konto/ }));
        await userEvent.type(screen.getByLabelText('Nummer'), '4920');
        await userEvent.type(screen.getByLabelText('Name'), 'Handy');
        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Die Nummer 4920 ist schon vergeben (Telefon).');
        expect(screen.getByRole('form', { name: 'Neues Konto' })).toBeInTheDocument();
    });

    it('Bearbeiten füllt das Formular vor; Systemkonten haben gesperrte Nummer und Art, Name und Beschreibung bleiben änderbar', async () => {
        zeige();
        await screen.findByText('Telefon');

        await userEvent.click(screen.getByRole('button', { name: 'Loehne & Gehaelter bearbeiten' }));

        const formular = screen.getByRole('form', { name: 'Konto bearbeiten' });
        expect(within(formular).getByLabelText('Nummer')).toBeDisabled();
        expect(within(formular).getByLabelText('Nummer')).toHaveValue('4120');
        expect(within(formular).getByLabelText('Name')).not.toBeDisabled();
        expect(within(formular).getByText(/braucht das Programm für die Kasse/)).toBeInTheDocument();
    });

    it('Ausblenden schickt nur die Sichtbarkeit; bei Systemkonten ist der Knopf gesperrt', async () => {
        zeige();
        await screen.findByText('Telefon');

        expect(screen.getByRole('button', { name: 'Loehne & Gehaelter ausblenden' })).toBeDisabled();
        await userEvent.click(screen.getByRole('button', { name: 'Telefon ausblenden' }));

        await waitFor(() => expect(aufrufe('PUT')).toHaveLength(1));
        expect(aufrufe('PUT')[0][0]).toBe('/api/buchhaltung/sachkonten/1');
        expect(JSON.parse(aufrufe('PUT')[0][1].body)).toEqual({ aktiv: false });
    });

    it('ohne Konten und bei Ladefehler steht eine Erklärung statt einer leeren Liste', async () => {
        konten = [];
        zeige();
        expect(await screen.findByText(/noch keine Konten angelegt/)).toBeInTheDocument();
    });

    it('ohne Recht erscheint die Meldung des Servers beim Laden', async () => {
        fetcher.mockImplementation(async () => antwort({}, false, 403));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('Recht');
    });
});
