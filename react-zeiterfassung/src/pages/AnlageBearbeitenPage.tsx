import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Save, Loader2, MapPin, Wrench, Layers, Phone, User } from 'lucide-react'
import {
    ladeAnlage, speichereAnlage, ladeKatalogModule, speichereAnlageModule, speichereObjekt,
    WartungFehler, type AnlageStammdaten, type KatalogModul
} from '../features/wartung/wartungApi'
import { useToast } from '../components/ui/toast'

export default function AnlageBearbeitenPage() {
    const { id } = useParams<{ id: string }>()
    const navigate = useNavigate()
    const toast = useToast()
    const token = localStorage.getItem('zeiterfassung_token') ?? ''

    const [anlage, setAnlage] = useState<AnlageStammdaten | null>(null)
    const [katalog, setKatalog] = useState<KatalogModul[]>([])
    const [loading, setLoading] = useState(true)

    // Anlage-Felder
    const [bezeichnung, setBezeichnung] = useState('')
    const [notizen, setNotizen] = useState('')
    const [intervallMonate, setIntervallMonate] = useState('')
    const [speichertAnlage, setSpeichertAnlage] = useState(false)

    // Modul-Auswahl: modulId → untertypSchluessel | null | undefined (undefined = nicht ausgewählt)
    const [modulAuswahl, setModulAuswahl] = useState<Map<number, string | null>>(new Map())
    const [speichertModule, setSpeichertModule] = useState(false)

    // Standort-Felder
    const [objBezeichnung, setObjBezeichnung] = useState('')
    const [strasse, setStrasse] = useState('')
    const [plz, setPlz] = useState('')
    const [ort, setOrt] = useState('')
    const [ansprechpartner, setAnsprechpartner] = useState('')
    const [telefon, setTelefon] = useState('')
    const [speichertObjekt, setSpeichertObjekt] = useState(false)

    useEffect(() => {
        if (!id) return
        const anId = parseInt(id, 10)
        Promise.all([ladeAnlage(token, anId), ladeKatalogModule(token)])
            .then(([a, kat]) => {
                setAnlage(a)
                setKatalog(kat)
                setBezeichnung(a.bezeichnung)
                setNotizen(a.notizen ?? '')
                setIntervallMonate(a.intervallMonate != null ? String(a.intervallMonate) : '')
                const auswahl = new Map<number, string | null>()
                a.module.forEach(m => auswahl.set(m.modulId, m.bauartSchluessel ?? null))
                setModulAuswahl(auswahl)
                if (a.objekt) {
                    setObjBezeichnung(a.objekt.bezeichnung)
                    setStrasse(a.objekt.strasse ?? '')
                    setPlz(a.objekt.plz ?? '')
                    setOrt(a.objekt.ort ?? '')
                    setAnsprechpartner(a.objekt.ansprechpartnerName ?? '')
                    setTelefon(a.objekt.ansprechpartnerTelefon ?? '')
                }
            })
            .catch(e => {
                toast.error(e instanceof WartungFehler ? e.message : 'Anlage konnte nicht geladen werden.')
                navigate(-1)
            })
            .finally(() => setLoading(false))
    }, [id]) // eslint-disable-line react-hooks/exhaustive-deps

    const speichereAnlageDaten = async () => {
        if (!id || !bezeichnung.trim()) { toast.error('Bitte eine Bezeichnung eingeben.'); return }
        setSpeichertAnlage(true)
        try {
            const updated = await speichereAnlage(token, parseInt(id, 10), {
                bezeichnung: bezeichnung.trim(),
                notizen: notizen.trim() || null,
                intervallMonate: intervallMonate ? parseInt(intervallMonate, 10) : null,
            })
            setAnlage(updated)
            toast.success('Anlage gespeichert.')
        } catch (e) {
            toast.error(e instanceof WartungFehler ? e.message : 'Speichern fehlgeschlagen.')
        } finally {
            setSpeichertAnlage(false)
        }
    }

    const speichereModuleDaten = async () => {
        if (!id) return
        setSpeichertModule(true)
        try {
            const module = Array.from(modulAuswahl.entries()).map(([modulId, untertypSchluessel]) => ({ modulId, untertypSchluessel }))
            const updated = await speichereAnlageModule(token, parseInt(id, 10), module)
            setAnlage(updated)
            const auswahl = new Map<number, string | null>()
            updated.module.forEach(m => auswahl.set(m.modulId, m.bauartSchluessel ?? null))
            setModulAuswahl(auswahl)
            toast.success('Module gespeichert.')
        } catch (e) {
            toast.error(e instanceof WartungFehler ? e.message : 'Speichern fehlgeschlagen.')
        } finally {
            setSpeichertModule(false)
        }
    }

    const speichereObjektDaten = async () => {
        if (!anlage?.objekt) return
        if (!objBezeichnung.trim()) { toast.error('Bitte eine Objektbezeichnung eingeben.'); return }
        setSpeichertObjekt(true)
        try {
            await speichereObjekt(token, anlage.objekt.id, {
                bezeichnung: objBezeichnung.trim(),
                strasse: strasse.trim() || null,
                plz: plz.trim() || null,
                ort: ort.trim() || null,
                ansprechpartnerName: ansprechpartner.trim() || null,
                ansprechpartnerTelefon: telefon.trim() || null,
            })
            toast.success('Standort gespeichert.')
        } catch (e) {
            toast.error(e instanceof WartungFehler ? e.message : 'Speichern fehlgeschlagen.')
        } finally {
            setSpeichertObjekt(false)
        }
    }

    const toggleModul = (modul: KatalogModul, ausgewaehlt: boolean) => {
        setModulAuswahl(prev => {
            const next = new Map(prev)
            if (ausgewaehlt) {
                next.set(modul.id, modul.untertypen.length > 0 ? modul.untertypen[0].schluessel : null)
            } else {
                next.delete(modul.id)
            }
            return next
        })
    }

    const setBauart = (modulId: number, schluessel: string) => {
        setModulAuswahl(prev => { const next = new Map(prev); next.set(modulId, schluessel); return next })
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
                </div>
            </header>

            <div className="flex-1 overflow-y-auto p-4 space-y-4 pb-8">

                {/* Anlage */}
                <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                    <div className="px-4 py-3 bg-slate-50 border-b border-slate-100 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                            <Wrench className="w-4 h-4 text-amber-600" />
                            <span className="font-semibold text-slate-700 text-sm uppercase tracking-wide">Anlage</span>
                        </div>
                        <button onClick={speichereAnlageDaten} disabled={speichertAnlage || !bezeichnung.trim()}
                            className="flex items-center gap-1.5 bg-rose-600 text-white px-3 py-1.5 rounded-lg text-xs font-medium disabled:opacity-50 transition-all active:scale-95">
                            {speichertAnlage ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
                            Speichern
                        </button>
                    </div>
                    <div className="p-4 space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1.5">
                                Bezeichnung <span className="text-rose-500">*</span>
                            </label>
                            <input type="text" value={bezeichnung} onChange={e => setBezeichnung(e.target.value)}
                                placeholder="z. B. Rauchmelder Keller" maxLength={200}
                                className="w-full px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 bg-white" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1.5">Prüfintervall (Monate)</label>
                            <input type="number" min={1} max={120} value={intervallMonate}
                                onChange={e => setIntervallMonate(e.target.value)} placeholder="z. B. 12"
                                className="w-32 px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 bg-white" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1.5">Notizen</label>
                            <textarea rows={4} value={notizen} onChange={e => setNotizen(e.target.value)}
                                maxLength={10000} placeholder="Besonderheiten, Zugänge, Hinweise…"
                                className="w-full px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 resize-none bg-white" />
                        </div>
                    </div>
                </div>

                {/* Module */}
                {katalog.length > 0 && (
                    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                        <div className="px-4 py-3 bg-slate-50 border-b border-slate-100 flex items-center justify-between">
                            <div className="flex items-center gap-2">
                                <Layers className="w-4 h-4 text-violet-600" />
                                <span className="font-semibold text-slate-700 text-sm uppercase tracking-wide">Module</span>
                            </div>
                            <button onClick={speichereModuleDaten} disabled={speichertModule}
                                className="flex items-center gap-1.5 bg-rose-600 text-white px-3 py-1.5 rounded-lg text-xs font-medium disabled:opacity-50 transition-all active:scale-95">
                                {speichertModule ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
                                Speichern
                            </button>
                        </div>
                        <div className="divide-y divide-slate-100">
                            {katalog.map(modul => {
                                const ausgewaehlt = modulAuswahl.has(modul.id)
                                const schluessel = modulAuswahl.get(modul.id) ?? null
                                return (
                                    <div key={modul.id} className="px-4 py-3">
                                        <label className="flex items-start gap-3 cursor-pointer">
                                            <input type="checkbox" checked={ausgewaehlt}
                                                onChange={e => toggleModul(modul, e.target.checked)}
                                                className="mt-0.5 w-4 h-4 rounded border-slate-300 text-rose-600 focus:ring-rose-400 flex-shrink-0" />
                                            <span className="text-sm font-medium text-slate-800 flex-1">{modul.name}</span>
                                        </label>
                                        {ausgewaehlt && modul.untertypen.length > 0 && (
                                            <div className="mt-2 ml-7">
                                                <label className="block text-xs text-slate-500 mb-1">Bauart</label>
                                                <select value={schluessel ?? ''} onChange={e => setBauart(modul.id, e.target.value)}
                                                    className="text-sm border border-slate-200 rounded-lg px-2.5 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-rose-400 w-full max-w-xs">
                                                    {modul.untertypen.map(u => (
                                                        <option key={u.schluessel} value={u.schluessel}>{u.label}</option>
                                                    ))}
                                                </select>
                                            </div>
                                        )}
                                    </div>
                                )
                            })}
                        </div>
                    </div>
                )}

                {/* Standort */}
                {anlage?.objekt && (
                    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                        <div className="px-4 py-3 bg-slate-50 border-b border-slate-100 flex items-center justify-between">
                            <div className="flex items-center gap-2">
                                <MapPin className="w-4 h-4 text-emerald-600" />
                                <span className="font-semibold text-slate-700 text-sm uppercase tracking-wide">Standort</span>
                            </div>
                            <button onClick={speichereObjektDaten} disabled={speichertObjekt || !objBezeichnung.trim()}
                                className="flex items-center gap-1.5 bg-rose-600 text-white px-3 py-1.5 rounded-lg text-xs font-medium disabled:opacity-50 transition-all active:scale-95">
                                {speichertObjekt ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
                                Speichern
                            </button>
                        </div>
                        <div className="p-4 space-y-4">
                            {anlage.kunde && (
                                <div className="flex items-center gap-1.5 text-sm text-slate-500">
                                    <User className="w-3.5 h-3.5 flex-shrink-0" />
                                    <span>{anlage.kunde.name}</span>
                                </div>
                            )}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                                    Objektbezeichnung <span className="text-rose-500">*</span>
                                </label>
                                <input type="text" value={objBezeichnung} onChange={e => setObjBezeichnung(e.target.value)}
                                    maxLength={200}
                                    className="w-full px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 bg-white" />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1.5">Straße</label>
                                <input type="text" value={strasse} onChange={e => setStrasse(e.target.value)} maxLength={200}
                                    className="w-full px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 bg-white" />
                            </div>
                            <div className="grid grid-cols-3 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1.5">PLZ</label>
                                    <input type="text" value={plz} onChange={e => setPlz(e.target.value)} maxLength={20}
                                        className="w-full px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 bg-white" />
                                </div>
                                <div className="col-span-2">
                                    <label className="block text-sm font-medium text-slate-700 mb-1.5">Ort</label>
                                    <input type="text" value={ort} onChange={e => setOrt(e.target.value)} maxLength={120}
                                        className="w-full px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 bg-white" />
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                                    <span className="flex items-center gap-1"><User className="w-3.5 h-3.5" /> Ansprechpartner</span>
                                </label>
                                <input type="text" value={ansprechpartner} onChange={e => setAnsprechpartner(e.target.value)}
                                    maxLength={200}
                                    className="w-full px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 bg-white" />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                                    <span className="flex items-center gap-1"><Phone className="w-3.5 h-3.5" /> Telefon</span>
                                </label>
                                <input type="tel" value={telefon} onChange={e => setTelefon(e.target.value)} maxLength={60}
                                    className="w-full px-3 py-2.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-400 bg-white" />
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    )
}
