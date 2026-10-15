import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { aendereTuer, legeTuerAn } from './api';
import { INTERVALL_OPTIONEN } from './format';
import { ModulWahl } from './ModulWahl';
import { bereinigeAuswahl, fehlendeAngaben } from './modulAuswahl';
import type { ModulAuswahl, WartungModul, WartungTuer } from './types';

interface TuerDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    objektId: number;
    module: WartungModul[];
    /** Gesetzt = Tür ändern, sonst neu anlegen. */
    tuer?: WartungTuer;
    onGespeichert: (tuer: WartungTuer) => void;
}

/** Anlegen und Ändern einer Tür oder eines Tors samt der Module, die daran geprüft werden. */
export function TuerDialog({ open, onOpenChange, objektId, module, tuer, onGespeichert }: TuerDialogProps) {
    const toast = useToast();
    const [bezeichnung, setBezeichnung] = useState('');
    const [notizen, setNotizen] = useState('');
    const [intervall, setIntervall] = useState('');
    const [auswahl, setAuswahl] = useState<ModulAuswahl[]>([]);
    const [speichert, setSpeichert] = useState(false);
    const [versucht, setVersucht] = useState(false);

    useEffect(() => {
        if (!open) return;
        setBezeichnung(tuer?.bezeichnung ?? '');
        setNotizen(tuer?.notizen ?? '');
        setIntervall(tuer?.intervallMonate ? String(tuer.intervallMonate) : '');
        setAuswahl(bereinigeAuswahl(module, tuer?.module ?? []));
        setVersucht(false);
    }, [open, tuer, module]);

    // An der Tür darf die Auswahl leer bleiben. Ist etwas gewählt, muss es aber vollständig sein.
    const fehlt = auswahl.length > 0 ? fehlendeAngaben(module, auswahl) : [];
    const bezeichnungFehlt = bezeichnung.trim() === '';

    const speichern = async () => {
        setVersucht(true);
        if (bezeichnungFehlt || fehlt.length > 0) return;
        setSpeichert(true);
        try {
            const eingabe = {
                bezeichnung,
                notizen,
                intervallMonate: intervall === '' ? null : Number(intervall),
                module: auswahl,
            };
            const ergebnis = tuer ? await aendereTuer(tuer.id, eingabe) : await legeTuerAn(objektId, eingabe);
            toast.success(tuer ? 'Tür gespeichert.' : 'Tür angelegt.');
            onGespeichert(ergebnis);
            onOpenChange(false);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Tür konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[640px]">
                <DialogHeader>
                    <DialogTitle>{tuer ? 'Tür bearbeiten' : 'Tür oder Tor anlegen'}</DialogTitle>
                    <DialogDescription>
                        Was hier gewählt ist, wird bei jeder neuen Wartung dieser Tür vorgeschlagen.
                    </DialogDescription>
                </DialogHeader>

                <div className="flex flex-col gap-4 py-2 min-h-0 overflow-y-auto">
                    <div>
                        <Label htmlFor="tuer-bezeichnung">Bezeichnung</Label>
                        <Input
                            id="tuer-bezeichnung"
                            value={bezeichnung}
                            maxLength={200}
                            placeholder="z. B. Tür 1.01 Treppenhaus"
                            aria-invalid={versucht && bezeichnungFehlt ? true : undefined}
                            onChange={(e) => setBezeichnung(e.target.value)}
                        />
                        {versucht && bezeichnungFehlt && (
                            <p role="alert" className="mt-1 text-sm text-rose-700">Bitte eine Bezeichnung eintragen.</p>
                        )}
                    </div>

                    <ModulWahl module={module} auswahl={auswahl} onChange={setAuswahl} />
                    {versucht && fehlt.length > 0 && (
                        <ul role="alert" className="text-sm text-rose-700 list-disc pl-5">
                            {fehlt.map((f) => <li key={f}>{f}</li>)}
                        </ul>
                    )}

                    <div>
                        <Label htmlFor="tuer-intervall">Prüfintervall</Label>
                        <Select
                            id="tuer-intervall"
                            value={intervall}
                            onChange={setIntervall}
                            options={[
                                { value: '', label: 'Standard des Moduls' },
                                ...INTERVALL_OPTIONEN.map((m) => ({ value: String(m), label: m === 1 ? 'Jeden Monat' : `Alle ${m} Monate` })),
                            ]}
                        />
                    </div>
                    <div>
                        <Label htmlFor="tuer-notizen">Notiz</Label>
                        <textarea
                            id="tuer-notizen"
                            className="w-full min-h-[70px] p-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                            value={notizen}
                            maxLength={10000}
                            placeholder="z. B. Lage im Gebäude, Besonderheiten"
                            onChange={(e) => setNotizen(e.target.value)}
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
