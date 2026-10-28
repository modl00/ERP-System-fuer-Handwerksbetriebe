import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtikelStammdatenForm } from './ArtikelStammdatenForm';
import { ToastProvider } from '../ui/toast';
import type { Artikel } from '../../types';

vi.mock('../CategoryTreeModal', () => ({ CategoryTreeModal: () => null }));

/** Nur Dummy-Daten. */
const rohr: Artikel = {
    id: 7, version: 3, produktname: 'Rundrohr 42,4 x 2', verrechnungseinheit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' },
    werkstoffId: 4, werkstoffName: 'Baustahl', profilform: { name: 'RUNDROHR', anzeigename: 'Rundrohr' }, durchmesser: 42.4, wandstaerke: 2,
};
const blech: Artikel = {
    id: 8, version: 1, produktname: 'Stahlblech 10 mm', verrechnungseinheit: { name: 'QUADRATMETER', anzeigename: 'Quadratmeter' },
    werkstoffId: 4, werkstoffName: 'Baustahl', profilform: { name: 'BLECH', anzeigename: 'Blech' }, wandstaerke: 10,
};
const konsole: Artikel = {
    id: 9, version: 1, produktname: 'Konsole', verrechnungseinheit: { name: 'STUECK', anzeigename: 'Stück' },
};

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let rechnenAntwort: () => ReturnType<typeof antwort>;
const puts = () => fetcher.mock.calls.filter(([u, i]) => String(u).endsWith('/stammdaten') && i?.method === 'PUT');
const sendeBody = () => JSON.parse(puts()[0][1].body as string);

beforeEach(() => {
    rechnenAntwort = () => antwort({ kgJeQm: 78.5, m2JeQm: 2, dichte: 7.85, hinweis: 'Berechnet aus Dicke × Dichte (7.85 kg/dm³), glattes Blech ohne Toleranz.' });
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url.endsWith('/filteroptionen')) return antwort({ profilform: [{ wert: 'RUNDROHR', name: 'Rundrohr' }, { wert: 'BLECH', name: 'Blech' }, { wert: 'RIFFELBLECH', name: 'Riffelblech' }], herstellverfahren: [], fertigungszustand: [] });
        if (url.endsWith('/werkstoffe/details')) return antwort([{ id: 4, name: 'Baustahl' }, { id: 5, name: 'Edelstahl' }]);
        if (url.endsWith('/profile/formen')) return antwort([]);
        if (url.endsWith('/profile/rechnen')) return rechnenAntwort();
        if (init?.method === 'PUT') return antwort({ id: 1 });
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (artikel: Artikel) => render(<ToastProvider><ArtikelStammdatenForm artikel={artikel} onGespeichert={vi.fn()} onAbbrechen={vi.fn()} /></ToastProvider>);
const speichern = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.click(screen.getByRole('button', { name: /^Speichern/ }));
    await waitFor(() => expect(puts()).toHaveLength(1));
    return sendeBody();
};

