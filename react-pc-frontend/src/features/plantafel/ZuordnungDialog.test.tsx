import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ZuordnungDialog } from './ZuordnungDialog';
import type { ArbeitsgangZuordnung, Vorgangstyp } from './plantafelApi';

/** Nur Dummy-Daten. */
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
const typen: Vorgangstyp[] = [
    { id: 30, name: 'Fertigung', farbe: '#be123c', sortierung: 30, standardDauerTage: 5, aktiv: true, abteilungId: 2, stundenProTag: null, wartet: false, buero: false },
    { id: 50, name: 'Montage', farbe: '#059669', sortierung: 50, standardDauerTage: 2, aktiv: true, abteilungId: 2, stundenProTag: null, wartet: false, buero: false },
    { id: 20, name: 'Material', farbe: '#d97706', sortierung: 20, standardDauerTage: 3, aktiv: true, abteilungId: null, stundenProTag: null, wartet: true, buero: false },
];
let liste: ArbeitsgangZuordnung[];
let gespeichert: { arbeitsgangId: number; typId: number | null } | null;
let fetcher: ReturnType<typeof vi.fn>;

beforeEach(() => {
    gespeichert = null;
    liste = [
        { arbeitsgangId: 5, arbeitsgang: 'Schweißen', abteilungId: 2, abteilung: 'Werkstatt', typId: null, mehrdeutig: true },
        { arbeitsgangId: 6, arbeitsgang: 'Montage Baustelle', abteilungId: 2, abteilung: 'Werkstatt', typId: null, mehrdeutig: true },
        { arbeitsgangId: 7, arbeitsgang: 'Büroarbeit', abteilungId: 3, abteilung: 'Büro', typId: null, mehrdeutig: false },
    ];
    fetcher = vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === 'PUT') {
            gespeichert = JSON.parse(String(init.body));
            liste = liste.map(z => (z.arbeitsgangId === gespeichert?.arbeitsgangId ? { ...z, typId: gespeichert.typId } : z));
        }
        return json(liste);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><ZuordnungDialog offen typen={typen} onSchliessen={() => undefined} /></ToastProvider>);

describe('ZuordnungDialog', { timeout: 30000 }, () => {
    it('listet die Arbeitsgänge und warnt bei geteilten Abteilungen', async () => {
        zeige();

        expect(await screen.findByTestId('zuordnung-5')).toHaveTextContent('Schweißen');
        expect(screen.getByTestId('zuordnung-5')).toHaveTextContent('Werkstatt (geteilt)');
        expect(screen.getByTestId('mehrdeutig')).toHaveTextContent('Bei 2 Arbeitsgängen');
    });

    it('bietet nur Arbeitsvorgangsarten an, keinen Wartevorgang', async () => {
        zeige();
        const wahl = await screen.findByLabelText('Vorgangsart für Schweißen');
        const optionen = Array.from((wahl as HTMLSelectElement).options).map(o => o.text);

        expect(optionen).toEqual(['automatisch (über die Abteilung)', 'Fertigung', 'Montage']);
    });

    it('speichert die gewählte Vorgangsart und aktualisiert die Liste', async () => {
        const user = userEvent.setup();
        zeige();
        await user.selectOptions(await screen.findByLabelText('Vorgangsart für Montage Baustelle'), 'Montage');

        await waitFor(() => expect(gespeichert).toEqual({ arbeitsgangId: 6, typId: 50 }));
        await waitFor(() => expect(screen.getByLabelText('Vorgangsart für Montage Baustelle')).toHaveValue('50'));
        await user.selectOptions(screen.getByLabelText('Vorgangsart für Montage Baustelle'), 'automatisch (über die Abteilung)');
        await waitFor(() => expect(gespeichert).toEqual({ arbeitsgangId: 6, typId: null }));
    });

    it('meldet einen Ladefehler im Klartext', async () => {
        fetcher.mockImplementation(async () => json({ message: 'Datenbank nicht erreichbar.' }, 500));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('Datenbank nicht erreichbar.');
    });
});
