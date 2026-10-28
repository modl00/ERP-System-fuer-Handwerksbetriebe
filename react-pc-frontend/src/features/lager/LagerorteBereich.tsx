import { useState } from 'react';
import { Archive, Loader2, Pencil, Plus, Power, RotateCcw, Save, Trash2 } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { useToast } from '../../components/ui/toast';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { cn } from '../../lib/utils';
import { aendereOrt, archiviereOrt, legeOrtAn, loescheOrt, setzeOrtAktiv, stelleOrtWieder, type Lagerort } from './lagerApi';

interface Props {
    orte: Lagerort[];
    istAdmin?: boolean;
    /** Nach jeder Änderung: die Liste neu laden */
    onGeaendert: () => void;
}

/** Lagerorte anlegen, umbenennen, stilllegen und archivieren. Endgültig löschen nur Admin, nur aus Archiv. */
export function LagerorteBereich({ orte, istAdmin, onGeaendert }: Props) {
    const toast = useToast();
    const confirm = useConfirm();
    const [bearbeiten, setBearbeiten] = useState<Lagerort | 'neu' | null>(null);
    const [name, setName] = useState('');
    const [beschreibung, setBeschreibung] = useState('');
    const [fehler, setFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [aenderungLaeuft, setAenderungLaeuft] = useState<number | null>(null);
    const [zeigeArchiviert, setZeigeArchiviert] = useState(false);

    const oeffnen = (ziel: Lagerort | 'neu') => {
        setBearbeiten(ziel);
        setName(ziel === 'neu' ? '' : ziel.name);
        setBeschreibung(ziel === 'neu' ? '' : (ziel.beschreibung ?? ''));
        setFehler('');
    };

    const speichern = async () => {
        if (!name.trim()) { setFehler('Bitte einen Namen eingeben.'); return; }
        setSpeichert(true);
        try {
            if (bearbeiten === 'neu') await legeOrtAn(name.trim(), beschreibung.trim());
            else if (bearbeiten) await aendereOrt(bearbeiten.id, name.trim(), beschreibung.trim());
            toast.success(bearbeiten === 'neu' ? 'Lagerort angelegt.' : 'Lagerort gespeichert.');
            setBearbeiten(null);
            onGeaendert();
        } catch (e) {
            const text = e instanceof Error ? e.message : 'Der Lagerort konnte nicht gespeichert werden.';
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    const umschalten = async (ort: Lagerort) => {
        setAenderungLaeuft(ort.id);
        try {
            await setzeOrtAktiv(ort.id, !ort.aktiv);
            toast.success(ort.aktiv ? `„${ort.name}" ist stillgelegt.` : `„${ort.name}" ist wieder aktiv.`);
            onGeaendert();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Lagerort konnte nicht geändert werden.');
        } finally {
            setAenderungLaeuft(null);
        }
    };

    const archivieren = async (ort: Lagerort) => {
        const ok = await confirm({
            title: 'Lagerort archivieren',
            message: `„${ort.name}" wird archiviert und aus der aktiven Liste ausgeblendet. Buchungen und Bestand bleiben erhalten.`,
            confirmLabel: 'Archivieren',
            variant: 'warning',
        });
        if (!ok) return;
        setAenderungLaeuft(ort.id);
        try {
            await archiviereOrt(ort.id);
            toast.success(`„${ort.name}" wurde archiviert.`);
            onGeaendert();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Lagerort konnte nicht archiviert werden.');
        } finally {
            setAenderungLaeuft(null);
        }
    };

    const wiederherstellen = async (ort: Lagerort) => {
        setAenderungLaeuft(ort.id);
        try {
            await stelleOrtWieder(ort.id);
            toast.success(`„${ort.name}" wurde wiederhergestellt.`);
            onGeaendert();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Lagerort konnte nicht wiederhergestellt werden.');
        } finally {
            setAenderungLaeuft(null);
        }
    };

    const loeschen = async (ort: Lagerort) => {
        const ok = await confirm({
            title: 'Lagerort endgültig löschen',
            message: `„${ort.name}" wird unwiderruflich gelöscht. Dies ist nur möglich, wenn keine Buchungen auf diesen Lagerort verweisen.`,
            confirmLabel: 'Endgültig löschen',
            variant: 'danger',
        });
        if (!ok) return;
        setAenderungLaeuft(ort.id);
        try {
            await loescheOrt(ort.id);
            toast.success(`„${ort.name}" wurde gelöscht.`);
            onGeaendert();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Lagerort konnte nicht gelöscht werden.');
        } finally {
            setAenderungLaeuft(null);
        }
    };

    const aktiveOrte = orte.filter(o => !o.archiviert);
    const archivierteOrte = orte.filter(o => o.archiviert);

    return (
        <div className="space-y-4" data-testid="lagerorte">
            <div className="flex items-center justify-between gap-3">
                <p className="text-sm text-slate-600 max-w-2xl">
                    Lagerorte sind die Plätze, an denen Ware liegt: Halle, Regal, Montagefahrzeug. Ein stillgelegter Lagerort behält seinen Bestand und seine Geschichte,
                    nimmt aber keine neuen Buchungen an.
                </p>
                <div className="flex items-center gap-2 shrink-0">
                    {archivierteOrte.length > 0 && (
                        <Button variant="outline" size="sm" className="text-slate-600 border-slate-200" onClick={() => setZeigeArchiviert(z => !z)}>
                            <Archive className="w-4 h-4" /> Archiv ({archivierteOrte.length})
                        </Button>
                    )}
                    <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => oeffnen('neu')}>
                        <Plus className="w-4 h-4" /> Lagerort anlegen
                    </Button>
                </div>
            </div>

            <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
                {aktiveOrte.map(o => (
                    <li key={o.id} className="flex items-center justify-between gap-3 px-4 py-3" data-testid={`lagerort-${o.id}`}>
                        <div className="min-w-0">
                            <div className="flex items-center gap-2">
                                <span className={cn('font-medium', o.aktiv ? 'text-slate-900' : 'text-slate-400')}>{o.name}</span>
                                {!o.aktiv && <span className="text-xs rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">stillgelegt</span>}
                            </div>
                            {o.beschreibung && <div className="text-sm text-slate-500 truncate">{o.beschreibung}</div>}
                        </div>
                        <div className="flex items-center gap-1 shrink-0">
                            <Button variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-100" onClick={() => oeffnen(o)} aria-label={`Lagerort ${o.name} bearbeiten`} title="Bearbeiten">
                                <Pencil className="w-4 h-4" />
                            </Button>
                            <Button variant="ghost" size="sm" className="text-slate-600 hover:bg-slate-100" onClick={() => umschalten(o)} disabled={aenderungLaeuft === o.id}
                                aria-label={o.aktiv ? `Lagerort ${o.name} stilllegen` : `Lagerort ${o.name} wieder aktivieren`} title={o.aktiv ? 'Stilllegen' : 'Wieder aktivieren'}>
                                {aenderungLaeuft === o.id ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Power className="w-4 h-4" />}
                            </Button>
                            <Button variant="ghost" size="sm" className="text-slate-500 hover:bg-slate-100" onClick={() => archivieren(o)} disabled={aenderungLaeuft === o.id}
                                aria-label={`Lagerort ${o.name} archivieren`} title="Archivieren">
                                <Archive className="w-4 h-4" />
                            </Button>
                        </div>
                    </li>
                ))}
                {aktiveOrte.length === 0 && (
                    <li className="px-4 py-6 text-center text-sm text-slate-400">Keine aktiven Lagerorte vorhanden.</li>
                )}
            </ul>

            {zeigeArchiviert && archivierteOrte.length > 0 && (
                <div className="space-y-2">
                    <h4 className="text-sm font-medium text-slate-500 flex items-center gap-1.5">
                        <Archive className="w-4 h-4" /> Archiv
                    </h4>
                    <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-slate-50">
                        {archivierteOrte.map(o => (
                            <li key={o.id} className="flex items-center justify-between gap-3 px-4 py-3">
                                <div className="min-w-0">
                                    <div className="flex items-center gap-2">
                                        <span className="font-medium text-slate-400">{o.name}</span>
                                        <span className="text-xs rounded-full bg-slate-200 px-2 py-0.5 text-slate-600">archiviert</span>
                                    </div>
                                    {o.beschreibung && <div className="text-sm text-slate-400 truncate">{o.beschreibung}</div>}
                                </div>
                                <div className="flex items-center gap-1 shrink-0">
                                    <Button variant="ghost" size="sm" className="text-slate-600 hover:bg-slate-200" onClick={() => wiederherstellen(o)} disabled={aenderungLaeuft === o.id}
                                        aria-label={`Lagerort ${o.name} wiederherstellen`} title="Wiederherstellen">
                                        {aenderungLaeuft === o.id ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <RotateCcw className="w-4 h-4" />}
                                    </Button>
                                    {istAdmin && (
                                        <Button variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-100" onClick={() => loeschen(o)} disabled={aenderungLaeuft === o.id}
                                            aria-label={`Lagerort ${o.name} endgültig löschen`} title="Endgültig löschen (nur Admin)">
                                            <Trash2 className="w-4 h-4" />
                                        </Button>
                                    )}
                                </div>
                            </li>
                        ))}
                    </ul>
                </div>
            )}

            <Dialog open={bearbeiten != null} onOpenChange={(o) => { if (!o) setBearbeiten(null); }}>
                <DialogContent className="sm:max-w-[420px]">
                    <DialogHeader>
                        <DialogTitle>{bearbeiten === 'neu' ? 'Lagerort anlegen' : 'Lagerort bearbeiten'}</DialogTitle>
                        <DialogDescription>Der Name muss eindeutig sein.</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3">
                        <div>
                            <Label>Name</Label>
                            <Input value={name} maxLength={120} onChange={e => setName(e.target.value)} placeholder="z. B. Montagefahrzeug" autoFocus />
                        </div>
                        <div>
                            <Label>Beschreibung (optional)</Label>
                            <Input value={beschreibung} maxLength={255} onChange={e => setBeschreibung(e.target.value)} placeholder="z. B. Sprinter, Kennzeichen …" />
                        </div>
                    </div>
                    {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}
                    <DialogFooter className="gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={() => setBearbeiten(null)}>Abbrechen</Button>
                        <Button type="button" size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={speichern} disabled={speichert}>
                            {speichert ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Save className="w-4 h-4" />} Speichern
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
