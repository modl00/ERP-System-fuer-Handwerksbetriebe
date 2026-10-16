import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LayoutEinstellungenDialog from './LayoutEinstellungenDialog';
import type { VorlagenEinstellungen } from './vorlagenEinstellungen';

const STANDARD = {
    schriftart: 'HELVETICA', schriftgroesse: 9, falzmarken: true, hauptblockOben: 118, hauptblockLinks: 25, hauptblockRechts: 25,
    adresszeileAnzeigen: true, adresszeileText: '', adresszeileOben: 47, adresszeileLinks: 26,
    betreff1Anzeigen: true, betreff1Fett: true, betreff1Inhalt: 'BAUVORHABEN', betreff1Schriftgroesse: 11, betreff1Praefix: 'BV:',
    betreff2Anzeigen: true, betreff2Fett: true, betreff2Schriftgroesse: 13, betreff2Text: '',
    infoblockAnzeigen: true, infoblockOben: 71, infoblockLinks: 116, infoblockBreite: 70,
    infoDokumentnummer: true, infoProjektnummer: true, infoReferenzdokument: false, infoKundennummer: true, infoDatum: true,
    infoAnsprechpartner: true, infoTelefon: true, infoMobil: true, infoFax: false, infoEmail: true,
    freitextAnzeigen: false, freitextOben: 50, freitextLinks: 20, freitextBreite: 50, freitextInhalt: '',
    fusszeileTyp: 'DOKUMENT_FIRMA_SEITE', fusszeileAbstand: 8, fusszeileSchriftgroesse: 7,
    briefpapierAnzeigen: true,
    positionsBeschreibung: true, artikelnummerAnzeigen: false, einzelpreiseAusblenden: false, tabellenstil: 'GRAU',
} as VorlagenEinstellungen;

