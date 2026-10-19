import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Wrench, ChevronRight, Loader2, Clock, AlertCircle } from 'lucide-react'
import { ladeAnlagen, starteFeldeinsatz, type ProjektWartung, WartungFehler } from '../features/wartung/wartungApi'
import { useToast } from '../components/ui/toast'

export default function WartungAnlagenPage() {
    const { projektId } = useParams<{ projektId: string }>()
    const navigate = useNavigate()
    const toast = useToast()
    const token = localStorage.getItem('zeiterfassung_token') ?? ''

    const [anlagen, setAnlagen] = useState<ProjektWartung[]>([])
    const [loading, setLoading] = useState(true)
    const [startend, setStartend] = useState<number | null>(null)

    useEffect(() => {
        if (!projektId) return
        ladeAnlagen(token, projektId)
            .then(setAnlagen)
            .catch(e => toast.error(e instanceof Error ? e.message : 'Wartungsanlagen konnten nicht geladen werden.'))
            .finally(() => setLoading(false))
    }, [projektId]) // eslint-disable-line react-hooks/exhaustive-deps

    const oeffne = async (anlage: ProjektWartung) => {
        if (startend != null) return
        setStartend(anlage.id)
        try {
            const p = await starteFeldeinsatz(token, anlage.id)
            navigate(`/wartung/protokoll/${p.id}`)
        } catch (e) {
            toast.error(e instanceof WartungFehler ? e.message : 'Feldeinsatz konnte nicht gestartet werden.')
            setStartend(null)
        }
    }

    const faelligkeitBadge = (datum: string | null): { text: string; klasse: string } | null => {
        if (!datum) return null
        const diff = Math.round((new Date(datum).getTime() - Date.now()) / 86_400_000)
        if (diff < 0) return { text: `${Math.abs(diff)} T. überfällig`, klasse: 'bg-rose-100 text-rose-700' }
        if (diff === 0) return { text: 'Heute fällig', klasse: 'bg-amber-100 text-amber-700' }
        if (diff <= 14) return { text: `in ${diff} T. fällig`, klasse: 'bg-amber-100 text-amber-700' }
        return { text: new Date(datum).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }), klasse: 'bg-emerald-100 text-emerald-700' }
    }

    return (
        <div className="h-full flex flex-col bg-slate-50">
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center gap-3">
                    <button onClick={() => navigate(-1)} aria-label="Zurück"
                        className="flex-shrink-0 p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95">
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="w-10 h-10 flex-shrink-0 bg-amber-50 rounded-lg flex items-center justify-center">
                        <Wrench className="w-5 h-5 text-amber-600" />
                    </div>
                    <div className="min-w-0">
                        <h1 className="font-bold text-slate-900">Wartung</h1>
                        <p className="text-sm text-slate-500">Anlage auswählen</p>
                    </div>
                </div>
            </header>

            <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-2">
                {loading ? (
                    <div className="flex justify-center py-12">
                        <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                    </div>
                ) : anlagen.length === 0 ? (
                    <div className="text-center py-12 text-slate-400">
                        <Wrench className="w-12 h-12 mx-auto mb-3 opacity-30" />
                        <p className="font-medium">Keine Wartungsanlagen</p>
                        <p className="text-sm mt-1">Für dieses Projekt sind keine Anlagen hinterlegt.</p>
                    </div>
                ) : (
                    anlagen.map(anlage => {
                        const badge = faelligkeitBadge(anlage.naechsteFaelligkeit)
                        const offen = anlage.offenerFeldeinsatzId != null
                        const istStartend = startend === anlage.id
                        return (
                            <button key={anlage.id} onClick={() => oeffne(anlage)}
                                disabled={startend != null}
                                className="w-full bg-white border border-slate-200 rounded-xl p-4 text-left transition-all hover:border-amber-200 hover:shadow-sm active:scale-98 disabled:opacity-60">
                                <div className="flex items-center gap-3">
                                    <div className="w-12 h-12 bg-amber-50 rounded-xl flex items-center justify-center flex-shrink-0">
                                        {istStartend
                                            ? <Loader2 className="w-5 h-5 text-amber-600 animate-spin" />
                                            : <Wrench className="w-5 h-5 text-amber-600" />}
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="font-medium text-slate-900 truncate">{anlage.bezeichnung}</p>
                                        {anlage.objektBezeichnung && (
                                            <p className="text-sm text-slate-500 truncate">{anlage.objektBezeichnung}</p>
                                        )}
                                        {anlage.kundeName && (
                                            <p className="text-xs text-slate-400 truncate">{anlage.kundeName}</p>
                                        )}
                                        <div className="flex flex-wrap items-center gap-1.5 mt-1.5">
                                            {offen && (
                                                <span className="inline-flex items-center gap-1 text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded-full font-medium">
                                                    <Clock className="w-3 h-3" />
                                                    Läuft: {anlage.offenerFeldeinsatzNummer}
                                                </span>
                                            )}
                                            {badge && (
                                                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${badge.klasse}`}>
                                                    {badge.text}
                                                </span>
                                            )}
                                            {anlage.anzahlPruefungen > 0 && (
                                                <span className="text-xs text-slate-400">
                                                    {anlage.anzahlPruefungen} Prüfung{anlage.anzahlPruefungen !== 1 ? 'en' : ''}
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                    <ChevronRight className="w-4 h-4 text-slate-400 flex-shrink-0" />
                                </div>
                            </button>
                        )
                    })
                )}
            </div>

            {anlagen.some(a => a.naechsteFaelligkeit && new Date(a.naechsteFaelligkeit) < new Date()) && (
                <div className="px-4 pb-4 safe-area-bottom">
                    <div className="flex items-start gap-2 p-3 bg-rose-50 border border-rose-100 rounded-xl text-sm text-rose-700">
                        <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                        <span>Mindestens eine Anlage ist überfällig.</span>
                    </div>
                </div>
            )}
        </div>
    )
}
