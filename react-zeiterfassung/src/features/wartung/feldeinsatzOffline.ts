import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import {
    bewertePruefpunkt, setzeDatenfeld, unterschreibe, wechsleStatus, WartungFehler,
    type Ergebnis, type Protokoll, type Status,
} from './wartungApi'

/**
 * Feldeinsatz ohne Netz (Keller, Dachboden): Das Gerät hält den geöffneten Feldeinsatz und legt jede Änderung zuerst in eine Warteschlange. Bei Verbindung
 * wird sie der Reihe nach abgeglichen. Nichts geht still verloren: Was der Server ablehnt (zum Beispiel weil der Feldeinsatz inzwischen abgeschlossen ist),
 * bleibt mit der Meldung des Servers sichtbar, bis der Mitarbeiter es verwirft. Die Warteschlange gilt je Zugangslink (Mitarbeiter) und wird beim Abmelden geleert.
 */

export type Aenderung =
    | { art: 'PRUEFPUNKT'; zielId: number; ergebnis: Ergebnis | null; bemerkung: string | null }
    | { art: 'DATENFELD'; zielId: number; wert: string }
    | { art: 'UNTERSCHRIFT'; rolle: 'TECHNIKER' | 'KUNDE'; bild: string | null; name: string }
    | { art: 'STATUS'; status: Status }

export type Eintrag = { id?: number; protokollId: number; schluessel: string; zeit: number; fehler?: string } & { aenderung: Aenderung }

interface DB extends DBSchema {
    protokolle: { key: number; value: Protokoll }
    warteschlange: { key: number; value: Eintrag; indexes: { protokoll: number } }
}

let dbPromise: Promise<IDBPDatabase<DB>> | null = null
const db = () => (dbPromise ??= openDB<DB>('feldeinsatz-offline', 1, {
    upgrade(d) {
        d.createObjectStore('protokolle')
        d.createObjectStore('warteschlange', { keyPath: 'id', autoIncrement: true }).createIndex('protokoll', 'protokollId')
    },
}))

/** Für Tests: Verbindung schließen, damit die Datenbank gelöscht und neu geöffnet werden kann. */
export async function _vergissVerbindung() {
    const d = dbPromise
    dbPromise = null
    if (d) (await d).close()
}

export async function merkeProtokoll(p: Protokoll): Promise<void> { await (await db()).put('protokolle', p, p.id) }
export async function gemerktesProtokoll(id: number): Promise<Protokoll | undefined> { return (await db()).get('protokolle', id) }

const schluesselVon = (a: Aenderung) => (a.art === 'PRUEFPUNKT' || a.art === 'DATENFELD' ? `${a.art}:${a.zielId}` : a.art === 'UNTERSCHRIFT' ? `UNTERSCHRIFT:${a.rolle}` : 'STATUS')

/** Legt eine Änderung in die Warteschlange. Dieselbe Stelle (derselbe Prüfpunkt, dasselbe Feld) ersetzt die frühere, die noch nicht gesendet wurde: Es zählt der letzte Stand. */
export async function legeAb(protokollId: number, aenderung: Aenderung): Promise<void> {
    const d = await db()
    const tx = d.transaction('warteschlange', 'readwrite')
    const schluessel = schluesselVon(aenderung)
    for (const e of await tx.store.index('protokoll').getAll(protokollId)) {
        if (e.schluessel === schluessel && e.id != null) await tx.store.delete(e.id)
    }
    await tx.store.add({ protokollId, schluessel, zeit: Date.now(), aenderung })
    await tx.done
}

export async function warteschlange(protokollId?: number): Promise<Eintrag[]> {
    const d = await db()
    const alle = protokollId == null ? await d.getAll('warteschlange') : await d.getAllFromIndex('warteschlange', 'protokoll', protokollId)
    return alle.sort((a, b) => (a.id ?? 0) - (b.id ?? 0))
}

export async function verwirf(id: number): Promise<void> { await (await db()).delete('warteschlange', id) }

/** Beim Abmelden: gemerkte Feldeinsätze entfernen, außer solchen mit noch wartenden Änderungen (die gehen sonst verloren). */
export async function raeumeAuf(): Promise<void> {
    const d = await db()
    const behalten = new Set((await d.getAll('warteschlange')).map(e => e.protokollId))
    for (const id of await d.getAllKeys('protokolle')) if (!behalten.has(id)) await d.delete('protokolle', id)
}

