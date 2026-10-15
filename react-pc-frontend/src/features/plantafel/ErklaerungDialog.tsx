import { useEffect, useState } from 'react';
import { Gem, Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { ladeKiErklaerung, type Auftrag } from './plantafelApi';

interface Props { offen: boolean; auftrag: Auftrag; onSchliessen: () => void }

/** Die KI erklärt in einfachen Worten, wie die Lieferzeit eines Auftrags zustande kommt. Die Termine rechnet das Programm, die KI rechnet nichts neu. */
export function ErklaerungDialog(props: Props) {
    return (
        <Dialog open={props.offen} onOpenChange={o => { if (!o) props.onSchliessen(); }} className="w-full max-w-lg" aria-labelledby="erklaerung-titel">
            <DialogContent>{props.offen && <Inhalt {...props} />}</DialogContent>
        </Dialog>
    );
}

type Zustand = { art: 'laedt' } | { art: 'text'; text: string } | { art: 'fehler'; meldung: string };

function Inhalt({ auftrag, onSchliessen }: Props) {
    const [zustand, setZustand] = useState<Zustand>({ art: 'laedt' });
    const projektId = auftrag.projektId;

    useEffect(() => {
        let aktuell = true;
        ladeKiErklaerung(projektId).then(
            e => { if (aktuell) setZustand({ art: 'text', text: e.text }); },
            e => { if (aktuell) setZustand({ art: 'fehler', meldung: e instanceof Error ? e.message : 'Die KI konnte den Termin nicht erklären.' }); },
        );
        return () => { aktuell = false; };
    }, [projektId]);

    return (
        <>
            <DialogHeader>
                <DialogTitle id="erklaerung-titel">Warum dieser Termin?</DialogTitle>
                <DialogDescription>{auftrag.bezeichnung}. Die Termine rechnet das Programm, die KI erklärt sie nur.</DialogDescription>
            </DialogHeader>
            <div className="py-1 text-sm text-slate-800" aria-live="polite">
                {zustand.art === 'laedt' && <p role="status" className="flex items-center gap-2 text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Die KI schaut sich den Auftrag an …</p>}
                {zustand.art === 'fehler' && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-rose-800">{zustand.meldung}</p>}
                {zustand.art === 'text' && (
                    <div className="flex gap-2">
                        <Gem className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" aria-hidden="true" />
                        <p className="whitespace-pre-line" data-testid="erklaerung-text">{zustand.text}</p>
                    </div>
                )}
            </div>
            <DialogFooter><Button variant="outline" onClick={onSchliessen}>Schließen</Button></DialogFooter>
        </>
    );
}
