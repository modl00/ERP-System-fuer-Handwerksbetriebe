import { useEffect, useState } from 'react';
import { Loader2, Plus } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { useToast } from '../../components/ui/toast';
import { ladeModule, ladeObjekte, ordneProjektZu } from './api';
import { ObjektDialog } from './ObjektDialog';
import { TuerDialog } from './TuerDialog';
import type { WartungModul, WartungObjekt } from './types';

interface Props {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    projektId: number;
    kundeId: number;
    /** Die Anlage ist angelegt und dem Projekt zugeordnet. */
    onAngelegt: () => void;
}

/**
 * Neue Anlage (Tür oder Tor) für den Kunden des Projekts: Objekt wählen oder gleich neu anlegen, dann die Anlage mit ihren
 * Modulen. Sie wird sofort diesem Projekt zugeordnet. Dieselben Masken wie beim Kunden, nur an einem Ort mit dem Projekt.
 */
export function NeueAnlageDialog(props: Props) {
    // Nur solange geöffnet vorhanden: Beim Öffnen beginnt alles frisch, ohne Zustand zurückzusetzen
    return props.open ? <NeueAnlageInhalt {...props} /> : null;
}

function NeueAnlageInhalt({ open, onOpenChange, projektId, kundeId, onAngelegt }: Props) {
    const toast = useToast();
    const [objekte, setObjekte] = useState<WartungObjekt[] | null>(null);
    const [module, setModule] = useState<WartungModul[]>([]);
    const [objektId, setObjektId] = useState('');
    const [fehler, setFehler] = useState(false);
    const [objektDialog, setObjektDialog] = useState(false);
    const [tuerDialog, setTuerDialog] = useState(false);

    useEffect(() => {
        Promise.all([ladeObjekte(kundeId), ladeModule()])
            .then(([o, m]) => {
                const aktive = o.filter((x) => x.aktiv);
                setObjekte(aktive);
                setModule(m);
                if (aktive.length === 1) setObjektId(String(aktive[0].id));
            })
            .catch(() => setFehler(true));
    }, [kundeId]);

    const neuesObjekt = (objekt: WartungObjekt) => {
        setObjekte((alt) => [...(alt ?? []), objekt]);
        setObjektId(String(objekt.id));
    };

    const angelegt = async (tuerId: number, name: string) => {
        try {
            await ordneProjektZu(tuerId, projektId);
            toast.success(`„${name}" ist angelegt und dem Projekt zugeordnet.`);
        } catch (e) {
            toast.error(e instanceof Error ? `„${name}" ist angelegt, aber nicht zugeordnet: ${e.message}` : `„${name}" ist angelegt, konnte aber nicht dem Projekt zugeordnet werden.`);
        }
        setTuerDialog(false);
        onOpenChange(false);
        onAngelegt();
    };

    return (
        <>
            <Dialog open={open} onOpenChange={onOpenChange}>
                <DialogContent className="sm:max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Neue Anlage</DialogTitle>
                        <DialogDescription>Wählen Sie das Objekt, in dem die Anlage steht. Sie wird nach dem Anlegen diesem Projekt zugeordnet.</DialogDescription>
                    </DialogHeader>
                    {fehler && <p role="alert" className="text-sm text-rose-700">Die Objekte des Kunden konnten nicht geladen werden.</p>}
                    {!fehler && objekte === null && (
                        <div className="flex items-center gap-2 py-4 text-sm text-slate-500" role="status">
                            <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …
                        </div>
                    )}
                    {objekte !== null && (
                        <div className="space-y-3">
                            <div>
                                <div className="mb-1.5 flex items-center justify-between">
                                    <Label htmlFor="neue-anlage-objekt">Objekt</Label>
                                    <Button type="button" variant="ghost" size="sm" onClick={() => setObjektDialog(true)}>
                                        <Plus className="h-4 w-4" aria-hidden="true" /> Neues Objekt
                                    </Button>
                                </div>
                                <Select
                                    id="neue-anlage-objekt"
                                    value={objektId}
                                    onChange={setObjektId}
                                    options={[{ value: '', label: objekte.length === 0 ? 'Noch kein Objekt, bitte eines anlegen' : 'Objekt wählen' }, ...objekte.map((o) => ({ value: String(o.id), label: o.bezeichnung }))]}
                                />
                            </div>
                        </div>
                    )}
                    <DialogFooter>
                        <Button variant="outline" onClick={() => onOpenChange(false)}>Abbrechen</Button>
                        <Button
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                            disabled={objektId === ''}
                            title={objektId === '' ? 'Erst ein Objekt wählen' : undefined}
                            onClick={() => setTuerDialog(true)}
                        >
                            Anlage anlegen …
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
            <ObjektDialog open={objektDialog} onOpenChange={setObjektDialog} kundeId={kundeId} onGespeichert={(o) => { neuesObjekt(o); setObjektDialog(false); }} />
            {objektId !== '' && (
                <TuerDialog open={tuerDialog} onOpenChange={setTuerDialog} objektId={Number(objektId)} module={module} onGespeichert={(t) => { void angelegt(t.id, t.bezeichnung); }} />
            )}
        </>
    );
}
