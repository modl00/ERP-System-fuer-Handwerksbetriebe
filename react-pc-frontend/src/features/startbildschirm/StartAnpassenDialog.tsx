import { useId, useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, Check, Loader2, RotateCcw, X } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { loescheEigenenStart, speichereEigenenStart, speichereStartVorgabe, type StartStand } from './startApi';
import { BAUSTEINE, baustein } from './bausteine';
import { bausteineAus, setzeStartStand, useStart } from './useStart';

interface Props { offen: boolean; onSchliessen: () => void; istAdmin: boolean }

/** „Startbildschirm anpassen“: Bausteine mit Haken wählen und ordnen. Der Admin kann den Aufbau als Vorgabe für alle speichern. */
export function StartAnpassenDialog({ offen, onSchliessen, istAdmin }: Props) {
    const titelId = useId();
    return (
        <Dialog open={offen} onOpenChange={o => { if (!o) onSchliessen(); }} className="w-full max-w-2xl" aria-labelledby={titelId}>
            <Inhalt titelId={titelId} onSchliessen={onSchliessen} istAdmin={istAdmin} />
        </Dialog>
    );
}

function Inhalt({ titelId, onSchliessen, istAdmin }: { titelId: string; onSchliessen: () => void; istAdmin: boolean }) {
    const toast = useToast();
    const confirm = useConfirm();
    const { stand } = useStart();
    const [entwurf, setEntwurf] = useState<string[]>(() => bausteineAus(stand).filter(id => baustein(id)));
    const [arbeitet, setArbeitet] = useState(false);

    const geaendert = useMemo(() => {
        const vorher = bausteineAus(stand).filter(id => baustein(id));
        return vorher.length !== entwurf.length || vorher.some((id, i) => id !== entwurf[i]);
    }, [stand, entwurf]);

    const umschalten = (id: string) => setEntwurf(alt => (alt.includes(id) ? alt.filter(x => x !== id) : [...alt, id]));
    const verschiebe = (index: number, richtung: -1 | 1) => setEntwurf(alt => {
        const ziel = index + richtung;
        if (ziel < 0 || ziel >= alt.length) return alt;
        const neu = [...alt];
        [neu[index], neu[ziel]] = [neu[ziel], neu[index]];
        return neu;
    });

    const ausfuehren = async (aktion: () => Promise<StartStand>, erfolg: string, fehler: string) => {
        setArbeitet(true);
        try {
            setzeStartStand(await aktion());
            toast.success(erfolg);
            return true;
        } catch (e) {
            toast.error(e instanceof Error ? e.message : fehler);
            return false;
        } finally {
            setArbeitet(false);
        }
    };

    const speichern = async () => {
        if (await ausfuehren(() => speichereEigenenStart(entwurf), 'Dein Startbildschirm ist gespeichert.', 'Dein Startbildschirm konnte nicht gespeichert werden.')) onSchliessen();
    };
    const aufVorgabe = async () => {
        if (await ausfuehren(loescheEigenenStart, 'Dein Startbildschirm folgt wieder der Vorgabe.', 'Dein Startbildschirm konnte nicht zurückgesetzt werden.')) onSchliessen();
    };
    const fuerAlle = async () => {
        const ja = await confirm({
            title: 'Als Vorgabe für alle speichern?',
            message: 'Wer seinen Startbildschirm noch nicht selbst eingestellt hat, sieht danach genau diese Auswahl. Eigene Einstellungen einzelner Benutzer bleiben unverändert.',
            confirmLabel: 'Für alle speichern',
            variant: 'warning',
        });
        if (!ja) return;
        if (await ausfuehren(() => speichereStartVorgabe(entwurf), 'Die Vorgabe für alle ist gespeichert.', 'Die Vorgabe konnte nicht gespeichert werden.')) onSchliessen();
    };

    return (
        <DialogContent>
            <DialogHeader>
                <DialogTitle id={titelId}>Startbildschirm anpassen</DialogTitle>
                <DialogDescription>Wähle die Bausteine und bring sie in die Reihenfolge, die dir passt.</DialogDescription>
            </DialogHeader>

            <section aria-label="Deine Bausteine" className="rounded-lg border border-slate-200 bg-slate-50 p-2">
                <h3 className="mb-1 px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Dein Startbildschirm</h3>
                {entwurf.length === 0 && <p className="px-1 py-1 text-sm text-slate-500">Noch nichts ausgewählt. Setze unten einen Haken.</p>}
                <ol className="space-y-0.5">
                    {entwurf.map((id, i) => {
                        const b = baustein(id);
                        const name = b?.titel ?? id;
                        return (
                            <li key={id} className="flex items-center gap-2 rounded-md bg-white px-2 py-1 text-sm">
                                <span className="min-w-0 flex-1 truncate" title={name}>{name}</span>
                                <button type="button" onClick={() => verschiebe(i, -1)} disabled={i === 0} aria-label={`${name} nach vorne`} className="rounded p-1 hover:bg-slate-100 disabled:opacity-30"><ArrowUp className="h-3.5 w-3.5" aria-hidden="true" /></button>
                                <button type="button" onClick={() => verschiebe(i, 1)} disabled={i === entwurf.length - 1} aria-label={`${name} nach hinten`} className="rounded p-1 hover:bg-slate-100 disabled:opacity-30"><ArrowDown className="h-3.5 w-3.5" aria-hidden="true" /></button>
                                <button type="button" onClick={() => umschalten(id)} aria-label={`${name} entfernen`} className="rounded p-1 hover:bg-slate-100"><X className="h-3.5 w-3.5" aria-hidden="true" /></button>
                            </li>
                        );
                    })}
                </ol>
            </section>

            <div className="min-h-0 flex-1 overflow-y-auto">
                <h3 className="mb-1 text-sm font-semibold text-rose-700">Verfügbare Bausteine</h3>
                {BAUSTEINE.map(b => (
                    <label key={b.id} className="flex cursor-pointer items-start gap-3 rounded-md px-2 py-1.5 hover:bg-slate-50">
                        <input type="checkbox" checked={entwurf.includes(b.id)} onChange={() => umschalten(b.id)} aria-label={`${b.titel} auf den Startbildschirm`}
                            className="mt-0.5 h-4 w-4 shrink-0 rounded border-slate-300 accent-rose-600 focus:ring-2 focus:ring-rose-500" />
                        <span className="min-w-0"><span className="block text-sm text-slate-900">{b.titel}</span><span className="block text-xs text-slate-500">{b.beschreibung}</span></span>
                    </label>
                ))}
            </div>

            <DialogFooter className="flex-wrap gap-2">
                <Button type="button" variant="ghost" size="sm" disabled={arbeitet || stand?.eigene == null} onClick={() => void aufVorgabe()}>
                    <RotateCcw className="mr-1.5 h-4 w-4" aria-hidden="true" />Wie die Vorgabe
                </Button>
                {istAdmin && <Button type="button" variant="outline" size="sm" disabled={arbeitet} onClick={() => void fuerAlle()}>Als Vorgabe für alle speichern</Button>}
                <Button type="button" variant="outline" size="sm" disabled={arbeitet} onClick={onSchliessen}>Abbrechen</Button>
                <Button type="button" size="sm" disabled={arbeitet || !geaendert} onClick={() => void speichern()}>
                    {arbeitet ? <Loader2 className="mr-1.5 h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Check className="mr-1.5 h-4 w-4" aria-hidden="true" />}
                    Speichern
                </Button>
            </DialogFooter>
        </DialogContent>
    );
}
