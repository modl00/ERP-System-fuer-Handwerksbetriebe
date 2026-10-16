import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KiHilfeChat } from './KiHilfeChat';

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
let status: Record<string, unknown>;
let chatAntwort: Record<string, unknown>;
let dokumentAntwort: Record<string, unknown>;
let dokumentOk: boolean;
let bestaetigung: { ok: boolean; status: number; meldung: string };
const aufrufe = (teil: string) => fetcher.mock.calls.filter(([u]) => String(u).includes(teil));

beforeEach(() => {
    localStorage.clear();
    Element.prototype.scrollIntoView = vi.fn();
    status = { bereit: true, anbieter: 'Lokale KI', lokal: true, datenschutz: 'NUR_LOKAL', meldung: null };
    chatAntwort = {
        text: 'Es gibt **12 m** Rundrohr.', vorschlaege: [], genutzteWerkzeuge: ['lagerbestand'],
        quelle: { anbieter: 'Lokale KI (Ollama u. a.)', modell: 'qwen2.5:7b', lokal: true },
    };
    dokumentOk = true;
    dokumentAntwort = { name: 'angebot.pdf', text: 'Angebot 4711 für Max Mustermann', gekuerzt: false, seiten: 1, hinweis: null };
    bestaetigung = { ok: true, status: 200, meldung: 'Artikel angelegt.' };
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/ki-assistent/status') return antwort(status);
        if (url === '/api/ki-assistent/chat') return antwort(chatAntwort);
        if (url === '/api/ki-assistent/dokument/lesen') return antwort(dokumentAntwort, dokumentOk, dokumentOk ? 200 : 403);
        if (url === '/api/ki-assistent/beleg/pruefen') return antwort({ beleg: { dokumentTyp: 'RECHNUNG', dokumentNummer: 'R-1', lieferantName: 'Muster GmbH', betragBrutto: 119 }, lieferantId: 7, lieferantName: 'Muster GmbH', kandidaten: [{ id: 7, name: 'Muster GmbH', aehnlichkeit: 1 }], hinweise: [] });
        if (url.includes('/vorschlaege/') && init?.method === 'POST') {
            return antwort({ ok: bestaetigung.ok, meldung: bestaetigung.meldung }, bestaetigung.ok, bestaetigung.status);
        }
        if (url === '/api/ki-hilfe/chat') return antwort({ reply: 'So geht es im Programm.', sources: [] });
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

async function oeffne() {
    const user = userEvent.setup();
    render(<MemoryRouter><KiHilfeChat /></MemoryRouter>);
    act(() => { window.dispatchEvent(new Event('ki-hilfe-open')); });
    await screen.findByRole('heading', { name: 'KI' });
    return user;
}

const frage = async (user: ReturnType<typeof userEvent.setup>, text: string) => {
    await user.type(screen.getByPlaceholderText('Frage stellen...'), text);
    await user.keyboard('{Enter}');
};

describe('KI-Assistent im Chat', () => {
    it('es gibt nur noch ein Fenster ohne Schalter, und jede Frage geht an die KI (sie wählt selbst Programmwissen oder Daten)', async () => {
        const user = await oeffne();
        expect(screen.queryByRole('button', { name: /Assistent/ })).toBeNull();
        await frage(user, 'Wo ist die Zeiterfassung?');
        expect(await screen.findByText('12 m')).toBeVisible();
        expect(aufrufe('/api/ki-assistent/chat')).toHaveLength(1);
        expect(aufrufe('/api/ki-hilfe/chat')).toHaveLength(0);
    });

    it('ist für die KI noch kein Anbieter eingerichtet, antwortet wie bisher die Programmhilfe', async () => {
        status = { bereit: false, anbieter: 'Lokale KI', lokal: true, datenschutz: 'NUR_LOKAL', meldung: 'Die lokale KI ist noch nicht eingerichtet.' };
        const user = await oeffne();
        await waitFor(() => expect(aufrufe('/api/ki-assistent/status')).toHaveLength(1));
        await screen.findByText('Programmhilfe');
        const hinweis = await screen.findByTestId('ki-nur-hilfe');
        expect(hinweis).toHaveTextContent('noch nicht eingerichtet');
        expect(hinweis).toHaveTextContent('Die lokale KI ist noch nicht eingerichtet.');
        expect(within(hinweis).getByRole('button', { name: 'Zu den KI-Einstellungen' })).toBeVisible();
        await frage(user, 'Wo ist die Zeiterfassung?');
        expect(await screen.findByText('So geht es im Programm.')).toBeVisible();
        expect(aufrufe('/api/ki-assistent/chat')).toHaveLength(0);
        expect(aufrufe('/api/ki-hilfe/chat')).toHaveLength(1);
    });

    it('die Antwort zeigt, wer geantwortet hat und wohin die Daten gingen', async () => {
        const user = await oeffne();
        await frage(user, 'Wie viel Rundrohr haben wir?');

        expect(await screen.findByText('12 m')).toBeVisible();
        expect(aufrufe('/api/ki-hilfe/chat')).toHaveLength(0);
        const body = JSON.parse(aufrufe('/api/ki-assistent/chat')[0][1].body);
        expect(body.messages).toEqual([{ role: 'user', text: 'Wie viel Rundrohr haben wir?' }]);
        expect(screen.getByTestId('ki-quelle')).toHaveTextContent('Lokale KI (Ollama u. a.) · qwen2.5:7b · Daten bleiben im Haus');
    });

    it('schickt mit, auf welcher Seite man gerade ist, damit die KI „Was bedeutet das hier?“ beantworten kann', async () => {
        const user = await oeffne();
        await frage(user, 'Was bedeutet das hier?');
        await screen.findByText('12 m');
        const body = JSON.parse(aufrufe('/api/ki-assistent/chat')[0][1].body);
        expect(body.seite).toMatchObject({ route: '/' });
        expect(body.seite).toHaveProperty('titel');
    });

    it('weist bei einer Cloud-Antwort darauf hin, dass die Daten das Haus verlassen haben', async () => {
        chatAntwort = { ...chatAntwort, quelle: { anbieter: 'Claude (Anthropic)', modell: 'claude-sonnet-5-5', lokal: false } };
        const user = await oeffne();
        await frage(user, 'Hallo');
        expect(await screen.findByTestId('ki-quelle')).toHaveTextContent('Fragen und Daten gingen an einen Cloud-Anbieter');
    });

    it('zeigt die Fehlermeldung des Servers, zum Beispiel wegen des Datenschutzes', async () => {
        fetcher.mockImplementation(async (url: string) => (url === '/api/ki-assistent/chat'
            ? antwort({ error: 'Für „KI-Assistent“ sind nur lokale KI-Anbieter erlaubt (Datenschutz).' }, false, 503) : antwort(status)));
        const user = await oeffne();
        await frage(user, 'Hallo');
        expect(await screen.findByText(/nur lokale KI-Anbieter erlaubt/)).toBeVisible();
    });

    it('ein Vorschlag wird erst nach „Ja, ausführen“ ausgeführt', async () => {
        chatAntwort = { ...chatAntwort, text: 'Bitte bestätigen Sie.', vorschlaege: [{ id: 'v1', werkzeug: 'artikel_anlegen', beschreibung: 'Artikel „Schraube M8“ anlegen', parameter: {} }] };
        const user = await oeffne();
        await frage(user, 'Lege Schraube M8 an');

        const karte = await screen.findByTestId('vorschlag-v1');
        expect(within(karte).getByText('Artikel „Schraube M8“ anlegen')).toBeVisible();
        expect(within(karte).getByText('Es wird nichts geändert, bevor Sie bestätigen.')).toBeVisible();
        expect(aufrufe('/vorschlaege/')).toHaveLength(0);

        await user.click(within(karte).getByRole('button', { name: /Ja, ausführen/ }));

        expect(await within(karte).findByRole('status')).toHaveTextContent('Artikel angelegt.');
        expect(aufrufe('/api/ki-assistent/vorschlaege/v1/bestaetigen')).toHaveLength(1);
        expect(within(karte).queryByRole('button', { name: /Ja, ausführen/ })).toBeNull();
    });

    it('verwerfen ändert nichts und meldet das', async () => {
        chatAntwort = { ...chatAntwort, vorschlaege: [{ id: 'v2', werkzeug: 'artikel_anlegen', beschreibung: 'Artikel „X“ anlegen', parameter: {} }] };
        const user = await oeffne();
        await frage(user, 'Lege X an');

        await user.click(within(await screen.findByTestId('vorschlag-v2')).getByRole('button', { name: /Verwerfen/ }));

        expect(await within(screen.getByTestId('vorschlag-v2')).findByRole('status')).toHaveTextContent('Verworfen. Es wurde nichts geändert.');
        expect(aufrufe('/api/ki-assistent/vorschlaege/v2/verwerfen')).toHaveLength(1);
    });

    it('meldet, wenn der Vorschlag nicht mehr gilt, und lässt kein zweites Bestätigen zu', async () => {
        chatAntwort = { ...chatAntwort, vorschlaege: [{ id: 'v3', werkzeug: 'artikel_anlegen', beschreibung: 'Artikel „Y“ anlegen', parameter: {} }] };
        bestaetigung = { ok: false, status: 409, meldung: 'Diesen Vorschlag gibt es nicht mehr. Er ist abgelaufen oder wurde schon bestätigt oder verworfen.' };
        const user = await oeffne();
        await frage(user, 'Lege Y an');

        await user.click(within(await screen.findByTestId('vorschlag-v3')).getByRole('button', { name: /Ja, ausführen/ }));

        const meldung = await within(screen.getByTestId('vorschlag-v3')).findByRole('status');
        expect(meldung).toHaveTextContent('abgelaufen');
        await waitFor(() => expect(within(screen.getByTestId('vorschlag-v3')).queryByRole('button', { name: /Ja, ausführen/ })).toBeNull());
        expect(aufrufe('/vorschlaege/')).toHaveLength(1);
    });

    describe('Dokument im Chat', () => {
        const datei = (name = 'angebot.pdf') => new File(['inhalt'], name, { type: 'application/pdf' });
        const hineinziehen = async (f: File) => {
            const fenster = (await screen.findByPlaceholderText('Frage stellen...')).closest('.fixed') as HTMLElement;
            const daten = { types: ['Files'], files: [f] };
            act(() => { fireEvent.dragOver(fenster, { dataTransfer: daten }); });
            expect(await screen.findByTestId('ki-drop')).toBeVisible();
            act(() => { fireEvent.drop(fenster, { dataTransfer: daten }); });
        };

        it('ein hineingezogenes Dokument wird gelesen und zeigt die Aktionsknöpfe', async () => {
            await oeffne();
            await hineinziehen(datei());
            const leiste = await screen.findByTestId('ki-anhang');
            expect(within(leiste).getByText('angebot.pdf')).toBeVisible();
            for (const t of ['Zusammenfassen', 'Wichtige Angaben', 'Was ist zu tun?', 'Als Beleg ablegen']) expect(within(leiste).getByRole('button', { name: t })).toBeVisible();
            expect(screen.queryByTestId('ki-drop')).toBeNull();
            expect(aufrufe('/api/ki-assistent/dokument/lesen')).toHaveLength(1);
        });

        it('ein Aktionsknopf stellt dem Dokument die Aufgabe, der gelesene Text geht mit', async () => {
            await oeffne();
            await hineinziehen(datei());
            const user = userEvent.setup();
            await user.click(within(await screen.findByTestId('ki-anhang')).getByRole('button', { name: 'Zusammenfassen' }));
            expect(await screen.findByText('12 m')).toBeVisible();
            const body = JSON.parse(aufrufe('/api/ki-assistent/chat')[0][1].body);
            expect(body.messages[0].text).toBe('Fasse das Dokument kurz zusammen.');
            expect(body.dokument).toEqual({ name: 'angebot.pdf', text: 'Angebot 4711 für Max Mustermann' });
        });

        it('eine eigene Aufgabe im Eingabefeld geht ebenfalls mit dem Dokument', async () => {
            const user = await oeffne();
            await hineinziehen(datei());
            await screen.findByTestId('ki-anhang');
            await frage(user, 'Wann soll geliefert werden?');
            await screen.findByText('12 m');
            const body = JSON.parse(aufrufe('/api/ki-assistent/chat')[0][1].body);
            expect(body.messages[0].text).toBe('Wann soll geliefert werden?');
            expect(body.dokument.name).toBe('angebot.pdf');
        });

        it('„Als Beleg ablegen“ wertet die Datei sofort mit dem gewohnten Beleg-Ablauf aus', async () => {
            await oeffne();
            await hineinziehen(datei());
            const user = userEvent.setup();
            await user.click(within(await screen.findByTestId('ki-anhang')).getByRole('button', { name: 'Als Beleg ablegen' }));
            expect(await screen.findByTestId('ki-beleg-vorschlag')).toBeVisible();
            expect(aufrufe('/api/ki-assistent/beleg/pruefen')).toHaveLength(1);
            expect(aufrufe('/api/lieferanten/')).toHaveLength(0);
        });

        it('ein Bild ohne lesbaren Text bietet nur das Ablegen als Beleg an und nennt den Grund', async () => {
            dokumentAntwort = { name: 'foto.jpg', text: null, gekuerzt: false, seiten: 0, hinweis: 'Diese Datei kann die KI hier noch nicht lesen (nur PDF und Textdateien). Als Beleg ablegen geht trotzdem.' };
            await oeffne();
            await hineinziehen(datei('foto.jpg'));
            const leiste = await screen.findByTestId('ki-anhang');
            expect(within(leiste).getByText(/noch nicht lesen/)).toBeVisible();
            expect(within(leiste).queryByRole('button', { name: 'Zusammenfassen' })).toBeNull();
            expect(within(leiste).getByRole('button', { name: 'Als Beleg ablegen' })).toBeVisible();
        });

        it('zeigt die Ablehnung des Servers und entfernt das Dokument auf Wunsch', async () => {
            dokumentOk = false;
            dokumentAntwort = { message: 'Nach der Einstellung „Wie weit darf die KI gehen?“ darf ein Dokument nicht an diesen KI-Anbieter gehen.' };
            await oeffne();
            await hineinziehen(datei());
            expect(await screen.findByText(/darf ein Dokument nicht an diesen KI-Anbieter/)).toBeVisible();
            expect(screen.queryByTestId('ki-anhang')).toBeNull();

            dokumentOk = true;
            dokumentAntwort = { name: 'angebot.pdf', text: 'Text', gekuerzt: false, seiten: 1, hinweis: null };
            await hineinziehen(datei());
            const user = userEvent.setup();
            await user.click(within(await screen.findByTestId('ki-anhang')).getByRole('button', { name: 'Dokument entfernen' }));
            expect(screen.queryByTestId('ki-anhang')).toBeNull();
        });

        it('ein mit Strg+V eingefügtes Bild wird wie ein hineingezogenes Dokument gelesen und bekommt einen Namen mit Datum', async () => {
            await oeffne();
            const bild = new File(['png'], 'image.png', { type: 'image/png' });
            fireEvent.paste(screen.getByPlaceholderText('Frage stellen...'), { clipboardData: { files: [bild], items: [], types: ['Files'], getData: () => '' } });

            expect(await screen.findByTestId('ki-anhang')).toBeVisible();
            const aufruf = aufrufe('/api/ki-assistent/dokument/lesen');
            expect(aufruf).toHaveLength(1);
            const hochgeladen = ((aufruf[0][1] as RequestInit).body as FormData).get('datei') as File;
            expect(hochgeladen.name).toMatch(/^Bild_\d{4}-\d{2}-\d{2}_\d{4}\.png$/);
            expect(hochgeladen.type).toBe('image/png');
        });

        it('eingefügter Text ohne Bild bleibt normales Einfügen und liest nichts', async () => {
            await oeffne();
            fireEvent.paste(screen.getByPlaceholderText('Frage stellen...'), { clipboardData: { files: [], items: [], types: ['text/plain'], getData: () => 'Hallo' } });
            expect(aufrufe('/api/ki-assistent/dokument/lesen')).toHaveLength(0);
            expect(screen.queryByTestId('ki-anhang')).toBeNull();
        });

        it('über die Büroklammer lässt sich ein Dokument ebenfalls anhängen', async () => {
            await oeffne();
            const user = userEvent.setup();
            await user.upload(screen.getByLabelText('Dokument auswählen'), datei());
            expect(await screen.findByTestId('ki-anhang')).toBeVisible();
        });
    });
});

describe('KI-Chat: E-Mail aus dem E-Mail-Center', () => {
    it('eine auf das Chatfenster gezogene Mail wird angehängt und bietet „Kunde prüfen und Anfrage anlegen“', async () => {
        fetcher.mockImplementation(async (url: string) => {
            if (url === '/api/ki-assistent/status') return antwort(status);
            if (url === '/api/ki-assistent/dokument/email/10') return antwort({ name: 'E-Mail: Anfrage Geländer', text: 'E-Mail-Nr. im Programm: 10\nVon: max@mustermann.example', gekuerzt: false, seiten: 1, hinweis: null });
            if (url === '/api/ki-assistent/chat') return antwort(chatAntwort);
            return antwort({});
        });
        await oeffne();

        act(() => { window.dispatchEvent(new CustomEvent('ki-email-anhaengen', { detail: { emailId: 10 } })); });

        const leiste = await screen.findByTestId('ki-anhang');
        expect(within(leiste).getByText('E-Mail: Anfrage Geländer')).toBeVisible();
        expect(within(leiste).getByTestId('ki-email-anfrage')).toBeVisible();
        expect(within(leiste).queryByRole('button', { name: 'Als Beleg ablegen' })).toBeNull();
    });

    it('der Knopf stellt die Aufgabe mit der E-Mail-Nummer, und die Mail geht als Dokument mit', async () => {
        fetcher.mockImplementation(async (url: string) => {
            if (url === '/api/ki-assistent/status') return antwort(status);
            if (url === '/api/ki-assistent/dokument/email/10') return antwort({ name: 'E-Mail: Anfrage', text: 'E-Mail-Nr. im Programm: 10', gekuerzt: false, seiten: 1, hinweis: null });
            if (url === '/api/ki-assistent/chat') return antwort(chatAntwort);
            return antwort({});
        });
        const user = await oeffne();
        act(() => { window.dispatchEvent(new CustomEvent('ki-email-anhaengen', { detail: { emailId: 10 } })); });

        await user.click(await screen.findByTestId('ki-email-anfrage'));

        await waitFor(() => expect(aufrufe('/api/ki-assistent/chat')).toHaveLength(1));
        const body = JSON.parse((aufrufe('/api/ki-assistent/chat')[0][1] as RequestInit).body as string);
        expect(body.messages.at(-1).text).toContain('E-Mail-Nr. 10');
        expect(body.messages.at(-1).text).toContain('kunden_suchen');
        expect(body.dokument.text).toContain('E-Mail-Nr. im Programm: 10');
    });

    it('meldet, wenn die Mail nicht gelesen werden kann', async () => {
        fetcher.mockImplementation(async (url: string) => (url === '/api/ki-assistent/status' ? antwort(status) : antwort({ message: 'Die E-Mail gibt es nicht mehr.' }, false, 404)));
        await oeffne();
        act(() => { window.dispatchEvent(new CustomEvent('ki-email-anhaengen', { detail: { emailId: 99 } })); });
        expect(await screen.findByText('Die E-Mail gibt es nicht mehr.')).toBeVisible();
    });
});

describe('KI-Chat: Fenster verschieben und in der Größe ändern (#251)', () => {
    const rahmenVon = () => {
        const f = document.querySelector('[data-ki-chat]') as HTMLElement;
        return { x: parseFloat(f.style.left), y: parseFloat(f.style.top), w: parseFloat(f.style.width), h: parseFloat(f.style.height) };
    };

    it('startet unten rechts in der Standardgröße', async () => {
        await oeffne();
        const r = rahmenVon();
        expect(r.w).toBe(420);
        expect(r.h).toBeLessThanOrEqual(600);
        expect(r.x + r.w).toBeLessThanOrEqual(window.innerWidth);
    });

    it('der Kopf verschiebt das Fenster und die Lage wird gemerkt', async () => {
        await oeffne();
        const vorher = rahmenVon();
        const kopf = screen.getByTestId('ki-kopf');

        fireEvent.pointerDown(kopf, { button: 0, clientX: 500, clientY: 300 });
        fireEvent(window, new MouseEvent('pointermove', { clientX: 440, clientY: 250 }));
        fireEvent(window, new MouseEvent('pointerup', {}));

        const nachher = rahmenVon();
        expect(nachher.x).toBe(vorher.x - 60);
        expect(nachher.y).toBe(vorher.y - 50);
        const gemerkt = Object.entries(localStorage).find(([k]) => k.startsWith('ki-hilfe-rahmen'));
        expect(gemerkt && JSON.parse(gemerkt[1])).toMatchObject({ x: nachher.x, y: nachher.y });
    });

    it('die Ecke unten rechts ändert die Größe, aber nicht unter das Minimum', async () => {
        await oeffne();
        const vorher = rahmenVon();
        fireEvent.pointerDown(screen.getByTestId('ki-groesse-rechts'), { button: 0, clientX: 800, clientY: 600 });
        fireEvent(window, new MouseEvent('pointermove', { clientX: 830, clientY: 640 }));
        fireEvent(window, new MouseEvent('pointerup', {}));
        expect(rahmenVon()).toMatchObject({ w: vorher.w + 30 > window.innerWidth ? window.innerWidth : vorher.w + 30 });

        fireEvent.pointerDown(screen.getByTestId('ki-groesse-rechts'), { button: 0, clientX: 800, clientY: 600 });
        fireEvent(window, new MouseEvent('pointermove', { clientX: -5000, clientY: -5000 }));
        fireEvent(window, new MouseEvent('pointerup', {}));
        expect(rahmenVon()).toMatchObject({ w: 320, h: 360 });
    });

    it('ein Klick auf einen Knopf im Kopf verschiebt nichts, ein Doppelklick auf den Kopf stellt zurück', async () => {
        await oeffne();
        const start = rahmenVon();
        const kopf = screen.getByTestId('ki-kopf');
        fireEvent.pointerDown(kopf, { button: 0, clientX: 500, clientY: 300 });
        fireEvent(window, new MouseEvent('pointermove', { clientX: 300, clientY: 100 }));
        fireEvent(window, new MouseEvent('pointerup', {}));
        expect(rahmenVon()).not.toEqual(start);

        fireEvent.doubleClick(kopf);
        expect(rahmenVon()).toEqual(start);
        expect(Object.keys(localStorage).some(k => k.startsWith('ki-hilfe-rahmen'))).toBe(false);
    });
});

describe('KI-Chat: Information aus einer E-Mail notieren', () => {
    it('„Beim Lieferanten notieren“ bittet die KI, die Information als Notiz beim passenden Lieferanten zu speichern', async () => {
        fetcher.mockImplementation(async (url: string) => {
            if (url === '/api/ki-assistent/status') return antwort(status);
            if (url === '/api/ki-assistent/dokument/email/10') return antwort({ name: 'E-Mail: Preisliste', text: 'E-Mail-Nr. im Programm: 10', gekuerzt: false, seiten: 1, hinweis: null });
            if (url === '/api/ki-assistent/chat') return antwort(chatAntwort);
            return antwort({});
        });
        const user = await oeffne();
        act(() => { window.dispatchEvent(new CustomEvent('ki-email-anhaengen', { detail: { emailId: 10 } })); });

        await user.click(await screen.findByTestId('ki-email-notiz'));

        await waitFor(() => expect(aufrufe('/api/ki-assistent/chat')).toHaveLength(1));
        const body = JSON.parse((aufrufe('/api/ki-assistent/chat')[0][1] as RequestInit).body as string);
        expect(body.messages.at(-1).text).toContain('E-Mail-Nr. 10');
        expect(body.messages.at(-1).text).toContain('lieferanten_suchen');
        expect(body.dokument.text).toContain('E-Mail-Nr. im Programm: 10');
    });
});
