/** Lieferscheine in der App: Zugang über den Zugangslink im Kopf der Anfrage, Rechte prüft der Server bei jedem Aufruf. */

export interface LieferscheinPosition { bezeichnung: string; menge: number; einheit: string | null }

export interface Lieferschein {
    id: number
    nummer: string
    clientId: string | null
    kundeId: number | null
    kundeName: string | null
    projektId: number | null
    projektName: string | null
    empfaengerName: string
    datum: string
    bemerkung: string | null
    positionen: LieferscheinPosition[]
    unterschrieben: boolean
    unterschriftName: string | null
    unterschriebenAm: string | null
    kopieAn: string | null
    kopieGesendetAm: string | null
}

export interface NeuerLieferschein {
    clientId: string
    nummer?: string | null
    projektId: number
    empfaengerName: string
    bemerkung?: string | null
    positionen: LieferscheinPosition[]
    unterschriftBild?: string | null
}

export class LieferscheinFehler extends Error {
    status: number
    constructor(message: string, status: number) { super(message); this.status = status }
}

const BASIS = '/api/zeiterfassung/lieferscheine'

async function aufruf<T>(token: string, pfad: string, methode = 'GET', body?: unknown, fallback = 'Das hat nicht geklappt.'): Promise<T> {
    let res: Response
    try {
        res = await fetch(`${BASIS}${pfad}`, {
            method: methode,
            headers: { 'X-Zugang': token, ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
            body: body === undefined ? undefined : JSON.stringify(body),
        })
    } catch {
        throw new LieferscheinFehler('Keine Verbindung. Bitte später noch einmal versuchen.', 0)
    }
    if (!res.ok) {
        let text = fallback
        try {
            const d = await res.json() as { message?: string }
            if (d?.message) text = d.message
        } catch { /* kein JSON */ }
        throw new LieferscheinFehler(text, res.status)
    }
    return (res.status === 204 ? undefined : await res.json()) as T
}

/** Eine neue Kennung fürs Gerät: Dasselbe Senden zweimal ergibt trotzdem nur einen Lieferschein. */
export function neueKennung(): string {
    try {
        return crypto.randomUUID()
    } catch {
        return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
    }
}

export const ladeLieferscheine = (token: string, projektId: number | string) =>
    aufruf<Lieferschein[]>(token, `/projekt/${projektId}`, 'GET', undefined, 'Die Lieferscheine konnten nicht geladen werden.')
export const legeLieferscheinAn = (token: string, dto: NeuerLieferschein) =>
    aufruf<Lieferschein>(token, '', 'POST', dto, 'Der Lieferschein konnte nicht gespeichert werden.')
export const unterschreibeLieferschein = (token: string, id: number, bild: string, name: string) =>
    aufruf<Lieferschein>(token, `/${id}/unterschrift`, 'PUT', { bild, name }, 'Die Unterschrift konnte nicht gespeichert werden.')
export const loescheLieferschein = (token: string, id: number) =>
    aufruf<void>(token, `/${id}`, 'DELETE', undefined, 'Der Entwurf konnte nicht gelöscht werden.')
export const sendeKopie = (token: string, id: number, an: string) =>
    aufruf<Lieferschein>(token, `/${id}/kopie`, 'POST', { an, bestaetigt: true }, 'Die E-Mail konnte nicht gesendet werden.')

/** Das PDF als Blob-Adresse für die Vorschau. */
export async function ladeLieferscheinPdf(token: string, id: number): Promise<string> {
    let res: Response
    try {
        res = await fetch(`${BASIS}/${id}/pdf`, { headers: { 'X-Zugang': token } })
    } catch {
        throw new LieferscheinFehler('Keine Verbindung.', 0)
    }
    if (!res.ok) throw new LieferscheinFehler('Das PDF konnte nicht erstellt werden.', res.status)
    return URL.createObjectURL(await res.blob())
}

/** Reserviert für dieses Gerät eine Reihe Nummern (auf {@code soll} aufgefüllt) und gibt alle noch freien zurück. */
export const holeNummernbereich = (token: string, soll = 20) =>
    aufruf<{ art: string; nummern: string[] }>(token, `/nummernbereich?soll=${soll}`, 'POST', undefined, 'Der Nummernbereich konnte nicht geholt werden.')
