import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Save, Loader2, MapPin, User, Wrench } from 'lucide-react'
import { ladeAnlage, speichereAnlage, WartungFehler, type AnlageStammdaten } from '../features/wartung/wartungApi'
import { useToast } from '../components/ui/toast'

export default function AnlageBearbeitenPage() {
    const { id } = useParams<{ id: string }>()
    const navigate = useNavigate()
    const toast = useToast()
    const token = localStorage.getItem('zeiterfassung_token') ?? ''

    const [anlage, setAnlage] = useState<AnlageStammdaten | null>(null)
    const [loading, setLoading] = useState(true)
    const [speichernd, setSpeichernd] = useState(false)

    const [bezeichnung, setBezeichnung] = useState('')
    const [notizen, setNotizen] = useState('')
    const [intervallMonate, setIntervallMonate] = useState<string>('')

    useEffect(() => {
        if (!id) return
        ladeAnlage(token, parseInt(id, 10))
            .then(a => {
                setAnlage(a)
                setBezeichnung(a.bezeichnung)
                setNotizen(a.notizen ?? '')
                setIntervallMonate(a.intervallMonate != null ? String(a.intervallMonate) : '')
            })
            .catch(e => {
                toast.error(e instanceof WartungFehler ? e.message : 'Anlage konnte nicht geladen werden.')
                navigate(-1)
            })
            .finally(() => setLoading(false))
    }, [id]) // eslint-disable-line react-hooks/exhaustive-deps

    const speichern = async () => {
        if (!id || !bezeichnung.trim()) {
            toast.error('Bitte eine Bezeichnung eingeben.')
            return
        }
        setSpeichernd(true)
        try {
            const updated = await speichereAnlage(token, parseInt(id, 10), {
                bezeichnung: bezeichnung.trim(),
                notizen: notizen.trim() || null,
                intervallMonate: intervallMonate ? parseInt(intervallMonate, 10) : null,
            })
            setAnlage(updated)
            toast.success('Gespeichert.')
            navigate(-1)
        } catch (e) {
            toast.error(e instanceof WartungFehler ? e.message : 'Speichern ist fehlgeschlagen.')
        } finally {
            setSpeichernd(false)
        }
    }

    if (loading) {
        return (
            <div className="h-full flex flex-col bg-slate-50">
                <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                    <div className="flex items-center gap-3">
                        <button onClick={() => navigate(-1)} aria-label="Zurück"
                            className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95">
                            <ArrowLeft className="w-5 h-5 text-slate-600" />
                        </button>
                        <span className="font-bold text-slate-900">Anlage bearbeiten</span>
                    </div>
                </header>
                <div className="flex-1 flex justify-center items-center">
                    <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                </div>
            </div>
        )
    }

    return (
        <div className="h-full flex flex-col bg-slate-50">
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center gap-3">
                    <button onClick={() => navigate(-1)} aria-label="Zurück"
                        className="flex-shrink-0 p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95">
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="flex-1 min-w-0">
                        <h1 className="font-bold text-slate-900">Anlage bearbeiten</h1>
                        {anlage?.objekt && (
                            <p className="text-sm text-slate-500 truncate">{anlage.objekt.bezeichnung}</p>
                        )}
                    </div>
                    <button onClick={speichern} disabled={speichernd || !bezeichnung.trim()}
                        className="flex items-center gap-1.5 bg-rose-600 text-white px-3 py-2 rounded-lg text-sm font-medium disabled:opacity-50 transition-all active:scale-95">
                        {speichernd ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                        Speichern
                    </button>
                </div>
            </header>

            <div className="flex-1 overflow-y-auto p-4 space-y-4">

                {/* Pflichtfelder */}
                <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                    <div className="px-4 py-3 bg-slate-50 border-b border-slate-100">
                        <div className="flex items-center gap-2">
                            <Wrench className="w-4 h-4 text-amber-600" />
                            <span className="font-semibold text-slate-700 text-sm uppercase tracking-wide">Anlage</span>
                        </div>
                    </div>
                    <div className="p-4 space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1.5">
                                Bezeichnung <span className="text-rose-500">*</span>
                            </label>
                            <input
                                type="text"
                                value={bezeichnung}
                                onChange={e => setBezeichnung(e.target.value)}
                                placeholder="z. B. Rauchmelder Keller"
                                maxLength={200}
                                className="w-full px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 bg-white"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1.5">
                                Prüfintervall (Monate)
                            </label>
                            <input
                                type="number"
                                min={1}
                                max={120}
                                value={intervallMonate}
                                onChange={e => setIntervallMonate(e.target.value)}
                                placeholder="z. B. 12"
                                className="w-32 px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 bg-white"
                            />
                            {intervallMonate && anlage?.naechsteFaelligkeit && (
                                <p className="text-xs text-slate-400 mt-1">
                                    Nächste Fälligkeit: {new Date(anlage.naechsteFaelligkeit).toLocaleDateString('de-DE')}
                                </p>
                            )}
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1.5">Notizen</label>
                            <textarea
                                rows={4}
                                value={notizen}
                                onChange={e => setNotizen(e.target.value)}
                                maxLength={10000}
                                placeholder="Besonderheiten, Zugänge, Hinweise…"
                                className="w-full px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 resize-none bg-white"
                            />
                        </div>
                    </div>
                </div>

                {/* Nur-Lese-Infos: Objekt und Kunde */}
                {anlage && (anlage.objekt || anlage.kunde) && (
                    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                        <div className="px-4 py-3 bg-slate-50 border-b border-slate-100">
                            <div className="flex items-center gap-2">
                                <MapPin className="w-4 h-4 text-slate-400" />
                                <span className="font-semibold text-slate-700 text-sm uppercase tracking-wide">Standort</span>
                                <span className="text-xs text-slate-400">(nur am PC änderbar)</span>
                            </div>
                        </div>
                        <div className="p-4 space-y-3">
                            {anlage.objekt && (
                                <div>
                                    <p className="text-xs font-medium text-slate-400 uppercase tracking-wide mb-0.5">Objekt</p>
                                    <p className="text-sm text-slate-800">{anlage.objekt.bezeichnung}</p>
                                    {(anlage.objekt.strasse || anlage.objekt.ort) && (
                                        <p className="text-xs text-slate-500 mt-0.5">
                                            {[anlage.objekt.strasse, [anlage.objekt.plz, anlage.objekt.ort].filter(Boolean).join(' ')].filter(Boolean).join(', ')}
                                        </p>
                                    )}
                                </div>
                            )}
                            {anlage.kunde && (
                                <div>
                                    <p className="text-xs font-medium text-slate-400 uppercase tracking-wide mb-0.5">Kunde</p>
                                    <div className="flex items-center gap-1.5">
                                        <User className="w-3.5 h-3.5 text-slate-400" />
                                        <p className="text-sm text-slate-800">{anlage.kunde.name}</p>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {/* Module (nur lesend) */}
                {anlage && anlage.module.length > 0 && (
                    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                        <div className="px-4 py-3 bg-slate-50 border-b border-slate-100">
                            <span className="font-semibold text-slate-700 text-sm uppercase tracking-wide">Module</span>
                            <span className="text-xs text-slate-400 ml-2">(nur am PC änderbar)</span>
                        </div>
                        <div className="divide-y divide-slate-100">
                            {anlage.module.map(m => (
                                <div key={m.modulId} className="px-4 py-3">
                                    <p className="text-sm text-slate-800">{m.name}</p>
                                    {m.bauart && <p className="text-xs text-slate-500">{m.bauart}</p>}
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    )
}
