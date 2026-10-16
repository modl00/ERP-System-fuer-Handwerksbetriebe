import type { Ansprechpartner, AnsprechpartnerEingabe, KundeObjekt, KundeObjektEingabe } from './types';

/** Fehler mit einer Meldung, die der Nutzer lesen darf (kommt vom Server oder ist unser Fallback). */
export class KundenstammFehler extends Error {
    readonly status: number;

    constructor(message: string, status: number) {
        super(message);
        this.name = 'KundenstammFehler';
        this.status = status;
    }
}

async function fehlerAus(res: Response, fallback: string): Promise<KundenstammFehler> {
    try {
        const body = await res.json();
        const text = body?.message ?? body?.reason ?? body?.error;
        if (typeof text === 'string' && text.trim()) return new KundenstammFehler(text.trim(), res.status);
    } catch {
        // kein oder kaputtes JSON: dann der Fallback
    }
    return new KundenstammFehler(fallback, res.status);
}

async function anfrage<T>(methode: string, pfad: string, fallback: string, body?: unknown): Promise<T> {
    let res: Response;
    try {
        res = await fetch(pfad, {
            method: methode,
            headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw new KundenstammFehler('Der Server ist nicht erreichbar. Bitte die Verbindung prüfen und noch einmal versuchen.', 0);
    }
    if (!res.ok) throw await fehlerAus(res, fallback);
    if (res.status === 204) return undefined as T;
    return (await res.json()) as T;
}

const id = (nummer: number) => encodeURIComponent(String(nummer));
const leerZuNull = (text: string): string | null => (text.trim() === '' ? null : text.trim());

// ── Ansprechpartner ─────────────────────────────────────────────────────────

export const ladeAnsprechpartner = (kundeId: number) =>
    anfrage<Ansprechpartner[]>('GET', `/api/kunden/${id(kundeId)}/ansprechpartner`, 'Die Ansprechpartner konnten nicht geladen werden.');

function ansprechpartnerKoerper(e: AnsprechpartnerEingabe) {
    return {
        name: e.name.trim(),
        funktion: leerZuNull(e.funktion),
        telefon: leerZuNull(e.telefon),
        mobil: leerZuNull(e.mobil),
        email: leerZuNull(e.email),
        notizen: leerZuNull(e.notizen),
        haupt: e.haupt,
    };
}

export const legeAnsprechpartnerAn = (kundeId: number, e: AnsprechpartnerEingabe) =>
    anfrage<Ansprechpartner>('POST', `/api/kunden/${id(kundeId)}/ansprechpartner`,
        'Der Ansprechpartner konnte nicht angelegt werden.', ansprechpartnerKoerper(e));

export const aendereAnsprechpartner = (kundeId: number, ansprechpartnerId: number, e: AnsprechpartnerEingabe) =>
    anfrage<Ansprechpartner>('PUT', `/api/kunden/${id(kundeId)}/ansprechpartner/${id(ansprechpartnerId)}`,
        'Der Ansprechpartner konnte nicht gespeichert werden.', ansprechpartnerKoerper(e));

export const setzeHauptansprechpartner = (kundeId: number, ansprechpartnerId: number) =>
    anfrage<Ansprechpartner>('PUT', `/api/kunden/${id(kundeId)}/ansprechpartner/${id(ansprechpartnerId)}/haupt`,
        'Der Hauptansprechpartner konnte nicht geändert werden.');

export const loescheAnsprechpartner = (kundeId: number, ansprechpartnerId: number) =>
    anfrage<void>('DELETE', `/api/kunden/${id(kundeId)}/ansprechpartner/${id(ansprechpartnerId)}`,
        'Der Ansprechpartner konnte nicht gelöscht werden.');

// ── Objekte ─────────────────────────────────────────────────────────────────

export const ladeKundeObjekte = (kundeId: number, mitInaktiven = false) =>
    anfrage<KundeObjekt[]>('GET', `/api/kunden/${id(kundeId)}/objekte${mitInaktiven ? '?inaktive=true' : ''}`,
        'Die Objekte konnten nicht geladen werden.');

function objektKoerper(e: KundeObjektEingabe) {
    return {
        bezeichnung: e.bezeichnung.trim(),
        strasse: leerZuNull(e.strasse),
        plz: leerZuNull(e.plz),
        ort: leerZuNull(e.ort),
        ansprechpartnerName: leerZuNull(e.ansprechpartnerName),
        ansprechpartnerTelefon: leerZuNull(e.ansprechpartnerTelefon),
        notizen: leerZuNull(e.notizen),
    };
}

export const legeKundeObjektAn = (kundeId: number, e: KundeObjektEingabe) =>
    anfrage<KundeObjekt>('POST', `/api/kunden/${id(kundeId)}/objekte`, 'Das Objekt konnte nicht angelegt werden.', objektKoerper(e));

export const aendereKundeObjekt = (kundeId: number, objektId: number, e: KundeObjektEingabe) =>
    anfrage<KundeObjekt>('PUT', `/api/kunden/${id(kundeId)}/objekte/${id(objektId)}`,
        'Das Objekt konnte nicht gespeichert werden.', objektKoerper(e));

export const setzeKundeObjektAktiv = (kundeId: number, objektId: number, aktiv: boolean) =>
    anfrage<KundeObjekt>('PUT', `/api/kunden/${id(kundeId)}/objekte/${id(objektId)}/aktiv`,
        'Das Objekt konnte nicht geändert werden.', { aktiv });
