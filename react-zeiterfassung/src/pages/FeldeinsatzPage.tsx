import { useState, useEffect, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
    ArrowLeft, CheckCircle2, XCircle, MinusCircle, ChevronDown, ChevronUp,
    FileText, Loader2, WifiOff, RefreshCw, CheckCheck, AlertTriangle, Wrench,
} from 'lucide-react'
import {
    ladeProtokoll, wechsleStatus, ladePdf, WartungFehler,
    type Protokoll, type Ergebnis, type Status,
} from '../features/wartung/wartungApi'
import {
    merkeProtokoll, gemerktesProtokoll, legeAb, warteschlange, gleicheAb,
    mitWartenden, verwirf, statistikVon,
    type Eintrag,
} from '../features/wartung/feldeinsatzOffline'
import { UnterschriftFeld } from '../features/wartung/UnterschriftFeld'
import { useToast } from '../components/ui/toast'

const STATUS_LABEL: Record<Status, string> = {
    OFFEN: 'Offen',
    IN_BEARBEITUNG: 'In Bearbeitung',
    ABGESCHLOSSEN: 'Abgeschlossen',
    ARCHIVIERT: 'Archiviert',
}
const STATUS_FARBE: Record<Status, string> = {
    OFFEN: 'bg-slate-100 text-slate-600',
    IN_BEARBEITUNG: 'bg-amber-100 text-amber-700',
    ABGESCHLOSSEN: 'bg-emerald-100 text-emerald-700',
    ARCHIVIERT: 'bg-slate-100 text-slate-500',
}

