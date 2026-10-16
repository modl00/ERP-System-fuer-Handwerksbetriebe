import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ArbeitsgangFormelModal } from './ArbeitsgangFormelModal';
import type { Arbeitsgang } from '../types';

/** Nur Dummy-Daten (DSGVO). Gerechnet wird im Backend; hier werden Eingabe, Testrechnung und Speichern geprüft. */
const gang = (a: Partial<Arbeitsgang> = {}): Arbeitsgang => ({ id: 6, beschreibung: 'Bohren', abteilungId: 1, abteilungName: 'Werkstatt', stundensatz: 60, stundensatzJahr: 2026, ...a });
const mitFormel = () => gang({
    formelBearbeitung: 'D*Tiefe/100', formelRuest: '5',
    formelFelder: [{ name: 'D', bezeichnung: 'Bohrerdurchmesser', einheit: 'mm', standard: 8 }, { name: 'Tiefe', bezeichnung: 'Bohrtiefe', einheit: 'mm', standard: 20 }],
});

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten });
let fetcher: ReturnType<typeof vi.fn>;
let rechnung: () => ReturnType<typeof antwort>;
const berechnungen = () => fetcher.mock.calls.filter(([u]) => String(u) === '/api/arbeitsgaenge/formel/berechnen');

beforeEach(() => {
    rechnung = () => antwort({ bearbeitungszeitMinuten: 1.6, ruestzeitMinuten: 5 });
    fetcher = vi.fn().mockImplementation(async () => rechnung());
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (a: Arbeitsgang, onSave = vi.fn().mockResolvedValue(undefined), onClose = vi.fn()) => {
    render(<ArbeitsgangFormelModal arbeitsgang={a} isOpen onClose={onClose} onSave={onSave} />);
    return { onSave, onClose };
};

it('zeigt die gespeicherte Formel mit ihren Feldern und rechnet sofort eine Testrechnung', async () => {
    zeige(mitFormel());
    expect(screen.getByRole('textbox', { name: 'Name von Feld 1' })).toHaveValue('D');
    expect(screen.getByRole('textbox', { name: 'Bezeichnung von Feld 2' })).toHaveValue('Bohrtiefe');
    expect(screen.getByRole('textbox', { name: 'Standardwert von Feld 1' })).toHaveValue('8');
    expect(screen.getByRole('textbox', { name: 'Formel für die Bearbeitungszeit (Minuten je Einheit)' })).toHaveValue('D*Tiefe/100');

    expect(await screen.findByTestId('formel-ergebnis-test')).toHaveTextContent('Ergebnis: 1,6 min je Einheit, 5 min rüsten');
    const body = JSON.parse(berechnungen()[0][1].body as string);
    expect(body).toMatchObject({ bearbeitungFormel: 'D*Tiefe/100', ruestFormel: '5', werte: {} });
    expect(body.felder[0]).toEqual({ name: 'D', bezeichnung: 'Bohrerdurchmesser', einheit: 'mm', standard: 8 });
});

it('Testwerte gehen als Zahlen an die Rechnung', async () => {
    const user = userEvent.setup();
    zeige(mitFormel());
    await screen.findByTestId('formel-ergebnis-test');
    await user.type(screen.getByRole('textbox', { name: 'Testwert Bohrerdurchmesser' }), '35');
    await waitFor(() => expect(berechnungen().some(([, i]) => JSON.parse(i.body as string).werte.d === 35)).toBe(true));
});

it('ein Fehler der Formel steht im Klartext da und sperrt nichts', async () => {
    rechnung = () => antwort({ message: 'Das Feld „V9“ gibt es nicht. Erlaubt sind nur die angelegten Eingabefelder.' }, false, 400);
    zeige(mitFormel());
    expect(await screen.findByTestId('formel-fehler')).toHaveTextContent('Das Feld „V9“ gibt es nicht');
    expect(screen.queryByTestId('formel-ergebnis-test')).not.toBeInTheDocument();
});

it('Felder lassen sich hinzufügen (höchstens 8) und entfernen', async () => {
    const user = userEvent.setup();
    zeige(gang());
    expect(screen.queryByTestId('formel-feld-0')).not.toBeInTheDocument();
    for (let i = 0; i < 9; i++) await user.click(screen.getByRole('button', { name: /Feld hinzufügen/ }));
    expect(screen.getAllByTestId(/^formel-feld-/)).toHaveLength(8);
    expect(screen.getByRole('button', { name: /Feld hinzufügen/ })).toBeDisabled();
    expect(screen.getByRole('textbox', { name: 'Name von Feld 8' })).toHaveValue('V8');

    await user.click(screen.getByRole('button', { name: 'Feld 1 entfernen' }));
    expect(screen.getAllByTestId(/^formel-feld-/)).toHaveLength(7);
});

it('Speichern schickt Felder und Formeln; ohne Bearbeitungsformel ist es gesperrt', async () => {
    const user = userEvent.setup();
    const { onSave, onClose } = zeige(gang());
    expect(screen.getByRole('button', { name: /Speichern/ })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: /Feld hinzufügen/ }));
    await user.type(screen.getByRole('textbox', { name: 'Bezeichnung von Feld 1' }), 'Länge');
    await user.type(screen.getByRole('textbox', { name: 'Standardwert von Feld 1' }), '2,5');
    await user.type(screen.getByRole('textbox', { name: 'Formel für die Bearbeitungszeit (Minuten je Einheit)' }), 'V1*4');
    await user.click(screen.getByRole('button', { name: /Speichern/ }));

    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onSave).toHaveBeenCalledWith(6, { felder: [{ name: 'V1', bezeichnung: 'Länge', einheit: '', standard: 2.5 }], bearbeitungFormel: 'V1*4', ruestFormel: '' });
    expect(onClose).toHaveBeenCalled();
});

it('ein Fehler beim Speichern bleibt im Dialog stehen', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockRejectedValue(new Error('Der Feldname „1x“ ist ungültig.'));
    const { onClose } = zeige(mitFormel(), onSave);
    await user.click(screen.getByRole('button', { name: /Speichern/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Der Feldname „1x“ ist ungültig.');
    expect(onClose).not.toHaveBeenCalled();
});

it('„Formel entfernen“ gibt es nur bei einem Arbeitsgang mit Formel', () => {
    zeige(gang());
    expect(screen.queryByRole('button', { name: /Formel entfernen/ })).not.toBeInTheDocument();
});

it('„Formel entfernen“ schickt leere Angaben', async () => {
    const user = userEvent.setup();
    const { onSave } = zeige(mitFormel());
    await user.click(await screen.findByRole('button', { name: /Formel entfernen/ }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith(6, { felder: [], bearbeitungFormel: '', ruestFormel: '' }));
});
