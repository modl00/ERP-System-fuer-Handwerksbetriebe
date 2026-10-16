import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { SmbAbgleichBereich } from './SmbAbgleichBereich';
import { ToastProvider } from '../../components/ui/toast';
import type { SmbAbgleichErgebnis, SmbEinstellungen } from './ablageApi';

/** Nur Dummy-Daten (DSGVO). */
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten });

const ergebnis = (a: Partial<SmbAbgleichErgebnis> = {}): SmbAbgleichErgebnis => ({
    probelauf: false, hochgeladen: 4, vomGeraetUebernommen: 1, neuVomGeraet: 2, konflikte: 0, unveraendert: 9, hinweise: [], fehler: [], ...a,
});

/** Ordnerbaum des Dummy-Geräts: Freigabe -> Pfad -> Unterordner */
const BAUM: Record<string, string[]> = {
    'Unternehmen|': ['test Ordner', 'Verwaltung'],
    'Unternehmen|test Ordner': ['Unternehmen'],
    'Unternehmen|test Ordner/Unternehmen': [],
};

let fetcher: ReturnType<typeof vi.fn>;
let stand: SmbEinstellungen;
let freigabenAntwort: { success: boolean; message?: string; freigaben: string[] };
let test: { success: boolean; message: string };
const aufrufe = (teil: string, methode = 'POST') => fetcher.mock.calls.filter(([u, i]) => String(u).includes(teil) && (i?.method ?? 'GET') === methode);
const bodyVon = (aufruf: unknown[]) => JSON.parse((aufruf[1] as RequestInit).body as string);

beforeEach(() => {
    stand = { aktiv: false, server: '', freigabe: '', unterordner: '', benutzer: '', domaene: '', passwortGesetzt: false, verschluesselt: true, intervallMinuten: 0, letzterLauf: '', letztesErgebnis: '' };
    freigabenAntwort = { success: true, freigaben: ['Dokumente', 'Unternehmen'] };
    test = { success: true, message: 'Verbindung steht: Anmelden, Schreiben und Lesen funktionieren.' };
    fetcher = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
        if (url === '/api/admin/ablage/smb' && !init?.method) return antwort(stand);
        if (url === '/api/admin/ablage/smb' && init?.method === 'PUT') return antwort({ message: 'Gespeichert.' });
        if (url === '/api/admin/ablage/smb/freigaben') return antwort(freigabenAntwort);
        if (url.startsWith('/api/admin/ablage/smb/ordner')) {
            const q = new URL(url, 'http://x').searchParams;
            return antwort({ success: true, ordner: BAUM[`${q.get('freigabe')}|${q.get('pfad')}`] ?? [] });
        }
        if (url === '/api/admin/ablage/smb/test') return antwort(test);
        if (url.startsWith('/api/admin/ablage/smb/abgleichen')) return antwort(ergebnis({ probelauf: url.includes('probelauf=true') }));
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});

afterEach(() => { vi.unstubAllGlobals(); });

const zeige = () => render(<ToastProvider><SmbAbgleichBereich /></ToastProvider>);

async function verbinde(user: ReturnType<typeof userEvent.setup>, benutzer = 'erika', passwort = 'Geheim-123') {
    await user.type(await screen.findByLabelText('Gerät (Name oder IP-Adresse)'), '192.168.178.21');
    if (benutzer) await user.type(screen.getByLabelText(/^Benutzer/), benutzer);
    if (passwort) await user.type(screen.getByLabelText('Passwort'), passwort);
    await user.click(screen.getByRole('button', { name: /Verbinden/ }));
}

it('erklärt den Ablauf in drei Schritten und ehrlich: beide Richtungen, nichts wird gelöscht, Ablage bleibt', async () => {
    zeige();
    expect(await screen.findByText(/Gelöscht wird auf keiner Seite etwas/)).toBeVisible();
    expect(screen.getByText(/Ihre bisherige Ablage bleibt unverändert/)).toBeVisible();
    expect(screen.getByText(/Freigabe wählen und den Ordner anklicken/)).toBeVisible();
});

it('Verbinden ist gesperrt, solange kein Gerät eingetragen ist; Benutzer und Passwort dürfen leer bleiben', async () => {
    const user = userEvent.setup();
    zeige();
    expect(await screen.findByRole('button', { name: /Verbinden/ })).toBeDisabled();
    expect(screen.getByText(/leer = Gastzugang/)).toBeVisible();
    await user.type(screen.getByLabelText('Gerät (Name oder IP-Adresse)'), '192.168.178.21');
    expect(screen.getByRole('button', { name: /Verbinden/ })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: /Verbinden/ }));
    await waitFor(() => expect(aufrufe('/smb/freigaben')).toHaveLength(1));
    expect(bodyVon(aufrufe('/smb/freigaben')[0])).toMatchObject({ server: '192.168.178.21', benutzer: '', passwort: '' });
});

