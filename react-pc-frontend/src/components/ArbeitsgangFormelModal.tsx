import React, { useEffect, useRef, useState } from 'react';
import { Calculator, Plus, Save, Trash2, X } from 'lucide-react';
import { Button } from './ui/button';
import { DecimalInput } from './ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { formatDecimalInput, validateDecimalInput } from '../lib/numberInput';
import type { Arbeitsgang, ArbeitsgangFormelFeld } from '../types';

interface Props {
    arbeitsgang: Arbeitsgang;
    isOpen: boolean;
    onClose: () => void;
    /** Speichert die Formel; eine leere Bearbeitungsformel entfernt sie. Wirft bei Fehlern mit Klartext. */
    onSave: (arbeitsgangId: number, formel: { felder: ArbeitsgangFormelFeld[]; bearbeitungFormel: string; ruestFormel: string }) => Promise<void>;
}

interface FeldText { name: string; bezeichnung: string; einheit: string; standard: string }

const MAX_FELDER = 8;

/**
 * Arbeitsgang mit Formel (#216): Eingabefelder (zum Beispiel Durchmesser, Länge, Vorschub) und eine Formel für die Bearbeitungszeit in Minuten.
 * In der Kalkulation einer Leistung trägt man die Werte ein, die Zeit rechnet das Programm. Die Testrechnung unten zeigt das Ergebnis sofort.
 */
