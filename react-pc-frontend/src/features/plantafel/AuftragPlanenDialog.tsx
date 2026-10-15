import { useEffect, useState } from 'react';
import { Link, useInRouterContext } from 'react-router-dom';
import { CalendarRange, Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { Label } from '../../components/ui/label';
import { DatePicker } from '../../components/ui/datepicker';
import { useToast } from '../../components/ui/toast';
import { ladeAufnahme, ladePersonen, planeAuftrag, setzeAbFenster, type Aufnahme, type Auftrag, type Person, type Prioritaet } from './plantafelApi';

interface Props {
    offen: boolean;
    projektId: number;
    onSchliessen: () => void;
    /** Wird mit dem geplanten Auftrag aufgerufen, nach „Übernehmen“ und nach „Später“. */
    onGeplant?: (a: Auftrag) => void;
    /** Kommt das Fenster von selbst (bei der Auftragsbestätigung), darf der Benutzer es abschalten. */
    vonSelbst?: boolean;
}

const PRIORITAETEN: { wert: Prioritaet; text: string }[] = [{ wert: 'DRINGEND', text: 'Dringend' }, { wert: 'NORMAL', text: 'Normal (laufender Auftrag)' }, { wert: 'NICHT_DRINGEND', text: 'Nicht dringend' }];
const zahlText = (n: number) => n.toLocaleString('de-DE', { maximumFractionDigits: 2 });

/**
 * Das Fenster zur Planung eines Auftrags: bei der Auftragsbestätigung (kommt von selbst, überspringbar, abschaltbar) und über den Knopf im Projekt.
 * Vorausgefüllt aus dem Auftrag, „Übernehmen“ genügt meist. „Später“ legt die Standardkette mit geschätzten Werten an und hält nichts auf.
 */
export function AuftragPlanenDialog(props: Props) {
    return (
        <Dialog open={props.offen} onOpenChange={o => { if (!o) props.onSchliessen(); }} className="w-full max-w-2xl max-h-[90vh]" aria-labelledby="planen-titel">
            <DialogContent>{props.offen && <Inhalt {...props} />}</DialogContent>
        </Dialog>
    );
}

function PlantafelLink({ klasse, kinder }: { klasse: string; kinder: React.ReactNode }) {
    const imRouter = useInRouterContext();
    return imRouter ? <Link to="/plantafel" className={klasse}>{kinder}</Link> : <a href="/plantafel" className={klasse}>{kinder}</a>;
}

function Inhalt({ projektId, onSchliessen, onGeplant, vonSelbst = false }: Props) {
    const toast = useToast();
    const [daten, setDaten] = useState<Aufnahme | null>(null);
    const [personen, setPersonen] = useState<Person[]>([]);
    const [fehler, setFehler] = useState('');
    const [ziel, setZiel] = useState('');
    const [prioritaet, setPrioritaet] = useState<Prioritaet>('NORMAL');
    const [gewaehlteFremd, setGewaehlteFremd] = useState<Set<number>>(new Set());
    const [montage, setMontage] = useState(false);
    const [montageTage, setMontageTage] = useState('1');
    const [wunsch, setWunsch] = useState<number[]>([]);
    const [nichtMehr, setNichtMehr] = useState(false);
    const [arbeitet, setArbeitet] = useState(false);

    useEffect(() => {
        let aktuell = true;
        Promise.all([ladeAufnahme(projektId), ladePersonen().catch(() => [] as Person[])]).then(([a, p]) => {
            if (!aktuell) return;
            setDaten(a);
            setPersonen(Array.isArray(p) ? p : []);
            setGewaehlteFremd(new Set(a.fremdleistungen.map(f => f.fremdleistungId))); // erkannte Fremdleistungen sind vorausgewählt
        }, e => { if (aktuell) setFehler(e instanceof Error ? e.message : 'Die Angaben zum Auftrag konnten nicht geladen werden.'); });
        return () => { aktuell = false; };
    }, [projektId]);

    const schliessen = async () => {
        if (nichtMehr) {
            try { await setzeAbFenster(false); toast.success('Das Fenster erscheint bei der Auftragsbestätigung nicht mehr. Über „Planen“ im Projekt lässt es sich jederzeit öffnen.'); } catch { /* der Anwender sieht es spätestens beim nächsten Mal */ }
        }
        onSchliessen();
    };

    const senden = async (spaeter: boolean) => {
        if (!daten) return;
        setFehler('');
        const tage = Number(montageTage);
        if (!spaeter && montage && (!Number.isInteger(tage) || tage < 1 || tage > 365)) {
            setFehler('Die Montage dauert eine ganze Zahl von Tagen zwischen 1 und 365.');
            return;
        }
        setArbeitet(true);
        try {
            const auftrag = await planeAuftrag(projektId, {
                spaeter, zielDatum: ziel || null, prioritaet,
                fremdleistungen: daten.fremdleistungen.filter(f => gewaehlteFremd.has(f.fremdleistungId))
                    .map(f => ({ fremdleistungId: f.fremdleistungId, bezeichnung: f.bezeichnung, lieferantId: f.lieferantId, menge: f.menge, einheit: f.einheit })),
                montageVorOrt: montage, montageTage: montage ? tage : null, mitarbeiterIds: wunsch,
            });
            onGeplant?.(auftrag);
            toast.success(spaeter ? 'Der Auftrag steht in der Plantafel mit geschätzten Werten, bis er eingeplant ist.' : 'Der Auftrag ist in der Plantafel geplant.');
            await schliessen();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Der Auftrag konnte nicht geplant werden.');
            setArbeitet(false);
        }
    };

    const umschalten = (id: number) => setGewaehlteFremd(alt => {
        const neu = new Set(alt);
        if (!neu.delete(id)) neu.add(id);
        return neu;
    });
    const wunschUmschalten = (id: number) => setWunsch(alt => (alt.includes(id) ? alt.filter(x => x !== id) : [...alt, id]));

    return (
        <>
            <DialogHeader>
                <DialogTitle id="planen-titel">Auftrag planen</DialogTitle>
                <DialogDescription>
                    {daten ? `${daten.bezeichnung}${daten.auftragsnummer ? ` (${daten.auftragsnummer})` : ''}` : 'Die Angaben werden geladen …'}
                    {vonSelbst ? ' · Die Auftragsbestätigung ist erstellt. Damit die Plantafel rechnen kann, genügen meist diese Angaben.' : ''}
                </DialogDescription>
            </DialogHeader>
            <div className="min-h-0 flex-1 space-y-4 overflow-y-auto py-1">
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                {!daten && !fehler && <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …</p>}
                {daten?.bereitsGeplant && (
                    <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-700" data-testid="schon-geplant">
                        Dieser Auftrag ist schon in der Plantafel. Änderungen macht man dort an den einzelnen Vorgängen.{' '}
                        <PlantafelLink klasse="font-medium text-rose-700 underline" kinder="Zur Plantafel" />
                    </p>
                )}
                {daten && !daten.bereitsGeplant && (
                    <>
                        <div className="grid gap-4 sm:grid-cols-2">
                            <div className="space-y-1.5">
                                <Label htmlFor="planen-ziel">Zieltermin</Label>
                                <DatePicker id="planen-ziel" value={ziel} onChange={setZiel} placeholder="Noch nicht festgelegt" aria-label="Zieltermin" />
                                <p className="text-xs text-slate-500">Der Termin, den der Kunde bekommt. Die Plantafel zeigt, ob er zu halten ist.</p>
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="planen-prio">Wie wichtig</Label>
                                <select id="planen-prio" value={prioritaet} onChange={e => setPrioritaet(e.target.value as Prioritaet)} className="w-full rounded-lg border border-slate-300 px-2 py-2 text-sm">
                                    {PRIORITAETEN.map(p => <option key={p.wert} value={p.wert}>{p.text}</option>)}
                                </select>
                            </div>
                        </div>

                        <fieldset className="space-y-2">
                            <legend className="text-sm font-medium text-slate-900">Fremdleistung</legend>
                            {daten.fremdleistungen.length === 0 ? (
                                <p className="text-sm text-slate-600" data-testid="keine-fremdleistung">Keine Fremdleistung erkannt (zum Beispiel Verzinken oder Pulvern).</p>
                            ) : (
                                <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                                    {daten.fremdleistungen.map(f => (
                                        <li key={f.fremdleistungId} className="px-3 py-2">
                                            <label className="flex items-start gap-2 text-sm">
                                                <input type="checkbox" className="mt-0.5 h-4 w-4 accent-rose-600" checked={gewaehlteFremd.has(f.fremdleistungId)} onChange={() => umschalten(f.fremdleistungId)} />
                                                <span>
                                                    <span className="font-medium text-slate-900">{f.bezeichnung}</span>
                                                    <span className="text-slate-600"> bei {f.lieferantName ?? 'unbekanntem Lieferanten'}, {zahlText(f.menge)} {f.einheit}{f.lieferzeitTage != null ? `, übliche Lieferzeit ${f.lieferzeitTage} Arbeitstage` : ''}</span>
                                                </span>
                                            </label>
                                        </li>
                                    ))}
                                </ul>
                            )}
                            <p className="text-xs text-slate-500">Erkannt aus den Leistungen des {daten.quelle === 'ANGEBOT' ? 'Angebots' : 'Auftrags'}. Hake ab, was nicht gebraucht wird.</p>
                        </fieldset>

                        <fieldset className="space-y-2">
                            <legend className="text-sm font-medium text-slate-900">Montage vor Ort</legend>
                            <div className="flex flex-wrap items-center gap-4 text-sm">
                                <label className="flex items-center gap-2"><input type="radio" name="montage" className="accent-rose-600" checked={!montage} onChange={() => setMontage(false)} /> Nein</label>
                                <label className="flex items-center gap-2"><input type="radio" name="montage" className="accent-rose-600" checked={montage} onChange={() => setMontage(true)} /> Ja</label>
                                {montage && (
                                    <label className="flex items-center gap-2">Dauer
                                        <input aria-label="Montage in Tagen" inputMode="numeric" value={montageTage} onChange={e => setMontageTage(e.target.value)} className="w-16 rounded border border-slate-300 px-2 py-1 text-right" /> Tage
                                    </label>
                                )}
                            </div>
                        </fieldset>

                        {personen.length > 0 && (
                            <fieldset className="space-y-2">
                                <legend className="text-sm font-medium text-slate-900">Wunschpersonen (nur wenn schon klar)</legend>
                                <ul className="grid gap-1 sm:grid-cols-2" aria-label="Wunschpersonen">
                                    {personen.map(p => (
                                        <li key={p.id}>
                                            <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-rose-600" checked={wunsch.includes(p.id)} onChange={() => wunschUmschalten(p.id)} />{p.name}</label>
                                        </li>
                                    ))}
                                </ul>
                                <p className="text-xs text-slate-500">Die Personen machen die Fertigung und die Montage. Ohne Auswahl rechnet die Plantafel mit dem Standardwert („geschätzt“).</p>
                            </fieldset>
                        )}

                        <p className="text-xs text-slate-500">
                            {daten.hatArbeitszeit ? 'Der Aufwand je Abteilung kommt aus der Kalkulation.' : 'Im Auftrag gibt es keine berechnete Arbeitszeit: Der Aufwand ist geschätzt und lässt sich in der Plantafel eintragen.'}
                            {daten.hinweise.length > 0 ? ` ${daten.hinweise[0]}` : ''}
                        </p>
                        {vonSelbst && (
                            <label className="flex items-center gap-2 text-xs text-slate-600">
                                <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={nichtMehr} onChange={e => setNichtMehr(e.target.checked)} /> Dieses Fenster bei der Auftragsbestätigung nicht mehr zeigen
                            </label>
                        )}
                    </>
                )}
            </div>
            <DialogFooter>
                <Button variant="outline" onClick={() => void schliessen()} disabled={arbeitet}>{daten?.bereitsGeplant ? 'Schließen' : 'Abbrechen'}</Button>
                {daten && !daten.bereitsGeplant && (
                    <>
                        <Button variant="outline" onClick={() => void senden(true)} disabled={arbeitet} title="Der Auftrag kommt mit geschätzten Werten in die Plantafel und steht dort als „neu, noch nicht eingeplant“">Später</Button>
                        <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void senden(false)} disabled={arbeitet}>
                            {arbeitet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <CalendarRange className="h-4 w-4" aria-hidden="true" />} Übernehmen
                        </Button>
                    </>
                )}
            </DialogFooter>
        </>
    );
}
