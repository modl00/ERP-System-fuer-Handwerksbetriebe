import { useCallback, useEffect, useState } from 'react';
import { FileSignature, Loader2, Pencil, Plus, Save, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { DatePicker } from '../../components/ui/datepicker';
import { DecimalInput } from '../../components/ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { heuteIso } from '../../lib/datum';
import { ladeVertrag, loescheVertrag, speichereVertrag } from './api';
import { formatDatum, INTERVALL_OPTIONEN } from './format';
import type { WartungVertrag } from './types';

const STATUS_TEXT: Record<WartungVertrag['status'], { label: string; klasse: string }> = {
    LAEUFT: { label: 'Läuft', klasse: 'border-emerald-200 bg-emerald-50 text-emerald-700' },
    NOCH_NICHT_GESTARTET: { label: 'Startet noch', klasse: 'border-slate-200 bg-slate-50 text-slate-600' },
    ABGELAUFEN: { label: 'Abgelaufen', klasse: 'border-rose-200 bg-rose-50 text-rose-700' },
};

const intervallText = (monate: number) => (monate === 12 ? 'jährlich' : monate === 6 ? 'halbjährlich' : monate === 3 ? 'vierteljährlich' : monate === 1 ? 'monatlich' : `alle ${monate} Monate`);
const geld = (wert: number) => wert.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });

