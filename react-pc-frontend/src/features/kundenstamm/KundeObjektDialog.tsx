import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { aendereKundeObjekt, legeKundeObjektAn } from './api';
import type { Ansprechpartner, KundeObjekt, KundeObjektEingabe } from './types';

interface KundeObjektDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    kundeId: number;
    /** Gesetzt = Objekt ändern, sonst neu anlegen. */
    objekt?: KundeObjekt;
    /** Die Ansprechpartner des Kunden: Name und Telefon lassen sich daraus übernehmen. */
    ansprechpartner: Ansprechpartner[];
    onGespeichert: () => void;
}

const LEER: KundeObjektEingabe = { bezeichnung: '', strasse: '', plz: '', ort: '', ansprechpartnerName: '', ansprechpartnerTelefon: '', notizen: '' };

function ausObjekt(o: KundeObjekt | undefined): KundeObjektEingabe {
    if (!o) return LEER;
    return {
        bezeichnung: o.bezeichnung, strasse: o.strasse ?? '', plz: o.plz ?? '', ort: o.ort ?? '',
        ansprechpartnerName: o.ansprechpartnerName ?? '', ansprechpartnerTelefon: o.ansprechpartnerTelefon ?? '', notizen: o.notizen ?? '',
    };
}

/** Anlegen und Ändern eines Objekts (Liegenschaft, Gebäude oder Standort) eines Kunden. */
export function KundeObjektDialog({ open, onOpenChange, kundeId, objekt, ansprechpartner, onGespeichert }: KundeObjektDialogProps) {
    const toast = useToast();
    const [eingabe, setEingabe] = useState<KundeObjektEingabe>(LEER);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState<string | undefined>();
    const [auswahl, setAuswahl] = useState('');

    useEffect(() => {
        if (open) {
            setEingabe(ausObjekt(objekt));
            setFehler(undefined);
            setAuswahl('');
        }
    }, [open, objekt]);

    const setze = (feld: keyof KundeObjektEingabe) => (wert: string) => setEingabe((alt) => ({ ...alt, [feld]: wert }));

    const uebernimm = (wert: string) => {
        setAuswahl(wert);
        const gewaehlt = ansprechpartner.find((a) => String(a.id) === wert);
        if (gewaehlt) {
            setEingabe((alt) => ({ ...alt, ansprechpartnerName: gewaehlt.name, ansprechpartnerTelefon: gewaehlt.mobil ?? gewaehlt.telefon ?? '' }));
        }
    };

    const speichern = async () => {
        if (eingabe.bezeichnung.trim() === '') {
            setFehler('Bitte eine Bezeichnung eintragen, zum Beispiel „Werk Nord" oder „Mehrfamilienhaus Gartenstraße 4".');
            return;
        }
        setSpeichert(true);
        try {
            if (objekt) await aendereKundeObjekt(kundeId, objekt.id, eingabe);
            else await legeKundeObjektAn(kundeId, eingabe);
            toast.success(objekt ? 'Objekt gespeichert.' : 'Objekt angelegt.');
            onGespeichert();
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
                        Ein Objekt ist eine Liegenschaft, ein Gebäude oder ein Standort des Kunden, an dem Sie arbeiten oder Wartungen durchführen.
                    </DialogDescription>
                </DialogHeader>

                <div className="flex flex-col gap-4 py-2">
                    <div>
                        <Label htmlFor="ko-bezeichnung">Bezeichnung</Label>
                        <Input
                            id="ko-bezeichnung"
                            value={eingabe.bezeichnung}
                            maxLength={200}
                            placeholder="z. B. Werk Nord, Verwaltungsgebäude"
                            aria-invalid={fehler ? true : undefined}
                            aria-describedby={fehler ? 'ko-bezeichnung-fehler' : undefined}
                            onChange={(e) => { setze('bezeichnung')(e.target.value); setFehler(undefined); }}
                        />
                        {fehler && <p id="ko-bezeichnung-fehler" role="alert" className="mt-1 text-sm text-rose-700">{fehler}</p>}
                    </div>
                    <div>
                        <Label htmlFor="ko-strasse">Straße</Label>
                        <Input id="ko-strasse" value={eingabe.strasse} maxLength={200} onChange={(e) => setze('strasse')(e.target.value)} />
                    </div>
                    <div className="grid grid-cols-3 gap-3">
                        <div>
                            <Label htmlFor="ko-plz">PLZ</Label>
                            <Input id="ko-plz" value={eingabe.plz} maxLength={20} inputMode="numeric" onChange={(e) => setze('plz')(e.target.value)} />
                        </div>
                        <div className="col-span-2">
                            <Label htmlFor="ko-ort">Ort</Label>
                            <Input id="ko-ort" value={eingabe.ort} maxLength={120} onChange={(e) => setze('ort')(e.target.value)} />
                        </div>
                    </div>
                    {ansprechpartner.length > 0 && (
                        <div>
                            <Label htmlFor="ko-auswahl">Ansprechpartner des Kunden übernehmen</Label>
                            <Select
                                id="ko-auswahl"
                                value={auswahl}
                                onChange={uebernimm}
                                placeholder="Aus der Liste wählen"
                                options={ansprechpartner.map((a) => ({ value: String(a.id), label: a.funktion ? `${a.name} (${a.funktion})` : a.name }))}
                            />
                        </div>
                    )}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div>
                            <Label htmlFor="ko-ap">Ansprechpartner vor Ort</Label>
                            <Input id="ko-ap" value={eingabe.ansprechpartnerName} maxLength={200} onChange={(e) => setze('ansprechpartnerName')(e.target.value)} />
                        </div>
                        <div>
                            <Label htmlFor="ko-ap-tel">Telefon</Label>
                            <Input id="ko-ap-tel" value={eingabe.ansprechpartnerTelefon} maxLength={60} inputMode="tel" onChange={(e) => setze('ansprechpartnerTelefon')(e.target.value)} />
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="ko-notizen">Notiz</Label>
                        <textarea
                            id="ko-notizen"
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
