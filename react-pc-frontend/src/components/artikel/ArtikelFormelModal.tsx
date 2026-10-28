import { useEffect, useRef, useState } from 'react';
import { Calculator, Plus, Save, Trash2, X } from 'lucide-react';
import { Button } from '../ui/button';
import { DecimalInput } from '../ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import type { ArbeitsgangFormelFeld } from '../../types';

export interface ArtikelFormel {
    felder: ArbeitsgangFormelFeld[];
    gewicht: string;
    flaeche: string;
}

interface Props {
    isOpen: boolean;
    onClose: () => void;
    /** Der Stand beim Öffnen */
    formel: ArtikelFormel;
    /** Für die Testrechnung: Werkstoff (Dichte) und Gewicht/Fläche je Meter des Artikels als Text */
    werkstoffId: number | null;
    kgJeMeter: string;
    m2JeMeter: string;
    /** Übernimmt die Formel ins Formular; gespeichert wird erst mit „Speichern" der Stammdaten. Eine leere Formel entfernt sie. */
    onUebernehmen: (formel: ArtikelFormel) => void;
}

interface FeldText { name: string; bezeichnung: string; einheit: string; standard: string }

const MAX_FELDER = 8;

/** Fertige Vorlagen: Man übernimmt sie und passt sie bei Bedarf an. Maße in mm, Gewicht in kg, Fläche in m². */
const VORLAGEN: { name: string; felder: FeldText[]; gewicht: string; flaeche: string; hinweis: string }[] = [
    {
        name: 'Blechzuschnitt',
        felder: [
            { name: 'L', bezeichnung: 'Länge', einheit: 'mm', standard: '1000' },
            { name: 'B', bezeichnung: 'Breite', einheit: 'mm', standard: '500' },
            { name: 'T', bezeichnung: 'Dicke', einheit: 'mm', standard: '10' },
        ],
        gewicht: '(L/1000) * (B/1000) * T * dichte',
        flaeche: '(L/1000) * (B/1000) * 2',
        hinweis: 'Gewicht = Länge × Breite × Dicke × Dichte des Werkstoffs; Fläche beide Seiten.',
    },
    {
        name: 'Abschnitt von Meterware',
        felder: [{ name: 'L', bezeichnung: 'Länge', einheit: 'mm', standard: '1000' }],
        gewicht: '(L/1000) * kg_je_meter',
        flaeche: '(L/1000) * m2_je_meter',
        hinweis: 'Braucht Gewicht und Außenfläche je Meter am Artikel (Bereich „Gewicht und Fläche", Meterware).',
    },
];

/**
 * Zuschnitt-Formel am Artikel (#228): Eingabefelder (zum Beispiel Länge, Breite, Dicke in mm) und eine Formel für das Gewicht in kg (Verzinken) und die Außenfläche in m²
 * (Pulverbeschichten) je Stück. In der Leistung trägt man die Maße ein, das Programm rechnet Gewicht, Fläche und den Preis je Stück.
 */
