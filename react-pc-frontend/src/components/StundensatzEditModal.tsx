import React, { useState, useEffect } from 'react';
import { Save, AlertTriangle, X } from 'lucide-react';
import { Button } from './ui/button';
import { DecimalInput } from './ui/decimal-input';
import { formatDecimalInput, validateDecimalInput } from '../lib/numberInput';
import { useToast } from './ui/toast';
import { Label } from './ui/label';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from './ui/dialog';
import { type Arbeitsgang } from '../types';

interface StundensatzEditModalProps {
    arbeitsgang: Arbeitsgang;
    isOpen: boolean;
    onClose: () => void;
    onSave: (arbeitsgangId: number, neuerStundensatz: number) => Promise<void>;
    /** Woher der Satz kommt (von Hand oder Verrechnungssatz); ohne diese Funktion gibt es nur die Eingabe von Hand. */
    onSaveQuelle?: (arbeitsgangId: number, quelle: 'EIGEN' | 'VERRECHNUNG', gruppe: string | null) => Promise<void>;
}

const GRUPPEN = [
    { value: '', label: 'Ganzer Betrieb (Durchschnitt)' },
    { value: 'AUSZUBILDENDER', label: 'Auszubildender' },
    { value: 'FACHARBEITER', label: 'Facharbeiter' },
    { value: 'MEISTER', label: 'Meister' },
];

