import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { _vergissVerbindung, gleicheAb, legeAb, merkeProtokoll, gemerktesProtokoll, mitWartenden, raeumeAuf, statistikVon, verwirf, warteschlange } from './feldeinsatzOffline'
import type { Protokoll } from './wartungApi'

/** Nur Dummy-Daten. */
const protokoll = (id = 3): Protokoll => ({
    id, nummer: 'W-1', kunde: null, objekt: null, tuer: null, titel: null, datum: '2026-10-06', status: 'OFFEN', bearbeitbar: true, moeglicheStatus: [], unterschriftTechniker: null, unterschriftKunde: null,
    abschnitte: [], datenfelder: [{ id: 200, abschnittId: 1, label: 'Hersteller', feldtyp: 'TEXT', optionen: null, wert: null, sortierung: 1 }],
    pruefpunkte: [{ id: 100, abschnittId: 1, label: 'A', ergebnis: null, bemerkung: null, vorherigesErgebnis: null, sortierung: 1 }, { id: 101, abschnittId: 1, label: 'B', ergebnis: 'IO', bemerkung: null, vorherigesErgebnis: null, sortierung: 2 }],
    statistik: { gesamt: 2, bewertet: 1, inOrdnung: 1, nichtInOrdnung: 0, nichtAnwendbar: 0 },
})
const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } })

beforeEach(async () => {
    await _vergissVerbindung()
    await new Promise<void>(r => { const q = indexedDB.deleteDatabase('feldeinsatz-offline'); q.onsuccess = q.onerror = q.onblocked = () => r() })
    vi.unstubAllGlobals()
})

