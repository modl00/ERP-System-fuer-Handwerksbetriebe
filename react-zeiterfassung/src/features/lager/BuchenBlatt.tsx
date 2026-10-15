import { useEffect, useMemo, useState } from 'react'
import { Loader2, Search, X } from 'lucide-react'
import { DecimalInput } from '../../components/ui/decimal-input'
import { Select } from '../../components/ui/select-custom'
import { useToast } from '../../components/ui/toast'
import { validateDecimalInput } from '../../lib/numberInput'
import { darf, useRechte } from '../rechte/rechte'
import {
    buche, formatMenge, LagerFehler, sucheProjekte, umbuche,
    type LagerBestand, type Lagerort, type ProjektKurz,
} from './lagerApi'

type Vorgang = 'ENTNAHME' | 'ZUGANG' | 'INVENTUR' | 'UMBUCHEN'

const VORGANG: Record<Vorgang, { titel: string; knopf: string; mengeLabel: string }> = {
    ENTNAHME: { titel: 'Entnahme', knopf: 'Entnehmen', mengeLabel: 'Wie viel entnommen?' },
    ZUGANG: { titel: 'Zugang', knopf: 'Einbuchen', mengeLabel: 'Wie viel eingelagert?' },
    INVENTUR: { titel: 'Inventur', knopf: 'Bestand setzen', mengeLabel: 'Gezählter Bestand' },
    UMBUCHEN: { titel: 'Umbuchen', knopf: 'Umbuchen', mengeLabel: 'Wie viel umlagern?' },
}

interface Props {
    token: string
    artikel: LagerBestand
    orte: Lagerort[]
    onFertig: (aktualisiert: LagerBestand) => void
    onClose: () => void
}

/**
 * Buchungsblatt zu einem Artikel: Entnahme (mit Projekt), Zugang, Inventur und Umbuchung. Welche Vorgänge erscheinen, bestimmen die Rechte der
 * Rechteverwaltung (Entnahme und Zugang: Anlegen; Inventur und Umbuchen: Ändern). Der Server prüft sie bei jeder Buchung noch einmal.
 */