export const StundensatzEditModal: React.FC<StundensatzEditModalProps> = ({
    arbeitsgang,
    isOpen,
    onClose,
    onSave,
    onSaveQuelle,
}) => {
    const toast = useToast();
    const [stundensatz, setStundensatz] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [quelle, setQuelle] = useState<'EIGEN' | 'VERRECHNUNG'>('EIGEN');
    const [gruppe, setGruppe] = useState('');

    const currentYear = new Date().getFullYear();
    const isOutdated = arbeitsgang.stundensatzJahr !== null &&
        currentYear - arbeitsgang.stundensatzJahr >= 1;

    // Bewusst auf id/stundensatz statt auf das ganze arbeitsgang-Objekt hoeren:
    // uebergibt der Aufrufer ein frisch erzeugtes Objekt, lief der Effekt sonst
    // nach jedem Render erneut und hat den getippten Entwurf wieder auf den
    // gespeicherten Wert zurueckgesetzt.
    useEffect(() => {
        if (isOpen) {
            setStundensatz(arbeitsgang.stundensatz == null ? '' : formatDecimalInput(arbeitsgang.stundensatz));
            setError('');
            setQuelle(arbeitsgang.satzQuelle === 'VERRECHNUNG' ? 'VERRECHNUNG' : 'EIGEN');
            setGruppe(arbeitsgang.satzGruppe ?? '');
        }
    }, [isOpen, arbeitsgang.id, arbeitsgang.stundensatz, arbeitsgang.satzQuelle, arbeitsgang.satzGruppe]);

    const handleSave = async () => {
        if (onSaveQuelle && quelle === 'VERRECHNUNG') {
            setSaving(true);
            setError('');
            try {
                await onSaveQuelle(arbeitsgang.id, 'VERRECHNUNG', gruppe || null);
                onClose();
            } catch (err) {
                setError('Fehler beim Speichern.'); toast.error('Die Einstellung konnte nicht gespeichert werden.');
                console.error(err);
            } finally {
                setSaving(false);
            }
            return;
        }
        const result = validateDecimalInput(stundensatz, { label: 'Kalkulationssatz', required: true, min: 0 });
        if (!result.valid || result.value === null) {
            const message = !result.valid ? result.message : 'Bitte Stundensatz eingeben.';
            setError(message); toast.error(message);
            return;
        }

        setSaving(true);
        setError('');
        try {
            await onSave(arbeitsgang.id, result.value);
            if (onSaveQuelle && arbeitsgang.satzQuelle === 'VERRECHNUNG') {
                await onSaveQuelle(arbeitsgang.id, 'EIGEN', null);
            }
            onClose();
        } catch (err) {
            setError('Fehler beim Speichern des Kalkulationssatzes.'); toast.error('Kalkulationssatz konnte nicht gespeichert werden.');
            console.error(err);
        } finally {
            setSaving(false);
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            handleSave();
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }}>
            <DialogContent className="sm:max-w-[420px]">
                <DialogHeader>
                    <DialogTitle>Kalkulationssatz bearbeiten</DialogTitle>
                    <DialogDescription>{arbeitsgang.beschreibung}</DialogDescription>
                </DialogHeader>

                <div className="space-y-4 py-2">
                    {isOutdated && (
                        <div className="flex items-start gap-3 p-3 rounded-lg bg-orange-50 border border-orange-200">
                            <AlertTriangle className="w-5 h-5 text-orange-500 flex-shrink-0 mt-0.5" />
                            <div>
                                <p className="text-sm font-medium text-orange-800">Kalkulationssatz veraltet</p>
                                <p className="text-xs text-orange-600 mt-0.5">
                                    Letzter Stundensatz aus {arbeitsgang.stundensatzJahr}.
                                    Bitte aktuellen Wert für {currentYear} eingeben.
                                </p>
                            </div>
                        </div>
                    )}

                    {error && (
                        <div className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">
                            {error}
                        </div>
                    )}

                    {onSaveQuelle && (
                        <fieldset className="space-y-2">
                            <legend className="text-sm font-medium text-slate-900">Woher kommt der Satz?</legend>
                            <label className="flex items-start gap-2 text-sm text-slate-700">
                                <input type="radio" name="satzquelle" className="mt-1 accent-rose-600" checked={quelle === 'EIGEN'} onChange={() => setQuelle('EIGEN')} />
                                <span>Von Hand eintragen</span>
                            </label>
                            <label className="flex items-start gap-2 text-sm text-slate-700">
                                <input type="radio" name="satzquelle" className="mt-1 accent-rose-600" checked={quelle === 'VERRECHNUNG'} onChange={() => setQuelle('VERRECHNUNG')} />
                                <span>Folgt dem Verrechnungssatz aus der Kostenrechnung</span>
                            </label>
                            {quelle === 'VERRECHNUNG' && (
                                <div className="ml-6 space-y-1">
                                    <Label htmlFor="satzgruppe">Welche Stunde?</Label>
                                    <select id="satzgruppe" value={gruppe} onChange={e => setGruppe(e.target.value)} className="w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm">
                                        {GRUPPEN.map(g => <option key={g.value} value={g.value}>{g.label}</option>)}
                                    </select>
                                    <p className="text-xs text-slate-500">
                                        Der Satz wird unter Kostenrechnung → Kostensätze → „In die Arbeitsgänge übernehmen“ aktualisiert, mit Vorschau. Bis dahin gilt der bisherige Satz.
                                    </p>
                                </div>
                            )}
                        </fieldset>
                    )}

                    {quelle === 'EIGEN' && (
                    <div className="space-y-2">
                        <Label htmlFor="stundensatz">Neuer Kalkulationssatz (€/h)</Label>
                        <DecimalInput
                            id="stundensatz"
                            min={0}
                            required
                            value={stundensatz}
                            onChange={setStundensatz}
                            onKeyDown={handleKeyDown}
                            placeholder="z. B. 65,00"
                            className="text-right font-mono"
                        />
                        {arbeitsgang.stundensatz !== null && (
                            <p className="text-xs text-slate-500">
                                Aktueller Wert: {arbeitsgang.stundensatz.toLocaleString('de-DE', { minimumFractionDigits: 2 })} €/h
                                {arbeitsgang.stundensatzJahr && ` (${arbeitsgang.stundensatzJahr})`}
                            </p>
                        )}
                    </div>
                    )}
                </div>

                <DialogFooter>
                    <Button variant="outline" size="sm" onClick={onClose} disabled={saving}>
                        <X className="w-4 h-4" /> Abbrechen
                    </Button>
                    <Button
                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                        size="sm"
                        onClick={handleSave}
                        disabled={saving}
                    >
                        <Save className="w-4 h-4" /> {saving ? 'Speichert...' : 'Speichern'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
};

export default StundensatzEditModal;
