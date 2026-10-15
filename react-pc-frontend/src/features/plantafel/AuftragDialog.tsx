import { useState } from 'react';
import { Loader2, Plus, Trash2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { Label } from '../../components/ui/label';
import { DatePicker } from '../../components/ui/datepicker';
import { useToast } from '../../components/ui/toast';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { entferneAuftrag, setzeAuftragPrioritaet, setzeTermine, type Auftrag, type Prioritaet } from './plantafelApi';
import { anzeigeDatum, anzeigeKurz } from './zeitachse';
import { VerknuepfungsLinks } from './VerknuepfungsLinks';

interface Props {
    offen: boolean;
    auftrag: Auftrag;
    onSchliessen: () => void;
    onAuftrag: (a: Auftrag) => void;
    /** Der Auftrag steht nicht mehr in der Plantafel. */
    onEntfernt: (projektId: number) => void;
    /** Einen Vorgang des Auftrags bearbeiten ({@code null} = neuen anlegen). */
    onVorgang: (projektId: number, vorgangId: number | null) => void;
    onAktion: (aktion: 'dauern' | 'erklaeren') => void;
}

const PRIORITAETEN: { wert: Prioritaet; text: string }[] = [{ wert: 'DRINGEND', text: 'Dringend' }, { wert: 'NORMAL', text: 'Normal (laufender Auftrag)' }, { wert: 'NICHT_DRINGEND', text: 'Nicht dringend' }];
const STAND: Record<string, string> = { GEPLANT: 'geplant', LAEUFT: 'läuft', FERTIG: 'fertig' };
const AMPEL: Record<Auftrag['ampel'], string> = { OK: 'im Plan', KNAPP: 'knapp', VERSPAETET: 'verspätet', OHNE_ZIEL: 'kein Zieltermin' };

/**
 * Der ganze Auftrag in einem Fenster (Klick auf den Auftrag in der Plantafel): Termine, Wichtigkeit für alle Vorgänge, die Vorgänge mit Zugang zum Bearbeiten, Vorgang hinzufügen,
 * Dauern aus dem Angebot, Termin erklären und das Entfernen des Auftrags aus der Plantafel.
 */
export function AuftragDialog(props: Props) {
    return (
        <Dialog open={props.offen} onOpenChange={o => { if (!o) props.onSchliessen(); }} className="w-full max-w-3xl max-h-[90vh]" aria-labelledby="auftrag-titel">
            <DialogContent>{props.offen && <Inhalt {...props} />}</DialogContent>
        </Dialog>
    );
}

function Inhalt({ auftrag, onSchliessen, onAuftrag, onEntfernt, onVorgang, onAktion }: Props) {
    const toast = useToast();
    const bestaetigen = useConfirm();
    const [start, setStart] = useState(auftrag.startDatum ?? '');
    const [ziel, setZiel] = useState(auftrag.zielDatum ?? '');
    const [prio, setPrio] = useState<Prioritaet>(auftrag.vorgaenge.find(v => v.status !== 'FERTIG')?.prioritaet ?? 'NORMAL');
    const [arbeitet, setArbeitet] = useState(false);
    const [fehler, setFehler] = useState('');

    const ausfuehren = async (was: () => Promise<void>) => {
        setFehler('');
        setArbeitet(true);
        try {
            await was();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Das hat nicht geklappt.');
        } finally {
            setArbeitet(false);
        }
    };

    const termineSpeichern = () => ausfuehren(async () => {
        if (start && ziel && ziel < start) throw new Error('Der Zieltermin darf nicht vor dem Start liegen.');
        onAuftrag(await setzeTermine(auftrag.projektId, start || null, ziel || null));
        toast.success('Die Termine sind gespeichert.');
    });
    const prioSpeichern = () => ausfuehren(async () => {
        onAuftrag(await setzeAuftragPrioritaet(auftrag.projektId, prio));
        toast.success('Die Wichtigkeit gilt jetzt für alle offenen Vorgänge.');
    });
    const entfernen = () => ausfuehren(async () => {
        const ja = await bestaetigen({
            title: 'Auftrag aus der Plantafel entfernen',
            message: `Sollen alle ${auftrag.vorgaenge.length} Vorgänge von „${auftrag.bezeichnung}“ aus der Plantafel entfernt werden? Der Auftrag selbst bleibt bestehen und lässt sich später wieder aufnehmen. Die anderen Aufträge rechnen sich neu.`,
            variant: 'danger', confirmLabel: 'Entfernen',
        });
        if (!ja) return;
        await entferneAuftrag(auftrag.projektId);
        toast.success(`„${auftrag.bezeichnung}“ steht nicht mehr in der Plantafel.`);
        onEntfernt(auftrag.projektId);
    });

    return (
        <>
            <DialogHeader>
                <DialogTitle id="auftrag-titel">{auftrag.bezeichnung}</DialogTitle>
                <DialogDescription>
                    {auftrag.auftragsnummer ? `Auftrag ${auftrag.auftragsnummer} · ` : ''}Lieferzeit {anzeigeDatum(auftrag.liefertermin)} · {AMPEL[auftrag.ampel]}{auftrag.ampel === 'VERSPAETET' && auftrag.verspaetungTage ? ` (+${auftrag.verspaetungTage} Arbeitstage)` : ''}
                </DialogDescription>
                <VerknuepfungsLinks projektId={auftrag.projektId} projektName={auftrag.bezeichnung} kundeId={auftrag.kundeId} kundeName={auftrag.kundeName} />
            </DialogHeader>
            <div className="min-h-0 flex-1 space-y-5 overflow-y-auto py-1" data-testid="auftrag-dialog">
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

                <section className="space-y-2" aria-label="Termine">
                    <h4 className="text-sm font-semibold text-slate-900">Termine</h4>
                    <div className="grid gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
                        <div className="space-y-1.5"><Label htmlFor="auftrag-start">Frühester Start</Label><DatePicker id="auftrag-start" value={start} onChange={setStart} placeholder="Heute" aria-label="Frühester Start" /></div>
                        <div className="space-y-1.5"><Label htmlFor="auftrag-ziel">Zieltermin</Label><DatePicker id="auftrag-ziel" value={ziel} onChange={setZiel} placeholder="Nicht festgelegt" aria-label="Zieltermin" /></div>
                        <Button variant="outline" onClick={() => void termineSpeichern()} disabled={arbeitet}>Termine speichern</Button>
                    </div>
                </section>

                <section className="space-y-2" aria-label="Wichtigkeit">
                    <h4 className="text-sm font-semibold text-slate-900">Wie wichtig</h4>
                    <div className="flex flex-wrap items-center gap-2">
                        <select aria-label="Wichtigkeit des Auftrags" value={prio} onChange={e => setPrio(e.target.value as Prioritaet)} className="rounded-lg border border-slate-300 px-2 py-2 text-sm">
                            {PRIORITAETEN.map(p => <option key={p.wert} value={p.wert}>{p.text}</option>)}
                        </select>
                        <Button variant="outline" onClick={() => void prioSpeichern()} disabled={arbeitet}>Für alle Vorgänge übernehmen</Button>
                    </div>
                </section>

                <section className="space-y-2" aria-label="Vorgänge">
                    <div className="flex items-center justify-between">
                        <h4 className="text-sm font-semibold text-slate-900">Vorgänge ({auftrag.vorgaenge.length})</h4>
                        <Button variant="outline" size="sm" onClick={() => onVorgang(auftrag.projektId, null)}><Plus className="mr-1 h-4 w-4" aria-hidden="true" /> Vorgang hinzufügen</Button>
                    </div>
                    {auftrag.vorgaenge.length === 0 ? <p className="text-sm text-slate-600">Dieser Auftrag hat keine Vorgänge.</p> : (
                        <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                            {auftrag.vorgaenge.map(v => (
                                <li key={v.id} className="flex flex-wrap items-center gap-2 px-3 py-2 text-sm" data-testid={`auftrag-vorgang-${v.id}`}>
                                    <span className="h-3 w-3 shrink-0 rounded-sm" style={{ backgroundColor: v.farbe }} aria-hidden="true" />
                                    <span className="min-w-40 flex-1 font-medium text-slate-900">{v.bezeichnung}</span>
                                    <span className="text-xs text-slate-600">{v.start ? `${anzeigeKurz(v.start)} bis ${anzeigeKurz(v.ende)}` : 'nicht gerechnet'}</span>
                                    <span className="text-xs text-slate-600">{v.wartet ? `${v.dauerTage} Arbeitstage` : v.aufwandStunden != null ? `${v.aufwandStunden.toLocaleString('de-DE', { maximumFractionDigits: 1 })} Std.` : ''}</span>
                                    <span className="text-xs text-slate-500">{STAND[v.status] ?? v.status}{v.prioritaet === 'DRINGEND' ? ' · dringend' : ''}</span>
                                    <Button variant="outline" size="sm" onClick={() => onVorgang(auftrag.projektId, v.id)} aria-label={`${v.bezeichnung} bearbeiten`}>Bearbeiten</Button>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>

                <section className="flex flex-wrap gap-2" aria-label="Weitere Aktionen">
                    <Button variant="outline" size="sm" onClick={() => onAktion('dauern')} title="Dauern aus der Arbeitszeit des Angebots vorschlagen">Dauern aus dem Angebot</Button>
                    <Button variant="outline" size="sm" onClick={() => onAktion('erklaeren')} title="Die KI erklärt, wie die Lieferzeit zustande kommt">Termin erklären</Button>
                </section>
            </div>
            <DialogFooter>
                <Button variant="outline" className="mr-auto border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => void entfernen()} disabled={arbeitet}>
                    {arbeitet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Trash2 className="h-4 w-4" aria-hidden="true" />} Aus der Plantafel entfernen
                </Button>
                <Button variant="outline" onClick={onSchliessen}>Schließen</Button>
            </DialogFooter>
        </>
    );
}