export default function FeldeinsatzPage() {
    const { id } = useParams<{ id: string }>()
    const navigate = useNavigate()
    const toast = useToast()
    const token = localStorage.getItem('zeiterfassung_token') ?? ''

    const [protokoll, setProtokoll] = useState<Protokoll | null>(null)
    const [loading, setLoading] = useState(true)
    const [offline, setOffline] = useState(false)
    const [abgleichend, setAbgleichend] = useState(false)
    const [wartend, setWartend] = useState<Eintrag[]>([])
    const [unterschriftOffen, setUnterschriftOffen] = useState(false)
    const [pdfLaed, setPdfLaed] = useState(false)
    const [aufgeklappt, setAufgeklappt] = useState<Set<number>>(new Set())

    const protokollId = id ? parseInt(id, 10) : NaN

    const ladeAktuell = useCallback(async (stille = false) => {
        if (!stille) setLoading(true)
        try {
            const p = await ladeProtokoll(token, protokollId)
            await merkeProtokoll(p)
            const wq = await warteschlange(protokollId)
            setWartend(wq)
            setProtokoll(mitWartenden(p, wq))
            setOffline(false)
        } catch {
            const gespeichert = await gemerktesProtokoll(protokollId)
            if (gespeichert) {
                const wq = await warteschlange(protokollId)
                setWartend(wq)
                setProtokoll(mitWartenden(gespeichert, wq))
                setOffline(true)
            } else {
                toast.error('Feldeinsatz konnte nicht geladen werden.')
                navigate(-1)
            }
        } finally {
            setLoading(false)
        }
    }, [protokollId, token]) // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { void ladeAktuell() }, [ladeAktuell])

    const aendere = async (eintrag: Eintrag['aenderung']) => {
        if (!protokoll) return
        await legeAb(protokollId, eintrag)
        const wq = await warteschlange(protokollId)
        setWartend(wq)
        // Optimistisch anzeigen
        const gespeichert = await gemerktesProtokoll(protokollId)
        if (gespeichert) setProtokoll(mitWartenden(gespeichert, wq))
        // Im Hintergrund abgleichen
        sende()
    }

    const sende = () => {
        if (abgleichend) return
        setAbgleichend(true)
        gleicheAb(token)
            .then(async ergebnis => {
                if (ergebnis.abgelehnt > 0) toast.error(`${ergebnis.abgelehnt} Änderung${ergebnis.abgelehnt > 1 ? 'en' : ''} wurde abgelehnt.`)
                if (!ergebnis.keineVerbindung) {
                    await ladeAktuell(true)
                }
            })
            .catch(() => { /* keine Verbindung, Queue bleibt */ })
            .finally(() => setAbgleichend(false))
    }

    const bewerte = (pruefpunktId: number, ergebnis: Ergebnis | null, bemerkung: string | null) =>
        aendere({ art: 'PRUEFPUNKT', zielId: pruefpunktId, ergebnis, bemerkung })

    const setzeFeld = (datenfeldId: number, wert: string) =>
        aendere({ art: 'DATENFELD', zielId: datenfeldId, wert })

    const statusAendern = async (status: Status) => {
        if (!protokoll) return
        try {
            const p = await wechsleStatus(token, protokollId, status)
            await merkeProtokoll(p)
            setProtokoll(mitWartenden(p, await warteschlange(protokollId)))
        } catch (e) {
            toast.error(e instanceof WartungFehler ? e.message : 'Status konnte nicht geändert werden.')
        }
    }

    const unterzeichne = async (rolle: 'TECHNIKER' | 'KUNDE', bild: string | null, name: string) => {
        if (bild === null) return
        await aendere({ art: 'UNTERSCHRIFT', rolle, bild, name })
    }

    const pdfAnzeigen = async () => {
        setPdfLaed(true)
        try {
            const url = await ladePdf(token, protokollId)
            window.open(url, '_blank')
        } catch {
            toast.error('PDF konnte nicht erstellt werden.')
        } finally {
            setPdfLaed(false)
        }
    }

    const fehlerhafteEntfernen = async (eintragId: number) => {
        await verwirf(eintragId)
        const wq = await warteschlange(protokollId)
        setWartend(wq)
        const gespeichert = await gemerktesProtokoll(protokollId)
        if (gespeichert) setProtokoll(mitWartenden(gespeichert, wq))
    }

    if (loading || !protokoll) {
        return (
            <div className="h-full flex flex-col bg-slate-50">
                <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                    <div className="flex items-center gap-3">
                        <button onClick={() => navigate(-1)} aria-label="Zurück"
                            className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95">
                            <ArrowLeft className="w-5 h-5 text-slate-600" />
                        </button>
                        <span className="font-bold text-slate-900">Feldeinsatz</span>
                    </div>
                </header>
                <div className="flex-1 flex justify-center items-center">
                    <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                </div>
            </div>
        )
    }

    const stats = protokoll.statistik ?? statistikVon(protokoll)
    const fortschritt = stats.gesamt > 0 ? Math.round((stats.bewertet / stats.gesamt) * 100) : 0
    const bearbeitbar = protokoll.bearbeitbar
    const fehlerhafte = wartend.filter(e => e.fehler)
    const ausstehend = wartend.filter(e => !e.fehler)

    return (
        <div className="h-full flex flex-col bg-slate-50">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-3 safe-area-top">
                <div className="flex items-center gap-3">
                    <button onClick={() => navigate(-1)} aria-label="Zurück"
                        className="flex-shrink-0 p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95">
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-bold text-slate-900 truncate">{protokoll.nummer}</span>
                            <span className={`text-xs px-2 py-0.5 rounded-full font-medium flex-shrink-0 ${STATUS_FARBE[protokoll.status]}`}>
                                {STATUS_LABEL[protokoll.status]}
                            </span>
                        </div>
                        {protokoll.titel && <p className="text-xs text-slate-500 truncate">{protokoll.titel}</p>}
                    </div>
                    <div className="flex items-center gap-1 flex-shrink-0">
                        {offline && <WifiOff className="w-4 h-4 text-amber-500" aria-label="Offline" />}
                        {abgleichend && <Loader2 className="w-4 h-4 text-slate-400 animate-spin" />}
                        {ausstehend.length > 0 && !abgleichend && (
                            <button onClick={sende} aria-label="Jetzt synchronisieren"
                                className="flex items-center gap-1 text-xs text-amber-600 bg-amber-50 border border-amber-100 px-2 py-1 rounded-lg">
                                <RefreshCw className="w-3 h-3" />
                                {ausstehend.length}
                            </button>
                        )}
                        <button onClick={pdfAnzeigen} disabled={pdfLaed} aria-label="PDF anzeigen"
                            className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95 disabled:opacity-50">
                            {pdfLaed ? <Loader2 className="w-5 h-5 animate-spin text-slate-400" /> : <FileText className="w-5 h-5 text-slate-500" />}
                        </button>
                    </div>
                </div>

                {/* Fortschrittsbalken */}
                {stats.gesamt > 0 && (
                    <div className="mt-2">
                        <div className="flex justify-between text-xs text-slate-500 mb-1">
                            <span>{stats.bewertet} von {stats.gesamt} Prüfpunkten</span>
                            <span>{fortschritt} %</span>
                        </div>
                        <div className="h-1.5 bg-slate-100 rounded-full overflow-hidden">
                            <div className="h-full bg-emerald-500 rounded-full transition-all duration-300"
                                style={{ width: `${fortschritt}%` }} />
                        </div>
                    </div>
                )}
            </header>

            {/* Abgelehnte Änderungen */}
            {fehlerhafte.length > 0 && (
                <div className="mx-4 mt-3 p-3 bg-rose-50 border border-rose-100 rounded-xl space-y-1.5">
                    <div className="flex items-center gap-2 text-sm font-medium text-rose-700">
                        <AlertTriangle className="w-4 h-4" />
                        {fehlerhafte.length} Änderung{fehlerhafte.length > 1 ? 'en' : ''} abgelehnt
                    </div>
                    {fehlerhafte.map(e => (
                        <div key={e.id} className="flex items-start justify-between gap-2 text-xs text-rose-600">
                            <span>{e.fehler}</span>
                            <button onClick={() => e.id != null && fehlerhafteEntfernen(e.id)}
                                className="text-rose-400 hover:text-rose-600 flex-shrink-0">Verwerfen</button>
                        </div>
                    ))}
                </div>
            )}

            {/* Inhalt */}
            <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-4">

                {/* Datenfelder ohne Abschnitt (globale Felder + Modul-Felder ohne Abschnitt-Zuweisung) */}
                {(() => {
                    const unzugeordnet = protokoll.datenfelder
                        .filter(d => d.abschnittId == null)
                        .sort((a, b) => a.sortierung - b.sortierung)
                    if (unzugeordnet.length === 0) return null

                    const modulNamen = new Map<string | null, string>()
                    modulNamen.set(null, 'Allgemein')
                    for (const m of protokoll.module) {
                        modulNamen.set(m.modulKey, m.name)
                    }

                    const gruppen = new Map<string | null, typeof unzugeordnet>()
                    for (const d of unzugeordnet) {
                        const key = d.modulKey ?? null
                        if (!gruppen.has(key)) gruppen.set(key, [])
                        gruppen.get(key)!.push(d)
                    }
                    const sortierteKeys = [...gruppen.keys()].sort((a, b) =>
                        a === null ? -1 : b === null ? 1 : 0
                    )

                    return sortierteKeys.map(key => {
                        const felder = gruppen.get(key)!
                        const sectionLabel = modulNamen.get(key) ?? key ?? 'Allgemein'
                        return (
                            <div key={key ?? '__global__'} className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                                <div className="px-4 py-3 bg-slate-50 border-b border-slate-100">
                                    <p className="font-semibold text-slate-700 text-sm uppercase tracking-wide">{sectionLabel}</p>
                                </div>
                                <div className="divide-y divide-slate-100">
                                    {felder.map(feld => (
                                        <div key={feld.id} className="px-4 py-3">
                                            <label className="block text-sm font-medium text-slate-700 mb-1.5">{feld.label}</label>
                                            {feld.feldtyp === 'TEXT' && (
                                                <input type="text" defaultValue={feld.wert ?? ''}
                                                    disabled={!bearbeitbar}
                                                    onBlur={e => { if (e.target.value !== (feld.wert ?? '')) void setzeFeld(feld.id, e.target.value) }}
                                                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-400 bg-white disabled:bg-slate-50 disabled:text-slate-400" />
                                            )}
                                            {feld.feldtyp === 'JAHR' && (
                                                <input type="number" min={1900} max={2100} defaultValue={feld.wert ?? ''}
                                                    disabled={!bearbeitbar}
                                                    onBlur={e => { if (e.target.value !== (feld.wert ?? '')) void setzeFeld(feld.id, e.target.value) }}
                                                    className="w-32 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-400 bg-white disabled:bg-slate-50 disabled:text-slate-400" />
                                            )}
                                            {feld.feldtyp === 'JA_NEIN' && (
                                                <div className="flex gap-2">
                                                    {(['Ja', 'Nein'] as const).map(opt => (
                                                        <button key={opt} type="button" disabled={!bearbeitbar}
                                                            onClick={() => void setzeFeld(feld.id, opt)}
                                                            className={`px-4 py-2 text-sm rounded-lg font-medium transition-colors disabled:opacity-50 ${
                                                                feld.wert === opt
                                                                    ? opt === 'Ja' ? 'bg-emerald-600 text-white' : 'bg-rose-600 text-white'
                                                                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}>
                                                            {opt}
                                                        </button>
                                                    ))}
                                                </div>
                                            )}
                                            {feld.feldtyp === 'AUSWAHL' && feld.optionen && (
                                                <select defaultValue={feld.wert ?? ''}
                                                    disabled={!bearbeitbar}
                                                    onChange={e => void setzeFeld(feld.id, e.target.value)}
                                                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-400 bg-white disabled:bg-slate-50 disabled:text-slate-400">
                                                    <option value="">– bitte wählen –</option>
                                                    {feld.optionen.map(o => <option key={o} value={o}>{o}</option>)}
                                                </select>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )
                    })
                })()}

                {/* Abschnitte */}
                {protokoll.abschnitte.map(abschnitt => {
                    const pruefpunkte = protokoll.pruefpunkte
                        .filter(p => p.abschnittId === abschnitt.id)
                        .sort((a, b) => a.sortierung - b.sortierung)
                    const datenfelder = protokoll.datenfelder
                        .filter(d => d.abschnittId === abschnitt.id)
                        .sort((a, b) => a.sortierung - b.sortierung)

                    if (pruefpunkte.length === 0 && datenfelder.length === 0) return null

                    return (
                        <div key={abschnitt.id} className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                            <div className="px-4 py-3 bg-slate-50 border-b border-slate-100">
                                <p className="font-semibold text-slate-700 text-sm uppercase tracking-wide">{abschnitt.label}</p>
                            </div>

                            <div className="divide-y divide-slate-100">
                                {/* Datenfelder */}
                                {datenfelder.map(feld => (
                                    <div key={feld.id} className="px-4 py-3">
                                        <label className="block text-sm font-medium text-slate-700 mb-1.5">{feld.label}</label>
                                        {feld.feldtyp === 'TEXT' && (
                                            <input type="text" defaultValue={feld.wert ?? ''}
                                                disabled={!bearbeitbar}
                                                onBlur={e => { if (e.target.value !== (feld.wert ?? '')) void setzeFeld(feld.id, e.target.value) }}
                                                className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-400 bg-white disabled:bg-slate-50 disabled:text-slate-400" />
                                        )}
                                        {feld.feldtyp === 'JAHR' && (
                                            <input type="number" min={1900} max={2100} defaultValue={feld.wert ?? ''}
                                                disabled={!bearbeitbar}
                                                onBlur={e => { if (e.target.value !== (feld.wert ?? '')) void setzeFeld(feld.id, e.target.value) }}
                                                className="w-32 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-400 bg-white disabled:bg-slate-50 disabled:text-slate-400" />
                                        )}
                                        {feld.feldtyp === 'JA_NEIN' && (
                                            <div className="flex gap-2">
                                                {(['Ja', 'Nein'] as const).map(opt => (
                                                    <button key={opt} type="button" disabled={!bearbeitbar}
                                                        onClick={() => void setzeFeld(feld.id, opt)}
                                                        className={`px-4 py-2 text-sm rounded-lg font-medium transition-colors disabled:opacity-50 ${
                                                            feld.wert === opt
                                                                ? opt === 'Ja' ? 'bg-emerald-600 text-white' : 'bg-rose-600 text-white'
                                                                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}>
                                                        {opt}
                                                    </button>
                                                ))}
                                            </div>
                                        )}
                                        {feld.feldtyp === 'AUSWAHL' && feld.optionen && (
                                            <select defaultValue={feld.wert ?? ''}
                                                disabled={!bearbeitbar}
                                                onChange={e => void setzeFeld(feld.id, e.target.value)}
                                                className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-400 bg-white disabled:bg-slate-50 disabled:text-slate-400">
                                                <option value="">– bitte wählen –</option>
                                                {feld.optionen.map(o => <option key={o} value={o}>{o}</option>)}
                                            </select>
                                        )}
                                    </div>
                                ))}

                                {/* Prüfpunkte */}
                                {pruefpunkte.map(pp => {
                                    const offen = aufgeklappt.has(pp.id)
                                    const toggleBemerkung = () => setAufgeklappt(prev => {
                                        const s = new Set(prev)
                                        if (s.has(pp.id)) s.delete(pp.id); else s.add(pp.id)
                                        return s
                                    })
                                    const hatBemerkung = pp.bemerkung && pp.bemerkung.trim().length > 0
                                    return (
                                        <div key={pp.id} className="px-4 py-3">
                                            <div className="flex items-start gap-3">
                                                <div className="flex-1 min-w-0">
                                                    <p className="text-sm text-slate-800 leading-snug">{pp.label}</p>
                                                    {pp.vorherigesErgebnis && (
                                                        <p className="text-xs text-slate-400 mt-0.5">
                                                            Zuletzt: {pp.vorherigesErgebnis === 'IO' ? 'In Ordnung' : pp.vorherigesErgebnis === 'NIO' ? 'Nicht in Ordnung' : 'N/A'}
                                                        </p>
                                                    )}
                                                </div>
                                                {/* IO / NIO / NA Buttons */}
                                                <div className="flex gap-1 flex-shrink-0">
                                                    {(['IO', 'NIO', 'NA'] as const).map(e => (
                                                        <button key={e} type="button" disabled={!bearbeitbar}
                                                            aria-label={e === 'IO' ? 'In Ordnung' : e === 'NIO' ? 'Nicht in Ordnung' : 'Nicht anwendbar'}
                                                            onClick={() => void bewerte(pp.id, pp.ergebnis === e ? null : e, pp.bemerkung ?? null)}
                                                            className={`w-9 h-9 rounded-lg flex items-center justify-center transition-all disabled:opacity-40 ${
                                                                pp.ergebnis === e
                                                                    ? e === 'IO' ? 'bg-emerald-500 text-white'
                                                                        : e === 'NIO' ? 'bg-rose-500 text-white'
                                                                        : 'bg-slate-400 text-white'
                                                                    : 'bg-slate-100 text-slate-400 hover:bg-slate-200'
                                                            }`}>
                                                            {e === 'IO' ? <CheckCircle2 className="w-4 h-4" />
                                                                : e === 'NIO' ? <XCircle className="w-4 h-4" />
                                                                : <MinusCircle className="w-4 h-4" />}
                                                        </button>
                                                    ))}
                                                </div>
                                            </div>

                                            {/* Bemerkung – immer zugänglich bei NIO oder vorhandener Bemerkung */}
                                            {(pp.ergebnis === 'NIO' || hatBemerkung || offen) && bearbeitbar && (
                                                <div className="mt-2">
                                                    <button type="button" onClick={toggleBemerkung}
                                                        className="flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600 mb-1">
                                                        {offen ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                                                        Bemerkung {hatBemerkung ? '(vorhanden)' : ''}
                                                    </button>
                                                    {offen && (
                                                        <textarea rows={2} defaultValue={pp.bemerkung ?? ''}
                                                            onBlur={e => void bewerte(pp.id, pp.ergebnis, e.target.value || null)}
                                                            placeholder="Bemerkung eingeben…"
                                                            className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-400 resize-none" />
                                                    )}
                                                </div>
                                            )}
                                            {!bearbeitbar && hatBemerkung && (
                                                <p className="mt-1 text-xs text-slate-500 italic">{pp.bemerkung}</p>
                                            )}
                                        </div>
                                    )
                                })}
                            </div>
                        </div>
                    )
                })}

                {/* Unterschriften */}
                {bearbeitbar && (
                    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                        <button type="button" onClick={() => setUnterschriftOffen(v => !v)}
                            className="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-slate-50 transition-colors">
                            <div className="flex items-center gap-2">
                                <CheckCheck className="w-4 h-4 text-emerald-600" />
                                <span className="font-semibold text-slate-700 text-sm">Unterschriften</span>
                                {protokoll.unterschriftTechniker?.bild && protokoll.unterschriftKunde?.bild && (
                                    <span className="text-xs bg-emerald-100 text-emerald-700 px-2 py-0.5 rounded-full">Beide vorhanden</span>
                                )}
                            </div>
                            {unterschriftOffen ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
                        </button>

                        {unterschriftOffen && (
                            <div className="px-4 pb-4 space-y-4 border-t border-slate-100">
                                {/* Techniker */}
                                {protokoll.unterschriftTechniker?.bild ? (
                                    <div>
                                        <p className="text-sm font-medium text-slate-700 mb-1">Unterschrift Techniker</p>
                                        <img src={protokoll.unterschriftTechniker.bild} alt="Unterschrift Techniker"
                                            className="h-20 border border-slate-200 rounded-xl bg-white" />
                                        <p className="text-xs text-slate-500 mt-1">{protokoll.unterschriftTechniker.name}</p>
                                    </div>
                                ) : (
                                    <div>
                                        <UnterschriftFeld label="Unterschrift Techniker"
                                            onChange={bild => void unterzeichne('TECHNIKER', bild, 'Techniker')} />
                                    </div>
                                )}
                                {/* Kunde */}
                                {protokoll.unterschriftKunde?.bild ? (
                                    <div>
                                        <p className="text-sm font-medium text-slate-700 mb-1">Unterschrift Kunde</p>
                                        <img src={protokoll.unterschriftKunde.bild} alt="Unterschrift Kunde"
                                            className="h-20 border border-slate-200 rounded-xl bg-white" />
                                        <p className="text-xs text-slate-500 mt-1">{protokoll.unterschriftKunde.name}</p>
                                    </div>
                                ) : (
                                    <div>
                                        <UnterschriftFeld label="Unterschrift Kunde"
                                            onChange={bild => void unterzeichne('KUNDE', bild, 'Kunde')} />
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                )}

                {/* Status-Aktionen */}
                {bearbeitbar && protokoll.moeglicheStatus.length > 0 && (
                    <div className="space-y-2">
                        {protokoll.moeglicheStatus.map(s => (
                            <button key={s} type="button"
                                onClick={() => void statusAendern(s)}
                                className={`w-full py-3 rounded-xl font-medium text-sm transition-colors ${
                                    s === 'ABGESCHLOSSEN'
                                        ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                                        : s === 'IN_BEARBEITUNG'
                                        ? 'bg-amber-500 hover:bg-amber-600 text-white'
                                        : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                                }`}>
                                {s === 'ABGESCHLOSSEN' ? 'Abschließen' : s === 'IN_BEARBEITUNG' ? 'In Bearbeitung' : STATUS_LABEL[s]}
                            </button>
                        ))}
                    </div>
                )}

                {/* Nicht bearbeitbar */}
                {!bearbeitbar && (
                    <div className="space-y-3">
                        <div className="flex items-center gap-2 p-3 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-500">
                            <Wrench className="w-4 h-4 flex-shrink-0" />
                            <span>Dieser Feldeinsatz ist {STATUS_LABEL[protokoll.status].toLowerCase()}.</span>
                        </div>
                        {protokoll.status === 'ABGESCHLOSSEN' && (
                            <button
                                type="button"
                                onClick={() => void statusAendern('IN_BEARBEITUNG')}
                                className="w-full py-3 rounded-xl font-medium text-sm bg-amber-500 hover:bg-amber-600 active:bg-amber-700 text-white transition-colors">
                                Nochmal bearbeiten
                            </button>
                        )}
                    </div>
                )}
            </div>
        </div>
    )
}
