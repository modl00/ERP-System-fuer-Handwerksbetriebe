import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import { holeNummernbereich, ladeLieferscheine, legeLieferscheinAn, LieferscheinFehler, type Lieferschein, type NeuerLieferschein } from './lieferscheinApi'

/**
 * Lieferscheine ohne Netz (Keller, Baustelle ohne Empfang): Mit Verbindung holt sich das Gerät vom Server einen Nummernbereich (reservierte Nummern aus dem
 * Nummernkreis). Ein ohne Netz erstellter Lieferschein bekommt daraus sofort seine endgültige Nummer, die schon auf dem vor Ort unterschriebenen Beleg
 * steht, und liegt in einer Warteschlange. Bei Verbindung wird er gesendet (dieselbe Kennung ergibt nie einen zweiten Lieferschein). Nichts geht still
 * verloren: Was der Server ablehnt, bleibt mit seiner Meldung sichtbar, bis es verworfen wird. Reicht der Bereich nicht, entsteht ein Entwurf ohne Nummer;
 * die Nummer vergibt dann der Server beim Senden.
 */

export type Wartend = { eingabe: NeuerLieferschein; zeit: number; fehler?: string }

interface DB extends DBSchema {
    warteschlange: { key: string; value: Wartend }
    nummern: { key: string; value: string[] }
    listen: { key: number; value: Lieferschein[] }
}

let dbPromise: Promise<IDBPDatabase<DB>> | null = null
const db = () => (dbPromise ??= openDB<DB>('lieferschein-offline', 1, {
    upgrade(d) {
        d.createObjectStore('warteschlange', { keyPath: 'eingabe.clientId' })
        d.createObjectStore('nummern')
        d.createObjectStore('listen')
    },
}))

/** Für Tests: Verbindung schließen, damit die Datenbank gelöscht und neu geöffnet werden kann. */
export async function _vergissVerbindung() {
    const d = dbPromise
    dbPromise = null
    if (d) (await d).close()
}

const BEREICH = 'bereich'
const SOLL = 20
/** Ab dieser Zahl freier Nummern wird bei Verbindung nachgefüllt. */
const NACHFUELLEN_UNTER = 10

export async function freieNummern(): Promise<string[]> { return (await (await db()).get('nummern', BEREICH)) ?? [] }

/** Füllt den Nummernbereich bei Verbindung nach. Ohne Verbindung oder ohne Recht bleibt der Bestand, wie er ist. */
export async function fuelleNummernNach(token: string): Promise<number> {
    const vorhanden = await freieNummern()
    if (vorhanden.length >= NACHFUELLEN_UNTER) return vorhanden.length
    try {
        const b = await holeNummernbereich(token, SOLL)
        // Was der Server als frei meldet, gilt; schon lokal vergebene Nummern stehen dort nicht mehr
        await (await db()).put('nummern', b.nummern, BEREICH)
        return b.nummern.length
    } catch {
        return vorhanden.length
    }
}

/** Nimmt die nächste freie Nummer aus dem Bereich, oder {@code null}, wenn keine mehr da ist. */
export async function nimmNummer(): Promise<string | null> {
    const d = await db()
    const tx = d.transaction('nummern', 'readwrite')
    const liste = (await tx.store.get(BEREICH)) ?? []
    const nummer = liste.shift() ?? null
    await tx.store.put(liste, BEREICH)
    await tx.done
    return nummer
}

/** Legt einen Lieferschein in die Warteschlange. Mit Nummer aus dem Bereich, wenn noch eine da ist. */
export async function legeLieferscheinAb(eingabe: NeuerLieferschein): Promise<{ nummer: string | null }> {
    const nummer = eingabe.nummer ?? await nimmNummer()
    await (await db()).put('warteschlange', { eingabe: { ...eingabe, nummer }, zeit: Date.now() })
    return { nummer }
}

