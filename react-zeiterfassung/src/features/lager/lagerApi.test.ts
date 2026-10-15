import { afterEach, describe, expect, it, vi } from 'vitest'
import {
    buche, findeNachEan, formatMenge, ladeOrte, LagerFehler, ordneEanZu, sucheArtikel, sucheProjekte, umbuche,
} from './lagerApi'

const antwort = (daten: unknown, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => daten }) as unknown as Response

afterEach(() => { vi.unstubAllGlobals() })

describe('lagerApi', () => {
    it('Abfragen tragen den Zugangslink im Pfad und den Suchbegriff gesichert in der Adresse', async () => {
        const f = vi.fn(async () => antwort([]))
        vi.stubGlobal('fetch', f)

        await ladeOrte('a b/c')
        await sucheArtikel('tok', 'Rohr 42,4 & Co')
        await findeNachEan('tok', ' 4006381333931 ')

        expect(f.mock.calls.map(c => c[0])).toEqual([
            '/api/zeiterfassung/lager/a%20b%2Fc/orte',
            '/api/zeiterfassung/lager/tok/suche?q=Rohr%2042%2C4%20%26%20Co',
            '/api/zeiterfassung/lager/tok/ean/4006381333931',
        ])
    })

    it('Buchungen schicken den Zugangslink im Text der Anfrage, nicht in der Adresse', async () => {
        const f = vi.fn(async () => antwort({ artikelId: 9 }))
        vi.stubGlobal('fetch', f)

        await buche('tok', { art: 'ABGANG', artikelId: 9, lagerortId: 1, menge: 3, projektId: 77, notiz: 'Süd' })
        await umbuche('tok', { artikelId: 9, vonLagerortId: 1, nachLagerortId: 2, menge: 2 })
        await ordneEanZu('tok', 9, '4006381333931')

        expect(f.mock.calls.map(c => [c[0], (c[1] as RequestInit).method])).toEqual([
            ['/api/zeiterfassung/lager/buchung', 'POST'], ['/api/zeiterfassung/lager/umbuchung', 'POST'], ['/api/zeiterfassung/lager/ean-zuordnen', 'POST'],
        ])
        expect(JSON.parse((f.mock.calls[0][1] as RequestInit).body as string)).toEqual({ token: 'tok', art: 'ABGANG', artikelId: 9, lagerortId: 1, menge: 3, projektId: 77, notiz: 'Süd' })
        expect(JSON.parse((f.mock.calls[2][1] as RequestInit).body as string)).toEqual({ token: 'tok', artikelId: 9, ean: '4006381333931' })
    })

    it('Fehler des Servers kommen als Satz an, auch ohne lesbare Antwort in verständlichem Deutsch', async () => {
        vi.stubGlobal('fetch', vi.fn()
            .mockResolvedValueOnce(antwort({ message: 'Dafür fehlt die Berechtigung: Lager, Anlegen.' }, 403))
            .mockResolvedValueOnce({ ok: false, status: 401, json: async () => { throw new Error('leer') } })
            .mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) }))

        await expect(buche('t', { art: 'ZUGANG', artikelId: 1, lagerortId: 1, menge: 1 })).rejects.toMatchObject({ message: 'Dafür fehlt die Berechtigung: Lager, Anlegen.', status: 403 })
        await expect(ladeOrte('t')).rejects.toMatchObject({ message: 'Der Zugang ist nicht gültig. Bitte neu anmelden.', status: 401 })
        await expect(sucheArtikel('t', 'xx')).rejects.toMatchObject({ message: 'Das hat nicht geklappt. Bitte noch einmal versuchen.', status: 500 })
    })

    it('ohne Netz: Status 0 und ein Satz, der Netz nennt', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))

        const fehler = await ladeOrte('t').catch(e => e)

        expect(fehler).toBeInstanceOf(LagerFehler)
        expect(fehler.status).toBe(0)
        expect(fehler.message).toMatch(/Netz/)
    })

    it('Projektsuche füllt fehlende Felder und formatiert Mengen auf Deutsch', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => antwort([{ id: 7, name: 'Anbau Halle' }])))

        expect(await sucheProjekte('anbau')).toEqual([{ id: 7, name: 'Anbau Halle', projektNummer: null, kundenName: null }])
        expect(formatMenge(12.5)).toBe('12,5')
        expect(formatMenge(1000)).toBe('1.000')
        expect(formatMenge(0.1234)).toBe('0,123')
    })
})
