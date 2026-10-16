import React, { useEffect, useState } from 'react';
import { Calculator, Save, X } from 'lucide-react';
import { Button } from './ui/button';
import { DecimalInput } from './ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { formatDecimalInput, validateDecimalInput } from '../lib/numberInput';
import { maschinenstundensatz } from '../lib/maschinenstundensatz';
import type { Arbeitsgang } from '../types';

interface Props {
    arbeitsgang: Arbeitsgang;
    isOpen: boolean;
    onClose: () => void;
    onSave: (arbeitsgangId: number, bezeichnung: string, stundensatz: number | null) => Promise<void>;
}

const REGEL_SATZ = { label: 'Maschinenstundensatz', required: false, min: 0, max: 1000 } as const;
const zahl = (text: string) => { const v = validateDecimalInput(text, { label: 'Wert', required: false, min: 0 }); return v.valid ? (v.value ?? 0) : 0; };

/**
 * Die Maschine eines Arbeitsgangs: Name und Kosten je Stunde. Der Rechner schlägt aus Anschaffung, Nutzungsdauer, Auslastung, Wartung, Energie und Raum
 * einen Satz vor, der sich übernehmen und ändern lässt. Ohne Satz nutzt der Arbeitsgang keine Maschine.
 */
export const ArbeitsgangMaschineModal: React.FC<Props> = ({ arbeitsgang, isOpen, onClose, onSave }) => {
    const [name, setName] = useState('');
    const [satz, setSatz] = useState('');
    const [rechnerOffen, setRechnerOffen] = useState(false);
    const [anschaffung, setAnschaffung] = useState('');
    const [jahre, setJahre] = useState('');
    const [stunden, setStunden] = useState('');
    const [wartung, setWartung] = useState('');
    const [energie, setEnergie] = useState('');
    const [raum, setRaum] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        if (!isOpen) return;
        setName(arbeitsgang.maschinenBezeichnung ?? '');
        setSatz(arbeitsgang.maschinenStundensatz == null ? '' : formatDecimalInput(arbeitsgang.maschinenStundensatz));
        setRechnerOffen(false);
        setFehler('');
    }, [isOpen, arbeitsgang.id, arbeitsgang.maschinenBezeichnung, arbeitsgang.maschinenStundensatz]);

    const vorschlag = maschinenstundensatz({ anschaffung: zahl(anschaffung), nutzungsdauerJahre: zahl(jahre), auslastungStundenJahr: zahl(stunden), wartungJahr: zahl(wartung), energieJahr: zahl(energie), raumJahr: zahl(raum) });

    const speichern = async () => {
        const s = validateDecimalInput(satz, REGEL_SATZ);
        if (!s.valid) { setFehler(s.message); return; }
        setSpeichert(true);
        setFehler('');
        try {
            await onSave(arbeitsgang.id, name.trim(), s.value);
            onClose();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Maschine konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={(offen) => { if (!offen) onClose(); }}>
            <DialogContent className="sm:max-w-[500px]">
                <DialogHeader>
                    <DialogTitle>Maschine bearbeiten</DialogTitle>
                    <DialogDescription>{arbeitsgang.beschreibung}</DialogDescription>
                </DialogHeader>
                <div className="space-y-4 py-2">
                    <p className="text-sm text-slate-600">
                        Hat der Arbeitsgang eine Maschine, rechnet die Kalkulation zum Lohn die Maschinenkosten dazu, mit denselben Zeiten. Ohne Satz gibt es keine Maschine.
                        Bestehende Kalkulationen und Angebote ändern sich dadurch nicht.
                    </p>
                    {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}
                    <div className="space-y-2">
                        <Label htmlFor="maschine-name">Name der Maschine</Label>
                        <Input id="maschine-name" value={name} onChange={e => setName(e.target.value)} maxLength={100} placeholder="z. B. Kappsäge" />
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="maschine-satz">Kosten je Maschinenstunde (€)</Label>
                        <DecimalInput id="maschine-satz" min={0} max={1000} value={satz} onChange={setSatz} placeholder="z. B. 38,50" className="text-right font-mono" />
                    </div>
                    <button type="button" aria-expanded={rechnerOffen} onClick={() => setRechnerOffen(o => !o)} className="flex items-center gap-1 text-sm text-rose-700 hover:text-rose-800">
                        <Calculator className="w-4 h-4" aria-hidden="true" /> Satz berechnen lassen
                    </button>
                    {rechnerOffen && (
                        <div className="space-y-3 rounded-lg bg-slate-50 p-3" data-testid="maschine-rechner">
                            <div className="grid grid-cols-2 gap-3">
                                <DecimalInput label="Anschaffung (€)" value={anschaffung} onChange={setAnschaffung} min={0} placeholder="24000" />
                                <DecimalInput label="Nutzungsdauer (Jahre)" value={jahre} onChange={setJahre} min={0} placeholder="8" />
                                <DecimalInput label="Läuft im Jahr (Stunden)" value={stunden} onChange={setStunden} min={0} placeholder="1200" />
                                <DecimalInput label="Wartung je Jahr (€)" value={wartung} onChange={setWartung} min={0} />
                                <DecimalInput label="Energie je Jahr (€)" value={energie} onChange={setEnergie} min={0} />
                                <DecimalInput label="Raum je Jahr (€)" value={raum} onChange={setRaum} min={0} />
                            </div>
                            <div className="flex items-center justify-between gap-2">
                                <p className="text-sm text-slate-700" data-testid="maschine-vorschlag">
                                    {vorschlag == null ? 'Für den Vorschlag bitte Nutzungsdauer und Stunden im Jahr eintragen.' : `Vorschlag: ${formatDecimalInput(vorschlag)} € je Stunde`}
                                </p>
                                <Button variant="outline" size="sm" disabled={vorschlag == null} onClick={() => { if (vorschlag != null) setSatz(formatDecimalInput(vorschlag)); }}>Übernehmen</Button>
                            </div>
                        </div>
                    )}
                </div>
                <DialogFooter>
                    <Button variant="outline" size="sm" onClick={onClose} disabled={speichert}><X className="w-4 h-4" /> Abbrechen</Button>
                    <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" size="sm" onClick={() => void speichern()} disabled={speichert}>
                        <Save className="w-4 h-4" /> {speichert ? 'Speichert...' : 'Speichern'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
};

export default ArbeitsgangMaschineModal;
