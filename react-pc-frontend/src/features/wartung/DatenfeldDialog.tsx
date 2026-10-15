import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { fuegeDatenfeldHinzu } from './api';
import { optionenAusText } from './optionen';
import type { WartungFeldtyp, WartungProtokoll } from './types';

export interface DatenfeldGruppe {
    /** Modul-Schlüssel, null = "Allgemein". */
    schluessel: string | null;
    titel: string;
}

interface DatenfeldDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    protokollId: number;
    gruppen: DatenfeldGruppe[];
    /** Gruppe, in der das Feld erscheinen soll (vorbelegt, änderbar). */
    vorbelegteGruppe: string | null;
    onHinzugefuegt: (protokoll: WartungProtokoll) => void;
}

const ARTEN: { value: WartungFeldtyp; label: string }[] = [
    { value: 'TEXT', label: 'Text (frei eintippen)' },
    { value: 'JA_NEIN', label: 'Ja oder Nein' },
    { value: 'AUSWAHL', label: 'Auswahl aus eigenen Möglichkeiten' },
    { value: 'JAHR', label: 'Jahreszahl' },
];

/** Ergänzt ein Datenfeld im laufenden Feldeinsatz. Es gilt nur für diesen Feldeinsatz, der Katalog bleibt unverändert. */
export function DatenfeldDialog({ open, onOpenChange, protokollId, gruppen, vorbelegteGruppe, onHinzugefuegt }: DatenfeldDialogProps) {
    const toast = useToast();
    const [label, setLabel] = useState('');
    const [art, setArt] = useState<WartungFeldtyp>('TEXT');
    const [optionenText, setOptionenText] = useState('');
    const [gruppe, setGruppe] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState<{ feld: 'label' | 'optionen'; text: string } | undefined>();

    useEffect(() => {
        if (open) {
            setLabel('');
            setArt('TEXT');
            setOptionenText('');
            setGruppe(vorbelegteGruppe ?? '');
            setFehler(undefined);
        }
    }, [open, vorbelegteGruppe]);

    const speichern = async () => {
        if (label.trim() === '') {
            setFehler({ feld: 'label', text: 'Bitte das Datenfeld benennen, zum Beispiel „Baujahr“.' });
            return;
        }
        const optionen = optionenAusText(optionenText);
        if (art === 'AUSWAHL' && optionen.length < 2) {
            setFehler({ feld: 'optionen', text: 'Bitte mindestens zwei Möglichkeiten eintragen, je eine pro Zeile.' });
            return;
        }
        setSpeichert(true);
        try {
            const protokoll = await fuegeDatenfeldHinzu(protokollId, { modulKey: gruppe === '' ? null : gruppe, label, feldtyp: art, optionen });
            toast.success('Datenfeld hinzugefügt.');
            onHinzugefuegt(protokoll);
            onOpenChange(false);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Datenfeld konnte nicht ergänzt werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[480px]">
                <DialogHeader>
                    <DialogTitle>Datenfeld hinzufügen</DialogTitle>
                    <DialogDescription>
                        Das Feld gilt nur für diesen Feldeinsatz. Der Katalog und frühere Prüfungen bleiben unverändert.
                    </DialogDescription>
                </DialogHeader>

                <div className="flex flex-col gap-4 py-2">
                    <div>
                        <Label htmlFor="df-label">Bezeichnung</Label>
                        <Input
                            id="df-label"
                            value={label}
                            maxLength={200}
                            placeholder="z. B. Baujahr, Rauchmelder Einbaudatum"
                            aria-invalid={fehler?.feld === 'label' ? true : undefined}
                            aria-describedby={fehler?.feld === 'label' ? 'df-label-fehler' : undefined}
                            onChange={(e) => { setLabel(e.target.value); setFehler(undefined); }}
                            onKeyDown={(e) => { if (e.key === 'Enter' && art !== 'AUSWAHL') { e.preventDefault(); void speichern(); } }}
                        />
                        {fehler?.feld === 'label' && <p id="df-label-fehler" role="alert" className="mt-1 text-sm text-rose-700">{fehler.text}</p>}
                    </div>
                    <div>
                        <Label htmlFor="df-art">Art des Feldes</Label>
                        <Select id="df-art" value={art} onChange={(w) => { setArt(w as WartungFeldtyp); setFehler(undefined); }} options={ARTEN} />
                    </div>
                    {art === 'AUSWAHL' && (
                        <div>
                            <Label htmlFor="df-optionen">Möglichkeiten (eine pro Zeile)</Label>
                            <textarea
                                id="df-optionen"
                                className="w-full min-h-[96px] p-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                                value={optionenText}
                                placeholder={'gut\nmittel\nschlecht'}
                                aria-invalid={fehler?.feld === 'optionen' ? true : undefined}
                                aria-describedby={fehler?.feld === 'optionen' ? 'df-optionen-fehler' : undefined}
                                onChange={(e) => { setOptionenText(e.target.value); setFehler(undefined); }}
                            />
                            {fehler?.feld === 'optionen' && <p id="df-optionen-fehler" role="alert" className="mt-1 text-sm text-rose-700">{fehler.text}</p>}
                        </div>
                    )}
                    <div>
                        <Label htmlFor="df-gruppe">In Gruppe</Label>
                        <Select
                            id="df-gruppe"
                            value={gruppe}
                            onChange={setGruppe}
                            options={gruppen.map((g) => ({ value: g.schluessel ?? '', label: g.titel }))}
                        />
                    </div>
                </div>

                <DialogFooter>
                    <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} disabled={speichert}>Abbrechen</Button>
                    <Button size="sm" onClick={() => void speichern()} disabled={speichert}>
                        {speichert && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
                        {speichert ? 'Speichert...' : 'Hinzufügen'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
