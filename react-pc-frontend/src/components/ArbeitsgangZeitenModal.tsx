import React, { useEffect, useState } from 'react';
import { Save, X } from 'lucide-react';
import { Button } from './ui/button';
import { DecimalInput } from './ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { Label } from './ui/label';
import { validateDecimalInput } from '../lib/numberInput';
import { minutenText } from './minutenUtils';
import type { Arbeitsgang } from '../types';

interface Props {
    arbeitsgang: Arbeitsgang;
    isOpen: boolean;
    onClose: () => void;
    onSave: (arbeitsgangId: number, ruestzeitMinuten: number | null, bearbeitungszeitMinuten: number | null) => Promise<void>;
}

const REGEL_RUEST = { label: 'Rüstzeit', required: false, min: 0, max: 100000 } as const;
const REGEL_BEARBEITUNG = { label: 'Bearbeitungszeit', required: false, min: 0, max: 100000 } as const;

/** Rüstzeit und Bearbeitungszeit eines Arbeitsgangs: die Normzeiten, mit denen die Leistungskalkulation die Arbeitszeit vorbelegt. */
export const ArbeitsgangZeitenModal: React.FC<Props> = ({ arbeitsgang, isOpen, onClose, onSave }) => {
    const [ruest, setRuest] = useState('');
    const [bearbeitung, setBearbeitung] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        if (!isOpen) return;
        setRuest(minutenText(arbeitsgang.ruestzeitMinuten));
        setBearbeitung(minutenText(arbeitsgang.bearbeitungszeitMinuten));
        setFehler('');
    }, [isOpen, arbeitsgang.id, arbeitsgang.ruestzeitMinuten, arbeitsgang.bearbeitungszeitMinuten]);

    const speichern = async () => {
        const r = validateDecimalInput(ruest, REGEL_RUEST);
        if (!r.valid) { setFehler(r.message); return; }
        const b = validateDecimalInput(bearbeitung, REGEL_BEARBEITUNG);
        if (!b.valid) { setFehler(b.message); return; }
        setSpeichert(true);
        setFehler('');
        try {
            await onSave(arbeitsgang.id, r.value, b.value);
            onClose();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Zeiten konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={(offen) => { if (!offen) onClose(); }}>
            <DialogContent className="sm:max-w-[460px]">
                <DialogHeader>
                    <DialogTitle>Zeiten bearbeiten</DialogTitle>
                    <DialogDescription>{arbeitsgang.beschreibung}</DialogDescription>
                </DialogHeader>
                <div className="space-y-4 py-2">
                    <p className="text-sm text-slate-600">
                        Die Zeiten sind Richtwerte. In der Kalkulation einer Leistung füllen sie die Zeile vor, dort lassen sie sich je Leistung noch ändern.
                        Bestehende Kalkulationen ändern sich dadurch nicht.
                    </p>
                    {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}
                    <div className="space-y-2">
                        <Label htmlFor="ruestzeit">Rüstzeit (Minuten, einmal je Los)</Label>
                        <DecimalInput id="ruestzeit" min={0} max={100000} value={ruest} onChange={setRuest} placeholder="z. B. 30"
                            className="text-right font-mono" />
                        <p className="text-xs text-slate-500">Maschine einrichten, Material holen, aufräumen: fällt einmal an, egal wie viele Stück gemacht werden.</p>
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="bearbeitungszeit">Bearbeitungszeit (Minuten je Einheit)</Label>
                        <DecimalInput id="bearbeitungszeit" min={0} max={100000} value={bearbeitung} onChange={setBearbeitung} placeholder="z. B. 6,5"
                            className="text-right font-mono" />
                        <p className="text-xs text-slate-500">Zeit für ein Stück, einen laufenden Meter oder einen Quadratmeter.</p>
                    </div>
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

export default ArbeitsgangZeitenModal;