it('nach dem Verbinden wählt man die Freigabe aus der Liste und klickt sich zum Ordner durch', async () => {
    const user = userEvent.setup();
    zeige();
    await verbinde(user);

    const wahl = await screen.findByLabelText('Freigabe');
    expect(within(wahl).getAllByRole('option').map(o => o.textContent)).toEqual(['Bitte wählen', 'Dokumente', 'Unternehmen']);
    await user.selectOptions(wahl, 'Unternehmen');

    await user.click(await screen.findByRole('button', { name: 'test Ordner' }));
    expect(screen.getByTestId('smb-pfad')).toHaveTextContent('Unternehmen / test Ordner');
    await user.click(await screen.findByRole('button', { name: 'Unternehmen' }));
    expect(screen.getByTestId('smb-pfad')).toHaveTextContent('Unternehmen / test Ordner / Unternehmen');
    expect(screen.getByTestId('smb-ziel')).toHaveTextContent('smb://192.168.178.21/Unternehmen/test Ordner/Unternehmen');

    await user.click(screen.getByRole('button', { name: 'Eine Ebene höher' }));
    expect(screen.getByTestId('smb-pfad')).toHaveTextContent('Unternehmen / test Ordner');
    expect(await screen.findByRole('button', { name: 'Unternehmen' })).toBeVisible();
});

it('ein neuer Ordner wird an den gewählten Pfad angehängt, gespeichert und der Zugriff danach geprüft', async () => {
    const user = userEvent.setup();
    zeige();
    await verbinde(user);
    await user.selectOptions(await screen.findByLabelText('Freigabe'), 'Unternehmen');
    await user.click(await screen.findByRole('button', { name: 'test Ordner' }));
    await user.type(screen.getByLabelText(/Neuer Ordner hier/), 'ERP');
    expect(screen.getByTestId('smb-ziel')).toHaveTextContent('smb://192.168.178.21/Unternehmen/test Ordner/ERP');
    await user.click(screen.getByLabelText('Abgleich einschalten'));
    await user.click(screen.getByRole('button', { name: /Speichern und Zugriff prüfen/ }));

    await waitFor(() => expect(aufrufe('/api/admin/ablage/smb', 'PUT')).toHaveLength(1));
    expect(bodyVon(aufrufe('/api/admin/ablage/smb', 'PUT')[0])).toMatchObject({
        aktiv: true, server: '192.168.178.21', freigabe: 'Unternehmen', unterordner: 'test Ordner/ERP', benutzer: 'erika', passwort: 'Geheim-123',
    });
    expect(await screen.findByText(/Verbindung steht/)).toBeVisible();
    expect(bodyVon(aufrufe('/smb/test')[0]).passwort).toBe('');
});

it('schlägt die Anmeldung fehl, steht der Grund da, und die Freigabe lässt sich von Hand eintragen', async () => {
    freigabenAntwort = { success: false, message: 'Benutzername oder Passwort stimmen nicht.', freigaben: [] };
    const user = userEvent.setup();
    zeige();
    await verbinde(user);

    expect(await screen.findByRole('alert')).toHaveTextContent('Benutzername oder Passwort stimmen nicht.');
    await user.type(await screen.findByLabelText('Name der Freigabe (von Hand)'), 'Unternehmen');
    await user.click(screen.getByRole('button', { name: 'Ordner anzeigen' }));
    expect(await screen.findByRole('button', { name: 'test Ordner' })).toBeVisible();
});

it('ein fehlgeschlagener Zugriffstest nach dem Speichern zeigt den Grund als Fehler', async () => {
    test = { success: false, message: 'Der Zugriff wurde verweigert. Hat der Benutzer auf der Freigabe Schreibrechte?' };
    const user = userEvent.setup();
    zeige();
    await verbinde(user);
    await user.selectOptions(await screen.findByLabelText('Freigabe'), 'Unternehmen');
    await user.click(screen.getByRole('button', { name: /Speichern und Zugriff prüfen/ }));
    expect(await screen.findByText(/Schreibrechte/)).toBeVisible();
});