/** Zählwerte aus den Prüfpunkten, wenn der Server nicht erreichbar ist. */
export function statistikVon(p: Protokoll): Protokoll['statistik'] {
    const bewertet = p.pruefpunkte.filter(x => x.ergebnis != null)
    return { gesamt: p.pruefpunkte.length, bewertet: bewertet.length, inOrdnung: bewertet.filter(x => x.ergebnis === 'IO').length, nichtInOrdnung: bewertet.filter(x => x.ergebnis === 'NIO').length, nichtAnwendbar: bewertet.filter(x => x.ergebnis === 'NA').length }
}

/** Legt die wartenden Änderungen über den vom Server (oder aus dem Speicher) gelesenen Stand, damit die Anzeige zeigt, was der Mitarbeiter eingegeben hat. */
export function mitWartenden(p: Protokoll, wartend: Eintrag[]): Protokoll {
    let r = p
    for (const e of wartend) {
        if (e.fehler) continue // abgelehnt: der Stand des Servers gilt, die Meldung steht daneben
        const a = e.aenderung
        if (a.art === 'PRUEFPUNKT') r = { ...r, pruefpunkte: r.pruefpunkte.map(x => (x.id === a.zielId ? { ...x, ergebnis: a.ergebnis, bemerkung: a.bemerkung } : x)) }
        else if (a.art === 'DATENFELD') r = { ...r, datenfelder: r.datenfelder.map(x => (x.id === a.zielId ? { ...x, wert: a.wert } : x)) }
        else if (a.art === 'UNTERSCHRIFT') {
            const feld = a.rolle === 'TECHNIKER' ? 'unterschriftTechniker' : 'unterschriftKunde'
            r = { ...r, [feld]: a.bild ? { bild: a.bild, name: a.name } : { bild: null, name: a.name } }
        }
    }
    return r
}

export interface AbgleichErgebnis { gesendet: number; abgelehnt: number; keineVerbindung: boolean }

let laeuft: Promise<AbgleichErgebnis> | null = null

/** Sendet die Warteschlange der Reihe nach. Ohne Verbindung wird abgebrochen (alles bleibt); vom Server abgelehnte Einträge bleiben mit Meldung stehen und blockieren die übrigen nicht. */
export async function gleicheAb(token: string): Promise<AbgleichErgebnis> {
    const ergebnis = await (laeuft ??= abgleich(token).finally(() => { laeuft = null }))
    // Eine Änderung, die zwischen der letzten Prüfung des laufenden Abgleichs und seinem Ende dazukam, darf nicht liegen bleiben
    if (!ergebnis.keineVerbindung && (await warteschlange()).some(e => !e.fehler)) return gleicheAb(token)
    return ergebnis
}

async function abgleich(token: string): Promise<AbgleichErgebnis> {
    const d = await db()
    const ergebnis: AbgleichErgebnis = { gesendet: 0, abgelehnt: 0, keineVerbindung: false }
    // Solange noch etwas Sendbares da ist: Änderungen, die während des Sendens dazukommen, werden gleich mitgenommen
    for (;;) {
        const offene = (await warteschlange()).filter(e => e.id != null && !e.fehler)
        if (offene.length === 0) break
        for (const e of offene) {
            try {
                const a = e.aenderung
                if (a.art === 'PRUEFPUNKT') await bewertePruefpunkt(token, e.protokollId, a.zielId, a.ergebnis, a.bemerkung)
                else if (a.art === 'DATENFELD') await setzeDatenfeld(token, e.protokollId, a.zielId, a.wert)
                else if (a.art === 'UNTERSCHRIFT') await unterschreibe(token, e.protokollId, a.rolle, a.bild, a.name)
                else await wechsleStatus(token, e.protokollId, a.status)
                await d.delete('warteschlange', e.id!)
                ergebnis.gesendet++
            } catch (fehler) {
                if (fehler instanceof WartungFehler && (fehler.status === 0 || fehler.status === 401)) { ergebnis.keineVerbindung = true; return ergebnis }
                // Der Eintrag kann inzwischen durch eine neuere Änderung ersetzt worden sein: Dann gibt es nichts mehr zu markieren
                const noch = await d.get('warteschlange', e.id!)
                if (noch) await d.put('warteschlange', { ...noch, fehler: fehler instanceof Error ? fehler.message : 'Der Abgleich ist fehlgeschlagen.' })
                ergebnis.abgelehnt++
            }
        }
    }
    return ergebnis
}
