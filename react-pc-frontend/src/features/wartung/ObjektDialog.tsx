import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { aendereObjekt, legeObjektAn } from './api';
import type { WartungObjekt, WartungObjektEingabe } from './types';

interface ObjektDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    kundeId: number;
    /** Gesetzt = Objekt ändern, sonst neu anlegen. */
    objekt?: WartungObjekt;
    onGespeichert: (objekt: WartungObjekt) => void;
}

const LEER: WartungObjektEingabe = {
    bezeichnung: '', strasse: '', plz: '', ort: '', ansprechpartnerName: '', ansprechpartnerTelefon: '', notizen: '',
};

function ausObjekt(objekt: WartungObjekt | undefined): WartungObjektEingabe {
    if (!objekt) return LEER;
    return {
        bezeichnung: objekt.bezeichnung,
        strasse: objekt.strasse ?? '',
        plz: objekt.plz ?? '',
        ort: objekt.ort ?? '',
        ansprechpartnerName: objekt.ansprechpartnerName ?? '',
        ansprechpartnerTelefon: objekt.ansprechpartnerTelefon ?? '',
        notizen: objekt.notizen ?? '',
    };
}

/** Anlegen und Ändern eines Objekts (Gebäude oder Standort eines Kunden). */
export function ObjektDialog({ open, onOpenChange, kundeId, objekt, onGespeichert }: ObjektDialogProps) {
    const toast = useToast();
    const [eingabe, setEingabe] = useState<WartungObjektEingabe>(LEER);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState<string | undefined>();

    useEffect(() => {
        if (open) {
            setEingabe(ausObjekt(objekt));
            setFehler(undefined);
        }
    }, [open, objekt]);

    const setze = (feld: keyof WartungObjektEingabe) => (wert: string) => setEingabe((alt) => ({ ...alt, [feld]: wert }));

    const speichern = async () => {
        if (eingabe.bezeichnung.trim() === '') {
            setFehler('Bitte eine Bezeichnung eintragen, zum Beispiel „Werk Nord“.');
            return;
        }
        setSpeichert(true);
        try {
            const ergebnis = objekt ? await aendereObjekt(objekt.id, eingabe) : await legeObjektAn(kundeId, eingabe);
            toast.success(objekt ? 'Objekt gespeichert.' : 'Objekt angelegt.');
            onGespeichert(ergebnis);
            onOpenChange(false);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Objekt konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[520px]">
                <DialogHeader>
                    <DialogTitle>{objekt ? 'Objekt bearbeiten' : 'Objekt anlegen'}</DialogTitle>
                    <DialogDescription>
                        Ein Objekt ist ein Gebäude oder Standort des Kunden, in dem Türen und Tore geprüft werden.
                    </DialogDescription>
                </DialogHeader>

                <div className="flex flex-col gap-4 py-2">
                    <div>
                        <Label htmlFor="objekt-bezeichnung">Bezeichnung</Label>
                        <Input
                            id="objekt-bezeichnung"
                            value={eingabe.bezeichnung}
                            maxLength={200}
                            placeholder="z. B. Werk Nord, Verwaltungsgebäude"
                            aria-invalid={fehler ? true : undefined}
                            aria-describedby={fehler ? 'objekt-bezeichnung-fehler' : undefined}
                            onChange={(e) => { setze('bezeichnung')(e.target.value); setFehler(undefined); }}
                        />
                        {fehler && <p id="objekt-bezeichnung-fehler" role="alert" className="mt-1 text-sm text-rose-700">{fehler}</p>}
                    </div>
                    <div>
                        <Label htmlFor="objekt-strasse">Straße</Label>
                        <Input id="objekt-strasse" value={eingabe.strasse} maxLength={200} onChange={(e) => setze('strasse')(e.target.value)} />
                    </div>
                    <div className="grid grid-cols-3 gap-3">
                        <div>
                            <Label htmlFor="objekt-plz">PLZ</Label>
                            <Input id="objekt-plz" value={eingabe.plz} maxLength={20} inputMode="numeric" onChange={(e) => setze('plz')(e.target.value)} />
                        </div>
                        <div className="col-span-2">
                            <Label htmlFor="objekt-ort">Ort</Label>
                            <Input id="objekt-ort" value={eingabe.ort} maxLength={120} onChange={(e) => setze('ort')(e.target.value)} />
                        </div>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div>
                            <Label htmlFor="objekt-ap">Ansprechpartner vor Ort</Label>
                            <Input id="objekt-ap" value={eingabe.ansprechpartnerName} maxLength={200} onChange={(e) => setze('ansprechpartnerName')(e.target.value)} />
                        </div>
                        <div>
                            <Label htmlFor="objekt-ap-tel">Telefon</Label>
                            <Input id="objekt-ap-tel" value={eingabe.ansprechpartnerTelefon} maxLength={60} inputMode="tel" onChange={(e) => setze('ansprechpartnerTelefon')(e.target.value)} />
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="objekt-notizen">Notiz</Label>
                        <textarea
                            id="objekt-notizen"
                            className="w-full min-h-[80px] p-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                            value={eingabe.notizen}
                            maxLength={10000}
                            placeholder="z. B. Schlüssel beim Hausmeister, Zugang über Hof"
                            onChange={(e) => setze('notizen')(e.target.value)}
                        />
                    </div>
                </div>

                <DialogFooter>
                    <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} disabled={speichert}>Abbrechen</Button>
                    <Button size="sm" onClick={speichern} disabled={speichert}>
                        {speichert && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
                        {speichert ? 'Speichert...' : 'Speichern'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
