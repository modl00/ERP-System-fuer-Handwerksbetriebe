import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AnsprechpartnerBereich } from './AnsprechpartnerBereich';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import type { Ansprechpartner } from './types';

const person = (id: number, name: string, extra: Partial<Ansprechpartner> = {}): Ansprechpartner => ({
    id, name, funktion: null, telefon: null, mobil: null, email: null, notizen: null, haupt: false, ...extra,
});

const istGet = (init?: RequestInit) => !init || !init.method || init.method === 'GET';

function json(status: number, body: unknown = {}): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

interface Stand { liste: Ansprechpartner[]; aufrufe: { methode: string; url: string; body: unknown }[] }

function stub(start: Ansprechpartner[], eigene?: (url: string, init: RequestInit | undefined, stand: Stand) => Response | undefined): Stand {
    const stand: Stand = { liste: start, aufrufe: [] };
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        stand.aufrufe.push({ methode, url, body: init?.body ? JSON.parse(init.body as string) : undefined });
        const e = eigene?.(url, init, stand);
        if (e) return e;
        if (url === '/api/kunden/3/ansprechpartner' && methode === 'GET') return json(200, stand.liste);
        if (url === '/api/kunden/3/ansprechpartner' && methode === 'POST') {
            const body = JSON.parse(init!.body as string);
            const neu = person(50, body.name, { ...body, haupt: stand.liste.length === 0 || body.haupt });
            stand.liste = [...stand.liste.map((a) => (neu.haupt ? { ...a, haupt: false } : a)), neu];
            return json(201, neu);
        }
        return json(404, { message: 'nicht gefunden: ' + url });
    }));
    return stand;
}

const zeige = (onAnzahl?: (n: number) => void) => render(
    <ToastProvider><ConfirmProvider><AnsprechpartnerBereich kundeId={3} onAnzahl={onAnzahl} /></ConfirmProvider></ToastProvider>,
);

beforeEach(() => undefined);
afterEach(() => { vi.unstubAllGlobals(); });

