import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { Label } from '../../components/ui/label';
import { DatePicker } from '../../components/ui/datepicker';
import { useToast } from '../../components/ui/toast';
import { setzeTermine, type Auftrag } from './plantafelApi';

interface Props { offen: boolean; auftrag: Auftrag; onSchliessen: () => void; onAuftrag: (a: Auftrag) => void }

/** Frühester Start und Zieltermin (der versprochene Liefertermin) eines Auftrags; an ihm misst sich die Ampel. */
export function TermineDialog(props: Props) {
    return (
        <Dialog open={props.offen} onOpenChange={o => { if (!o) props.onSchliessen(); }} className="w-full max-w-md" aria-labelledby="termine-titel">
            <DialogContent>{props.offen && <Inhalt {...props} />}</DialogContent>
        </Dialog>
    );
}

function Inhalt({ auftrag, onSchliessen, onAuftrag }: Props) {
    const toast = useToast();
    const [start, setStart] = useState(auftrag.startDatum ?? '');
    const [ziel, setZiel] = useState(auftrag.zielDatum ?? '');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');

    const speichern = async () => {
        setFehler('');
        if (start && ziel && ziel < start) {
            setFehler('Der Zieltermin darf nicht vor dem Start liegen.');
            return;
        }
        setSpeichert(true);
        try {
            onAuftrag(await setzeTermine(auftrag.projektId, start || null, ziel || null));
            toast.success('Die Termine wurden gespeichert.');
            onSchliessen();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Termine konnten nicht gespeichert werden.');
            setSpeichert(false);
        }
    };

    return (
        <>
            <DialogHeader>
                <DialogTitle id="termine-titel">Termine des Auftrags</DialogTitle>
                <DialogDescription>{auftrag.bezeichnung}</DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-1">
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                <div className="space-y-1.5">
                    <Label htmlFor="termin-start">Frühester Start</Label>
                    <DatePicker id="termin-start" value={start} onChange={setStart} placeholder="Heute" aria-label="Frühester Start" />
                    <p className="text-xs text-slate-500">Leer: Die Planung beginnt heute.</p>
                </div>
                <div className="space-y-1.5">
                    <Label htmlFor="termin-ziel">Zieltermin (versprochener Liefertermin)</Label>
                    <DatePicker id="termin-ziel" value={ziel} onChange={setZiel} placeholder="Kein Zieltermin" aria-label="Zieltermin" />
                    <p className="text-xs text-slate-500">Daran misst sich die Ampel: „Knapp" heißt weniger als 2 Arbeitstage Luft, „Verspätet" heißt, die errechnete Lieferzeit liegt danach.</p>
                </div>
            </div>
            <DialogFooter>
                <Button variant="outline" onClick={onSchliessen} disabled={speichert}>Abbrechen</Button>
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void speichern()} disabled={speichert}>
                    {speichert && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Speichern
                </Button>
            </DialogFooter>
        </>
    );
}
