import { parseErrorMessage } from '../../components/settings/settingsApi';

/** Ein persönliches Postfach (Issue #272). Das Passwort kommt nie vom Server zurück, nur ob eines gesetzt ist. */
export interface Postfach {
    id: number;
    bezeichnung: string;
    emailAdresse: string;
    imapHost: string;
    imapPort: number;
    smtpHost: string | null;
    smtpPort: number | null;
    benutzername: string;
    passwortGesetzt: boolean;
    aktiv: boolean;
}

export interface PostfachEingabe {
    bezeichnung: string;
    emailAdresse: string;
    imapHost: string;
    imapPort: number;
    smtpHost: string;
    smtpPort: number | null;
    benutzername: string;
    /** Leer beim Ändern = das gespeicherte Passwort bleibt. */
    passwort: string;
    aktiv: boolean;
}

export interface Testergebnis { ok: boolean; meldung: string }

const BASIS = '/api/mein/postfaecher';
const JSON_KOPF = { 'Content-Type': 'application/json' };

async function lies<T>(res: Response, fallback: string): Promise<T> {
    if (!res.ok) throw new Error(await parseErrorMessage(res, fallback));
    return res.status === 204 ? (undefined as T) : (res.json() as Promise<T>);
}

const koerper = (e: PostfachEingabe) => JSON.stringify({ ...e, smtpHost: e.smtpHost.trim() === '' ? null : e.smtpHost, smtpPort: e.smtpHost.trim() === '' ? null : e.smtpPort });

export const ladePostfaecher = () => fetch(BASIS).then((r) => lies<Postfach[]>(r, 'Die Postfächer konnten nicht geladen werden.'));
export const legePostfachAn = (e: PostfachEingabe) => fetch(BASIS, { method: 'POST', headers: JSON_KOPF, body: koerper(e) }).then((r) => lies<Postfach>(r, 'Das Postfach konnte nicht angelegt werden.'));
export const aenderePostfach = (id: number, e: PostfachEingabe) => fetch(`${BASIS}/${id}`, { method: 'PUT', headers: JSON_KOPF, body: koerper(e) }).then((r) => lies<Postfach>(r, 'Das Postfach konnte nicht gespeichert werden.'));
export const loeschePostfach = (id: number) => fetch(`${BASIS}/${id}`, { method: 'DELETE' }).then((r) => lies<void>(r, 'Das Postfach konnte nicht gelöscht werden.'));
export const testePostfach = (e: PostfachEingabe, id?: number) =>
    fetch(id == null ? `${BASIS}/test` : `${BASIS}/${id}/test`, { method: 'POST', headers: JSON_KOPF, body: koerper(e) }).then((r) => lies<Testergebnis>(r, 'Der Test ist fehlgeschlagen.'));