describe('AnsprechpartnerBereich', () => {
    it('zeigt die Ansprechpartner mit Hauptansprechpartner, Telefon, Mobil und E-Mail als Links und meldet die Anzahl', async () => {
        stub([
            person(1, 'Hans Beispiel', { funktion: 'Hausmeister', telefon: '0931 123456', mobil: '0171 1234567', email: 'hans@example.org', notizen: 'nur vormittags', haupt: true }),
            person(2, 'Erika Muster'),
        ]);
        const onAnzahl = vi.fn();
        zeige(onAnzahl);

        const karte = await screen.findByTestId('ansprechpartner-1');
        expect(karte).toHaveTextContent('Hans Beispiel');
        expect(karte).toHaveTextContent('Hauptansprechpartner');
        expect(karte).toHaveTextContent('Hausmeister');
        expect(karte).toHaveTextContent('nur vormittags');
        expect(within(karte).getByRole('link', { name: /0931 123456/ })).toHaveAttribute('href', 'tel:0931123456');
        expect(within(karte).getByRole('link', { name: /0171 1234567/ })).toHaveAttribute('href', 'tel:01711234567');
        expect(within(karte).getByRole('link', { name: /hans@example.org/ })).toHaveAttribute('href', 'mailto:hans@example.org');
        expect(within(karte).getByText('Hauptansprechpartner')).toBeVisible();
        expect(within(screen.getByTestId('ansprechpartner-2')).queryByText('Hauptansprechpartner')).toBeNull();
        expect(onAnzahl).toHaveBeenLastCalledWith(2);
    });

    it('sagt bei einem Kunden ohne Ansprechpartner, dass noch keiner eingetragen ist', async () => {
        stub([]);
        zeige();
        expect(await screen.findByText('Für diesen Kunden ist noch kein Ansprechpartner eingetragen.')).toBeVisible();
    });

    it('zeigt beim Laden eine Wartefläche und bei einem Fehler die Meldung mit neuem Versuch', async () => {
        let aufrufe = 0;
        stub([person(1, 'Hans Beispiel', { haupt: true })], (url, init) => {
            if (url === '/api/kunden/3/ansprechpartner' && istGet(init)) return ++aufrufe === 1 ? json(500, { message: 'Die Datenbank antwortet nicht.' }) : undefined;
            return undefined;
        });
        zeige();

        expect(await screen.findByRole('alert')).toHaveTextContent('Die Datenbank antwortet nicht.');
        await userEvent.click(screen.getByRole('button', { name: 'Noch einmal versuchen' }));
        expect(await screen.findByTestId('ansprechpartner-1')).toBeVisible();
    });

    it('legt den ersten Ansprechpartner an: Er wird automatisch zum Hauptansprechpartner', async () => {
        const stand = stub([]);
        zeige();
        await screen.findByText(/noch kein Ansprechpartner/);
        await userEvent.click(screen.getByRole('button', { name: /Ansprechpartner hinzufügen/ }));

        const dialog = await screen.findByRole('dialog');
        const haupt = within(dialog).getByRole('checkbox', { name: /Hauptansprechpartner/ });
        expect(haupt).toBeChecked();
        expect(haupt).toBeDisabled();
        await userEvent.type(within(dialog).getByLabelText('Name'), 'Hans Beispiel');
        await userEvent.type(within(dialog).getByLabelText('Funktion'), 'Hausmeister');
        await userEvent.type(within(dialog).getByLabelText('E-Mail'), 'hans@example.org');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByTestId('ansprechpartner-50')).toHaveTextContent('Hans Beispiel');
        expect(screen.queryByRole('dialog')).toBeNull();
        const post = stand.aufrufe.find((a) => a.methode === 'POST')!;
        expect(post.body).toMatchObject({ name: 'Hans Beispiel', funktion: 'Hausmeister', email: 'hans@example.org', telefon: null, mobil: null, notizen: null });
    });

    it('verlangt einen Namen und eine gültige E-Mail-Adresse und schickt vorher nichts ab', async () => {
        const stand = stub([person(1, 'Hans Beispiel', { haupt: true })]);
        zeige();
        await screen.findByTestId('ansprechpartner-1');
        await userEvent.click(screen.getByRole('button', { name: /Ansprechpartner hinzufügen/ }));
        const dialog = await screen.findByRole('dialog');

        await userEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        expect(await within(dialog).findByText('Bitte einen Namen eintragen.')).toBeVisible();

        await userEvent.type(within(dialog).getByLabelText('Name'), 'Erika Muster');
        await userEvent.type(within(dialog).getByLabelText('E-Mail'), 'keine-adresse');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        expect(await within(dialog).findByText(/gültige E-Mail-Adresse/)).toBeVisible();
        expect(stand.aufrufe.some((a) => a.methode === 'POST')).toBe(false);
    });

    it('bearbeitet einen Ansprechpartner mit seinen bisherigen Werten und speichert die Änderung', async () => {
        const stand = stub([person(1, 'Hans Beispiel', { funktion: 'Hausmeister', telefon: '0931 1', haupt: true })], (url, init) => {
            if (url === '/api/kunden/3/ansprechpartner/1' && init?.method === 'PUT') return json(200, person(1, 'Hans Neu', { haupt: true }));
            return undefined;
        });
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Hans Beispiel bearbeiten' }));

        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByLabelText('Name')).toHaveValue('Hans Beispiel');
        expect(within(dialog).getByLabelText('Funktion')).toHaveValue('Hausmeister');
        expect(within(dialog).getByRole('checkbox', { name: /Hauptansprechpartner/ })).toBeDisabled();
        await userEvent.clear(within(dialog).getByLabelText('Name'));
        await userEvent.type(within(dialog).getByLabelText('Name'), 'Hans Neu');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
        const put = stand.aufrufe.find((a) => a.methode === 'PUT')!;
        expect(put.body).toMatchObject({ name: 'Hans Neu', funktion: 'Hausmeister' });
    });

    it('macht einen anderen zum Hauptansprechpartner und lädt die Liste neu', async () => {
        let geladen = 0;
        const stand = stub([person(1, 'Hans Beispiel', { haupt: true }), person(2, 'Erika Muster')], (url, init) => {
            if (url === '/api/kunden/3/ansprechpartner' && istGet(init)) {
                geladen += 1;
                return json(200, geladen === 1 ? [person(1, 'Hans Beispiel', { haupt: true }), person(2, 'Erika Muster')]
                    : [person(2, 'Erika Muster', { haupt: true }), person(1, 'Hans Beispiel')]);
            }
            if (url === '/api/kunden/3/ansprechpartner/2/haupt' && init?.method === 'PUT') return json(200, person(2, 'Erika Muster', { haupt: true }));
            return undefined;
        });
        zeige();
        expect(screen.queryByRole('button', { name: 'Hans Beispiel zum Hauptansprechpartner machen' })).toBeNull();
        await userEvent.click(await screen.findByRole('button', { name: 'Erika Muster zum Hauptansprechpartner machen' }));

        await waitFor(() => expect(within(screen.getByTestId('ansprechpartner-2')).getByText('Hauptansprechpartner')).toBeVisible());
        expect(within(screen.getByTestId('ansprechpartner-1')).queryByText('Hauptansprechpartner')).toBeNull();
        expect(stand.aufrufe.some((a) => a.methode === 'PUT' && a.url.endsWith('/2/haupt'))).toBe(true);
    });

    it('löscht erst nach Rückfrage und nennt, dass dann der nächste Hauptansprechpartner wird', async () => {
        let geloescht = false;
        const stand = stub([person(1, 'Hans Beispiel', { haupt: true }), person(2, 'Erika Muster')], (url, init) => {
            if (url === '/api/kunden/3/ansprechpartner/1' && init?.method === 'DELETE') { geloescht = true; return json(204); }
            if (url === '/api/kunden/3/ansprechpartner' && istGet(init)) return json(200, geloescht ? [person(2, 'Erika Muster', { haupt: true })] : [person(1, 'Hans Beispiel', { haupt: true }), person(2, 'Erika Muster')]);
            return undefined;
        });
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Hans Beispiel löschen' }));
        expect(await screen.findByText(/nächste Ansprechpartner wird dann zum Hauptansprechpartner/)).toBeVisible();
        expect(stand.aufrufe.some((a) => a.methode === 'DELETE')).toBe(false);
        await userEvent.click(screen.getByRole('button', { name: 'Löschen' }));

        await waitFor(() => expect(screen.queryByTestId('ansprechpartner-1')).toBeNull());
        expect(within(screen.getByTestId('ansprechpartner-2')).getByText('Hauptansprechpartner')).toBeVisible();
    });

    it('löscht nichts, wenn die Rückfrage abgebrochen wird', async () => {
        const stand = stub([person(1, 'Hans Beispiel', { haupt: true })]);
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Hans Beispiel löschen' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }));

        expect(screen.getByTestId('ansprechpartner-1')).toBeVisible();
        expect(stand.aufrufe.some((a) => a.methode === 'DELETE')).toBe(false);
    });

    it('zeigt die Meldung des Servers, wenn das Speichern scheitert, und lässt den Dialog offen', async () => {
        stub([person(1, 'Hans Beispiel', { haupt: true })], (url, init) => {
            if (init?.method === 'POST') return json(409, { message: 'Pro Kunde sind höchstens 50 Ansprechpartner möglich.' });
            return undefined;
        });
        zeige();
        await screen.findByTestId('ansprechpartner-1');
        await userEvent.click(screen.getByRole('button', { name: /Ansprechpartner hinzufügen/ }));
        const dialog = await screen.findByRole('dialog');
        await userEvent.type(within(dialog).getByLabelText('Name'), 'Eine zu viel');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText('Pro Kunde sind höchstens 50 Ansprechpartner möglich.')).toBeVisible();
        expect(screen.getByRole('dialog')).toBeVisible();
    });
});
