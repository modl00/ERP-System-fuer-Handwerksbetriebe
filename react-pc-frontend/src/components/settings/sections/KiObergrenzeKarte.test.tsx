import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { KiObergrenzeKarte } from './KiObergrenzeKarte';
import { ToastProvider } from '../../ui/toast';

/** Nur Dummy-Daten. */
const STAND = {
    datenschutz: 'CLOUD', eingriff: 'MIT_BESTAETIGUNG',
    anbieter: [
        { typ: 'GEMINI', anzeige: 'Gemini (Google)', lokal: false, vertrag: false },
        { typ: 'LOKAL', anzeige: 'Lokale KI (Ollama u. a.)', lokal: true, vertrag: false },
        { typ: 'CLAUDE', anzeige: 'Claude (Anthropic)', lokal: false, vertrag: false },
        { typ: 'MISTRAL', anzeige: 'Mistral', lokal: false, vertrag: true },
    ],
    werkzeuge: [
        { name: 'artikel_suchen', beschreibung: 'Sucht Artikel', aendert: false, freischaltbar: false, freigegeben: false },
        { name: 'zeit_buchen', beschreibung: 'Trägt Arbeitszeit ein', aendert: true, freischaltbar: true, freigegeben: false },
        { name: 'lager_buchen', beschreibung: 'Bucht Lagerzugang oder Entnahme', aendert: true, freischaltbar: true, freigegeben: true },
    ],
};

let puts: Record<string, unknown>[];

function stub(putStatus = 200, putBody: unknown = { ...STAND, datenschutz: 'ALLGEMEIN' }) {
    const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
    vi.stubGlobal('fetch', vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'PUT') { puts.push(JSON.parse(String(init.body))); return json(putBody, putStatus); }
        return json(STAND);
    }));
}

const rendern = () => render(<ToastProvider><KiObergrenzeKarte /></ToastProvider>);

async function waehle(user: ReturnType<typeof userEvent.setup>, feld: string | RegExp, option: string) {
    await user.click(screen.getByRole('combobox', { name: feld }));
    // Der Name einer Option enthält die kurze Erklärung darunter: nach dem Anfang suchen
    const optionen = await screen.findAllByRole('option');
    const treffer = optionen.find((o) => (o.textContent ?? '').startsWith(option));
    if (!treffer) throw new Error('Option nicht gefunden: ' + option);
    await user.click(treffer);
}

beforeEach(() => { puts = []; });
afterEach(() => { vi.unstubAllGlobals(); });

