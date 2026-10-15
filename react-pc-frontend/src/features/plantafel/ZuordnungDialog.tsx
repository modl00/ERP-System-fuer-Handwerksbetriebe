import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { ladeZuordnungen, speichereZuordnung, type ArbeitsgangZuordnung, type Vorgangstyp } from './plantafelApi';

interface Props { offen: boolean; typen: Vorgangstyp[]; onSchliessen: () => void }

/**
 * Stempelung als Fortschritt: Zu welcher Vorgangsart zählt die gestempelte Zeit eines Arbeitsgangs? Meist genügt die Abteilung (die Vorgangsart rechnet ihren Aufwand
 * aus einer Abteilung). Teilen sich mehrere Vorgangsarten eine Abteilung, sagt man hier, wohin ein Arbeitsgang gehört.
 */
export function ZuordnungDialog(props: Props) {
    return (
        <Dialog open={props.offen} onOpenChange={o => { if (!o) props.onSchliessen(); }} className="w-full max-w-3xl max-h-[90vh]" aria-labelledby="zuordnung-titel">
            <DialogContent>{props.offen && <Inhalt {...props} />}</DialogContent>
        </Dialog>
    );
}

function Inhalt({ typen, onSchliessen }: Props) {
    const toast = useToast();
    const [liste, setListe] = useState<ArbeitsgangZuordnung[] | null>(null);
    const [fehler, setFehler] = useState('');
    const [arbeitet, setArbeitet] = useState<number | null>(null);
    const arten = typen.filter(t => t.aktiv && !t.wartet);

    useEffect(() => {
        let aktuell = true;
        ladeZuordnungen().then(l => { if (aktuell) setListe(Array.isArray(l) ? l : []); }, e => { if (aktuell) setFehler(e instanceof Error ? e.message : 'Die Zuordnung konnte nicht geladen werden.'); });
        return () => { aktuell = false; };
    }, []);

    const aendere = async (z: ArbeitsgangZuordnung, wert: string) => {
        setArbeitet(z.arbeitsgangId);
        setFehler('');
        try {
            setListe(await speichereZuordnung(z.arbeitsgangId, wert === '' ? null : Number(wert)));
            toast.success(`„${z.arbeitsgang}“ ist zugeordnet. Die Plantafel hat sich neu gerechnet.`);
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Zuordnung konnte nicht gespeichert werden.');
        } finally {
            setArbeitet(null);
        }
    };

    const mehrdeutige = (liste ?? []).filter(z => z.mehrdeutig && z.typId == null).length;
    return (
        <>
            <DialogHeader>
                <DialogTitle id="zuordnung-titel">Gestempelte Zeit zuordnen</DialogTitle>
                <DialogDescription>Die Stunden, die in der Zeiterfassung auf einen Auftrag gestempelt werden, zählen vom Aufwand der Vorgänge ab. Meist erkennt das Programm die Vorgangsart an der Abteilung des Arbeitsgangs. Teilen sich mehrere Vorgangsarten eine Abteilung, legt man hier fest, wohin ein Arbeitsgang gehört.</DialogDescription>
            </DialogHeader>
            <div className="min-h-0 flex-1 space-y-3 overflow-y-auto py-1">
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                {liste === null && !fehler && <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …</p>}
                {mehrdeutige > 0 && <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900" data-testid="mehrdeutig">Bei {mehrdeutige} Arbeitsgängen teilen sich mehrere Vorgangsarten die Abteilung. Ohne Zuordnung zählt die Zeit für die erste Vorgangsart der Abteilung.</p>}
                {liste !== null && liste.length === 0 && <p className="text-sm text-slate-600">Es gibt noch keine Arbeitsgänge.</p>}
                {liste !== null && liste.length > 0 && (
                    <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                        {liste.map(z => (
                            <li key={z.arbeitsgangId} className="flex flex-wrap items-center gap-2 px-3 py-2 text-sm" data-testid={`zuordnung-${z.arbeitsgangId}`}>
                                <span className="min-w-40 flex-1 font-medium text-slate-900">{z.arbeitsgang}</span>
                                <span className="w-40 text-xs text-slate-500">{z.abteilung ?? 'ohne Abteilung'}{z.mehrdeutig ? ' (geteilt)' : ''}</span>
                                <select aria-label={`Vorgangsart für ${z.arbeitsgang}`} value={z.typId ?? ''} disabled={arbeitet === z.arbeitsgangId} onChange={e => void aendere(z, e.target.value)}
                                    className="max-w-56 rounded-lg border border-slate-300 bg-white px-2 py-1 text-sm">
                                    <option value="">automatisch (über die Abteilung)</option>
                                    {arten.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                                </select>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
            <DialogFooter><Button variant="outline" onClick={onSchliessen}>Schließen</Button></DialogFooter>
        </>
    );
}
