/** Feldeinsatz in der App (Wartung): Zugang über den Zugangslink im Kopf der Anfrage, Rechte und Modulschalter prüft der Server bei jedem Aufruf. */

export type Ergebnis = 'IO' | 'NIO' | 'NA'
export type Status = 'OFFEN' | 'IN_BEARBEITUNG' | 'ABGESCHLOSSEN' | 'ARCHIVIERT'

export interface AnlageStammdaten {
    id: number
    bezeichnung: string
    notizen: string | null
    intervallMonate: number | null
    naechsteFaelligkeit: string | null
    aktiv: boolean
    kunde: { id: number; name: string } | null
    objekt: {
        id: number
        bezeichnung: string
        strasse: string | null
        plz: string | null
        ort: string | null
        ansprechpartnerName: string | null
        ansprechpartnerTelefon: string | null
    } | null
    module: { modulId: number; name: string; bauart: string | null; bauartSchluessel: string | null }[]
    offenerFeldeinsatzId: number | null
}

export interface KatalogModul {
    id: number
    name: string
    icon: string | null
    untertypen: { schluessel: string; label: string }[]
}

export interface ProjektWartung {
    id: number
    bezeichnung: string
    objektBezeichnung: string | null
    kundeName: string | null
    module: string | null
    naechsteFaelligkeit: string | null
    letztePruefungDatum: string | null
    anzahlPruefungen: number
    offenerFeldeinsatzId: number | null
    offenerFeldeinsatzNummer: string | null
}

export interface Pruefpunkt { id: number; abschnittId: number; label: string; ergebnis: Ergebnis | null; bemerkung: string | null; vorherigesErgebnis: Ergebnis | null; sortierung: number }
export interface Datenfeld { id: number; abschnittId: number; label: string; feldtyp: 'TEXT' | 'JA_NEIN' | 'AUSWAHL' | 'JAHR'; optionen: string[] | null; wert: string | null; sortierung: number }
export interface Abschnitt { id: number; bereich: string; label: string; sortierung: number }
export interface Unterschrift { bild: string | null; name: string | null }
export interface Statistik { gesamt: number; bewertet: number; inOrdnung: number; nichtInOrdnung: number; nichtAnwendbar: number }

export interface Protokoll {
    id: number
    nummer: string
    kunde: { id: number; name: string } | null
    objekt: { id: number; name: string } | null
    tuer: { id: number; name: string } | null
    titel: string | null
    datum: string
    status: Status
    bearbeitbar: boolean
    moeglicheStatus: Status[]
    unterschriftTechniker: Unterschrift | null
    unterschriftKunde: Unterschrift | null
    abschnitte: Abschnitt[]
    pruefpunkte: Pruefpunkt[]
    datenfelder: Datenfeld[]
    statistik: Statistik
}

export interface Aenderung { status: Status; statistik: Statistik; pruefpunkt: Pruefpunkt | null; datenfeld: Datenfeld | null }

export class WartungFehler extends Error {
    status: number
    constructor(message: string, status: number) { super(message); this.status = status }
}

const BASIS = '/api/zeiterfassung/wartung'

async function aufruf<T>(token: string, pfad: string, methode = 'GET', body?: unknown, fallback = 'Das hat nicht geklappt.'): Promise<T> {
    let res: Response
    try {
        res = await fetch(`${BASIS}${pfad}`, {
            method: methode,
            headers: { 'X-Zugang': token, ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
            body: body === undefined ? undefined : JSON.stringify(body),
        })
    } catch {
        throw new WartungFehler('Keine Verbindung. Die Änderung ist noch nicht gespeichert.', 0)
    }
    if (!res.ok) {
        let text = fallback
        try {
            const d = await res.json() as { message?: string }
            if (d?.message) text = d.message
        } catch { /* kein JSON */ }
        throw new WartungFehler(text, res.status)
    }
    return (res.status === 204 ? undefined : await res.json()) as T
}

export const ladeAnlagen = (token: string, projektId: number | string) => aufruf<ProjektWartung[]>(token, `/projekte/${projektId}/anlagen`, 'GET', undefined, 'Die Wartungen konnten nicht geladen werden.')
export const ladeAnlage = (token: string, id: number) => aufruf<AnlageStammdaten>(token, `/anlagen/${id}`, 'GET', undefined, 'Die Anlage konnte nicht geladen werden.')
export const speichereAnlage = (token: string, id: number, daten: { bezeichnung: string; notizen: string | null; intervallMonate: number | null }) =>
    aufruf<AnlageStammdaten>(token, `/anlagen/${id}`, 'PATCH', daten, 'Die Anlage konnte nicht gespeichert werden.')
export const ladeKatalogModule = (token: string) => aufruf<KatalogModul[]>(token, '/module', 'GET', undefined, 'Die Module konnten nicht geladen werden.')
export const speichereAnlageModule = (token: string, id: number, module: { modulId: number; untertypSchluessel: string | null }[]) =>
    aufruf<AnlageStammdaten>(token, `/anlagen/${id}/module`, 'PATCH', { module }, 'Die Module konnten nicht gespeichert werden.')
export const speichereObjekt = (token: string, objektId: number, daten: { bezeichnung: string; strasse: string | null; plz: string | null; ort: string | null; ansprechpartnerName: string | null; ansprechpartnerTelefon: string | null }) =>
    aufruf<unknown>(token, `/objekte/${objektId}`, 'PATCH', daten, 'Der Standort konnte nicht gespeichert werden.')
export const starteFeldeinsatz = (token: string, anlageId: number) => aufruf<Protokoll>(token, `/anlagen/${anlageId}/feldeinsatz`, 'POST', undefined, 'Der Feldeinsatz konnte nicht gestartet werden.')
export const ladeProtokoll = (token: string, id: number | string) => aufruf<Protokoll>(token, `/protokolle/${id}`, 'GET', undefined, 'Der Feldeinsatz konnte nicht geladen werden.')
export const bewertePruefpunkt = (token: string, id: number, pruefpunktId: number, ergebnis: Ergebnis | null, bemerkung: string | null) =>
    aufruf<Aenderung>(token, `/protokolle/${id}/pruefpunkte/${pruefpunktId}`, 'PUT', { ergebnis, bemerkung }, 'Die Bewertung konnte nicht gespeichert werden.')
export const setzeDatenfeld = (token: string, id: number, datenfeldId: number, wert: string) =>
    aufruf<Aenderung>(token, `/protokolle/${id}/datenfelder/${datenfeldId}`, 'PUT', { wert }, 'Der Wert konnte nicht gespeichert werden.')
export const unterschreibe = (token: string, id: number, rolle: 'TECHNIKER' | 'KUNDE', bild: string | null, name: string) =>
    aufruf<Protokoll>(token, `/protokolle/${id}/unterschrift`, 'PUT', { rolle, bild, name }, 'Die Unterschrift konnte nicht gespeichert werden.')
export const wechsleStatus = (token: string, id: number, status: Status) =>
    aufruf<Protokoll>(token, `/protokolle/${id}/status`, 'PUT', { status }, 'Der Status konnte nicht geändert werden.')

/** Das PDF als Blob-Adresse für die Vorschau. */
export async function ladePdf(token: string, id: number): Promise<string> {
    let res: Response
    try {
        res = await fetch(`${BASIS}/protokolle/${id}/pdf`, { headers: { 'X-Zugang': token } })
    } catch {
        throw new WartungFehler('Keine Verbindung.', 0)
    }
    if (!res.ok) throw new WartungFehler('Das PDF konnte nicht erstellt werden.', res.status)
    return URL.createObjectURL(await res.blob())
}