describe('KI-Obergrenze: Wie weit darf die KI gehen?', { timeout: 30000 }, () => {
    it('zeigt den heutigen Stand und sagt in einem Satz, was er bedeutet; Speichern ist erst nach einer Änderung möglich', async () => {
        stub();
        rendern();

        expect(await screen.findByTestId('ki-obergrenze-wirkung')).toHaveTextContent('Alle eingerichteten Anbieter dürfen Daten aus dem Programm sehen. Jede Änderung braucht Ihre Bestätigung.');
        expect(screen.getByRole('button', { name: /^Speichern$/ })).toBeDisabled();
    });

    it('„Nur allgemeine Anfragen“ erklärt sich selbst und wird mit den übrigen Angaben gespeichert', async () => {
        const user = userEvent.setup();
        stub();
        rendern();
        await screen.findByTestId('ki-obergrenze-wirkung');

        await waehle(user, 'Wohin dürfen Daten gehen?', 'Nur allgemeine Anfragen');
        expect(screen.getByTestId('ki-obergrenze-wirkung')).toHaveTextContent('Cloud-Anbieter sehen nur allgemeine Fragen, nie Daten aus dem Programm.');
        await user.click(screen.getByRole('button', { name: /^Speichern$/ }));

        await waitFor(() => expect(puts).toHaveLength(1));
        expect(puts[0]).toMatchObject({ datenschutz: 'ALLGEMEIN', eingriff: 'MIT_BESTAETIGUNG', vertrag: { GEMINI: false, CLAUDE: false, MISTRAL: true }, ohneRueckfrage: [] });
        expect(await screen.findByText('Gespeichert. Die Änderung steht im KI-Protokoll.')).toBeVisible();
    });

    it('„Anbieter mit Vertrag“ zeigt die Cloud-Anbieter zum Ankreuzen, nicht die lokale KI, mit dem Hinweis, dass die Prüfung beim Betrieb bleibt', async () => {
        const user = userEvent.setup();
        stub();
        rendern();
        await screen.findByTestId('ki-obergrenze-wirkung');
        expect(screen.queryByText(/Mit welchen Anbietern besteht ein Vertrag/)).toBeNull();

        await waehle(user, 'Wohin dürfen Daten gehen?', 'Anbieter mit Vertrag');

        expect(screen.getByText('Mit welchen Anbietern besteht ein Vertrag?')).toBeVisible();
        expect(screen.getByLabelText('Mit Mistral besteht ein Vertrag')).toBeChecked();
        expect(screen.getByLabelText('Mit Claude (Anthropic) besteht ein Vertrag')).not.toBeChecked();
        expect(screen.queryByLabelText(/Lokale KI.*Vertrag/)).toBeNull();
        expect(screen.getByText(/rechtliche Prüfung bleibt bei Ihnen/)).toBeVisible();
        await user.click(screen.getByLabelText('Mit Claude (Anthropic) besteht ein Vertrag'));
        await user.click(screen.getByRole('button', { name: /^Speichern$/ }));
        await waitFor(() => expect(puts[0]).toMatchObject({ datenschutz: 'MIT_VERTRAG', vertrag: { CLAUDE: true, MISTRAL: true } }));
    });

    it('„Erweitert“ zeigt nur freischaltbare Werkzeuge mit Warnung; angehakte gehen als „ohne Rückfrage“ mit', async () => {
        const user = userEvent.setup();
        stub();
        rendern();
        await screen.findByTestId('ki-obergrenze-wirkung');

        await waehle(user, 'Was darf die KI tun?', 'Erweitert');

        expect(screen.getByText('Diese Werkzeuge dürfen ohne Rückfrage laufen')).toBeVisible();
        expect(screen.getByText(/Werkzeuge, die etwas löschen, gibt es hier nie/)).toBeVisible();
        expect(screen.queryByLabelText('artikel_suchen')).toBeNull();
        expect(screen.getByLabelText('lager_buchen')).toBeChecked();
        await user.click(screen.getByLabelText('zeit_buchen'));
        await user.click(screen.getByRole('button', { name: /^Speichern$/ }));

        await waitFor(() => expect(puts).toHaveLength(1));
        expect(puts[0]).toMatchObject({ eingriff: 'ERWEITERT' });
        expect([...(puts[0].ohneRueckfrage as string[])].sort()).toEqual(['lager_buchen', 'zeit_buchen']);
    });

    it('wird „Erweitert“ verlassen, werden keine Werkzeuge ohne Rückfrage mitgeschickt', async () => {
        const user = userEvent.setup();
        stub();
        rendern();
        await screen.findByTestId('ki-obergrenze-wirkung');

        await waehle(user, 'Was darf die KI tun?', 'Nur lesen');
        await user.click(screen.getByRole('button', { name: /^Speichern$/ }));

        await waitFor(() => expect(puts).toHaveLength(1));
        expect(puts[0]).toMatchObject({ eingriff: 'NUR_LESEN', ohneRueckfrage: [] });
    });

    it('ausgeschaltet heißt es klar; ein Fehler des Servers steht im Klartext da', async () => {
        const user = userEvent.setup();
        stub(400, { message: 'Unbekannte Stufe für Datenschutz: GEHEIM' });
        rendern();
        await screen.findByTestId('ki-obergrenze-wirkung');

        await waehle(user, 'Was darf die KI tun?', 'Aus');
        expect(screen.getByTestId('ki-obergrenze-wirkung')).toHaveTextContent('Zurzeit ist die KI ausgeschaltet.');
        await user.click(screen.getByRole('button', { name: /^Speichern$/ }));

        expect(await screen.findByText('Unbekannte Stufe für Datenschutz: GEHEIM')).toBeVisible();
    });

    it('kann der Stand nicht geladen werden, steht der Grund da', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ message: 'Nur für Administratoren.' }), { status: 403, headers: { 'Content-Type': 'application/json' } })));
        rendern();

        expect(await screen.findByRole('alert')).toHaveTextContent('Nur für Administratoren.');
    });
});
