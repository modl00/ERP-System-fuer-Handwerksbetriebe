import { useEffect, useState } from 'react';
import { VerknuepfungsLinks } from './VerknuepfungsLinks';
import { Loader2, Trash2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { Label } from '../../components/ui/label';
import { Input } from '../../components/ui/input';
import { DatePicker } from '../../components/ui/datepicker';
import { TimeInput } from '../../components/ui/time-input';
import { useToast } from '../../components/ui/toast';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { ladePersonen, ladeTermin, loescheTermin, speichereTermin, type Person, type Termin } from './plantafelApi';
import { anzeigeDatum } from './zeitachse';

interface Props {
    offen: boolean;
    /** Leer = neuer Termin. */
    terminId: number | null;
    /** Bei einem neuen Termin: die in der Plantafel gewählten Tage und die Zeile (Mitarbeiter), in der geklickt wurde. */
    vorgabe: { von: string; bis: string; mitarbeiterId: number | null };
    onSchliessen: () => void;
    /** Nach Speichern oder Löschen: Die Ansicht lädt neu. */
    onGeaendert: () => void;
}

/**
 * Termin in der Plantafel anlegen, ändern oder löschen. Ein Klick auf einen Tag legt einen Termin für den Tag an, ein Aufziehen über mehrere Tage einen durchgehenden
 * Termin über alle gewählten Tage (ganztägig). Der Termin steht beim gewählten Mitarbeiter; ohne Mitarbeiter ist es ein Termin der Firma.
 */
export function TerminDialog(props: Props) {
    return (
        <Dialog open={props.offen} onOpenChange={o => { if (!o) props.onSchliessen(); }} className="w-full max-w-lg max-h-[90vh]" aria-labelledby="termin-titel">
            <DialogContent>{props.offen && <Inhalt {...props} />}</DialogContent>
        </Dialog>
    );
}

function Inhalt({ terminId, vorgabe, onSchliessen, onGeaendert }: Props) {
    const toast = useToast();
    const bestaetigen = useConfirm();
    const neu = terminId == null;
    const [laedt, setLaedt] = useState(!neu);
    const [alt, setAlt] = useState<Termin | null>(null);
    const [personen, setPersonen] = useState<Person[]>([]);
    const [titel, setTitel] = useState('');
    const [beschreibung, setBeschreibung] = useState('');
    const [von, setVon] = useState(vorgabe.von);
    const [bis, setBis] = useState(vorgabe.bis);
    const [ganztaegig, setGanztaegig] = useState(vorgabe.bis > vorgabe.von);
    const [startZeit, setStartZeit] = useState('08:00');
    const [endeZeit, setEndeZeit] = useState('09:00');
    const [teilnehmerIds, setTeilnehmerIds] = useState<number[]>(vorgabe.mitarbeiterId != null ? [vorgabe.mitarbeiterId] : []);
    const [fehler, setFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        let aktuell = true;
        ladePersonen().then(p => { if (aktuell) setPersonen(p); }, () => undefined);
        if (terminId != null) {
            ladeTermin(terminId).then(t => {
                if (!aktuell) return;
                setAlt(t);
                setTitel(t.titel);
                setBeschreibung(t.beschreibung ?? '');
                setVon(t.datum);
                setBis(t.datumBis ?? t.datum);
                setGanztaegig(t.ganztaegig || t.datumBis != null);
                setStartZeit(t.startZeit?.slice(0, 5) ?? '08:00');
                setEndeZeit(t.endeZeit?.slice(0, 5) ?? '09:00');
                setTeilnehmerIds(t.teilnehmer.map(x => x.id));
                setLaedt(false);
            }, e => { if (aktuell) { setFehler(e instanceof Error ? e.message : 'Der Termin konnte nicht geladen werden.'); setLaedt(false); } });
        }
        return () => { aktuell = false; };
    }, [terminId]);

    const mehrtaegig = bis > von;
    const umschalten = (id: number) => setTeilnehmerIds(alt => (alt.includes(id) ? alt.filter(x => x !== id) : [...alt, id]));

    const speichern = async () => {
        if (!titel.trim()) { setFehler('Bitte einen Titel eingeben.'); return; }
        if (!von) { setFehler('Bitte den ersten Tag wählen.'); return; }
        if (bis && bis < von) { setFehler('Das Ende darf nicht vor dem Beginn liegen.'); return; }
        const mitUhrzeit = !ganztaegig && !mehrtaegig;
        if (mitUhrzeit && endeZeit <= startZeit) { setFehler('Das Ende muss nach dem Beginn liegen.'); return; }
        setSpeichert(true);
        setFehler('');
        try {
            await speichereTermin(terminId, {
                titel: titel.trim(), beschreibung: beschreibung.trim() || null, datum: von, datumBis: mehrtaegig ? bis : von,
                startZeit: mitUhrzeit ? startZeit : null, endeZeit: mitUhrzeit ? endeZeit : null, ganztaegig: !mitUhrzeit,
                farbe: alt?.farbe ?? null, projektId: alt?.projektId ?? null, kundeId: alt?.kundeId ?? null, lieferantId: alt?.lieferantId ?? null, anfrageId: alt?.anfrageId ?? null,
                teilnehmerIds,
            });
            toast.success(neu ? 'Der Termin wurde angelegt.' : 'Der Termin wurde gespeichert.');
            onGeaendert();
            onSchliessen();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Der Termin konnte nicht gespeichert werden.');
            setSpeichert(false);
        }
    };

    const loeschen = async () => {
        if (terminId == null) return;
        if (!(await bestaetigen({ title: 'Termin löschen?', message: `„${titel}" wird gelöscht.`, confirmLabel: 'Termin löschen', variant: 'danger' }))) return;
        setSpeichert(true);
        try {
            await loescheTermin(terminId);
            toast.success('Der Termin wurde gelöscht.');
            onGeaendert();
            onSchliessen();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Der Termin konnte nicht gelöscht werden.');
            setSpeichert(false);
        }
    };

    return (
        <>
            <DialogHeader>
                <DialogTitle id="termin-titel">{neu ? 'Termin anlegen' : 'Termin bearbeiten'}</DialogTitle>
                <VerknuepfungsLinks projektId={alt?.projektId} kundeId={alt?.kundeId} />
                <DialogDescription>{mehrtaegig ? `${anzeigeDatum(von)} bis ${anzeigeDatum(bis)}` : anzeigeDatum(von)}</DialogDescription>
            </DialogHeader>
            <div className="min-h-0 flex-1 space-y-4 overflow-y-auto py-1">
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                {laedt ? <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Der Termin wird geladen …</p> : (
                    <>
                        <div className="space-y-1.5">
                            <Label htmlFor="termin-titel-feld">Titel</Label>
                            <Input id="termin-titel-feld" value={titel} onChange={e => setTitel(e.target.value)} placeholder="Zum Beispiel Aufmaß, Messe, Montage vor Ort" autoFocus />
                        </div>
                        <div className="grid gap-4 sm:grid-cols-2">
                            <div className="space-y-1.5">
                                <Label htmlFor="termin-von">Von</Label>
                                <DatePicker id="termin-von" value={von} onChange={v => { setVon(v); if (bis < v) setBis(v); }} aria-label="Erster Tag" />
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="termin-bis">Bis (letzter Tag)</Label>
                                <DatePicker id="termin-bis" value={bis} min={von} onChange={setBis} aria-label="Letzter Tag" />
                            </div>
                        </div>
                        <label className="flex items-center gap-2 text-sm">
                            <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={ganztaegig || mehrtaegig} disabled={mehrtaegig} onChange={e => setGanztaegig(e.target.checked)} />
                            Ganztägig{mehrtaegig ? ' (ein Termin über mehrere Tage ist immer ganztägig)' : ''}
                        </label>
                        {!ganztaegig && !mehrtaegig && (
                            <div className="grid gap-4 sm:grid-cols-2">
                                <div className="space-y-1.5"><Label htmlFor="termin-start">Beginn</Label><TimeInput id="termin-start" value={startZeit} onChange={setStartZeit} label="Beginn" /></div>
                                <div className="space-y-1.5"><Label htmlFor="termin-ende">Ende</Label><TimeInput id="termin-ende" value={endeZeit} onChange={setEndeZeit} label="Ende" /></div>
                            </div>
                        )}
                        <div className="space-y-1.5">
                            <Label>Wer ist dabei?</Label>
                            {personen.length === 0 ? <p className="text-sm text-slate-500">Keine Mitarbeiter gefunden.</p> : (
                                <ul className="grid gap-1.5 sm:grid-cols-2">
                                    {personen.map(x => (
                                        <li key={x.id}>
                                            <label className="flex items-center gap-2 text-sm">
                                                <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={teilnehmerIds.includes(x.id)} onChange={() => umschalten(x.id)} />
                                                {x.name}
                                            </label>
                                        </li>
                                    ))}
                                </ul>
                            )}
                            {teilnehmerIds.length === 0 && <p className="text-xs text-slate-500">Ohne Auswahl ist es ein Termin der Firma.</p>}
                        </div>
                        <div className="space-y-1.5">
                            <Label htmlFor="termin-notiz">Notiz</Label>
                            <textarea id="termin-notiz" rows={3} value={beschreibung} onChange={e => setBeschreibung(e.target.value)}
                                className="w-full rounded border border-slate-200 px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500" />
                        </div>
                    </>
                )}
            </div>
            <DialogFooter>
                {!neu && <Button variant="outline" className="mr-auto border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => void loeschen()} disabled={speichert || laedt}><Trash2 className="h-4 w-4" aria-hidden="true" /> Löschen</Button>}
                <Button variant="outline" onClick={onSchliessen} disabled={speichert}>Abbrechen</Button>
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void speichern()} disabled={speichert || laedt}>
                    {speichert && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Speichern
                </Button>
            </DialogFooter>
        </>
    );
}