export async function wartendeLieferscheine(projektId?: number): Promise<Wartend[]> {
    const alle = await (await db()).getAll('warteschlange')
    return alle.filter(w => projektId == null || w.eingabe.projektId === projektId).sort((a, b) => a.zeit - b.zeit)
}

export async function verwirfLieferschein(clientId: string): Promise<void> { await (await db()).delete('warteschlange', clientId) }

/** Die Unterschrift eines noch nicht gesendeten Lieferscheins nachtragen (Empfänger unterschreibt erst später am Gerät). */
export async function unterschreibeWartenden(clientId: string, bild: string): Promise<boolean> {
    const d = await db()
    const w = await d.get('warteschlange', clientId)
    if (!w) return false
    await d.put('warteschlange', { ...w, eingabe: { ...w.eingabe, unterschriftBild: bild }, fehler: undefined })
    return true
}

/** Merkt die Liste eines Projekts, damit sie auch ohne Netz zu sehen ist. */
export async function merkeListe(projektId: number, liste: Lieferschein[]): Promise<void> { await (await db()).put('listen', liste, projektId) }
export async function gemerkteListe(projektId: number): Promise<Lieferschein[] | undefined> { return (await db()).get('listen', projektId) }

export interface AbgleichErgebnis { gesendet: number; abgelehnt: number; keineVerbindung: boolean }
let laeuft: Promise<AbgleichErgebnis> | null = null

/** Sendet die wartenden Lieferscheine der Reihe nach. Ohne Verbindung bleibt alles liegen; Abgelehntes bleibt mit Meldung stehen und blockiert die übrigen nicht. */
export async function gleicheLieferscheineAb(token: string): Promise<AbgleichErgebnis> {
    const ergebnis = await (laeuft ??= abgleich(token).finally(() => { laeuft = null }))
    if (!ergebnis.keineVerbindung && (await wartendeLieferscheine()).some(w => !w.fehler)) return gleicheLieferscheineAb(token)
    return ergebnis
}

async function abgleich(token: string): Promise<AbgleichErgebnis> {
    const d = await db()
    const ergebnis: AbgleichErgebnis = { gesendet: 0, abgelehnt: 0, keineVerbindung: false }
    for (const w of (await wartendeLieferscheine()).filter(x => !x.fehler)) {
        try {
            await legeLieferscheinAn(token, w.eingabe)
            await d.delete('warteschlange', w.eingabe.clientId)
            ergebnis.gesendet++
        } catch (e) {
            if (e instanceof LieferscheinFehler && (e.status === 0 || e.status === 401)) { ergebnis.keineVerbindung = true; return ergebnis }
            const noch = await d.get('warteschlange', w.eingabe.clientId)
            if (noch) await d.put('warteschlange', { ...noch, fehler: e instanceof Error ? e.message : 'Der Abgleich ist fehlgeschlagen.' })
            ergebnis.abgelehnt++
        }
    }
    return ergebnis
}

/** Lädt die Liste eines Projekts und merkt sie; ohne Verbindung die gemerkte. {@code vomGeraet} sagt, dass es der Stand des Geräts ist. */
export async function ladeListeMitGeraetestand(token: string, projektId: number): Promise<{ liste: Lieferschein[]; vomGeraet: boolean }> {
    try {
        const liste = await ladeLieferscheine(token, projektId)
        await merkeListe(projektId, liste)
        return { liste, vomGeraet: false }
    } catch (e) {
        if (e instanceof LieferscheinFehler && e.status === 0) {
            const gemerkt = await gemerkteListe(projektId)
            if (gemerkt) return { liste: gemerkt, vomGeraet: true }
        }
        throw e
    }
}

/** Beim Abmelden: gemerkte Listen und den Nummernbereich entfernen, die Warteschlange bleibt (sonst gingen Lieferscheine verloren). Die Nummern bleiben ebenfalls, solange etwas wartet. */
export async function raeumeLieferscheineAuf(): Promise<void> {
    const d = await db()
    await d.clear('listen')
    if ((await d.count('warteschlange')) === 0) await d.delete('nummern', BEREICH)
}
