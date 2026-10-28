import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AktivitaetenImSystem from './AktivitaetenImSystem';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import type { Einstellungen, Ereignis } from '../features/aktivitaet/aktivitaetApi';

/** Nur Dummy-Daten. */
const EREIGNISSE: Ereignis[] = [
    { id: 2, zeitpunkt: '2026-10-03T10:30:00', art: 'VERSENDET', text: 'Dokument per E-Mail versendet', objektTyp: 'Dokument', objektId: 50, objektName: null, benutzer: 'Max Mustermann' },
    { id: 1, zeitpunkt: '2026-10-03T08:15:00', art: 'GEAENDERT', text: 'Projekt geändert', objektTyp: 'Projekt', objektId: 7, objektName: '2026/014 Anbau Halle', benutzer: 'Erika Musterfrau' },
];
const antwort = (daten: unknown, status = 200) => ({ ok: status < 300, status, json: async () => daten });
let fetcher: ReturnType<typeof vi.fn>;
let einstellungen: Einstellungen;
const aufrufe = (methode: string, teil: string) => fetcher.mock.calls.filter(([u, i]) => String(u).includes(teil) && ((i as RequestInit | undefined)?.method ?? 'GET') === methode);

beforeEach(() => {
    einstellungen = { mitarbeiterAktiv: false, aufbewahrungTage: 365, ereignisseGesamt: 12 };
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const m = init?.method ?? 'GET';
        if (url.startsWith('/api/frontend-users')) return antwort([{ id: 2, displayName: 'Erika Musterfrau', username: 'erika' }, { id: 1, displayName: 'Max Mustermann', username: 'max' }]);
        if (url === '/api/admin/aktivitaet/einstellungen' && m === 'PUT') { einstellungen = { ...einstellungen, ...JSON.parse(init?.body as string) }; return antwort(einstellungen); }
        if (url === '/api/admin/aktivitaet/einstellungen') return antwort(einstellungen);
        if (url.startsWith('/api/admin/aktivitaet/person/') && m === 'DELETE') return antwort({ geloescht: 3 });
        if (url.startsWith('/api/admin/aktivitaet/person/')) return antwort([EREIGNISSE[1]]);
        if (url.startsWith('/api/admin/aktivitaet')) return antwort(EREIGNISSE);
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><ConfirmProvider><AktivitaetenImSystem /></ConfirmProvider></ToastProvider>);

describe('Aktivitäten im System', { timeout: 30000 }, () => {
    it('zeigt wer wann was gemacht hat; Filter gehen als Parameter an den Server', async () => {
        const user = userEvent.setup();
        zeige();

        const zeile = await screen.findByRole('row', { name: /Erika Musterfrau/ });
        expect(zeile).toHaveTextContent('Projekt geändert: 2026/014 Anbau Halle');
        expect(screen.getByRole('row', { name: /Max Mustermann/ })).toHaveTextContent('Dokument per E-Mail versendet (Dokument 50)');
        expect(fetcher.mock.calls.some(([u]) => u === '/api/admin/aktivitaet?tage=30')).toBe(true);

        await user.selectOptions(screen.getByLabelText('Objektart'), 'Projekt');
        await user.selectOptions(screen.getByLabelText('Art'), 'GEAENDERT');
        await user.selectOptions(screen.getByLabelText('Zeitraum'), '365');
        await waitFor(() => expect(fetcher.mock.calls.some(([u]) => u === '/api/admin/aktivitaet?objektTyp=Projekt&art=GEAENDERT&tage=365')).toBe(true));
    });

    it('Einstellungen: Vorgabe 1 Jahr, 12 Ereignisse; Aufbewahrung 90 Tage speichern', async () => {
        const user = userEvent.setup();
        zeige();
        expect(await screen.findByText(/Jetzt: 365 Tage, 12 Ereignisse gespeichert/)).toBeInTheDocument();

        await user.selectOptions(screen.getByLabelText('Aufbewahrung'), '90');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/einstellungen')).toHaveLength(1));
        expect(JSON.parse((aufrufe('PUT', '/einstellungen')[0][1] as RequestInit).body as string)).toEqual({ aufbewahrungTage: 90 });
        expect(await screen.findByText(/Jetzt: 90 Tage/)).toBeInTheDocument();
    });

    it('eigene Zahl von Tagen wird geprüft; „unbegrenzt" verlangt eine Bestätigung mit Datenschutz-Hinweis', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByText(/Jetzt: 365 Tage/);

        await user.selectOptions(screen.getByLabelText('Aufbewahrung'), 'eigene');
        await user.type(screen.getByLabelText('Tage'), 'abc');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText(/ganze Zahl von Tagen/)).toBeInTheDocument();
        expect(aufrufe('PUT', '/einstellungen')).toHaveLength(0);

        await user.selectOptions(screen.getByLabelText('Aufbewahrung'), '0');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText(/nur so lange gespeichert werden, wie sie gebraucht werden/)).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Unbegrenzt aufbewahren' }));
        await waitFor(() => expect(aufrufe('PUT', '/einstellungen')).toHaveLength(1));
        expect(JSON.parse((aufrufe('PUT', '/einstellungen')[0][1] as RequestInit).body as string)).toEqual({ aufbewahrungTage: 0 });
    });

    it('Tagesverlauf für Mitarbeiter einschalten: erst nach Bestätigung mit Hinweis an die Mitarbeiter, danach steht der Hinweistext da', async () => {
        const user = userEvent.setup();
        zeige();
        const schalter = await screen.findByRole('checkbox', { name: /Tagesverlauf auch für Mitarbeiter/ });
        expect(schalter).not.toBeChecked();

        await user.click(schalter);
        expect(await screen.findByText(/Bitte teilen Sie das Ihren Mitarbeitern vorher mit/)).toBeInTheDocument();
        expect(aufrufe('PUT', '/einstellungen')).toHaveLength(0);
        await user.click(screen.getByRole('button', { name: 'Einschalten' }));

        await waitFor(() => expect(aufrufe('PUT', '/einstellungen')).toHaveLength(1));
        expect(JSON.parse((aufrufe('PUT', '/einstellungen')[0][1] as RequestInit).body as string)).toEqual({ mitarbeiterAktiv: true });
        expect(await screen.findByText(/nur der jeweilige Mitarbeiter sieht seinen eigenen Verlauf/)).toBeInTheDocument();
    });

    it('Ausschalten braucht keine Rückfrage', async () => {
        einstellungen = { ...einstellungen, mitarbeiterAktiv: true };
        const user = userEvent.setup();
        zeige();

        await user.click(await screen.findByRole('checkbox', { name: /Tagesverlauf auch für Mitarbeiter/ }));

        await waitFor(() => expect(aufrufe('PUT', '/einstellungen')).toHaveLength(1));
        expect(JSON.parse((aufrufe('PUT', '/einstellungen')[0][1] as RequestInit).body as string)).toEqual({ mitarbeiterAktiv: false });
    });

    it('Auskunft und Löschung einer Person: erst wählen, Löschen fragt nach und meldet die Zahl', async () => {
        const user = userEvent.setup();
        vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn() }));
        zeige();
        await screen.findByRole('row', { name: /Erika Musterfrau/ });
        expect(screen.getByRole('button', { name: /Verlauf löschen/ })).toBeDisabled();
        await waitFor(() => expect(within(screen.getByLabelText('Person')).getAllByRole('option').length).toBeGreaterThan(1));

        await user.selectOptions(screen.getByLabelText('Person'), '2');
        await user.click(screen.getByRole('button', { name: /Auskunft herunterladen/ }));
        await waitFor(() => expect(aufrufe('GET', '/person/2')).toHaveLength(1));

        await user.click(screen.getByRole('button', { name: /Verlauf löschen/ }));
        expect(await screen.findByText(/Alle gespeicherten Ereignisse von Erika Musterfrau werden gelöscht/)).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(aufrufe('DELETE', '/person/2')).toHaveLength(1));
    });

    it('Fehler (kein Administrator) wird gemeldet; leere Auswahl sagt es', async () => {
        fetcher.mockImplementation(async (url: string) => (url.startsWith('/api/frontend-users') ? antwort([]) : antwort({ message: 'Zugriff verweigert.' }, 403)));
        zeige();

        expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent(/Zugriff verweigert|nur für Administratoren/);
    });

    it('Auswahl ohne Treffer: freundlicher Satz', async () => {
        fetcher.mockImplementation(async (url: string) => (url.startsWith('/api/admin/aktivitaet/einstellungen') ? antwort(einstellungen) : url.startsWith('/api/frontend-users') ? antwort([]) : antwort([])));
        zeige();

        expect(await screen.findByText('Für diese Auswahl gibt es keine Einträge.')).toBeInTheDocument();
    });
});
