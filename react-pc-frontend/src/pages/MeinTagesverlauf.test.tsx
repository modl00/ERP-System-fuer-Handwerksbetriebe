import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MeinTagesverlauf from './MeinTagesverlauf';
import { heute, tagVerschieben, type MeinTag } from '../features/aktivitaet/aktivitaetApi';

/** Nur Dummy-Daten. */
const tag = (datum: string, extra: Partial<MeinTag> = {}): MeinTag => ({
    datum, aktiv: true, aufbewahrungTage: 365, ereignisse: [
        { id: 1, zeitpunkt: `${datum}T08:15:00`, art: 'GEAENDERT', text: 'Projekt geändert', objektTyp: 'Projekt', objektId: 7, objektName: '2026/014 Anbau Halle', benutzer: null },
        { id: 2, zeitpunkt: `${datum}T09:40:00`, art: 'VERSENDET', text: 'E-Mail gesendet', objektTyp: null, objektId: null, objektName: null, benutzer: null },
        { id: 3, zeitpunkt: `${datum}T11:05:00`, art: 'GELOESCHT', text: 'Anfrage gelöscht', objektTyp: 'Anfrage', objektId: 12, objektName: null, benutzer: null },
    ], ...extra,
});

const antwort = (daten: unknown, status = 200) => ({ ok: status < 300, status, json: async () => daten });
let fetcher: ReturnType<typeof vi.fn>;

beforeEach(() => {
    fetcher = vi.fn(async (url: string) => antwort(tag(new URL(url, 'http://x').searchParams.get('datum') as string)));
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('Mein Tagesverlauf', { timeout: 30000 }, () => {
    it('zeigt die Ereignisse des Tages nach Uhrzeit mit Objektnamen, dazu den Hinweis auf Inhalte und Aufbewahrung', async () => {
        render(<MeinTagesverlauf />);

        const eintraege = await screen.findAllByRole('listitem');
        expect(eintraege).toHaveLength(3);
        expect(eintraege[0]).toHaveTextContent('08:15');
        expect(eintraege[0]).toHaveTextContent('Projekt geändert: 2026/014 Anbau Halle');
        expect(eintraege[1]).toHaveTextContent('E-Mail gesendet');
        expect(eintraege[2]).toHaveTextContent('Anfrage gelöscht (Anfrage 12)');
        expect(screen.getByText(/keine Inhalte wie E-Mail-Texte oder Beträge/)).toHaveTextContent('Aufbewahrung: 365 Tage');
        expect(fetcher.mock.calls[0][0]).toBe(`/api/aktivitaet/mein?datum=${heute()}`);
    });

    it('Tag zurück und wieder vor; „Nächster Tag" ist heute gesperrt, „Heute" führt zurück', async () => {
        const user = userEvent.setup();
        render(<MeinTagesverlauf />);
        await screen.findAllByRole('listitem');
        expect(screen.getByRole('button', { name: 'Nächster Tag' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Heute' })).not.toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Vorheriger Tag' }));

        await waitFor(() => expect(fetcher.mock.calls.at(-1)?.[0]).toBe(`/api/aktivitaet/mein?datum=${tagVerschieben(heute(), -1)}`));
        expect(screen.getByLabelText('Tag wählen')).toHaveValue(tagVerschieben(heute(), -1));
        expect(screen.getByRole('button', { name: 'Nächster Tag' })).toBeEnabled();
        await user.click(screen.getByRole('button', { name: 'Heute' }));
        await waitFor(() => expect(screen.getByLabelText('Tag wählen')).toHaveValue(heute()));
    });

    it('ein Tag ohne Ereignisse sagt es; ausgeschalteter Verlauf für Mitarbeiter wird erklärt', async () => {
        fetcher.mockImplementation(async (url: string) => antwort(tag(new URL(url, 'http://x').searchParams.get('datum') as string, { ereignisse: [], aktiv: false, aufbewahrungTage: 0 })));
        render(<MeinTagesverlauf />);

        expect(await screen.findByText(/Der Tagesverlauf ist für Mitarbeiter nicht eingeschaltet/)).toBeInTheDocument();
        expect(screen.getByText('Es gibt keine Einträge.')).toBeInTheDocument();
        expect(screen.getByText(/Aufbewahrung: unbegrenzt/)).toBeInTheDocument();
    });

    it('aktiver Verlauf ohne Einträge: „Für diesen Tag gibt es keine Einträge."; Fehler: Meldung mit dem Satz des Servers', async () => {
        fetcher.mockImplementation(async (url: string) => antwort(tag(new URL(url, 'http://x').searchParams.get('datum') as string, { ereignisse: [] })));
        const { unmount } = render(<MeinTagesverlauf />);
        expect(await screen.findByText('Für diesen Tag gibt es keine Einträge.')).toBeInTheDocument();
        unmount();

        fetcher.mockImplementation(async () => antwort({ message: 'Bitte anmelden.' }, 401));
        render(<MeinTagesverlauf />);
        expect(await screen.findByRole('alert')).toHaveTextContent('Bitte anmelden.');
    });
});
