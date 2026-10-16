/** Test-Box (/api/testbox, nur Administratoren): Rückfragen aus Gitea beantworten. */
export type Einordnung = 'FUNKTIONIERT' | 'PROBLEM' | 'WUNSCH' | 'ENTSCHEIDUNG' | 'ANTWORT';

export interface Einstellung { url: string; repo: string; tokenGesetzt: boolean }
export interface Punkt { nummer: number; titel: string; url: string; labels: string[]; frage: string; wartet: boolean }
export interface Vorschlag { einordnung: Einordnung; kommentar: string; ki: boolean; hinweis: string | null }

async function anfrage<T>(url: string, fallback: string, init?: RequestInit): Promise<T> {
    let res: Response;
    try {
        res = await fetch(url, init && { headers: { 'Content-Type': 'application/json' }, ...init });
    } catch {
        throw new Error('Der Server ist nicht erreichbar. Bitte später noch einmal versuchen.');
    }
    if (!res.ok) {
        let meldung = fallback;
        try {
            const body = await res.json() as { message?: string };
            if (body?.message) meldung = body.message;
        } catch { /* keine Meldung vom Server */ }
        throw new Error(meldung);
    }
    return res.json() as Promise<T>;
}

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const ladeEinstellung = () => anfrage<Einstellung>('/api/testbox/einstellung', 'Die Einstellung konnte nicht geladen werden.');
/** {@code token} undefined = unverändert, leer = entfernen. */
export const speichereEinstellung = (e: { url: string; repo: string; token?: string }) =>
    anfrage<Einstellung>('/api/testbox/einstellung', 'Die Einstellung konnte nicht gespeichert werden.', json('PUT', e));
export const ladePunkte = () => anfrage<Punkt[]>('/api/testbox/punkte', 'Die offenen Punkte konnten nicht geladen werden.');
export const bereiteAuf = (nummer: number, antwort: string, einordnung: Einordnung) =>
    anfrage<Vorschlag>('/api/testbox/aufbereiten', 'Die Antwort konnte nicht aufbereitet werden.', json('POST', { nummer, antwort, einordnung }));
export const sende = (nummer: number, einordnung: Einordnung, kommentar: string, original: string) =>
    anfrage<{ gesendet: boolean }>('/api/testbox/senden', 'Die Antwort konnte nicht gesendet werden.', json('POST', { nummer, einordnung, kommentar, original }));
