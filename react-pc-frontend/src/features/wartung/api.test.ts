import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    aendereKopf,
    bewertePruefpunkt,
    ladeObjekte,
    legeProtokollAn,
    sucheProtokolle,
    unterschreibe,
    WartungFehler,
    wechsleStatus,
} from './api';

function antwort(status: number, body: unknown): Response {
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

afterEach(() => { vi.unstubAllGlobals(); });

describe('Fehlermeldungen', () => {
    it('gibt die Meldung des Servers weiter', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort(409, { status: 409, message: 'Es sind noch 3 Prüfpunkte nicht bewertet.' })));
        await expect(wechsleStatus(1, 'ABGESCHLOSSEN')).rejects.toMatchObject({ message: 'Es sind noch 3 Prüfpunkte nicht bewertet.', status: 409 });
    });

    it('nimmt den eigenen Text, wenn der Server keine Meldung schickt', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort(500, {})));
        await expect(wechsleStatus(1, 'ABGESCHLOSSEN')).rejects.toMatchObject({ message: 'Der Status konnte nicht geändert werden.', status: 500 });
    });

    it('kommt mit einer Antwort ohne JSON zurecht', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 502, json: async () => { throw new Error('kein json'); } }));
        await expect(ladeObjekte(1)).rejects.toMatchObject({ message: 'Die Objekte konnten nicht geladen werden.' });
    });

    it('meldet einen nicht erreichbaren Server verständlich', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
        const fehler = await ladeObjekte(1).catch((e) => e);
        expect(fehler).toBeInstanceOf(WartungFehler);
        expect(fehler.message).toMatch(/nicht erreichbar/);
        expect(fehler.status).toBe(0);
    });

    it('zeigt keinen Fehlertext aus anderen Feldern als message, reason oder error', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort(400, { detail: 'interne Details', stack: 'at …' })));
        const fehler = await ladeObjekte(1).catch((e) => e);
        expect(fehler.message).not.toMatch(/interne Details/);
    });
});

describe('Anfragen', () => {
    it('ruft die Liste mit Filtern auf und lässt leere Filter weg', async () => {
        const fetcher = vi.fn().mockResolvedValue(antwort(200, { content: [] }));
        vi.stubGlobal('fetch', fetcher);
        await sucheProtokolle({ kundeId: 7, status: 'OFFEN', suche: '  Werk Nord ', seite: 2, groesse: 10 });
        const url = new URL(fetcher.mock.calls[0][0], 'http://x');
        expect(url.pathname).toBe('/api/wartung/protokolle');
        expect(url.searchParams.get('kundeId')).toBe('7');
        expect(url.searchParams.get('status')).toBe('OFFEN');
        expect(url.searchParams.get('suche')).toBe('Werk Nord');
        expect(url.searchParams.get('seite')).toBe('2');
        expect(url.searchParams.get('groesse')).toBe('10');
        expect(url.searchParams.has('datumVon')).toBe(false);
        expect(url.searchParams.has('mitArchiv')).toBe(false);
    });

    it('verpackt Sonderzeichen im Suchtext, statt sie in die Adresse zu schreiben', async () => {
        const fetcher = vi.fn().mockResolvedValue(antwort(200, { content: [] }));
        vi.stubGlobal('fetch', fetcher);
        await sucheProtokolle({ suche: "a&b=c#d' OR '1'='1" });
        const url = new URL(fetcher.mock.calls[0][0], 'http://x');
        expect(url.searchParams.get('suche')).toBe("a&b=c#d' OR '1'='1");
        expect(url.searchParams.has('b')).toBe(false);
    });

    it('sendet bei GET keinen Body und keinen Content-Type', async () => {
        const fetcher = vi.fn().mockResolvedValue(antwort(200, []));
        vi.stubGlobal('fetch', fetcher);
        await ladeObjekte(5, true);
        expect(fetcher.mock.calls[0][0]).toBe('/api/wartung/kunden/5/objekte?inaktive=true');
        expect(fetcher.mock.calls[0][1].body).toBeUndefined();
        expect(fetcher.mock.calls[0][1].headers).toBeUndefined();
    });

    it('wandelt leere Texte beim Anlegen in null und schneidet Leerzeichen ab', async () => {
        const fetcher = vi.fn().mockResolvedValue(antwort(201, { id: 1 }));
        vi.stubGlobal('fetch', fetcher);
        await legeProtokollAn({
            kundeId: 1, objektId: null, tuerId: 4, technikerId: null, titel: '   ', datum: '2026-05-01',
            intervallMonate: null, module: [{ modulId: 1, untertypSchluessel: 'einflg' }], bemerkungen: ' ok ',
        });
        const body = JSON.parse(fetcher.mock.calls[0][1].body);
        expect(fetcher.mock.calls[0][1].method).toBe('POST');
        expect(body.titel).toBeNull();
        expect(body.bemerkungen).toBe('ok');
        expect(body.intervallMonate).toBeNull();
        expect(body.module).toEqual([{ modulId: 1, untertypSchluessel: 'einflg' }]);
    });

    it('sendet beim Bewerten Ergebnis und Bemerkung, und null für "zurücksetzen"', async () => {
        const fetcher = vi.fn().mockResolvedValue(antwort(200, {}));
        vi.stubGlobal('fetch', fetcher);
        await bewertePruefpunkt(3, 9, 'NIO', 'Dichtung gerissen');
        await bewertePruefpunkt(3, 9, null, '');
        expect(fetcher.mock.calls[0][0]).toBe('/api/wartung/protokolle/3/pruefpunkte/9');
        expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ ergebnis: 'NIO', bemerkung: 'Dichtung gerissen' });
        expect(JSON.parse(fetcher.mock.calls[1][1].body)).toEqual({ ergebnis: null, bemerkung: null });
    });

    it('sendet beim Ändern des Kopfes immer alle Felder', async () => {
        const fetcher = vi.fn().mockResolvedValue(antwort(200, {}));
        vi.stubGlobal('fetch', fetcher);
        await aendereKopf(3, {
            titel: '', protokollTyp: ' Prüfprotokoll ', datum: '2026-05-01', intervallMonate: 6,
            technikerId: null, projektId: 12, bemerkungen: '', ansprechpartnerName: '',
        });
        expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({
            titel: null, protokollTyp: 'Prüfprotokoll', datum: '2026-05-01', intervallMonate: 6,
            technikerId: null, projektId: 12, bemerkungen: null, ansprechpartnerName: null,
        });
    });

    it('verpackt Ids in der Adresse, damit nichts eingeschleust werden kann', async () => {
        const fetcher = vi.fn().mockResolvedValue(antwort(200, {}));
        vi.stubGlobal('fetch', fetcher);
        await unterschreibe(Number('1/../2'), 'KUNDE', null, '');
        expect(fetcher.mock.calls[0][0]).toBe('/api/wartung/protokolle/NaN/unterschrift');
    });
});
