import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, Loader2, Package, ScanBarcode, Search, X } from 'lucide-react'
import { useToast } from '../components/ui/toast'
import BarcodeScanner from '../features/lager/BarcodeScanner'
import BuchenBlatt from '../features/lager/BuchenBlatt'
import { useDarf } from '../features/rechte/rechte'
import {
    findeNachEan, formatMenge, ladeHaeufige, ladeOrte, LagerFehler, ordneEanZu, sucheArtikel,
    type LagerBestand, type Lagerort,
} from '../features/lager/lagerApi'

/**
 * Lager in der Handy-App (Issue #69, Stufe 2): Artikel suchen oder per Strichcode finden, dann Entnahme, Zugang, Inventur oder Umbuchung. Ein
 * unbekannter Strichcode lässt sich einem Artikel zuordnen (mit dem Recht "Lager ändern"), danach wird er beim nächsten Scan erkannt.
 */
export default function LagerPage() {
    const navigate = useNavigate()
    const toast = useToast()
    const token = localStorage.getItem('zeiterfassung_token')
    const darfAnsehen = useDarf('LAGER', 'ANSEHEN')
    const darfAendern = useDarf('LAGER', 'AENDERN')
    const [orte, setOrte] = useState<Lagerort[]>([])
    const [suche, setSuche] = useState('')
    // Ein Ergebnis gehört immer zu einem Suchbegriff; zu kurze oder veraltete Begriffe zeigen nichts (so braucht der Effekt kein setState vorab)
    const [ergebnis, setErgebnis] = useState<{ q: string; liste: LagerBestand[] }>({ q: '', liste: [] })
    const [zuordnenErgebnis, setZuordnenErgebnis] = useState<{ q: string; liste: LagerBestand[] }>({ q: '', liste: [] })
    const [fehler, setFehler] = useState('')
    // Die meistgebuchten Artikel, damit man sie ohne Suche findet (null = noch nicht geladen, 'fehler' = konnte nicht geladen werden)
    const [haeufig, setHaeufig] = useState<LagerBestand[] | 'fehler' | null>(null)
    const [scannerOffen, setScannerOffen] = useState(false)
    const [gewaehlt, setGewaehlt] = useState<LagerBestand | null>(null)
    const [unbekannt, setUnbekannt] = useState<string | null>(null)
    const [zuordnenSuche, setZuordnenSuche] = useState('')

    const q = suche.trim()
    const treffer = q.length >= 2 && ergebnis.q === q ? ergebnis.liste : []
    const sucht = darfAnsehen && q.length >= 2 && ergebnis.q !== q && !fehler
    const zq = zuordnenSuche.trim()
    const zuordnenTreffer = zq.length >= 2 && zuordnenErgebnis.q === zq ? zuordnenErgebnis.liste : []

    useEffect(() => {
        if (!token || !darfAnsehen) return
        ladeOrte(token).then(setOrte).catch(e => setFehler(e instanceof LagerFehler ? e.message : 'Die Lagerorte konnten nicht geladen werden.'))
    }, [token, darfAnsehen])

    useEffect(() => {
        if (!token || !darfAnsehen) return
        ladeHaeufige(token).then(setHaeufig).catch(() => setHaeufig('fehler'))
    }, [token, darfAnsehen])

    // Suche mit kurzer Wartezeit nach dem Tippen
    useEffect(() => {
        if (!token || !darfAnsehen || q.length < 2) return
        let abgebrochen = false
        const t = window.setTimeout(() => {
            sucheArtikel(token, q)
                .then(l => { if (!abgebrochen) { setErgebnis({ q, liste: l }); setFehler('') } })
                .catch(e => { if (!abgebrochen) { setErgebnis({ q, liste: [] }); setFehler(e instanceof LagerFehler ? e.message : 'Die Suche hat nicht geklappt.') } })
        }, 300)
        return () => { abgebrochen = true; window.clearTimeout(t) }
    }, [q, token, darfAnsehen])

    useEffect(() => {
        if (!token || unbekannt == null || zq.length < 2) return
        let abgebrochen = false
        const t = window.setTimeout(() => {
            sucheArtikel(token, zq)
                .then(l => { if (!abgebrochen) setZuordnenErgebnis({ q: zq, liste: l }) })
                .catch(() => { if (!abgebrochen) setZuordnenErgebnis({ q: zq, liste: [] }) })
        }, 300)
        return () => { abgebrochen = true; window.clearTimeout(t) }
    }, [zq, token, unbekannt])

    const nachCode = useCallback(async (code: string) => {
        setScannerOffen(false)
        if (!token) return
        setFehler('')
        try {
            const r = await findeNachEan(token, code)
            if (r.gefunden && r.artikel) {
                setGewaehlt(r.artikel)
            } else {
                setUnbekannt(r.ean)
                setZuordnenSuche('')
            }
        } catch (e) {
            const text = e instanceof LagerFehler ? e.message : 'Der Strichcode konnte nicht geprüft werden.'
            setFehler(text)
            toast.error(text)
        }
    }, [token, toast])

    const zuordnen = async (artikel: LagerBestand) => {
        if (!token || unbekannt == null) return
        try {
            const aktualisiert = await ordneEanZu(token, artikel.artikelId, unbekannt)
            toast.success('Der Strichcode gehört jetzt zu diesem Artikel.')
            setUnbekannt(null)
            setGewaehlt(aktualisiert)
        } catch (e) {
            toast.error(e instanceof LagerFehler ? e.message : 'Das Zuordnen hat nicht geklappt.')
        }
    }

    const nachBuchung = (aktualisiert: LagerBestand) => {
        setErgebnis(e => ({ ...e, liste: e.liste.map(a => a.artikelId === aktualisiert.artikelId ? aktualisiert : a) }))
        setHaeufig(h => (Array.isArray(h) ? h.map(a => a.artikelId === aktualisiert.artikelId ? aktualisiert : a) : h))
        setGewaehlt(null)
    }

    const artikelZeile = (a: LagerBestand) => (
                        <li key={a.artikelId}>
                            <button onClick={() => setGewaehlt(a)} className="w-full rounded-2xl border border-slate-200 bg-white p-4 text-left shadow-sm transition-colors hover:border-rose-200 active:scale-[0.99]">
                                <div className="flex items-start justify-between gap-3">
                                    <div className="min-w-0">
                                        <p className="truncate font-semibold text-slate-900">{a.bezeichnung}</p>
                                        {a.artikelnummer && <p className="text-xs text-slate-500">{a.artikelnummer}</p>}
                                    </div>
                                    <p className={`shrink-0 text-right font-semibold ${a.negativ ? 'text-rose-700' : a.unterMindestbestand ? 'text-amber-700' : 'text-slate-900'}`}>
                                        {formatMenge(a.bestand)} <span className="text-sm font-normal text-slate-500">{a.einheit}</span>
                                    </p>
                                </div>
                                {a.orte.filter(o => o.bestand !== 0).length > 0 && (
                                    <p className="mt-1 text-xs text-slate-500">{a.orte.filter(o => o.bestand !== 0).map(o => `${o.lagerortName}: ${formatMenge(o.bestand)}`).join(' · ')}</p>
                                )}
                                {(a.unterMindestbestand || a.negativ) && (
                                    <p className={`mt-1 text-xs font-medium ${a.negativ ? 'text-rose-700' : 'text-amber-700'}`}>{a.negativ ? 'Bestand im Minus: Zugang oder Inventur fehlt' : 'Unter dem Mindestbestand'}</p>
                                )}
                            </button>
                        </li>
    )

    return (
        <div className="flex h-full flex-col overflow-hidden bg-slate-50">
            <header className="safe-area-top border-b border-slate-200 bg-white px-4 py-4">
                <div className="flex items-center gap-3">
                    <button onClick={() => navigate('/')} aria-label="Zurück" className="-ml-2 rounded-full p-2 hover:bg-slate-100"><ArrowLeft className="h-5 w-5 text-slate-600" /></button>
                    <h1 className="text-xl font-bold text-slate-900">Lager</h1>
                </div>
            </header>

            {!darfAnsehen ? (
                <main className="p-4"><p className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Das Lager ist für dich nicht freigegeben. Wer es freischaltet, steht in der Rechteverwaltung am PC.</p></main>
            ) : (
                <main className="flex-1 space-y-3 overflow-y-auto overflow-x-hidden p-4 pb-8">
                    <div className="flex gap-2">
                        <div className="relative flex-1">
                            <Search aria-hidden="true" className="absolute left-3 top-3.5 h-5 w-5 text-slate-400" />
                            <input aria-label="Artikel suchen" value={suche} onChange={e => setSuche(e.target.value)} placeholder="Artikel, Nummer oder Strichcode"
                                className="min-h-12 w-full rounded-xl border border-slate-200 bg-white pl-10 pr-3 text-base outline-none focus:ring-2 focus:ring-rose-500" />
                        </div>
                        <button onClick={() => setScannerOffen(true)} aria-label="Strichcode scannen"
                            className="flex min-h-12 min-w-12 items-center justify-center rounded-xl bg-rose-600 text-white hover:bg-rose-700"><ScanBarcode className="h-6 w-6" /></button>
                    </div>

                    {fehler && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

                    {unbekannt != null && (
                        <section aria-label="Unbekannter Strichcode" className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
                            <div className="flex items-start justify-between gap-2">
                                <p className="flex items-start gap-2 text-sm text-amber-900"><AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
                                    <span>Der Strichcode <strong className="font-mono">{unbekannt}</strong> ist noch keinem Artikel zugeordnet.</span></p>
                                <button onClick={() => setUnbekannt(null)} aria-label="Schließen" className="rounded p-1 hover:bg-amber-100"><X className="h-4 w-4" /></button>
                            </div>
                            {darfAendern ? (
                                <>
                                    <label htmlFor="zuordnen-suche" className="mt-3 block text-sm font-medium text-amber-900">Zu welchem Artikel gehört er?</label>
                                    <input id="zuordnen-suche" value={zuordnenSuche} onChange={e => setZuordnenSuche(e.target.value)} placeholder="Artikel suchen"
                                        className="mt-1 min-h-12 w-full rounded-lg border border-amber-200 bg-white px-3 text-base outline-none focus:ring-2 focus:ring-rose-500" />
                                    <ul className="mt-2 space-y-1">
                                        {zuordnenTreffer.map(a => (
                                            <li key={a.artikelId}><button onClick={() => void zuordnen(a)} className="w-full rounded-lg bg-white p-3 text-left text-sm shadow-sm hover:bg-rose-50">
                                                <strong>{a.bezeichnung}</strong>{a.artikelnummer ? <span className="text-slate-500"> · {a.artikelnummer}</span> : null}</button></li>
                                        ))}
                                    </ul>
                                </>
                            ) : <p className="mt-2 text-sm text-amber-900">Zuordnen darf, wer das Recht „Lager ändern“ hat. Sag im Büro Bescheid.</p>}
                        </section>
                    )}

                    {sucht && <div className="flex justify-center py-4 text-slate-400"><Loader2 className="h-5 w-5 animate-spin" aria-label="Suche läuft" /></div>}

                    {q.length >= 2 && !sucht && treffer.length === 0 && !fehler && (
                        <p className="py-6 text-center text-sm text-slate-500">Kein Artikel gefunden. Probier es mit einem Teil des Namens, der Artikelnummer oder scanne den Strichcode.</p>
                    )}
                    {q.length < 2 && unbekannt == null && (
                        <div className="flex flex-col items-center gap-2 py-6 text-center text-slate-400">
                            <Package className="h-10 w-10" aria-hidden="true" />
                            <p className="text-sm">Suche einen Artikel oder scanne den Strichcode.</p>
                        </div>
                    )}
                    {q.length < 2 && unbekannt == null && Array.isArray(haeufig) && haeufig.length > 0 && (
                        <section aria-label="Häufig benutzt">
                            <h2 className="mb-2 text-sm font-semibold text-slate-600">Häufig benutzt</h2>
                            <ul className="space-y-2">{haeufig.map(artikelZeile)}</ul>
                        </section>
                    )}
                    {q.length < 2 && unbekannt == null && haeufig === 'fehler' && (
                        <p className="text-center text-xs text-slate-400">Die häufig benutzten Artikel konnten nicht geladen werden.</p>
                    )}

                    <ul className="space-y-2">
                        {treffer.map(artikelZeile)}
                    </ul>
                </main>
            )}

            {scannerOffen && <BarcodeScanner onCode={code => void nachCode(code)} onClose={() => setScannerOffen(false)} />}
            {gewaehlt && token && <BuchenBlatt token={token} artikel={gewaehlt} orte={orte} onFertig={nachBuchung} onClose={() => setGewaehlt(null)} />}
        </div>
    )
}
