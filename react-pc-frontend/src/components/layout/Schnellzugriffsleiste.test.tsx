import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Folder, Users } from 'lucide-react';
import { Schnellzugriffsleiste } from './Schnellzugriffsleiste';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import { setzeLeisteZurueck } from '../../features/schnellzugriff/useSchnellzugriff';
import type { FunktionsKategorie } from './AlleFunktionenDialog';

/** Nur Dummy-Daten. */
const navigation: FunktionsKategorie[] = [
    { category: 'Verkauf', subgroups: [{ label: 'Aufträge', items: [{ name: 'Projekte', href: '/projekte', icon: Folder }, { name: 'Kunden', href: '/kunden', icon: Users }] }] },
];

const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
let stand: { vorgabe: string[] | null; eigene: string[] | null };
let fetcher: ReturnType<typeof vi.fn>;
const alle = vi.fn();

function zeige(admin = false) {
    return render(<MemoryRouter><ToastProvider><ConfirmProvider><Schnellzugriffsleiste navigation={navigation} istAdmin={admin} onAlleFunktionen={alle} /></ConfirmProvider></ToastProvider></MemoryRouter>);
}

beforeEach(() => {
    setzeLeisteZurueck();
    alle.mockClear();
    stand = { vorgabe: null, eigene: null };
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const u = String(url);
        if (u === '/api/schnellzugriff/eigene' && init?.method === 'PUT') { stand = { ...stand, eigene: JSON.parse(init.body as string).eintraege }; return json(stand); }
        if (u === '/api/settings/schnellzugriff/vorgabe' && init?.method === 'PUT') { stand = { ...stand, vorgabe: JSON.parse(init.body as string).eintraege }; return json(stand); }
        if (u === '/api/schnellzugriff') return json(stand);
        return json({}, 404);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('Schnellzugriffsleiste', { timeout: 30000 }, () => {
    it('zeigt ohne Eintrag einen Hinweis, mit Vorgabe der Firma deren Funktionen', async () => {
        stand = { vorgabe: ['/projekte'], eigene: null };
        zeige();
        expect(await screen.findByRole('link', { name: 'Projekte' })).toHaveAttribute('href', '/projekte');
        expect(screen.queryByRole('link', { name: 'Kunden' })).toBeNull();
    });

    it('die eigene Leiste hat Vorrang; „Alle Funktionen“ öffnet den Dialog und unbekannte Adressen fehlen', async () => {
        stand = { vorgabe: ['/projekte'], eigene: ['/kunden', '/alle-funktionen', '/gibt-es-nicht'] }; // ein alter Eintrag „Alle Funktionen“ wird nicht doppelt gezeigt
        const user = userEvent.setup();
        zeige();
        const leiste = await screen.findByTestId('schnellzugriff');
        await waitFor(() => expect(within(leiste).getByRole('link', { name: 'Kunden' })).toBeInTheDocument());
        expect(within(leiste).queryByRole('link', { name: 'Projekte' })).toBeNull();
        await user.click(within(leiste).getByRole('button', { name: 'Alle Funktionen' }));
        expect(alle).toHaveBeenCalledTimes(1);
        expect(within(leiste).getAllByRole('button', { name: 'Alle Funktionen' })).toHaveLength(1);
    });

    it('ohne Eintrag steht ein Hinweis da', async () => {
        zeige();
        expect(await screen.findByText(/Lege hier die Funktionen ab/)).toBeInTheDocument();
    });

    it('beim Anpassen lassen sich Funktionen wählen, ordnen und speichern', async () => {
        stand = { vorgabe: null, eigene: ['/projekte'] };
        const user = userEvent.setup();
        zeige();
        await screen.findByRole('link', { name: 'Projekte' });
        await user.click(screen.getByRole('button', { name: 'Schnellzugriff anpassen' }));

        await user.click(await screen.findByRole('checkbox', { name: 'Kunden auf die Leiste legen' }));
        await user.click(screen.getByRole('button', { name: 'Kunden nach vorne' }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(stand.eigene).toEqual(['/kunden', '/projekte']));
        const leiste = screen.getByTestId('schnellzugriff');
        await waitFor(() => expect(within(leiste).getAllByRole('link').map(a => a.textContent)).toEqual(['Kunden', 'Projekte']));
    });

    it('der Admin kann die Leiste als Vorgabe für alle speichern', async () => {
        const user = userEvent.setup();
        zeige(true);
        await screen.findByTestId('schnellzugriff');
        await user.click(screen.getByRole('button', { name: 'Schnellzugriff anpassen' }));
        await user.click(await screen.findByRole('checkbox', { name: 'Projekte auf die Leiste legen' }));
        await user.click(screen.getByRole('button', { name: 'Als Vorgabe für alle speichern' }));
        await user.click(await screen.findByRole('button', { name: 'Für alle speichern' }));
        await waitFor(() => expect(stand.vorgabe).toEqual(['/projekte']));
    });
});