export function ArtikelFormelModal({ isOpen, onClose, formel, werkstoffId, kgJeMeter, m2JeMeter, onUebernehmen }: Props) {
    const [felder, setFelder] = useState<FeldText[]>([]);
    const [gewicht, setGewicht] = useState('');
    const [flaeche, setFlaeche] = useState('');
    const [testWerte, setTestWerte] = useState<Record<string, string>>({});
    const [ergebnis, setErgebnis] = useState<{ kg: number | null; m2: number | null } | null>(null);
    const [rechenFehler, setRechenFehler] = useState('');
    const [fehler, setFehler] = useState('');
    const anfrageNr = useRef(0);

    useEffect(() => {
        if (!isOpen) return;
        setFelder(formel.felder.map(f => ({ name: f.name, bezeichnung: f.bezeichnung, einheit: f.einheit ?? '', standard: f.standard == null ? '' : formatDecimalInput(f.standard) })));
        setGewicht(formel.gewicht);
        setFlaeche(formel.flaeche);
        setTestWerte({});
        setErgebnis(null);
        setRechenFehler('');
        setFehler('');
    }, [isOpen, formel]);

    const alsFelder = (): ArbeitsgangFormelFeld[] | string => {
        const aus: ArbeitsgangFormelFeld[] = [];
        for (const f of felder) {
            if (f.name.trim() === '') continue;
            const standard = validateDecimalInput(f.standard, { label: `Standardwert von ${f.name}`, required: false, min: -1000000000, max: 1000000000 });
            if (!standard.valid) return standard.message;
            aus.push({ name: f.name.trim(), bezeichnung: f.bezeichnung.trim(), einheit: f.einheit.trim(), standard: standard.value ?? null });
        }
        return aus;
    };

    const zahlOderNull = (t: string) => { const r = validateDecimalInput(t, { label: 'Wert', required: false, min: 0, max: 100000 }); return r.valid ? r.value ?? null : null; };

    // Testrechnung: nach jeder Änderung (kurz verzögert) auf dem Server rechnen lassen
    useEffect(() => {
        if (!isOpen || (gewicht.trim() === '' && flaeche.trim() === '')) { setErgebnis(null); setRechenFehler(''); return; }
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
                const res = await fetch('/api/artikel/profile/formel', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ felder: f, gewicht, flaeche, werte, werkstoffId, kgJeMeter: zahlOderNull(kgJeMeter), m2JeMeter: zahlOderNull(m2JeMeter) }),
                });
                const daten = await res.json().catch(() => ({})) as { message?: string; kg?: number | null; m2?: number | null };
                if (nr !== anfrageNr.current) return;
                if (!res.ok) { setErgebnis(null); setRechenFehler(daten.message ?? 'Die Formel lässt sich nicht berechnen.'); return; }
                setRechenFehler('');
                setErgebnis({ kg: daten.kg ?? null, m2: daten.m2 ?? null });
            } catch {
                if (nr === anfrageNr.current) { setErgebnis(null); setRechenFehler('Die Testrechnung ist nicht erreichbar. Ist der Server erreichbar?'); }
            }
        }, 350);
        return () => window.clearTimeout(timer);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOpen, felder, gewicht, flaeche, testWerte, werkstoffId, kgJeMeter, m2JeMeter]);

    const aendereFeld = (i: number, teil: Partial<FeldText>) => setFelder(alt => alt.map((f, j) => (j === i ? { ...f, ...teil } : f)));

    const feldHinzu = () => setFelder(alt => {
        if (alt.length >= MAX_FELDER) return alt;
        let n = alt.length + 1;
        while (alt.some(f => f.name.toLowerCase() === `v${n}`)) n++;
        return [...alt, { name: `V${n}`, bezeichnung: '', einheit: 'mm', standard: '' }];
    });

    const vorlage = (v: typeof VORLAGEN[number]) => {
        setFelder(v.felder);
        setGewicht(v.gewicht);
        setFlaeche(v.flaeche);
        setTestWerte({});
    };

    const uebernehmen = (entfernen = false) => {
        setFehler('');
        if (entfernen) { onUebernehmen({ felder: [], gewicht: '', flaeche: '' }); onClose(); return; }
        const f = alsFelder();
        if (typeof f === 'string') { setFehler(f); return; }
        if (gewicht.trim() === '' && flaeche.trim() === '') { setFehler('Bitte eine Formel für das Gewicht oder die Fläche eintragen, oder die Formel entfernen.'); return; }
        onUebernehmen({ felder: f, gewicht: gewicht.trim(), flaeche: flaeche.trim() });
        onClose();
    };

    const mitFormel = formel.gewicht.trim() !== '' || formel.flaeche.trim() !== '';

    return (
        <Dialog open={isOpen} onOpenChange={(offen) => { if (!offen) onClose(); }}>
            <DialogContent className="sm:max-w-[780px] max-h-[90vh] overflow-y-auto" aria-label="Zuschnitt-Formel bearbeiten">
                <DialogHeader>
                    <DialogTitle>Gewicht und Fläche aus Maßen berechnen</DialogTitle>
                    <DialogDescription>Für Zuschnitte und Abschnitte, deren Maße sich je Auftrag ändern.</DialogDescription>
                </DialogHeader>
                <div className="space-y-4 py-2">
                    <p className="text-sm text-slate-600">
                        Legen Sie Eingabefelder an (zum Beispiel Länge, Breite, Dicke in mm) und je eine Formel für das <strong>Gewicht in kg</strong> (für das Verzinken)
                        und die <strong>Außenfläche in m²</strong> (zum Pulverbeschichten), jeweils <strong>für ein Stück</strong>. In der Leistung tragen Sie nur die Maße ein.
                        Ist der Einkauf je kg oder je m² angegeben, rechnet das Programm daraus auch den Preis je Stück.
                    </p>
                    {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}

                    <div className="space-y-1.5">
                        <Label>Vorlage</Label>
                        <div className="flex flex-wrap gap-2">
                            {VORLAGEN.map(v => (
                                <Button key={v.name} type="button" variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" title={v.hinweis} onClick={() => vorlage(v)}>
                                    {v.name}
                                </Button>
                            ))}
                        </div>
                    </div>

                    <div className="space-y-2">
                        <div className="flex items-center justify-between">
                            <Label>Eingabefelder (bis {MAX_FELDER})</Label>
                            <Button type="button" variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={feldHinzu} disabled={felder.length >= MAX_FELDER}>
                                <Plus className="w-4 h-4" /> Feld hinzufügen
                            </Button>
                        </div>
                        {felder.length === 0 && <p className="text-sm text-slate-500">Noch keine Felder. Die Formel kann auch nur die eingebauten Werte nutzen.</p>}
                        {felder.map((f, i) => (
                            <div key={i} className="grid grid-cols-[5rem_1fr_5rem_7rem_2.25rem] gap-2 items-center" data-testid={`artikel-formel-feld-${i}`}>
                                <Input aria-label={`Name von Feld ${i + 1}`} value={f.name} placeholder="L" className="font-mono" onChange={e => aendereFeld(i, { name: e.target.value })} />
                                <Input aria-label={`Bezeichnung von Feld ${i + 1}`} value={f.bezeichnung} placeholder="Länge" onChange={e => aendereFeld(i, { bezeichnung: e.target.value })} />
                                <Input aria-label={`Einheit von Feld ${i + 1}`} value={f.einheit} placeholder="mm" onChange={e => aendereFeld(i, { einheit: e.target.value })} />
                                <DecimalInput aria-label={`Standardwert von Feld ${i + 1}`} value={f.standard} min={-1000000000} max={1000000000} placeholder="Standard" className="text-right font-mono"
                                    onChange={wert => aendereFeld(i, { standard: wert })} />
                                <Button type="button" variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-100 px-2" aria-label={`Feld ${i + 1} entfernen`}
                                    onClick={() => setFelder(alt => alt.filter((_, j) => j !== i))}><Trash2 className="w-4 h-4" /></Button>
                            </div>
                        ))}
                    </div>

                    <div className="space-y-2">
                        <Label htmlFor="artikel-formel-gewicht">Formel für das Gewicht (kg je Stück)</Label>
                        <Input id="artikel-formel-gewicht" value={gewicht} placeholder="(L/1000) * (B/1000) * T * dichte" className="font-mono" onChange={e => setGewicht(e.target.value)} />
                        <Label htmlFor="artikel-formel-flaeche">Formel für die Außenfläche (m² je Stück, optional)</Label>
                        <Input id="artikel-formel-flaeche" value={flaeche} placeholder="(L/1000) * (B/1000) * 2" className="font-mono" onChange={e => setFlaeche(e.target.value)} />
                        <p className="text-xs text-slate-500">
                            Eingebaute Werte: <code>dichte</code> (kg/dm³ des Werkstoffs, ohne Werkstoff Stahl 7,85), <code>kg_je_meter</code> und <code>m2_je_meter</code> (vom Artikel).
                            Erlaubt: Zahlen, Feldnamen, + - * / ^, Klammern, <code>pi</code>, <code>min(a;b)</code>, <code>max(a;b)</code>, <code>runden(zahl;stellen)</code>,
                            <code> wenn(bedingung;dann;sonst)</code>. Mehrere Werte mit Semikolon trennen. Zum Rechnen in kg: 1 mm Dicke bei 1 m² sind 1 dm³.
                        </p>
                    </div>

                    {(gewicht.trim() !== '' || flaeche.trim() !== '') && (
                        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-2" data-testid="artikel-formel-test">
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
                                <p role="alert" className="text-sm text-rose-800" data-testid="artikel-formel-fehler">{rechenFehler}</p>
                            ) : ergebnis ? (
                                <p className="text-sm text-slate-900" role="status" data-testid="artikel-formel-ergebnis">
                                    Ergebnis je Stück:{' '}
                                    <strong>{ergebnis.kg == null ? 'Gewicht unbekannt' : `${ergebnis.kg.toLocaleString('de-DE', { maximumFractionDigits: 3 })} kg`}</strong>
                                    {', '}
                                    <strong>{ergebnis.m2 == null ? 'Fläche unbekannt' : `${ergebnis.m2.toLocaleString('de-DE', { maximumFractionDigits: 4 })} m²`}</strong>
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
                        <Button variant="outline" size="sm" className="mr-auto border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => uebernehmen(true)}>
                            <Trash2 className="w-4 h-4" /> Formel entfernen
                        </Button>
                    )}
                    <Button variant="outline" size="sm" onClick={onClose}><X className="w-4 h-4" /> Abbrechen</Button>
                    <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" size="sm" onClick={() => uebernehmen()}>
                        <Save className="w-4 h-4" /> Übernehmen
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
