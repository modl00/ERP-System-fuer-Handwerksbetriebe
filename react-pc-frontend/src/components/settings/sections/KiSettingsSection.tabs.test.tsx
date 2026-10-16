import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { KiSettingsSection } from './KiSettingsSection';
import { ToastProvider } from '../../ui/toast';

/** Nur Dummy-Daten. */
const ANBIETER = {
    funktionen: [
        { funktion: 'hilfe', anzeige: 'KI-Hilfe (Chat)', anbieter: 'GEMINI', datenschutz: 'CLOUD_ERLAUBT' },
        { funktion: 'assistent', anzeige: 'KI-Assistent mit Auskünften', anbieter: 'LOKAL', datenschutz: 'NUR_LOKAL' },
    ],
    anbieter: [
        { typ: 'GEMINI', anzeige: 'Gemini (Google)', lokal: false, eingerichtet: true, modell: '', schluesselGesetzt: true },
        { typ: 'LOKAL', anzeige: 'Lokale KI', lokal: true, eingerichtet: true, modell: 'llama3', schluesselGesetzt: false },
        { typ: 'CLAUDE', anzeige: 'Claude (Anthropic)', lokal: false, eingerichtet: false, modell: 'claude-x', schluesselGesetzt: false },
        { typ: 'MISTRAL', anzeige: 'Mistral', lokal: false, eingerichtet: false, modell: 'mistral-x', schluesselGesetzt: false },
    ],
    nochGemini: [],
};
const MODELLE = {
    funktionen: [
        { schluessel: 'ki-hilfe', anzeige: 'KI-Hilfe (Chat)', modell: 'gemini-hilfe', standard: 'gemini-hilfe', gewaehlt: false },
        { schluessel: 'spracheingabe', anzeige: 'Spracheingabe', modell: 'gemini-sprache', standard: 'gemini-sprache', gewaehlt: false },
    ],
    websuche: true, websucheStandard: true,
};
const GRENZE = { datenschutz: 'CLOUD', eingriff: 'MIT_BESTAETIGUNG', anbieter: [{ typ: 'GEMINI', lokal: false }, { typ: 'LOKAL', lokal: true }], werkzeuge: [] };
const PROTOKOLL = [
    { id: 2, zeit: '2026-10-06T09:30:00', benutzer: 'Max Mustermann', funktion: 'assistent', anbieter: 'LOKAL', modell: 'llama3', werkzeug: 'zeit_buchen', status: 'VORGESCHLAGEN', ergebnis: 'Zeit buchen: 2 Stunden' },
    { id: 1, zeit: '2026-10-06T09:00:00', benutzer: null, funktion: 'einstellung', anbieter: 'LOKAL', modell: null, werkzeug: 'obergrenze_aendern', status: 'AUSGEFUEHRT', ergebnis: null },
];

function stub(grenze: unknown = GRENZE, protokollStatus = 200) {
    const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL) => {
        const u = String(url);
        if (u.startsWith('/api/settings/ki-anbieter')) return json(ANBIETER);
        if (u.startsWith('/api/settings/ki-modelle')) return json(MODELLE);
        if (u.startsWith('/api/settings/ki-obergrenze')) return json(grenze);
        if (u.startsWith('/api/settings/ki-protokoll')) return protokollStatus === 200 ? json(PROTOKOLL) : json({ message: 'Nur für Administratoren.' }, protokollStatus);
        if (u.startsWith('/api/settings/ki-lokal')) return json({ adresse: '', modell: '', schluesselGesetzt: false, hilfeLokal: false });
        if (u.startsWith('/api/settings/gemini')) return json({ apiKeySet: true });
        if (u.startsWith('/api/settings/anfrage-funnel-spamfilter')) return json({ aktiv: true });
        return json({});
    }));
}

const rendern = () => render(<ToastProvider><KiSettingsSection /></ToastProvider>);
afterEach(() => { vi.unstubAllGlobals(); });