describe('Warteschlange des Feldeinsatzes', () => {
    it('dieselbe Stelle ersetzt die frühere Änderung, verschiedene Stellen bleiben in der Reihenfolge', async () => {
        await legeAb(3, { art: 'PRUEFPUNKT', zielId: 100, ergebnis: 'NIO', bemerkung: null })
        await legeAb(3, { art: 'DATENFELD', zielId: 200, wert: 'Schüco' })
        await legeAb(3, { art: 'PRUEFPUNKT', zielId: 100, ergebnis: 'NIO', bemerkung: 'Kratzer' })
        await legeAb(4, { art: 'STATUS', status: 'ABGESCHLOSSEN' })

        const q = await warteschlange(3)

        expect(q.map(e => e.schluessel)).toEqual(['DATENFELD:200', 'PRUEFPUNKT:100'])
        expect(q[1].aenderung).toMatchObject({ bemerkung: 'Kratzer' })
        expect(await warteschlange()).toHaveLength(3)
    })

    it('sendet der Reihe nach mit dem Zugangslink im Kopf und leert die Warteschlange', async () => {
        const f = vi.fn(async () => json({ status: 'IN_BEARBEITUNG', statistik: {}, pruefpunkt: null, datenfeld: null }))
        vi.stubGlobal('fetch', f)
        await legeAb(3, { art: 'PRUEFPUNKT', zielId: 100, ergebnis: 'IO', bemerkung: null })
        await legeAb(3, { art: 'UNTERSCHRIFT', rolle: 'TECHNIKER', bild: 'data:image/png;base64,AAAA', name: 'Max Mustermann' })
        await legeAb(3, { art: 'STATUS', status: 'ABGESCHLOSSEN' })

        const r = await gleicheAb('zugang-xyz')

        expect(r).toEqual({ gesendet: 3, abgelehnt: 0, keineVerbindung: false })
        expect(f.mock.calls.map(c => String(c[0]).split('/wartung')[1])).toEqual(['/protokolle/3/pruefpunkte/100', '/protokolle/3/unterschrift', '/protokolle/3/status'])
        expect((f.mock.calls[0][1] as RequestInit).headers).toMatchObject({ 'X-Zugang': 'zugang-xyz' })
        expect(await warteschlange()).toHaveLength(0)
    })

    it('ohne Verbindung bleibt alles liegen und der Abgleich bricht ab', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))
        await legeAb(3, { art: 'PRUEFPUNKT', zielId: 100, ergebnis: 'IO', bemerkung: null })
        await legeAb(3, { art: 'DATENFELD', zielId: 200, wert: 'x' })

        const r = await gleicheAb('t')

        expect(r.keineVerbindung).toBe(true)
        expect(await warteschlange()).toHaveLength(2)
        expect((await warteschlange()).every(e => !e.fehler)).toBe(true)
    })

    it('vom Server Abgelehntes bleibt mit Meldung stehen, blockiert die übrigen nicht und wird nicht erneut gesendet; Verwerfen entfernt es', async () => {
        const f = vi.fn(async (url: RequestInfo | URL) => (String(url).includes('/pruefpunkte/') ? json({ message: 'Dieses Protokoll ist „abgeschlossen“.' }, 409) : json({ status: 'OFFEN', statistik: {}, pruefpunkt: null, datenfeld: null })))
        vi.stubGlobal('fetch', f)
        await legeAb(3, { art: 'PRUEFPUNKT', zielId: 100, ergebnis: 'IO', bemerkung: null })
        await legeAb(3, { art: 'DATENFELD', zielId: 200, wert: 'x' })

        const r = await gleicheAb('t')

        expect(r).toMatchObject({ gesendet: 1, abgelehnt: 1 })
        const q = await warteschlange()
        expect(q).toHaveLength(1)
        expect(q[0].fehler).toBe('Dieses Protokoll ist „abgeschlossen“.')
        await gleicheAb('t')
        expect(f).toHaveBeenCalledTimes(2)
        await verwirf(q[0].id!)
        expect(await warteschlange()).toHaveLength(0)
    })

    it('eine Änderung, die während des Sendens dazukommt, wird mitgenommen', async () => {
        let ersterAufruf = true
        vi.stubGlobal('fetch', vi.fn(async () => {
            if (ersterAufruf) { ersterAufruf = false; await legeAb(3, { art: 'DATENFELD', zielId: 200, wert: 'neu' }) }
            return json({ status: 'OFFEN', statistik: {}, pruefpunkt: null, datenfeld: null })
        }))
        await legeAb(3, { art: 'PRUEFPUNKT', zielId: 100, ergebnis: 'IO', bemerkung: null })

        const r = await gleicheAb('t')

        expect(r.gesendet).toBe(2)
        expect(await warteschlange()).toHaveLength(0)
    })

    it('wartende Änderungen liegen über dem gelesenen Stand, abgelehnte nicht; Zählwerte kommen aus den Prüfpunkten', async () => {
        const q = [
            { id: 1, protokollId: 3, schluessel: 'a', zeit: 1, aenderung: { art: 'PRUEFPUNKT', zielId: 100, ergebnis: 'NIO', bemerkung: 'Kratzer' } },
            { id: 2, protokollId: 3, schluessel: 'b', zeit: 2, aenderung: { art: 'DATENFELD', zielId: 200, wert: 'Schüco' }, fehler: 'abgelehnt' },
            { id: 3, protokollId: 3, schluessel: 'c', zeit: 3, aenderung: { art: 'UNTERSCHRIFT', rolle: 'KUNDE', bild: 'data:image/png;base64,AA', name: 'Erika Beispiel' } },
        ] as Parameters<typeof mitWartenden>[1]

        const r = mitWartenden(protokoll(), q)

        expect(r.pruefpunkte[0]).toMatchObject({ ergebnis: 'NIO', bemerkung: 'Kratzer' })
        expect(r.datenfelder[0].wert).toBeNull()
        expect(r.unterschriftKunde).toEqual({ bild: 'data:image/png;base64,AA', name: 'Erika Beispiel' })
        expect(statistikVon(r)).toEqual({ gesamt: 2, bewertet: 2, inOrdnung: 1, nichtInOrdnung: 1, nichtAnwendbar: 0 })
    })

    it('beim Abmelden bleiben nur Feldeinsätze mit wartenden Änderungen auf dem Gerät', async () => {
        await merkeProtokoll(protokoll(3))
        await merkeProtokoll(protokoll(4))
        await legeAb(4, { art: 'STATUS', status: 'ABGESCHLOSSEN' })

        await raeumeAuf()

        expect(await gemerktesProtokoll(3)).toBeUndefined()
        expect(await gemerktesProtokoll(4)).toBeDefined()
    })
})