const antwort = (daten: unknown, ok = true) => ({ ok, status: ok ? 200 : 500, json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
beforeEach(() => {
    fetcher = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
        if (url.startsWith('/api/formulare/layout-einstellungen/standard')) {
            return antwort(url.includes('lieferschein') ? { ...STANDARD, einzelpreiseAusblenden: true, artikelnummerAnzeigen: true } : STANDARD);
        }
        if (url === '/api/formulare/layout-aus-einstellungen' && init?.method === 'POST') {
            return antwort({ html: '<div class="page"></div>', einstellungen: JSON.parse(init.body as string) });
        }
        return antwort({}, false);
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (props: Partial<React.ComponentProps<typeof LayoutEinstellungenDialog>> = {}) => {
    const onUebernehmen = vi.fn();
    const onSchliessen = vi.fn();
    render(<LayoutEinstellungenDialog offen einstellungen={null} lieferschein={false} onSchliessen={onSchliessen} onUebernehmen={onUebernehmen} {...props} />);
    return { onUebernehmen, onSchliessen };
};

describe('LayoutEinstellungenDialog', () => {
    it('lädt ohne gespeicherte Einstellungen die Standardwerte und zeigt alle Gruppen', async () => {
        zeige();
        const dialog = await screen.findByRole('dialog');
        expect(await within(dialog).findByLabelText('Präfix der Betreffzeile')).toHaveValue('BV:');
        for (const gruppe of ['Schrift und Seite', 'Adresszeile (Absender über dem Empfänger)', 'Betreffzeile 1 (Projektanschrift)',
            'Betreffzeile 2 (Titel mit Dokumentnummer)', 'Dokumentinformationen und Kontaktdaten (Infoblock)', 'Freitext-Infoblock', 'Fußzeile', 'Briefpapier', 'Positionen']) {
            expect(within(dialog).getAllByText(gruppe)[0]).toBeVisible();
        }
        expect(within(dialog).getByLabelText('Mobil')).toBeChecked();
        expect(within(dialog).getByLabelText('Fax')).not.toBeChecked();
    });

    it('nimmt beim Lieferschein die Standardwerte ohne Preise und mit Artikelnummer', async () => {
        zeige({ lieferschein: true });
        const dialog = await screen.findByRole('dialog');
        await waitFor(() => expect(within(dialog).getByLabelText('Artikelnummer anzeigen')).toBeChecked());
        expect(within(dialog).getByLabelText('Einzelpreise und Summen ausblenden')).toBeChecked();
        expect(fetcher.mock.calls[0][0]).toContain('art=lieferschein');
    });

    it('baut das Layout mit den geänderten Werten neu auf und gibt es zurück', async () => {
        const user = userEvent.setup();
        const { onUebernehmen, onSchliessen } = zeige({ einstellungen: STANDARD });
        const dialog = await screen.findByRole('dialog');

        const praefix = within(dialog).getByLabelText('Präfix der Betreffzeile');
        await user.clear(praefix);
        await user.type(praefix, 'Objekt:');
        await user.click(within(dialog).getByLabelText('Falzmarken auf der ersten Seite anzeigen'));
        await user.click(within(dialog).getByRole('button', { name: /Layout neu aufbauen/ }));

        await waitFor(() => expect(onUebernehmen).toHaveBeenCalledTimes(1));
        const [html, einstellungen] = onUebernehmen.mock.calls[0] as [string, VorlagenEinstellungen];
        expect(html).toBe('<div class="page"></div>');
        expect(einstellungen.betreff1Praefix).toBe('Objekt:');
        expect(einstellungen.falzmarken).toBe(false);
        expect(onSchliessen).toHaveBeenCalled();
        expect(fetcher.mock.calls.filter(([u]) => String(u).startsWith('/api/formulare/layout-einstellungen/standard'))).toHaveLength(0);
    });

    it('die Schrift der Tabelle steht bei einer älteren Vorlage auf 10 pt und lässt sich ändern (#286)', async () => {
        const user = userEvent.setup();
        const { onUebernehmen } = zeige({ einstellungen: STANDARD });
        const dialog = await screen.findByRole('dialog');
        const feld = within(dialog).getByLabelText('Schriftgröße der Tabelle (pt)');
        expect(feld).toHaveValue(10);

        await user.clear(feld);
        await user.type(feld, '12');
        await user.click(within(dialog).getByRole('button', { name: /Layout neu aufbauen/ }));

        await waitFor(() => expect(onUebernehmen).toHaveBeenCalledTimes(1));
        expect((onUebernehmen.mock.calls[0] as [string, VorlagenEinstellungen])[1].tabellenSchriftgroesse).toBe(12);
    });

    it('verlangt ausgefüllte Zahlenfelder und schickt sonst nichts', async () => {
        const user = userEvent.setup();
        const { onUebernehmen } = zeige({ einstellungen: STANDARD });
        const dialog = await screen.findByRole('dialog');

        await user.clear(within(dialog).getAllByLabelText('Schriftgröße (pt)')[0]);
        await user.click(within(dialog).getByRole('button', { name: /Layout neu aufbauen/ }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Bitte alle Zahlenfelder ausfüllen.');
        expect(onUebernehmen).not.toHaveBeenCalled();
        expect(fetcher.mock.calls.filter(([u]) => u === '/api/formulare/layout-aus-einstellungen')).toHaveLength(0);
    });

    it('setzt mit "Standardwerte" alles zurück', async () => {
        const user = userEvent.setup();
        zeige({ einstellungen: { ...STANDARD, betreff1Praefix: 'X:' } });
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByLabelText('Präfix der Betreffzeile')).toHaveValue('X:');

        await user.click(within(dialog).getByRole('button', { name: /Standardwerte/ }));

        await waitFor(() => expect(within(dialog).getByLabelText('Präfix der Betreffzeile')).toHaveValue('BV:'));
    });

    it('meldet einen Fehler des Servers, statt still zu scheitern', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async () => antwort({}, false));
        const { onUebernehmen } = zeige({ einstellungen: STANDARD });
        const dialog = await screen.findByRole('dialog');

        await user.click(within(dialog).getByRole('button', { name: /Layout neu aufbauen/ }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Das Layout konnte nicht erstellt werden.');
        expect(onUebernehmen).not.toHaveBeenCalled();
    });
});