it('ein gespeichertes Passwort wird nie angezeigt, bleibt bei leerem Feld und lässt sich entfernen', async () => {
    stand = { ...stand, server: '192.168.178.21', freigabe: 'Unternehmen', benutzer: 'erika', passwortGesetzt: true, aktiv: true };
    const user = userEvent.setup();
    zeige();
    const pw = await screen.findByLabelText('Passwort');
    expect(pw).toHaveValue('');
    expect(pw).toHaveAttribute('placeholder', expect.stringContaining('gespeichert'));

    await user.click(screen.getByRole('button', { name: /Speichern und Zugriff prüfen/ }));
    await waitFor(() => expect(aufrufe('/api/admin/ablage/smb', 'PUT')).toHaveLength(1));
    expect(bodyVon(aufrufe('/api/admin/ablage/smb', 'PUT')[0])).toMatchObject({ aktiv: true, passwort: '', passwortLoeschen: false });

    await user.click(screen.getByLabelText('Gespeichertes Passwort entfernen'));
    await user.click(screen.getByRole('button', { name: /Speichern und Zugriff prüfen/ }));
    await waitFor(() => expect(aufrufe('/api/admin/ablage/smb', 'PUT')).toHaveLength(2));
    expect(bodyVon(aufrufe('/api/admin/ablage/smb', 'PUT')[1]).passwortLoeschen).toBe(true);
});

it('ohne Verschlüsselungsschlüssel warnt die Seite, dass das Passwort unverschlüsselt liegt', async () => {
    stand = { ...stand, passwortGesetzt: true, verschluesselt: false };
    zeige();
    expect(await screen.findByText(/unverschlüsselt/)).toBeVisible();
});

it('Vorschau und Abgleich zeigen die Zahlen, Hinweise und Probleme', async () => {
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        if (url === '/api/admin/ablage/smb' && !init?.method) return antwort(stand);
        if (url.startsWith('/api/admin/ablage/smb/abgleichen')) return antwort(ergebnis({ probelauf: url.includes('probelauf=true'), konflikte: 1, hinweise: ['Auf dem Gerät gelöscht oder verschoben (bleibt in der App): Projekte/2026/x/a.pdf'], fehler: ['Projekte/2026/x/b.pdf: Zugriff verweigert'] }));
        return antwort({});
    });
    const user = userEvent.setup();
    zeige();
    await user.click(await screen.findByRole('button', { name: /Vorschau/ }));
    const box = await screen.findByTestId('smb-ergebnis');
    expect(box).toHaveTextContent('Vorschau (es wurde nichts geändert)');
    expect(box).toHaveTextContent('Konflikte: 1');
    expect(box).toHaveTextContent('1 Hinweise');
    expect(screen.getByRole('alert')).toHaveTextContent('Zugriff verweigert');

    await user.click(screen.getByRole('button', { name: /Jetzt abgleichen/ }));
    await waitFor(() => expect(aufrufe('abgleichen?probelauf=false')).toHaveLength(1));
});

it('der Abstand für den automatischen Abgleich wird angezeigt und mit dem Speichern geschickt', async () => {
    stand = { ...stand, server: '192.168.178.21', freigabe: 'Unternehmen', aktiv: true, intervallMinuten: 15 };
    const user = userEvent.setup();
    zeige();
    const wahl = await screen.findByLabelText('Automatisch abgleichen');
    expect(wahl).toHaveValue('15');
    expect(within(wahl).getAllByRole('option').map(o => o.textContent)).toEqual(['Nur auf Knopfdruck', 'Alle 5 Minuten', 'Alle 15 Minuten', 'Jede Stunde']);

    await user.selectOptions(wahl, '60');
    await user.click(screen.getByRole('button', { name: /Speichern und Zugriff prüfen/ }));
    await waitFor(() => expect(aufrufe('/api/admin/ablage/smb', 'PUT')).toHaveLength(1));
    expect(bodyVon(aufrufe('/api/admin/ablage/smb', 'PUT')[0]).intervallMinuten).toBe(60);
});

it('zeigt, wann zuletzt abgeglichen wurde und mit welchem Ergebnis', async () => {
    stand = { ...stand, letzterLauf: '2026-10-08T12:30:00', letztesErgebnis: '3 auf die Freigabe, 1 vom Gerät' };
    zeige();
    const zeile = await screen.findByTestId('smb-letzter');
    expect(zeile).toHaveTextContent('Letzter Abgleich:');
    expect(zeile).toHaveTextContent('3 auf die Freigabe, 1 vom Gerät');
});

it('ohne bisherigen Abgleich steht keine Zeile da', async () => {
    zeige();
    await screen.findByRole('button', { name: /Vorschau/ });
    expect(screen.queryByTestId('smb-letzter')).toBeNull();
});

it('ein Fehler beim Abgleich (zum Beispiel ausgeschaltet) erscheint als Meldung', async () => {
    fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
        if (url === '/api/admin/ablage/smb' && !init?.method) return antwort(stand);
        return antwort({ message: 'Der Abgleich ist ausgeschaltet.' }, false, 409);
    });
    const user = userEvent.setup();
    zeige();
    await user.click(await screen.findByRole('button', { name: /Jetzt abgleichen/ }));
    expect(await screen.findByText('Der Abgleich ist ausgeschaltet.')).toBeVisible();
});
