import { useEffect, useState } from 'react';
import { Briefcase, Clock, FileCheck, Loader2, Lock, Save, Trash2, Truck, User, Users, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { DatePicker } from '../../components/ui/datepicker';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { SearchableSelect } from '../../components/ui/searchable-select';
import { TimeInput, normalizeForgivingTime, validateTimeInput } from '../../components/ui/time-input';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { leseFehlermeldung } from '../../lib/apiFehler';
import { cn } from '../../lib/utils';
import { ladePersonen, ladeTermin, loescheTermin, speichereTermin, type Person } from '../plantafel/plantafelApi';

export const FARB_OPTIONEN = [
    { value: '#dc2626', label: 'Rot' },
    { value: '#ea580c', label: 'Orange' },
    { value: '#ca8a04', label: 'Gelb' },
    { value: '#16a34a', label: 'Grün' },
    { value: '#0891b2', label: 'Türkis' },
    { value: '#2563eb', label: 'Blau' },
    { value: '#7c3aed', label: 'Violett' },
    { value: '#db2777', label: 'Pink' },
];
export const STANDARD_FARBE = '#7c3aed';

function datumLang(datum: string): string {
    const [y, m, d] = datum.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('de-DE', {
        weekday: 'long', day: '2-digit', month: 'long', year: 'numeric',
    });
}

function endeVorBeginn(start: string, ende: string): boolean {
    return start !== '' && ende !== '' && ende < start;
}

export interface TerminModalProps {
    offen: boolean;
    /** null = neuer Termin */
    terminId: number | null;
    /** Von/Bis und optional der Mitarbeiter, der in der Plantafel angeklickt wurde. */
    vorgabe: { von: string; bis: string; mitarbeiterId: number | null };
    onSchliessen: () => void;
    /** Wird nach Speichern oder Löschen aufgerufen. */
    onGeaendert: () => void;
    /**
     * Bereits bekannte Metadaten zum Termin (z. B. aus der Kalender-Listenansicht).
     * Werden als Initialwerte für Verknüpfungs-Labels und Bearbeitungsrechte genutzt.
     */
    eintragHinweis?: {
        projektName?: string | null;
        kundeName?: string | null;
        lieferantName?: string | null;
        anfrageBetreff?: string | null;
        erstellerName?: string | null;
        darfBearbeiten?: boolean;
    } | null;
}

export function TerminModal(props: TerminModalProps) {
    if (!props.offen) return null;
    return (
        <>
            <div className="fixed inset-0 bg-black/50 z-50" onClick={props.onSchliessen} />
            <div className="fixed inset-0 z-50 flex items-center justify-center pointer-events-none">
                <div
                    role="dialog"
                    aria-modal="true"
                    className="bg-white rounded-2xl shadow-2xl w-full max-w-lg mx-4 pointer-events-auto max-h-[90vh] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200"
                >
                    <Inhalt {...props} />
                </div>
            </div>
        </>
    );
}

function Inhalt({ terminId, vorgabe, eintragHinweis, onSchliessen, onGeaendert }: TerminModalProps) {
    const toast = useToast();
    const bestaetigen = useConfirm();
    const neu = terminId == null;

    const [laedt, setLaedt] = useState(!neu);
    const [personen, setPersonen] = useState<Person[]>([]);

    const [titel, setTitel] = useState('');
    const [beschreibung, setBeschreibung] = useState('');
    const [von, setVon] = useState(vorgabe.von);
    const [bis, setBis] = useState(vorgabe.bis);
    const [ganztaegig, setGanztaegig] = useState(vorgabe.bis > vorgabe.von);
    const [startZeit, setStartZeit] = useState('08:00');
    const [endeZeit, setEndeZeit] = useState('17:00');
    const [farbe, setFarbe] = useState(STANDARD_FARBE);

    const [projektId, setProjektId] = useState('');
    const [kundeId, setKundeId] = useState('');
    const [lieferantId, setLieferantId] = useState('');
    const [anfrageId, setAnfrageId] = useState('');
    const [projektName, setProjektName] = useState(eintragHinweis?.projektName ?? null);
    const [kundeName, setKundeName] = useState(eintragHinweis?.kundeName ?? null);
    const [lieferantName, setLieferantName] = useState(eintragHinweis?.lieferantName ?? null);
    const [anfrageBetreff, setAnfrageBetreff] = useState(eintragHinweis?.anfrageBetreff ?? null);

    const [teilnehmerIds, setTeilnehmerIds] = useState<number[]>(
        vorgabe.mitarbeiterId != null ? [vorgabe.mitarbeiterId] : []
    );

    const darfBearbeiten = eintragHinweis?.darfBearbeiten !== false;
    const erstellerName = eintragHinweis?.erstellerName ?? null;

    const [fehler, setFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [loescht, setLoescht] = useState(false);

    useEffect(() => {
        let aktuell = true;
        ladePersonen().then(p => { if (aktuell) setPersonen(p); }, () => undefined);
        if (terminId != null) {
            ladeTermin(terminId).then(t => {
                if (!aktuell) return;
                setTitel(t.titel);
                setBeschreibung(t.beschreibung ?? '');
                setVon(t.datum);
                setBis(t.datumBis ?? t.datum);
                setGanztaegig(t.ganztaegig || t.datumBis != null);
                setStartZeit(t.startZeit?.slice(0, 5) ?? '08:00');
                setEndeZeit(t.endeZeit?.slice(0, 5) ?? '17:00');
                setFarbe(t.farbe ?? STANDARD_FARBE);
                setProjektId(t.projektId?.toString() ?? '');
                setKundeId(t.kundeId?.toString() ?? '');
                setLieferantId(t.lieferantId?.toString() ?? '');
                setAnfrageId(t.anfrageId?.toString() ?? '');
                setTeilnehmerIds(t.teilnehmer.map(x => x.id));
                setLaedt(false);
            }, e => {
                if (!aktuell) return;
                setFehler(e instanceof Error ? e.message : 'Der Termin konnte nicht geladen werden.');
                setLaedt(false);
            });
        }
        return () => { aktuell = false; };
    }, [terminId]);

    const mehrtaegig = bis > von;
    const mitUhrzeit = !ganztaegig && !mehrtaegig;

    const endeFehler = mitUhrzeit && endeVorBeginn(
        normalizeForgivingTime(startZeit) ?? '',
        normalizeForgivingTime(endeZeit) ?? ''
    ) ? 'Das Ende liegt vor dem Beginn.' : '';

    const speichernGesperrt = !titel.trim()
        ? 'Bitte zuerst einen Titel eingeben.'
        : endeFehler ? 'Das Ende liegt vor dem Beginn – bitte die Uhrzeiten prüfen.' : undefined;

    const dialogTitel = neu ? 'Termin anlegen' : darfBearbeiten ? 'Termin bearbeiten' : 'Termin ansehen';
    const kopfzeile = mehrtaegig
        ? `${datumLang(von)} bis ${datumLang(bis)}`
        : von ? datumLang(von) : '';

    const speichern = async () => {
        if (!darfBearbeiten) return;
        if (!titel.trim()) { toast.error('Bitte einen Titel eingeben.'); return; }
        if (!von) { toast.error('Bitte den ersten Tag wählen.'); return; }
        if (bis && bis < von) { toast.error('Das Ende darf nicht vor dem Beginn liegen.'); return; }

        let start: string | null = null;
        let ende: string | null = null;
        if (mitUhrzeit) {
            const startCheck = validateTimeInput(startZeit, { label: 'Beginn', required: true });
            const endeCheck = validateTimeInput(endeZeit, { label: 'Ende', required: true });
            if (!startCheck.valid || !endeCheck.valid) {
                toast.error(!startCheck.valid ? startCheck.message : endeCheck.message ?? 'Uhrzeiten prüfen.');
                return;
            }
            if (endeVorBeginn(startCheck.value, endeCheck.value)) {
                toast.error('Das Ende liegt vor dem Beginn. Bitte die Uhrzeiten prüfen.');
                return;
            }
            start = `${startCheck.value}:00`;
            ende = `${endeCheck.value}:00`;
        }

        setSpeichert(true);
        setFehler('');
        try {
            await speichereTermin(terminId, {
                titel: titel.trim(),
                beschreibung: beschreibung.trim() || null,
                datum: von,
                datumBis: mehrtaegig ? bis : von,
                startZeit: start,
                endeZeit: ende,
                ganztaegig: !mitUhrzeit,
                farbe,
                projektId: projektId ? parseInt(projektId) : null,
                kundeId: kundeId ? parseInt(kundeId) : null,
                lieferantId: lieferantId ? parseInt(lieferantId) : null,
                anfrageId: anfrageId ? parseInt(anfrageId) : null,
                teilnehmerIds,
            });
            toast.success(neu ? 'Der Termin wurde angelegt.' : 'Der Termin wurde gespeichert.');
            onGeaendert();
            onSchliessen();
        } catch (e) {
            const msg = e instanceof Error ? e.message : 'Der Termin konnte nicht gespeichert werden.';
            if (e instanceof Response) {
                leseFehlermeldung(e, msg).then(m => { setFehler(m); setSpeichert(false); });
            } else {
                setFehler(msg);
                setSpeichert(false);
            }
        }
    };

    const loeschen = async () => {
        if (terminId == null || !darfBearbeiten) return;
        if (!(await bestaetigen({
            title: 'Termin löschen',
            message: `„${titel}" wird gelöscht.`,
            confirmLabel: 'Löschen',
            variant: 'danger',
        }))) return;
        setLoescht(true);
        try {
            await loescheTermin(terminId);
            toast.success('Der Termin wurde gelöscht.');
            onGeaendert();
            onSchliessen();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Termin konnte nicht gelöscht werden.');
            setLoescht(false);
        }
    };

    const searchProjekte = async (term: string) => {
        try {
            const res = await fetch(`/api/projekte/simple?q=${encodeURIComponent(term)}&size=50&nurOffene=false`);
            if (!res.ok) return [];
            const data = await res.json() as unknown[];
            return [
                { value: '', label: 'Kein Projekt' },
                ...(Array.isArray(data) ? data : []).map((p: unknown) => {
                    const proj = p as { id: number; bauvorhaben: string; auftragsnummer: string; kunde?: string };
                    return { value: proj.id.toString(), label: proj.bauvorhaben || `Projekt #${proj.id}`, sublabel: `${proj.auftragsnummer || ''}${proj.kunde ? ' • ' + proj.kunde : ''}` || undefined };
                }),
            ];
        } catch { return []; }
    };

    const searchKunden = async (term: string) => {
        try {
            const res = await fetch(`/api/kunden?q=${encodeURIComponent(term)}&size=50`);
            if (!res.ok) return [];
            const data = await res.json() as { kunden?: unknown[] };
            const arr = data?.kunden ?? (Array.isArray(data) ? data : []);
            return [
                { value: '', label: 'Kein Kunde' },
                ...(Array.isArray(arr) ? arr : []).map((k: unknown) => {
                    const ku = k as { id: number; name: string; kundennummer?: string; ort?: string };
                    return { value: ku.id.toString(), label: ku.name, sublabel: [ku.kundennummer, ku.ort].filter(Boolean).join(' • ') || undefined };
                }),
            ];
        } catch { return []; }
    };

    const searchLieferanten = async (term: string) => {
        try {
            const res = await fetch(`/api/lieferanten?q=${encodeURIComponent(term)}&size=50`);
            if (!res.ok) return [];
            const data = await res.json() as { lieferanten?: unknown[] };
            const arr = data?.lieferanten ?? (Array.isArray(data) ? data : []);
            return [
                { value: '', label: 'Kein Lieferant' },
                ...(Array.isArray(arr) ? arr : []).map((l: unknown) => {
                    const li = l as { id: number; lieferantenname: string };
                    return { value: li.id.toString(), label: li.lieferantenname };
                }),
            ];
        } catch { return []; }
    };

    const searchAnfragen = async (term: string) => {
        try {
            const res = await fetch(`/api/anfragen?q=${encodeURIComponent(term)}`);
            if (!res.ok) return [];
            const data = await res.json() as { anfragen?: unknown[] };
            const arr = data?.anfragen ?? (Array.isArray(data) ? data : []);
            return [
                { value: '', label: 'Keine Anfrage' },
                ...(Array.isArray(arr) ? arr : []).map((a: unknown) => {
                    const an = a as { id: number; bauvorhaben: string; kundenName?: string; projektOrt?: string };
                    return { value: an.id.toString(), label: an.bauvorhaben || `Anfrage #${an.id}`, sublabel: [an.kundenName, an.projektOrt].filter(Boolean).join(' • ') || undefined };
                }),
            ];
        } catch { return []; }
    };

    const busy = speichert || loescht;

    return (
        <>
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-slate-200 bg-slate-50 rounded-t-2xl shrink-0">
                <div>
                    <h2 className="text-lg font-bold text-slate-900">{dialogTitel}</h2>
                    {kopfzeile && <p className="text-sm text-slate-500">{kopfzeile}</p>}
                </div>
                <button type="button" aria-label="Dialog schließen" onClick={onSchliessen}
                    className="p-2 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer">
                    <X className="w-5 h-5 text-slate-500" />
                </button>
            </div>

            {/* Body */}
            <div className="p-4 min-h-0 overflow-y-auto flex-1 space-y-4">
                {fehler && (
                    <p role="alert" className="rounded-lg bg-rose-50 border border-rose-200 p-3 text-sm text-rose-800">
                        {fehler}
                    </p>
                )}

                {!darfBearbeiten && (
                    <div role="note" className="flex items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
                        <Lock className="w-4 h-4 mt-0.5 shrink-0 text-slate-500" aria-hidden="true" />
                        <div>
                            <p>Diesen Termin können nur der Ersteller, die Teilnehmer und Admins ändern.</p>
                            {erstellerName && <p className="text-xs text-slate-500 mt-0.5">Erstellt von {erstellerName}</p>}
                        </div>
                    </div>
                )}

                {laedt ? (
                    <p role="status" className="flex items-center gap-2 text-sm text-slate-600">
                        <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />
                        Der Termin wird geladen …
                    </p>
                ) : (
                    <fieldset disabled={!darfBearbeiten} className="space-y-4 min-w-0">
                        <legend className="sr-only">Angaben zum Termin</legend>

                        {/* Titel */}
                        <div>
                            <label htmlFor="tm-titel" className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                                Titel *
                            </label>
                            <input
                                id="tm-titel"
                                type="text"
                                value={titel}
                                onChange={e => setTitel(e.target.value)}
                                placeholder="z.B. Montage, Besprechung, Aufmaß …"
                                autoFocus={darfBearbeiten}
                                className="w-full px-3 py-2 border border-slate-300 rounded-md focus:ring-2 focus:ring-rose-500 focus:border-rose-500 disabled:bg-slate-50 disabled:text-slate-600"
                            />
                        </div>

                        {/* Von / Bis */}
                        <div className="grid gap-4 sm:grid-cols-2">
                            <div className="space-y-1">
                                <label htmlFor="tm-von" className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Von</label>
                                <DatePicker id="tm-von" value={von} onChange={v => { setVon(v); if (bis < v) setBis(v); }} />
                            </div>
                            <div className="space-y-1">
                                <label htmlFor="tm-bis" className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Bis (letzter Tag)</label>
                                <DatePicker id="tm-bis" value={bis} min={von} onChange={setBis} />
                            </div>
                        </div>

                        {/* Ganztägig */}
                        <label className="flex items-center gap-2 text-sm cursor-pointer">
                            <input
                                type="checkbox"
                                className="h-4 w-4 accent-rose-600"
                                checked={ganztaegig || mehrtaegig}
                                disabled={mehrtaegig}
                                onChange={e => setGanztaegig(e.target.checked)}
                            />
                            <span className="text-slate-700">
                                Ganztägig
                                {mehrtaegig && <span className="text-slate-500"> (mehrere Tage sind immer ganztägig)</span>}
                            </span>
                        </label>

                        {/* Uhrzeiten */}
                        {mitUhrzeit && (
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-1">
                                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">
                                        <Clock className="w-3 h-3 inline mr-1" aria-hidden="true" />Von
                                    </label>
                                    <TimeInput aria-label="Uhrzeit von" label="Von" required value={startZeit} onChange={setStartZeit} />
                                </div>
                                <div className="space-y-1">
                                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Bis</label>
                                    <TimeInput aria-label="Uhrzeit bis" label="Bis" required value={endeZeit} onChange={setEndeZeit} error={endeFehler || undefined} />
                                </div>
                            </div>
                        )}

                        {/* Farbe */}
                        <div>
                            <p className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Farbe</p>
                            <div className="flex flex-wrap gap-2" role="group" aria-label="Farbe wählen">
                                {FARB_OPTIONEN.map(f => (
                                    <button
                                        type="button"
                                        key={f.value}
                                        aria-label={f.label}
                                        aria-pressed={farbe === f.value}
                                        onClick={() => setFarbe(f.value)}
                                        className={cn(
                                            'w-8 h-8 rounded-full border-2 transition-all cursor-pointer disabled:cursor-not-allowed',
                                            farbe === f.value ? 'ring-2 ring-offset-2 ring-slate-400 scale-110' : 'hover:scale-110',
                                        )}
                                        style={{ backgroundColor: f.value }}
                                        title={f.label}
                                    />
                                ))}
                            </div>
                        </div>

                        {/* Beschreibung */}
                        <div>
                            <label htmlFor="tm-beschreibung" className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                                Notiz
                            </label>
                            <textarea
                                id="tm-beschreibung"
                                rows={3}
                                value={beschreibung}
                                onChange={e => setBeschreibung(e.target.value)}
                                placeholder="Optionale Details …"
                                className="w-full px-3 py-2 border border-slate-300 rounded-md focus:ring-2 focus:ring-rose-500 focus:border-rose-500 resize-none disabled:bg-slate-50 disabled:text-slate-600"
                            />
                        </div>

                        {/* Verknüpfungen */}
                        <div className="border-t border-slate-200 pt-4 space-y-3">
                            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Verknüpfungen (optional)</p>

                            <div className="flex items-start gap-2">
                                <div className="p-2 bg-rose-50 rounded-lg mt-1"><Briefcase className="w-4 h-4 text-rose-600" /></div>
                                <div className="flex-1">
                                    <SearchableSelect value={projektId} onChange={v => { setProjektId(v); if (!v) setProjektName(null); }}
                                        onSearch={searchProjekte} selectedLabel={projektName ?? undefined}
                                        placeholder="Projekt auswählen …" searchPlaceholder="Projekt suchen …" />
                                </div>
                            </div>

                            <div className="flex items-start gap-2">
                                <div className="p-2 bg-blue-50 rounded-lg mt-1"><User className="w-4 h-4 text-blue-600" /></div>
                                <div className="flex-1">
                                    <SearchableSelect value={kundeId} onChange={v => { setKundeId(v); if (!v) setKundeName(null); }}
                                        onSearch={searchKunden} selectedLabel={kundeName ?? undefined}
                                        placeholder="Kunde auswählen …" searchPlaceholder="Kunde suchen …" />
                                </div>
                            </div>

                            <div className="flex items-start gap-2">
                                <div className="p-2 bg-orange-50 rounded-lg mt-1"><Truck className="w-4 h-4 text-orange-600" /></div>
                                <div className="flex-1">
                                    <SearchableSelect value={lieferantId} onChange={v => { setLieferantId(v); if (!v) setLieferantName(null); }}
                                        onSearch={searchLieferanten} selectedLabel={lieferantName ?? undefined}
                                        placeholder="Lieferant auswählen …" searchPlaceholder="Lieferant suchen …" />
                                </div>
                            </div>

                            <div className="flex items-start gap-2">
                                <div className="p-2 bg-green-50 rounded-lg mt-1"><FileCheck className="w-4 h-4 text-green-600" /></div>
                                <div className="flex-1">
                                    <SearchableSelect value={anfrageId} onChange={v => { setAnfrageId(v); if (!v) setAnfrageBetreff(null); }}
                                        onSearch={searchAnfragen} selectedLabel={anfrageBetreff ?? undefined}
                                        placeholder="Anfrage auswählen …" searchPlaceholder="Anfrage suchen …" />
                                </div>
                            </div>
                        </div>
                    </fieldset>
                )}

                {/* Teilnehmer (außerhalb fieldset, damit die Überschrift + InfoButton immer sichtbar sind) */}
                {!laedt && personen.length > 0 && (
                    <div className="border-t border-slate-200 pt-4">
                        <div className="flex items-center gap-1 mb-3">
                            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Teilnehmer einladen</p>
                            <TeilnehmerInfoButton />
                        </div>
                        <fieldset disabled={!darfBearbeiten} className="flex items-start gap-2 min-w-0">
                            <legend className="sr-only">Teilnehmer auswählen</legend>
                            <div className="p-2 bg-violet-50 rounded-lg mt-1"><Users className="w-4 h-4 text-violet-600" /></div>
                            <div className="flex-1">
                                <div className="grid grid-cols-2 gap-2">
                                    {personen.map(p => (
                                        <label key={p.id} className={cn(
                                            'flex items-center gap-2 p-2 rounded-lg border transition-colors',
                                            darfBearbeiten ? 'cursor-pointer' : 'cursor-not-allowed',
                                            teilnehmerIds.includes(p.id)
                                                ? 'bg-violet-50 border-violet-300'
                                                : 'border-slate-200 hover:bg-slate-50',
                                        )}>
                                            <input
                                                type="checkbox"
                                                checked={teilnehmerIds.includes(p.id)}
                                                onChange={e => setTeilnehmerIds(
                                                    e.target.checked
                                                        ? [...teilnehmerIds, p.id]
                                                        : teilnehmerIds.filter(id => id !== p.id)
                                                )}
                                                className="rounded border-slate-300 text-violet-600 focus:ring-violet-500"
                                            />
                                            <span className="text-sm text-slate-700">{p.name}</span>
                                        </label>
                                    ))}
                                </div>
                                <p className="text-xs text-violet-600 mt-2">
                                    {teilnehmerIds.length > 0
                                        ? `${teilnehmerIds.length} Teilnehmer ausgewählt`
                                        : 'Ohne Auswahl ist es ein Termin der Firma.'}
                                </p>
                            </div>
                        </fieldset>
                    </div>
                )}
            </div>

            {/* Footer */}
            <div className="flex items-center justify-between p-4 border-t border-slate-200 bg-slate-50 rounded-b-2xl shrink-0">
                {!neu && darfBearbeiten ? (
                    <Button variant="ghost" size="sm" onClick={() => void loeschen()} disabled={busy}
                        className="text-red-600 hover:bg-red-50">
                        {loescht
                            ? <Loader2 className="w-4 h-4 motion-safe:animate-spin mr-1" aria-hidden="true" />
                            : <Trash2 className="w-4 h-4 mr-1" aria-hidden="true" />}
                        Löschen
                    </Button>
                ) : <div />}

                {darfBearbeiten ? (
                    <div className="flex gap-2">
                        <Button variant="outline" size="sm" onClick={onSchliessen} disabled={busy}>Abbrechen</Button>
                        <span title={speichernGesperrt}>
                            <Button size="sm" className="bg-rose-600 text-white hover:bg-rose-700"
                                onClick={() => void speichern()} disabled={!!speichernGesperrt || busy}>
                                {speichert
                                    ? <Loader2 className="w-4 h-4 motion-safe:animate-spin mr-1" aria-hidden="true" />
                                    : <Save className="w-4 h-4 mr-1" aria-hidden="true" />}
                                Speichern
                            </Button>
                        </span>
                    </div>
                ) : (
                    <Button variant="outline" size="sm" onClick={onSchliessen}>Schließen</Button>
                )}
            </div>
        </>
    );
}

function TeilnehmerInfoButton() {
    return (
        <InfoButton titel="Teilnehmer">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Den Termin sehen nur Sie (als Ersteller) und die angehakten Teilnehmer. Termine ohne Ersteller und ohne Teilnehmer sind Firmentermine und für alle sichtbar.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Ändern und löschen dürfen den Termin der Ersteller, alle Teilnehmer und Admins. Entfernen Sie einen Teilnehmer, verschwindet der Termin aus dessen Kalender.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
