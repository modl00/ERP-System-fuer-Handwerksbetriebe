import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
    _vergissVerbindung, freieNummern, fuelleNummernNach, gleicheLieferscheineAb, ladeListeMitGeraetestand, legeLieferscheinAb, nimmNummer, raeumeLieferscheineAuf,
    unterschreibeWartenden, verwirfLieferschein, wartendeLieferscheine,
} from './lieferscheinOffline'
import type { NeuerLieferschein } from './lieferscheinApi'

/** Nur Dummy-Daten. */
const eingabe = (id: string, aenderung: Partial<NeuerLieferschein> = {}): NeuerLieferschein => ({
    clientId: id, projektId: 7, empfaengerName: 'Erika Mustermann', positionen: [{ bezeichnung: 'Rigips', menge: 4, einheit: 'Stk' }], ...aenderung,
})
const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } })

beforeEach(async () => {
    await _vergissVerbindung()
    await new Promise<void>(r => { const q = indexedDB.deleteDatabase('lieferschein-offline'); q.onsuccess = q.onerror = q.onblocked = () => r() })
    vi.unstubAllGlobals()
})

describe('Nummernbereich auf dem Gerät', () => {
    it('füllt bei Verbindung nach, schickt den Zugangslink im Kopf und vergibt die Nummern der Reihe nach genau einmal', async () => {
        const f = vi.fn(async () => json({ art: 'LIEFERSCHEIN', nummern: ['LS-1', 'LS-2', 'LS-3'] }))
        vi.stubGlobal('fetch', f)
        expect(await fuelleNummernNach('zugang-xyz')).toBe(3)
        const [url, init] = f.mock.calls[0] as unknown as [string, RequestInit]
        expect(url).toContain('/nummernbereich?soll=20')
        expect((init.headers as Record<string, string>)['X-Zugang']).toBe('zugang-xyz')

        expect(await nimmNummer()).toBe('LS-1')
        expect(await nimmNummer()).toBe('LS-2')
        expect(await freieNummern()).toEqual(['LS-3'])
        expect(await nimmNummer()).toBe('LS-3')
        expect(await nimmNummer()).toBeNull()
    })

    it('füllt erst nach, wenn der Vorrat knapp wird, und behält ihn ohne Verbindung', async () => {
        const f = vi.fn(async () => json({ art: 'LIEFERSCHEIN', nummern: Array.from({ length: 20 }, (_, i) => `LS-${i + 1}`) }))
        vi.stubGlobal('fetch', f)
        await fuelleNummernNach('t')
        await fuelleNummernNach('t')
        expect(f).toHaveBeenCalledTimes(1)

        for (let i = 0; i < 11; i++) await nimmNummer()
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('offline') }))
        expect(await fuelleNummernNach('t')).toBe(9)
        expect(await freieNummern()).toHaveLength(9)
    })
})

describe('Warteschlange der Lieferscheine', () => {
    it('legt mit Nummer aus dem Bereich ab; ohne Bereich ohne Nummer', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({ art: 'LIEFERSCHEIN', nummern: ['LS-1'] })))
        await fuelleNummernNach('t')
        expect((await legeLieferscheinAb(eingabe('a'))).nummer).toBe('LS-1')
        expect((await legeLieferscheinAb(eingabe('b'))).nummer).toBeNull()
        const w = await wartendeLieferscheine(7)
        expect(w.map(x => [x.eingabe.clientId, x.eingabe.nummer])).toEqual([['a', 'LS-1'], ['b', null]])
        expect(await wartendeLieferscheine(99)).toEqual([])
    })

    it('Unterschrift nachtragen und Verwerfen', async () => {
        await legeLieferscheinAb(eingabe('a'))
        expect(await unterschreibeWartenden('a', 'data:image/png;base64,AAAA')).toBe(true)
        expect(await unterschreibeWartenden('gibtsnicht', 'x')).toBe(false)
        expect((await wartendeLieferscheine())[0].eingabe.unterschriftBild).toBe('data:image/png;base64,AAAA')
        await verwirfLieferschein('a')
        expect(await wartendeLieferscheine()).toEqual([])
    })

    it('gleicht der Reihe nach ab: gesendet verschwindet, abgelehntes bleibt mit Meldung und blockiert nichts, ohne Netz bleibt alles liegen', async () => {
        await legeLieferscheinAb(eingabe('a', { nummer: 'LS-1' }))
        await legeLieferscheinAb(eingabe('b', { nummer: 'LS-2' }))
        await legeLieferscheinAb(eingabe('c', { nummer: 'LS-3' }))

        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('offline') }))
        expect(await gleicheLieferscheineAb('t')).toMatchObject({ gesendet: 0, keineVerbindung: true })
        expect(await wartendeLieferscheine()).toHaveLength(3)

        const gesendet: string[] = []
        vi.stubGlobal('fetch', vi.fn(async (_u: string, init?: RequestInit) => {
            const b = JSON.parse(String(init!.body))
            if (b.clientId === 'b') return json({ message: 'Die Nummer LS-2 gehört zu einem anderen Gerät.' }, 409)
            gesendet.push(b.clientId)
            return json({ id: 1, nummer: b.nummer })
        }))
        const r = await gleicheLieferscheineAb('t')

        expect(r).toMatchObject({ gesendet: 2, abgelehnt: 1, keineVerbindung: false })
        expect(gesendet).toEqual(['a', 'c'])
        const rest = await wartendeLieferscheine()
        expect(rest).toHaveLength(1)
        expect(rest[0].fehler).toContain('anderen Gerät')
        // Abgelehntes wird nicht endlos wiederholt
        expect((await gleicheLieferscheineAb('t')).gesendet).toBe(0)
    })

    it('ein ungültiger Zugang (401) bricht den Abgleich ab, ohne etwas als abgelehnt zu markieren', async () => {
        await legeLieferscheinAb(eingabe('a', { nummer: 'LS-1' }))
        vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'Der Zugang ist nicht gültig.' }, 401)))
        expect(await gleicheLieferscheineAb('t')).toMatchObject({ keineVerbindung: true })
        expect((await wartendeLieferscheine())[0].fehler).toBeUndefined()
    })
})

describe('Liste vom Gerät und Aufräumen', () => {
    it('merkt die Liste bei Verbindung und liefert sie ohne Netz; Fehler des Servers werden nicht verschluckt', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json([{ id: 1, nummer: 'LS-1' }])))
        expect((await ladeListeMitGeraetestand('t', 7)).vomGeraet).toBe(false)
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('offline') }))
        const r = await ladeListeMitGeraetestand('t', 7)
        expect(r.vomGeraet).toBe(true)
        expect(r.liste).toHaveLength(1)
        await expect(ladeListeMitGeraetestand('t', 8)).rejects.toThrow()
        vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'Dafür fehlt die Berechtigung' }, 403)))
        await expect(ladeListeMitGeraetestand('t', 7)).rejects.toThrow('Berechtigung')
    })

    it('beim Abmelden bleibt, was noch wartet (samt Nummern); sonst wird alles entfernt', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({ art: 'LIEFERSCHEIN', nummern: ['LS-1', 'LS-2'] })))
        await fuelleNummernNach('t')
        await legeLieferscheinAb(eingabe('a'))
        await raeumeLieferscheineAuf()
        expect(await wartendeLieferscheine()).toHaveLength(1)
        expect(await freieNummern()).toEqual(['LS-2'])

        await verwirfLieferschein('a')
        await raeumeLieferscheineAuf()
        expect(await freieNummern()).toEqual([])
    })
})