/** Der Wartungsvertrag eines Projekts: Laufzeit, Intervall, Preis. Anlegen, ändern, löschen. */
export function WartungVertragKarte({ projektId }: { projektId: number }) {
    const toast = useToast();
    const confirm = useConfirm();
    const [vertrag, setVertrag] = useState<WartungVertrag | null | undefined>(undefined);
    const [fehler, setFehler] = useState(false);
    const [dialog, setDialog] = useState(false);

    const laden = useCallback(async () => {
        setFehler(false);
        try {
            setVertrag(await ladeVertrag(projektId));
        } catch (e) {
            setFehler(true);
            toast.error(e instanceof Error ? e.message : 'Der Wartungsvertrag konnte nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [projektId]);

    useEffect(() => { setVertrag(undefined); void laden(); }, [laden]);

    const loeschen = async () => {
        const ja = await confirm({
            title: 'Wartungsvertrag löschen?',
            message: 'Die Vertragsdaten werden entfernt. Anlagen, Wartungen und Berichte des Projekts bleiben erhalten.',
            confirmLabel: 'Löschen',
            variant: 'warning',
        });
        if (!ja) return;
        try {
            await loescheVertrag(projektId);
            setVertrag(null);
            toast.success('Der Wartungsvertrag wurde gelöscht.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Wartungsvertrag konnte nicht gelöscht werden.');
        }
    };

    return (
        <section className="rounded-xl border border-slate-200 bg-white p-4" aria-labelledby="wartungsvertrag-titel" data-testid="wartungsvertrag">
            <div className="mb-3 flex items-center justify-between gap-2">
                <h3 id="wartungsvertrag-titel" className="flex items-center gap-2 text-sm font-medium text-slate-900">
                    <FileSignature className="h-4 w-4 text-rose-500" aria-hidden="true" /> Wartungsvertrag
                </h3>
                {vertrag !== undefined && !fehler && (
                    <div className="flex items-center gap-1">
                        <Button variant="ghost" size="sm" onClick={() => setDialog(true)}>
                            {vertrag ? <><Pencil className="h-4 w-4" aria-hidden="true" /> Bearbeiten</> : <><Plus className="h-4 w-4" aria-hidden="true" /> Vertrag anlegen</>}
                        </Button>
                        {vertrag && (
                            <Button variant="ghost" size="sm" className="text-slate-500" onClick={() => void loeschen()} aria-label="Wartungsvertrag löschen" title="Vertrag löschen">
                                <Trash2 className="h-4 w-4" aria-hidden="true" />
                            </Button>
                        )}
                    </div>
                )}
            </div>

            {fehler && (
                <p className="text-sm text-slate-600">Der Vertrag konnte nicht geladen werden.{' '}
                    <button type="button" className="text-rose-700 underline underline-offset-2" onClick={() => void laden()}>Noch einmal versuchen</button></p>
            )}
            {!fehler && vertrag === undefined && (
                <div className="flex items-center gap-2 text-sm text-slate-500" role="status"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …</div>
            )}
            {vertrag === null && <p className="text-sm text-slate-500">Für dieses Projekt ist noch kein Wartungsvertrag hinterlegt.</p>}
            {vertrag && (
                <dl className="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
                    <div className="sm:col-span-2 flex flex-wrap items-center gap-2">
                        <span className={cn('inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold', STATUS_TEXT[vertrag.status].klasse)}>{STATUS_TEXT[vertrag.status].label}</span>
                        {vertrag.endetBald && <span className="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700">Endet bald</span>}
                        {vertrag.vertragsnummer && <span className="text-slate-600">Nr. {vertrag.vertragsnummer}</span>}
                    </div>
                    <div><dt className="text-slate-500">Laufzeit</dt><dd className="text-slate-900">{formatDatum(vertrag.beginn)} bis {vertrag.ende ? formatDatum(vertrag.ende) : 'unbefristet'}</dd></div>
                    <div><dt className="text-slate-500">Wartung</dt><dd className="text-slate-900">{intervallText(vertrag.intervallMonate)}</dd></div>
                    <div><dt className="text-slate-500">Preis je Wartung (netto)</dt><dd className="text-slate-900">{vertrag.preisNetto != null ? geld(vertrag.preisNetto) : 'nicht festgelegt'}</dd></div>
                    {vertrag.notiz && <div className="sm:col-span-2"><dt className="text-slate-500">Notiz</dt><dd className="whitespace-pre-line break-words text-slate-900">{vertrag.notiz}</dd></div>}
                </dl>
            )}

            <VertragDialog offen={dialog} projektId={projektId} vertrag={vertrag ?? null} onSchliessen={() => setDialog(false)} onGespeichert={(v) => { setVertrag(v); setDialog(false); }} />
        </section>
    );
}

interface DialogProps {
    offen: boolean;
    projektId: number;
    vertrag: WartungVertrag | null;
    onSchliessen: () => void;
    onGespeichert: (v: WartungVertrag) => void;
}

function VertragDialog({ offen, projektId, vertrag, onSchliessen, onGespeichert }: DialogProps) {
    const toast = useToast();
    const [nummer, setNummer] = useState('');
    const [beginn, setBeginn] = useState('');
    const [ende, setEnde] = useState('');
    const [intervall, setIntervall] = useState('12');
    const [preis, setPreis] = useState('');
    const [notiz, setNotiz] = useState('');
    const [fehler, setFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        if (!offen) return;
        setNummer(vertrag?.vertragsnummer ?? '');
        setBeginn(vertrag?.beginn ?? heuteIso());
        setEnde(vertrag?.ende ?? '');
        setIntervall(String(vertrag?.intervallMonate ?? 12));
        setPreis(vertrag?.preisNetto != null ? formatDecimalInput(vertrag.preisNetto) : '');
        setNotiz(vertrag?.notiz ?? '');
        setFehler('');
    }, [offen, vertrag]);

    const speichern = async () => {
        if (!beginn) { setFehler('Bitte den Beginn des Vertrags angeben.'); return; }
        if (ende && ende < beginn) { setFehler('Das Ende darf nicht vor dem Beginn liegen.'); return; }
        const p = validateDecimalInput(preis, { label: 'Preis', required: false, min: 0, max: 10000000 });
        if (!p.valid) { setFehler(p.message); return; }
        setSpeichert(true);
        setFehler('');
        try {
            const v = await speichereVertrag(projektId, {
                vertragsnummer: nummer.trim() || null, beginn, ende: ende || null, intervallMonate: Number(intervall), preisNetto: p.value, notiz: notiz.trim() || null,
            });
            toast.success('Der Wartungsvertrag wurde gespeichert.');
            onGespeichert(v);
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Der Wartungsvertrag konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const intervalle = [...new Set([...INTERVALL_OPTIONEN, Number(intervall)])].filter(n => n > 0).sort((a, b) => a - b);

    return (
        <Dialog open={offen} onOpenChange={(o) => { if (!o) onSchliessen(); }}>
            <DialogContent className="sm:max-w-[520px]">
                <DialogHeader>
                    <DialogTitle>{vertrag ? 'Wartungsvertrag bearbeiten' : 'Wartungsvertrag anlegen'}</DialogTitle>
                    <DialogDescription>Laufzeit, Abstand und Preis der Wartung. Die fälligen Prüfungen kommen weiter aus dem Wartungsplan der Anlagen.</DialogDescription>
                </DialogHeader>
                <div className="space-y-4">
                    <div>
                        <Label htmlFor="vertrag-nummer">Vertragsnummer (optional)</Label>
                        <Input id="vertrag-nummer" value={nummer} maxLength={60} onChange={e => setNummer(e.target.value)} placeholder="z. B. WV-2026-1" />
                    </div>
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                        <div>
                            <Label htmlFor="vertrag-beginn">Beginn</Label>
                            <DatePicker id="vertrag-beginn" value={beginn} onChange={setBeginn} placeholder="Beginn" />
                        </div>
                        <div>
                            <Label htmlFor="vertrag-ende">Ende (leer = unbefristet)</Label>
                            <DatePicker id="vertrag-ende" value={ende} onChange={setEnde} placeholder="unbefristet" />
                        </div>
                        <div>
                            <Label>Wartung alle</Label>
                            <Select value={intervall} onChange={setIntervall} aria-label="Abstand der Wartungen"
                                options={intervalle.map(n => ({ value: String(n), label: n === 1 ? '1 Monat' : `${n} Monate` }))} />
                        </div>
                        <DecimalInput label="Preis je Wartung, netto (€)" value={preis} min={0} max={10000000} onChange={setPreis} placeholder="z. B. 450,00" />
                    </div>
                    <div>
                        <Label htmlFor="vertrag-notiz">Notiz (optional)</Label>
                        <textarea id="vertrag-notiz" rows={3} maxLength={1000} value={notiz} onChange={e => setNotiz(e.target.value)}
                            className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400" />
                    </div>
                </div>
                {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}
                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" size="sm" onClick={onSchliessen}>Abbrechen</Button>
                    <Button type="button" size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void speichern()} disabled={speichert}>
                        {speichert ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Save className="w-4 h-4" />} Speichern
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
