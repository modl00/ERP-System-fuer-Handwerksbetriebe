import { useState } from 'react';
import { Loader2, Pencil, Plus, Power, Save } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { aendereOrt, legeOrtAn, setzeOrtAktiv, type Lagerort } from './lagerApi';

interface Props {
    orte: Lagerort[];
    /** Nach jeder Änderung: die Liste neu laden */
    onGeaendert: () => void;
}

/** Lagerorte anlegen, umbenennen und stilllegen. Gelöscht wird nie, weil Buchungen darauf verweisen. */
export function LagerorteBereich({ orte, onGeaendert }: Props) {
    const toast = useToast();
    const [bearbeiten, setBearbeiten] = useState<Lagerort | 'neu' | null>(null);
    const [name, setName] = useState('');
    const [beschreibung, setBeschreibung] = useState('');
    const [fehler, setFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [aenderungLaeuft, setAenderungLaeuft] = useState<number | null>(null);

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

    return (
        <div className="space-y-4" data-testid="lagerorte">
            <div className="flex items-center justify-between gap-3">
                <p className="text-sm text-slate-600 max-w-2xl">
                    Lagerorte sind die Plätze, an denen Ware liegt: Halle, Regal, Montagefahrzeug. Ein stillgelegter Lagerort behält seinen Bestand und seine Geschichte,
                    nimmt aber keine neuen Buchungen an.
                </p>
                <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700 shrink-0" onClick={() => oeffnen('neu')}>
                    <Plus className="w-4 h-4" /> Lagerort anlegen
                </Button>
            </div>

            <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
                {orte.map(o => (
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
                        </div>
                    </li>
                ))}
            </ul>

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