describe('KI-Einstellungen: Reiter und Übersicht', { timeout: 30000 }, () => {
    it('öffnet mit der Übersicht: eine Zeile je Funktion mit Anbieter, Modell, Datenschutz und Status', async () => {
        stub();
        rendern();

        const hilfe = await screen.findByTestId('ki-uebersicht-hilfe');
        expect(within(hilfe).getByText('Gemini (Google)')).toBeVisible();
        expect(within(hilfe).getByText('gemini-hilfe')).toBeVisible();
        expect(within(hilfe).getByText('Cloud erlaubt')).toBeVisible();
        expect(within(hilfe).getByText('Läuft')).toBeVisible();

        const assistent = screen.getByTestId('ki-uebersicht-assistent');
        expect(within(assistent).getByText('llama3')).toBeVisible();
        expect(within(assistent).getByText('Nur im Haus')).toBeVisible();

        // Funktionen ohne Wahl laufen fest über Gemini; die wählbare KI-Hilfe steht nicht doppelt da
        const sprache = screen.getByTestId('ki-uebersicht-spracheingabe');
        expect(within(sprache).getByText('gemini-sprache')).toBeVisible();
        expect(within(sprache).getByText('Cloud (Gemini)')).toBeVisible();
        expect(screen.getAllByText('KI-Hilfe (Chat)')).toHaveLength(1);
        // Die Karten der anderen Reiter sind nicht gleichzeitig auf der Seite
        expect(screen.queryByText('KI-Schlüssel (Google Gemini)')).toBeNull();
        expect(screen.queryByText('Wie weit darf die KI gehen?')).toBeNull();
    });

    it('zeigt, was die Grenze sperrt: „nur im Haus“ sperrt Cloud-Anbieter, „aus“ alles, nicht eingerichtet ist „Schlüssel fehlt“', async () => {
        stub({ ...GRENZE, datenschutz: 'LOKAL' });
        rendern();
        const hilfe = await screen.findByTestId('ki-uebersicht-hilfe');
        expect(within(hilfe).getByText('Gesperrt (Grenze)')).toBeVisible();
        expect(within(screen.getByTestId('ki-uebersicht-assistent')).getByText('Läuft')).toBeVisible();
        expect(screen.getByTestId('ki-uebersicht-grenze')).toHaveTextContent('Grenze: nur im Haus');
        // Die festen Gemini-Funktionen gehen nicht über diese Grenze
        expect(within(screen.getByTestId('ki-uebersicht-spracheingabe')).getByText('Läuft')).toBeVisible();
        vi.unstubAllGlobals();

        stub({ ...GRENZE, eingriff: 'AUS' });
        rendern();
        expect((await screen.findAllByText('Ausgeschaltet (Grenze)')).length).toBeGreaterThan(0);
    });

    it('ein Klick auf eine Funktion oder die Grenze führt zum passenden Reiter', async () => {
        const user = userEvent.setup();
        stub();
        rendern();
        await screen.findByTestId('ki-uebersicht-hilfe');

        await user.click(screen.getByRole('button', { name: 'Spracheingabe ändern' }));
        expect(screen.getByRole('tab', { name: 'Anbieter und Modelle' })).toHaveAttribute('aria-selected', 'true');
        expect(await screen.findByText('KI-Schlüssel (Google Gemini)')).toBeVisible();
        expect(screen.queryByTestId('ki-uebersicht')).toBeNull();

        await user.click(screen.getByRole('tab', { name: 'Übersicht' }));
        await user.click(await screen.findByRole('button', { name: 'alle eingerichteten Anbieter' }));
        expect(screen.getByRole('tab', { name: 'Grenzen' })).toHaveAttribute('aria-selected', 'true');
        expect(await screen.findByTestId('ki-obergrenze-wirkung')).toBeVisible();
    });

    it('die Reiter lassen sich mit den Pfeiltasten wechseln', async () => {
        const user = userEvent.setup();
        stub();
        rendern();
        await screen.findByTestId('ki-uebersicht');

        screen.getByRole('tab', { name: 'Übersicht' }).focus();
        await user.keyboard('{ArrowRight}');
        expect(screen.getByRole('tab', { name: 'Anbieter und Modelle' })).toHaveAttribute('aria-selected', 'true');
        await user.keyboard('{ArrowLeft}{ArrowLeft}');
        expect(screen.getByRole('tab', { name: 'Protokoll' })).toHaveAttribute('aria-selected', 'true');
    });

    it('das Protokoll ist ein eigener Reiter: neueste zuerst, Klartext-Status, Einzelheiten zum Aufklappen', async () => {
        const user = userEvent.setup();
        stub();
        rendern();
        await screen.findByTestId('ki-uebersicht');

        await user.click(screen.getByRole('tab', { name: 'Protokoll' }));
        const tabelle = await screen.findByTestId('ki-protokoll');

        const zeilen = within(tabelle).getAllByRole('row');
        expect(zeilen).toHaveLength(3);
        expect(within(zeilen[1]).getByText('Zeit buchen')).toBeVisible();
        expect(within(zeilen[1]).getByText('Max Mustermann')).toBeVisible();
        expect(within(zeilen[1]).getByText('Vorgeschlagen')).toBeVisible();
        expect(within(zeilen[1]).getByText('LOKAL (llama3)')).toBeVisible();
        expect(within(zeilen[2]).getByText('Obergrenze aendern')).toBeVisible();
        expect(within(zeilen[2]).getByText('Ausgeführt')).toBeVisible();
        expect(within(zeilen[2]).getByText('–')).toBeVisible();
        expect(screen.getByText('Zeit buchen: 2 Stunden')).not.toBeVisible();
        await user.click(screen.getByText('Zeit buchen'));
        expect(screen.getByText('Zeit buchen: 2 Stunden')).toBeVisible();
    });

    it('kann das Protokoll nicht geladen werden, steht der Grund da', async () => {
        const user = userEvent.setup();
        stub(GRENZE, 403);
        rendern();
        await screen.findByTestId('ki-uebersicht');

        await user.click(screen.getByRole('tab', { name: 'Protokoll' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Nur für Administratoren.');
    });
});