export default function BuchenBlatt({ token, artikel, orte, onFertig, onClose }: Props) {
    const toast = useToast()
    const rechte = useRechte()
    const erlaubt = useMemo(() => (['ENTNAHME', 'ZUGANG', 'INVENTUR', 'UMBUCHEN'] as Vorgang[]).filter(v =>
        darf(rechte, 'LAGER', v === 'ENTNAHME' || v === 'ZUGANG' ? 'ANLEGEN' : 'AENDERN')), [rechte])
    const [vorgang, setVorgang] = useState<Vorgang>(erlaubt[0] ?? 'ENTNAHME')
    const einheit = artikel.einheit ?? ''
    const bestandOrt = (ortId: number) => artikel.orte.find(o => o.lagerortId === ortId)?.bestand ?? 0
    const ersterOrtMitBestand = artikel.orte.find(o => o.bestand > 0)?.lagerortId ?? orte[0]?.id
    const [ortId, setOrtId] = useState<string>(ersterOrtMitBestand != null ? String(ersterOrtMitBestand) : '')
    const [nachOrtId, setNachOrtId] = useState('')
    const [menge, setMenge] = useState('')
    const [notiz, setNotiz] = useState('')
    const [projekt, setProjekt] = useState<ProjektKurz | null>(null)
    const [suche, setSuche] = useState('')
    const [treffer, setTreffer] = useState<ProjektKurz[]>([])
    const [laeuft, setLaeuft] = useState(false)
    const [fehler, setFehler] = useState('')

    useEffect(() => { if (!erlaubt.includes(vorgang) && erlaubt[0]) setVorgang(erlaubt[0]) }, [erlaubt, vorgang])

    // Projektsuche nur bei der Entnahme, mit kurzer Wartezeit nach dem Tippen
    useEffect(() => {
        if (vorgang !== 'ENTNAHME' || projekt || suche.trim().length < 2) { setTreffer([]); return }
        let abgebrochen = false
        const t = window.setTimeout(() => {
            sucheProjekte(suche.trim()).then(l => { if (!abgebrochen) setTreffer(l) }).catch(() => { if (!abgebrochen) setTreffer([]) })
        }, 300)
        return () => { abgebrochen = true; window.clearTimeout(t) }
    }, [suche, vorgang, projekt])

    const ortOptionen = orte.map(o => ({ value: String(o.id), label: `${o.name} (${formatMenge(bestandOrt(o.id))} ${einheit})`.trim() }))
    const verfuegbar = ortId ? bestandOrt(Number(ortId)) : 0
    const mengeErgebnis = validateDecimalInput(menge, { label: 'die Menge', min: 0, max: 1_000_000, required: true })
    const mengeZahl = mengeErgebnis.valid ? mengeErgebnis.value : null
    const zuViel = (vorgang === 'ENTNAHME' || vorgang === 'UMBUCHEN') && mengeZahl != null && mengeZahl > verfuegbar

    const absenden = async () => {
        setFehler('')
        if (!mengeErgebnis.valid) { setFehler(mengeErgebnis.message); return }
        if (mengeZahl == null || (vorgang !== 'INVENTUR' && mengeZahl <= 0)) { setFehler('Die Menge muss größer als 0 sein.'); return }
        if (!ortId) { setFehler('Bitte einen Lagerort wählen.'); return }
        if (vorgang === 'UMBUCHEN' && (!nachOrtId || nachOrtId === ortId)) { setFehler('Bitte einen anderen Lagerort als Ziel wählen.'); return }
        setLaeuft(true)
        try {
            const aktualisiert = vorgang === 'UMBUCHEN'
                ? await umbuche(token, { artikelId: artikel.artikelId, vonLagerortId: Number(ortId), nachLagerortId: Number(nachOrtId), menge: mengeZahl, notiz: notiz.trim() || undefined })
                : await buche(token, {
                    art: vorgang === 'ENTNAHME' ? 'ABGANG' : vorgang === 'ZUGANG' ? 'ZUGANG' : 'KORREKTUR',
                    artikelId: artikel.artikelId, lagerortId: Number(ortId), menge: mengeZahl,
                    projektId: vorgang === 'ENTNAHME' ? projekt?.id ?? null : null, notiz: notiz.trim() || undefined,
                })
            toast.success(vorgang === 'UMBUCHEN' ? 'Umgebucht.' : vorgang === 'INVENTUR' ? 'Bestand gesetzt.' : vorgang === 'ZUGANG' ? 'Eingebucht.' : 'Entnommen.')
            onFertig(aktualisiert)
        } catch (e) {
            setFehler(e instanceof LagerFehler ? e.message : 'Das hat nicht geklappt. Bitte noch einmal versuchen.')
        } finally {
            setLaeuft(false)
        }
    }

    if (erlaubt.length === 0) {
        return (
            <Blatt titel={artikel.bezeichnung} onClose={onClose}>
                <p className="text-sm text-slate-600">Du darfst hier nur ansehen. Buchen kann nur, wer das Recht dazu hat (Rechteverwaltung am PC).</p>
            </Blatt>
        )
    }

    return (
        <Blatt titel={artikel.bezeichnung} untertitel={`Bestand: ${formatMenge(artikel.bestand)} ${einheit}`.trim()} onClose={onClose}>
            <div role="tablist" aria-label="Was möchtest du buchen?" className="mb-4 grid grid-cols-2 gap-2">
                {erlaubt.map(v => (
                    <button key={v} role="tab" aria-selected={vorgang === v} onClick={() => { setVorgang(v); setFehler('') }}
                        className={`min-h-11 rounded-xl border px-3 text-sm font-medium ${vorgang === v ? 'border-rose-600 bg-rose-50 text-rose-700' : 'border-slate-200 bg-white text-slate-700'}`}>
                        {VORGANG[v].titel}
                    </button>
                ))}
            </div>

            <label className="mb-1 block text-sm font-medium text-slate-700">{vorgang === 'UMBUCHEN' ? 'Von Lagerort' : 'Lagerort'}</label>
            <Select aria-label="Lagerort" value={ortId} onChange={setOrtId} options={ortOptionen} placeholder="Lagerort wählen" />

            {vorgang === 'UMBUCHEN' && (
                <div className="mt-3">
                    <label className="mb-1 block text-sm font-medium text-slate-700">Nach Lagerort</label>
                    <Select aria-label="Ziel-Lagerort" value={nachOrtId} onChange={setNachOrtId} options={ortOptionen.filter(o => o.value !== ortId)} placeholder="Ziel wählen" />
                </div>
            )}

            <div className="mt-3">
                <DecimalInput label={`${VORGANG[vorgang].mengeLabel}${einheit ? ` (${einheit})` : ''}`} value={menge} onChange={setMenge} min={0} max={1_000_000} placeholder="0" />
                {zuViel && <p className="mt-1 text-sm text-amber-700">Im Lager sind nur {formatMenge(verfuegbar)} {einheit}. Der Bestand würde ins Minus laufen.</p>}
            </div>

            {vorgang === 'ENTNAHME' && (
                <div className="mt-3">
                    <label className="mb-1 block text-sm font-medium text-slate-700">Für welches Projekt? (optional)</label>
                    {projekt ? (
                        <div className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm">
                            <span className="min-w-0 truncate"><strong>{projekt.projektNummer ? `${projekt.projektNummer} · ` : ''}{projekt.name}</strong>{projekt.kundenName ? ` (${projekt.kundenName})` : ''}</span>
                            <button onClick={() => { setProjekt(null); setSuche('') }} aria-label="Projekt entfernen" className="ml-2 rounded p-1 hover:bg-slate-200"><X className="h-4 w-4" /></button>
                        </div>
                    ) : (
                        <>
                            <div className="relative">
                                <Search aria-hidden="true" className="absolute left-3 top-3.5 h-5 w-5 text-slate-400" />
                                <input aria-label="Projekt suchen" value={suche} onChange={e => setSuche(e.target.value)} placeholder="Projekt oder Kunde suchen"
                                    className="min-h-12 w-full rounded-lg border border-slate-200 pl-10 pr-3 text-base outline-none focus:ring-2 focus:ring-rose-500" />
                            </div>
                            {treffer.length > 0 && (
                                <ul className="mt-1 max-h-48 divide-y divide-slate-100 overflow-y-auto rounded-xl border border-slate-200 bg-white">
                                    {treffer.map(p => (
                                        <li key={p.id}>
                                            <button onClick={() => { setProjekt(p); setTreffer([]) }} className="w-full px-3 py-2 text-left text-sm hover:bg-rose-50">
                                                <strong>{p.projektNummer ? `${p.projektNummer} · ` : ''}{p.name}</strong>{p.kundenName ? <span className="text-slate-500"> ({p.kundenName})</span> : null}
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </>
                    )}
                </div>
            )}

            <div className="mt-3">
                <label htmlFor="lager-notiz" className="mb-1 block text-sm font-medium text-slate-700">Notiz (optional)</label>
                <input id="lager-notiz" value={notiz} maxLength={200} onChange={e => setNotiz(e.target.value)}
                    className="min-h-12 w-full rounded-lg border border-slate-200 px-3 text-base outline-none focus:ring-2 focus:ring-rose-500" />
            </div>

            {fehler && <p role="alert" className="mt-3 rounded-xl bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

            <button onClick={() => void absenden()} disabled={laeuft}
                className="mt-4 flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-rose-600 font-medium text-white transition-colors hover:bg-rose-700 disabled:bg-slate-300">
                {laeuft ? <Loader2 className="h-5 w-5 animate-spin" /> : VORGANG[vorgang].knopf}
            </button>
        </Blatt>
    )
}

function Blatt({ titel, untertitel, onClose, children }: { titel: string; untertitel?: string; onClose: () => void; children: React.ReactNode }) {
    return (
        <div role="dialog" aria-label={`Buchen: ${titel}`} className="fixed inset-0 z-40 flex items-end bg-slate-900/50" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
            <div className="max-h-[90vh] w-full overflow-y-auto rounded-t-3xl bg-white p-4 pb-8 shadow-xl">
                <div className="mb-3 flex items-start justify-between gap-3">
                    <div className="min-w-0">
                        <h2 className="truncate text-lg font-semibold text-slate-900">{titel}</h2>
                        {untertitel && <p className="text-sm text-slate-500">{untertitel}</p>}
                    </div>
                    <button onClick={onClose} aria-label="Schließen" className="rounded-full p-2 hover:bg-slate-100"><X className="h-5 w-5" /></button>
                </div>
                {children}
            </div>
        </div>
    )
}