export const ArbeitsgangFormelModal: React.FC<Props> = ({ arbeitsgang, isOpen, onClose, onSave }) => {
    const [felder, setFelder] = useState<FeldText[]>([]);
    const [bearbeitung, setBearbeitung] = useState('');
    const [ruest, setRuest] = useState('');
    const [testWerte, setTestWerte] = useState<Record<string, string>>({});
    const [ergebnis, setErgebnis] = useState<{ ruest: number | null; bearbeitung: number } | null>(null);
    const [rechenFehler, setRechenFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');
    const anfrageNr = useRef(0);

    useEffect(() => {
        if (!isOpen) return;
        setFelder((arbeitsgang.formelFelder ?? []).map(f => ({ name: f.name, bezeichnung: f.bezeichnung, einheit: f.einheit ?? '', standard: f.standard == null ? '' : formatDecimalInput(f.standard) })));
        setBearbeitung(arbeitsgang.formelBearbeitung ?? '');
        setRuest(arbeitsgang.formelRuest ?? '');
        setTestWerte({});
        setErgebnis(null);
        setRechenFehler('');
        setFehler('');
    }, [isOpen, arbeitsgang.id, arbeitsgang.formelFelder, arbeitsgang.formelBearbeitung, arbeitsgang.formelRuest]);

    const alsFelder = (): ArbeitsgangFormelFeld[] | string => {
        const ergebnisFelder: ArbeitsgangFormelFeld[] = [];
        for (const f of felder) {
            if (f.name.trim() === '') continue;
            const standard = validateDecimalInput(f.standard, { label: `Standardwert von ${f.name}`, required: false, min: -1000000000, max: 1000000000 });
            if (!standard.valid) return standard.message;
            ergebnisFelder.push({ name: f.name.trim(), bezeichnung: f.bezeichnung.trim(), einheit: f.einheit.trim(), standard: standard.value });
        }
        return ergebnisFelder;
    };

    // Testrechnung: nach jeder Änderung (kurz verzögert) mit den Standardwerten bzw. den Testwerten rechnen lassen
    useEffect(() => {
        if (!isOpen || bearbeitung.trim() === '') { setErgebnis(null); setRechenFehler(''); return; }
        const nr = ++anfrageNr.current;
        const timer = window.setTimeout(async () => {
            const f = alsFelder();
            if (typeof f === 'string') { setErgebnis(null); setRechenFehler(f); return; }
            const werte: Record<string, number> = {};
            for (const [name, text] of Object.entries(testWerte)) {
                const w = validateDecimalInput(text, { label: name, required: false, min: -1000000000, max: 1000000000 });
                if (w.valid && w.value != null) werte[name] = w.value;
            }
            try {
                const res = await fetch('/api/arbeitsgaenge/formel/berechnen', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ felder: f, bearbeitungFormel: bearbeitung, ruestFormel: ruest, werte }),
                });
                const daten = await res.json().catch(() => ({})) as { message?: string; bearbeitungszeitMinuten?: number; ruestzeitMinuten?: number | null };
                if (nr !== anfrageNr.current) return;
                if (!res.ok) { setErgebnis(null); setRechenFehler(daten.message ?? 'Die Formel lässt sich nicht berechnen.'); return; }
                setRechenFehler('');
                setErgebnis({ bearbeitung: daten.bearbeitungszeitMinuten ?? 0, ruest: daten.ruestzeitMinuten ?? null });
            } catch {
                if (nr === anfrageNr.current) { setErgebnis(null); setRechenFehler('Die Testrechnung ist nicht erreichbar. Ist der Server erreichbar?'); }
            }
        }, 350);
        return () => window.clearTimeout(timer);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOpen, felder, bearbeitung, ruest, testWerte]);

    const aendereFeld = (i: number, teil: Partial<FeldText>) => setFelder(alt => alt.map((f, j) => j === i ? { ...f, ...teil } : f));

    const feldHinzu = () => setFelder(alt => {
        if (alt.length >= MAX_FELDER) return alt;
        let n = alt.length + 1;
        while (alt.some(f => f.name.toLowerCase() === `v${n}`)) n++;
        return [...alt, { name: `V${n}`, bezeichnung: '', einheit: '', standard: '' }];
    });

    const speichern = async (entfernen = false) => {
        setFehler('');
        const f = alsFelder();
        if (typeof f === 'string') { setFehler(f); return; }
        setSpeichert(true);
        try {
            await onSave(arbeitsgang.id, entfernen ? { felder: [], bearbeitungFormel: '', ruestFormel: '' } : { felder: f, bearbeitungFormel: bearbeitung, ruestFormel: ruest });
            onClose();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Formel konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const mitFormel = !!arbeitsgang.formelBearbeitung;

    return (
        <Dialog open={isOpen} onOpenChange={(offen) => { if (!offen) onClose(); }}>
            <DialogContent className="sm:max-w-[760px] max-h-[90vh] overflow-y-auto" aria-label="Formel bearbeiten">
                <DialogHeader>
                    <DialogTitle>Zeit mit Formel berechnen</DialogTitle>
                    <DialogDescription>{arbeitsgang.beschreibung}</DialogDescription>
                </DialogHeader>
                <div className="space-y-4 py-2">
                    <p className="text-sm text-slate-600">
                        Legen Sie Eingabefelder an (zum Beispiel Bohrerdurchmesser, Bohrtiefe) und eine Formel für die Bearbeitungszeit je Einheit in Minuten.
                        In der Leistung tragen Sie dann nur die Werte ein, die Zeit rechnet das Programm. Bestehende Kalkulationen ändern sich nicht.
                    </p>
                    {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}

                    <div className="space-y-2">
                        <div className="flex items-center justify-between">
                            <Label>Eingabefelder (bis {MAX_FELDER})</Label>
                            <Button type="button" variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={feldHinzu} disabled={felder.length >= MAX_FELDER}>
                                <Plus className="w-4 h-4" /> Feld hinzufügen
                            </Button>
                        </div>
                        {felder.length === 0 && <p className="text-sm text-slate-500">Noch keine Felder. Die Formel kann auch nur aus Zahlen bestehen.</p>}
                        {felder.map((f, i) => (
                            <div key={i} className="grid grid-cols-[5rem_1fr_5rem_7rem_2.25rem] gap-2 items-center" data-testid={`formel-feld-${i}`}>
                                <Input aria-label={`Name von Feld ${i + 1}`} value={f.name} placeholder="V1" className="font-mono" onChange={e => aendereFeld(i, { name: e.target.value })} />
                                <Input aria-label={`Bezeichnung von Feld ${i + 1}`} value={f.bezeichnung} placeholder="Durchmesser außen" onChange={e => aendereFeld(i, { bezeichnung: e.target.value })} />
                                <Input aria-label={`Einheit von Feld ${i + 1}`} value={f.einheit} placeholder="mm" onChange={e => aendereFeld(i, { einheit: e.target.value })} />
                                <DecimalInput aria-label={`Standardwert von Feld ${i + 1}`} value={f.standard} min={-1000000000} max={1000000000} placeholder="Standard" className="text-right font-mono"
                                    onChange={wert => aendereFeld(i, { standard: wert })} />
                                <Button type="button" variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-100 px-2" aria-label={`Feld ${i + 1} entfernen`}
                                    onClick={() => setFelder(alt => alt.filter((_, j) => j !== i))}><Trash2 className="w-4 h-4" /></Button>
                            </div>
                        ))}
                    </div>

                    <div className="space-y-2">
                        <Label htmlFor="formel-bearbeitung">Formel für die Bearbeitungszeit (Minuten je Einheit)</Label>
                        <Input id="formel-bearbeitung" value={bearbeitung} placeholder="(V2*V3)/(V5*(V4/(3,14*V1/1000)))+V6" className="font-mono" onChange={e => setBearbeitung(e.target.value)} />
                        <Label htmlFor="formel-ruest">Formel für die Rüstzeit (Minuten, einmal je Los, optional)</Label>
                        <Input id="formel-ruest" value={ruest} placeholder="30" className="font-mono" onChange={e => setRuest(e.target.value)} />
                        <p className="text-xs text-slate-500">
                            Erlaubt: Zahlen (Komma oder Punkt), Feldnamen, + - * / ^ und Klammern, <code>pi</code>, <code>min(a;b)</code>, <code>max(a;b)</code>,
                            <code> runden(zahl;stellen)</code> und <code>wenn(bedingung;dann;sonst)</code> mit &lt; &gt; = &lt;&gt;. Mehrere Werte mit Semikolon trennen.
                        </p>
                    </div>

                    {bearbeitung.trim() !== '' && (
                        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-2" data-testid="formel-test">
                            <div className="flex items-center gap-2 text-sm font-medium text-slate-800"><Calculator className="w-4 h-4 text-rose-600" aria-hidden="true" /> Testrechnung</div>
                            {felder.filter(f => f.name.trim() !== '').length > 0 && (
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                                    {felder.filter(f => f.name.trim() !== '').map(f => (
                                        <div key={f.name} className="flex items-center gap-2">
                                            <DecimalInput aria-label={`Testwert ${f.bezeichnung || f.name}`} value={testWerte[f.name.toLowerCase()] ?? ''} min={-1000000000} max={1000000000}
                                                placeholder={f.standard} onChange={wert => setTestWerte(alt => ({ ...alt, [f.name.toLowerCase()]: wert }))} />
                                            <span className="text-xs text-slate-600">{f.bezeichnung || f.name}{f.einheit ? ` (${f.einheit})` : ''}</span>
                                        </div>
                                    ))}
                                </div>
                            )}
                            {rechenFehler ? (
                                <p role="alert" className="text-sm text-rose-800" data-testid="formel-fehler">{rechenFehler}</p>
                            ) : ergebnis ? (
                                <p className="text-sm text-slate-900" role="status" data-testid="formel-ergebnis-test">
                                    Ergebnis: <strong>{ergebnis.bearbeitung.toLocaleString('de-DE', { maximumFractionDigits: 2 })} min je Einheit</strong>
                                    {ergebnis.ruest != null && <>, <strong>{ergebnis.ruest.toLocaleString('de-DE', { maximumFractionDigits: 2 })} min rüsten</strong></>}
                                </p>
                            ) : (
                                <p className="text-sm text-slate-500">Wird gerechnet …</p>
                            )}
                            <p className="text-xs text-slate-500">Leere Testwerte nehmen den Standardwert des Feldes.</p>
                        </div>
                    )}
                </div>
                <DialogFooter>
                    {mitFormel && (
                        <Button variant="outline" size="sm" className="mr-auto border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => void speichern(true)} disabled={speichert}>
                            <Trash2 className="w-4 h-4" /> Formel entfernen
                        </Button>
                    )}
                    <Button variant="outline" size="sm" onClick={onClose} disabled={speichert}><X className="w-4 h-4" /> Abbrechen</Button>
                    <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" size="sm" onClick={() => void speichern()} disabled={speichert || bearbeitung.trim() === ''}>
                        <Save className="w-4 h-4" /> {speichert ? 'Speichert...' : 'Speichern'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
};

export default ArbeitsgangFormelModal;