describe('Artikel: Gewicht und Fläche eindeutig', { timeout: 30000 }, () => {
    it('sagt klar, wonach verzinkt und wonach beschichtet wird', async () => {
        zeige(rohr);
        const bereich = await screen.findByTestId('gewicht-oberflaeche');
        expect(bereich).toHaveTextContent('Verzinken immer nach Gewicht (kg)');
        expect(bereich).toHaveTextContent('Pulverbeschichten immer nach Außenfläche (m²)');
    });

    it('eine Rohr-Meterware zeigt Gewicht und Außenfläche je Meter', async () => {
        zeige(rohr);
        expect(await screen.findByLabelText('Gewicht je Meter (kg/m)')).toBeVisible();
        expect(screen.getByLabelText('Außenfläche je Meter (m²/m)')).toBeVisible();
        expect(screen.queryByLabelText('Gewicht je m² (kg/m²)')).toBeNull();
        expect(screen.queryByLabelText('Gewicht je Stück (kg)')).toBeNull();
    });

    it('ein Blech zeigt Dicke, Gewicht je m² und Fläche je m² und speichert keine Meterwerte', async () => {
        const user = userEvent.setup();
        render(<ToastProvider><ArtikelStammdatenForm artikel={{ ...blech, kgProQm: 78.5, kgProMeter: undefined, flaecheJeQm: 1.8 }} onGespeichert={vi.fn()} onAbbrechen={vi.fn()} /></ToastProvider>);
        expect(await screen.findByLabelText('Gewicht je m² (kg/m²)')).toHaveValue('78,5');
        expect(screen.getByLabelText('Fläche je m² Blech (m²/m²)')).toHaveValue('1,8');
        expect(screen.getByLabelText('Blechdicke (mm)')).toHaveValue('10');
        expect(screen.queryByLabelText('Gewicht je Meter (kg/m)')).toBeNull();

        const b = await speichern(user);
        expect(b).toMatchObject({ kgProQm: 78.5, flaecheJeQm: 1.8, kgProMeter: null, mantelflaeche: null, kgJeStueck: null, m2JeStueck: null });
    });

    it('Stückware zeigt Gewicht und Außenfläche je Stück', async () => {
        const user = userEvent.setup();
        zeige(konsole);
        await user.type(await screen.findByLabelText('Gewicht je Stück (kg)'), '2,5');
        await user.type(screen.getByLabelText('Außenfläche je Stück (m²)'), '0,3');

        const b = await speichern(user);
        expect(b).toMatchObject({ kgJeStueck: 2.5, m2JeStueck: 0.3, kgProMeter: null, kgProQm: null, gewichtHerkunft: 'HAND' });
    });

    it('beim Blech rechnet „Aus Dicke und Dichte berechnen" und merkt sich Herkunft und Dichte', async () => {
        const user = userEvent.setup();
        zeige(blech);
        await user.click(await screen.findByRole('button', { name: 'Aus Dicke und Dichte berechnen' }));

        await waitFor(() => expect(screen.getByLabelText('Gewicht je m² (kg/m²)')).toHaveValue('78,5'));
        expect(screen.getByLabelText('Fläche je m² Blech (m²/m²)')).toHaveValue('2');
        expect(screen.getByTestId('gewicht-herkunft')).toHaveTextContent('aus den Maßen berechnet');
        const aufruf = fetcher.mock.calls.find(([u]) => String(u).endsWith('/profile/rechnen'))!;
        expect(JSON.parse(aufruf[1].body as string)).toMatchObject({ profilform: 'BLECH', wandstaerke: 10, werkstoffId: 4 });

        const b = await speichern(user);
        expect(b).toMatchObject({ kgProQm: 78.5, flaecheJeQm: 2, gewichtHerkunft: 'BERECHNET', gewichtDichte: 7.85 });
    });

    it('Riffelblech bietet keine Rechnung an und verweist auf das Datenblatt', async () => {
        zeige({ ...blech, profilform: { name: 'RIFFELBLECH', anzeigename: 'Riffelblech' } });
        expect(await screen.findByText(/aus dem Datenblatt eintragen/)).toBeVisible();
        expect(screen.queryByRole('button', { name: 'Aus Dicke und Dichte berechnen' })).toBeNull();
    });

    it('wer die Berechnungsart wechselt, speichert nur noch die Werte dieser Art', async () => {
        const user = userEvent.setup();
        render(<ToastProvider><ArtikelStammdatenForm artikel={{ ...rohr, kgProMeter: 1.99, mantelflaeche: 0.13 }} onGespeichert={vi.fn()} onAbbrechen={vi.fn()} /></ToastProvider>);
        await user.click(await screen.findByRole('combobox', { name: 'Gerechnet wird als' }));
        await user.click(await screen.findByRole('option', { name: 'Stückware (je Stück)' }));
        await user.type(await screen.findByLabelText('Gewicht je Stück (kg)'), '3');

        const b = await speichern(user);
        expect(b).toMatchObject({ kgJeStueck: 3, kgProMeter: null, mantelflaeche: null, kgProQm: null });
    });

    it('warnt, wenn sich der Werkstoff nach der Berechnung ändert, und nimmt die Warnung nach dem Neuberechnen zurück', async () => {
        const user = userEvent.setup();
        zeige(blech);
        await user.click(await screen.findByRole('button', { name: 'Aus Dicke und Dichte berechnen' }));
        await screen.findByTestId('gewicht-herkunft');
        expect(screen.queryByTestId('gewicht-veraltet')).toBeNull();

        await user.type(screen.getByLabelText('Blechdicke (mm)'), '5');
        expect(await screen.findByTestId('gewicht-veraltet')).toHaveTextContent('bitte neu berechnen');

        await user.click(screen.getByRole('button', { name: 'Aus Dicke und Dichte berechnen' }));
        await waitFor(() => expect(screen.queryByTestId('gewicht-veraltet')).toBeNull());
    });

    it('warnt auch, wenn der Server meldet, dass die Dichte des Werkstoffs inzwischen anders ist', async () => {
        zeige({ ...blech, kgProQm: 78.5, gewichtHerkunft: 'BERECHNET', gewichtVeraltet: true });
        expect(await screen.findByTestId('gewicht-veraltet')).toBeVisible();
    });

    it('ein von Hand getipptes Gewicht gilt als von Hand und wird nicht als veraltet gemeldet', async () => {
        const user = userEvent.setup();
        zeige({ ...blech, kgProQm: 78.5, gewichtHerkunft: 'BERECHNET', gewichtVeraltet: true });
        await screen.findByTestId('gewicht-veraltet');
        await user.clear(screen.getByLabelText('Gewicht je m² (kg/m²)'));
        await user.type(screen.getByLabelText('Gewicht je m² (kg/m²)'), '80');

        expect(screen.queryByTestId('gewicht-veraltet')).toBeNull();
        expect(screen.getByTestId('gewicht-herkunft')).toHaveTextContent('von Hand eingetragen');
        const b = await speichern(user);
        expect(b).toMatchObject({ kgProQm: 80, gewichtHerkunft: 'HAND', gewichtDichte: null });
    });

    it('lehnt eine Fläche je m² über 10 und ein Gewicht von 0 ab, bevor etwas gesendet wird', async () => {
        const user = userEvent.setup();
        zeige(blech);
        await user.type(await screen.findByLabelText('Gewicht je m² (kg/m²)'), '78,5');
        await user.type(screen.getByLabelText('Fläche je m² Blech (m²/m²)'), '11');
        await user.click(screen.getByRole('button', { name: /^Speichern/ }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Fläche je m² Blech');
        expect(puts()).toHaveLength(0);
    });

    it('zeigt den Fehler der Berechnung in Klartext', async () => {
        const user = userEvent.setup();
        rechnenAntwort = () => antwort({ message: 'Bitte die Blechdicke (bei Wandstärke) eintragen.' }, false, 400);
        zeige({ ...blech, wandstaerke: undefined });
        await user.click(await screen.findByRole('button', { name: 'Aus Dicke und Dichte berechnen' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Blechdicke');
    });

    it('legt eine Zuschnitt-Formel an und speichert Felder und Formeln mit den Stammdaten', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
            if (url.endsWith('/profile/formel')) return antwort({ kg: 39.25, m2: 1 });
            if (url.endsWith('/filteroptionen')) return antwort({ profilform: [], herstellverfahren: [], fertigungszustand: [] });
            if (url.endsWith('/werkstoffe/details')) return antwort([]);
            if (url.endsWith('/profile/formen')) return antwort([]);
            if (init?.method === 'PUT') return antwort({ id: 1 });
            return antwort({});
        });
        zeige(konsole);
        await user.click(await screen.findByRole('button', { name: 'Formel anlegen' }));
        await user.click(await screen.findByRole('button', { name: 'Blechzuschnitt' }));
        await user.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(await screen.findByTestId('zuschnitt-formel-text')).toHaveTextContent('Länge, Breite, Dicke');
        const b = await speichern(user);
        expect(b.formelGewicht).toBe('(L/1000) * (B/1000) * T * dichte');
        expect(b.formelFlaeche).toBe('(L/1000) * (B/1000) * 2');
        expect(b.formelFelder).toHaveLength(3);
        expect(b.formelFelder[0]).toMatchObject({ name: 'L', bezeichnung: 'Länge', einheit: 'mm', standard: 1000 });
    });

    it('ein Artikel mit Formel zeigt sie an und speichert sie unverändert wieder mit', async () => {
        const user = userEvent.setup();
        zeige({ ...konsole, formelGewicht: 'L/1000', formelFlaeche: null, formelFelder: [{ name: 'L', bezeichnung: 'Länge', einheit: 'mm', standard: 500 }] });
        expect(await screen.findByRole('button', { name: 'Formel bearbeiten' })).toBeVisible();
        expect(screen.getByTestId('zuschnitt-formel-text')).toHaveTextContent('Länge');

        const b = await speichern(user);
        expect(b).toMatchObject({ formelGewicht: 'L/1000', formelFlaeche: null });
        expect(b.formelFelder).toEqual([{ name: 'L', bezeichnung: 'Länge', einheit: 'mm', standard: 500 }]);
    });

    it('ohne Formel schickt das Formular leere Formelangaben, damit eine alte nicht stehen bleibt', async () => {
        const user = userEvent.setup();
        zeige(rohr);
        await screen.findByRole('button', { name: 'Formel anlegen' });
        const b = await speichern(user);
        expect(b).toMatchObject({ formelGewicht: null, formelFlaeche: null, formelFelder: [] });
    });
});
