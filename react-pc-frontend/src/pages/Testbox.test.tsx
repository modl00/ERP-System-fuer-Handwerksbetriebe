import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Testbox from './Testbox';
import { ToastProvider } from '../components/ui/toast';

/** Nur Dummy-Daten, kein echter Zugangsschlüssel. */
const json = (body: unknown, status = 200) => ({ ok: status < 300, status, json: async () => body }) as Response;
const zeige = () => render(<MemoryRouter><ToastProvider><Testbox /></ToastProvider></MemoryRouter>);

const PUNKTE = [
    { nummer: 5, titel: 'Plantafel: Lieferzeit', url: 'http://g/5', labels: ['Wartet auf Antwort'], frage: 'Welche Reihenfolge soll gelten?', wartet: true },
    { nummer: 7, titel: 'Menü', url: 'http://g/7', labels: ['Im Pull Request'], frage: 'Testweg: Menü öffnen', wartet: false },
];

let einstellung: { url: string; repo: string; tokenGesetzt: boolean };
let punkte: typeof PUNKTE;
let fetcher: ReturnType<typeof vi.fn>;
const aufrufe = (methode: string, pfad: string) => fetcher.mock.calls.filter(([u, init]) => String(u) === pfad && (init?.method ?? 'GET') === methode);

beforeEach(() => {
    einstellung = { url: 'http://g', repo: 'M/Demo', tokenGesetzt: true };
    punkte = PUNKTE;
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        const u = String(url);
        if (u === '/api/testbox/einstellung' && init?.method === 'PUT') {
            const e = JSON.parse(String(init.body));
            einstellung = { url: e.url, repo: e.repo, tokenGesetzt: e.token === undefined ? einstellung.tokenGesetzt : e.token !== '' };
            return json(einstellung);
        }
        if (u === '/api/testbox/einstellung') return json(einstellung);
        if (u === '/api/testbox/punkte') return json(punkte);
        if (u === '/api/testbox/aufbereiten') {
            const e = JSON.parse(String(init?.body));
            return json(e.antwort === 'ok' ? { einordnung: 'FUNKTIONIERT', kommentar: 'Funktioniert wie erwartet.', ki: false, hinweis: null }
                : { einordnung: 'ENTSCHEIDUNG', kommentar: 'Der Planer soll die Reihenfolge festlegen.', ki: true, hinweis: null });
        }
        if (u === '/api/testbox/senden') return json({ gesendet: true });
        return json({}, 404);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('Test-Box', { timeout: 30000 }, () => {
    it('zeigt die offenen Punkte, die wartenden zuerst markiert, mit Frage und Link', async () => {
        zeige();

        expect(await screen.findByRole('heading', { name: '#5 Plantafel: Lieferzeit' })).toBeInTheDocument();
        expect(screen.getByText('wartet auf Ihre Antwort')).toBeInTheDocument();
        expect(screen.getByLabelText('Frage zu Ticket 5')).toHaveTextContent('Welche Reihenfolge soll gelten?');
        expect(screen.getByRole('link', { name: 'Ticket 7 in Gitea öffnen' })).toHaveAttribute('href', 'http://g/7');
        expect(screen.getByRole('heading', { name: '#7 Menü' })).toBeInTheDocument();
    });

    it('bereitet eine Antwort auf, lässt sie ändern und sendet sie erst nach dem Klick', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByRole('heading', { name: '#5 Plantafel: Lieferzeit' });

        await user.click(screen.getAllByRole('button', { name: 'Entscheidung' })[0]);
        await user.type(screen.getByLabelText('Ihre Antwort zu Ticket 5'), 'der planer soll das entscheiden');
        await user.click(screen.getAllByRole('button', { name: 'Aufbereiten' })[0]);

        const kommentar = await screen.findByLabelText('Kommentar für Ticket 5');
        expect(kommentar).toHaveValue('Der Planer soll die Reihenfolge festlegen.');
        expect(screen.getByText(/von der KI aufbereitet/)).toBeInTheDocument();
        expect(aufrufe('POST', '/api/testbox/senden')).toHaveLength(0);

        await user.type(kommentar, ' Danke.');
        await user.click(screen.getByRole('button', { name: 'In Gitea senden' }));

        await waitFor(() => expect(aufrufe('POST', '/api/testbox/senden')).toHaveLength(1));
        expect(JSON.parse(String(aufrufe('POST', '/api/testbox/senden')[0][1]?.body))).toEqual({
            nummer: 5, einordnung: 'ENTSCHEIDUNG', kommentar: 'Der Planer soll die Reihenfolge festlegen. Danke.', original: 'der planer soll das entscheiden',
        });
        await waitFor(() => expect(aufrufe('GET', '/api/testbox/punkte')).toHaveLength(2));
    });

    it('„Funktioniert“ ohne Text liefert gleich einen Vorschlag zum Bestätigen', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByRole('heading', { name: '#7 Menü' });

        await user.click(screen.getAllByRole('button', { name: 'Funktioniert' })[1]);

        expect(await screen.findByLabelText('Kommentar für Ticket 7')).toHaveValue('Funktioniert wie erwartet.');
        expect(aufrufe('POST', '/api/testbox/senden')).toHaveLength(0);
    });

    it('ohne Zugang steht die Einrichtung offen; der Schlüssel wird gesendet, aber nie angezeigt', async () => {
        einstellung = { url: '', repo: '', tokenGesetzt: false };
        const user = userEvent.setup();
        zeige();

        const schluessel = await screen.findByLabelText('Zugangsschlüssel');
        expect(aufrufe('GET', '/api/testbox/punkte')).toHaveLength(0);
        await user.type(screen.getByLabelText('Adresse von Gitea'), 'http://g');
        await user.type(screen.getByLabelText('Repository'), 'M/Demo');
        await user.type(schluessel, 'geheim');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(aufrufe('PUT', '/api/testbox/einstellung')).toHaveLength(1));
        expect(JSON.parse(String(aufrufe('PUT', '/api/testbox/einstellung')[0][1]?.body))).toEqual({ url: 'http://g', repo: 'M/Demo', token: 'geheim' });
        await waitFor(() => expect(aufrufe('GET', '/api/testbox/punkte')).toHaveLength(1));
        expect(screen.getByLabelText('Zugangsschlüssel')).toHaveValue('');
    });

    it('Fehler von Gitea stehen als Klartext da', async () => {
        fetcher.mockImplementation(async (url: string) => (String(url) === '/api/testbox/einstellung' ? json(einstellung)
            : json({ message: 'Gitea ist nicht erreichbar. Bitte die Adresse prüfen.' }, 400)));
        zeige();

        expect(await screen.findByRole('alert')).toHaveTextContent('Gitea ist nicht erreichbar. Bitte die Adresse prüfen.');
    });
});
