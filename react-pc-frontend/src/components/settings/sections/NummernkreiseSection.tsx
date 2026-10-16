import { useCallback, useEffect, useState } from 'react';
import { Hash, RotateCcw, Save } from 'lucide-react';
import { Button } from '../../ui/button';
import { DecimalInput } from '../../ui/decimal-input';
import { Input } from '../../ui/input';
import { Label } from '../../ui/label';
import { useToast } from '../../ui/toast';
import { validateDecimalInput } from '../../../lib/numberInput';
import { SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

type Datumsteil = 'KEINER' | 'JAHR' | 'JAHR_MONAT';
type Ruecksetzung = 'NIE' | 'JAEHRLICH' | 'MONATLICH';

interface Zeile {
    art: string;
    bezeichnung: string;
    eigenesFormat: boolean;
    praefix: string;
    datumsteil: Datumsteil;
    ruecksetzung: Ruecksetzung;
    stellen: number;
    startnummer: number;
    naechsteNummer: string;
    warnung: string | null;
}

const DATUM: { wert: Datumsteil; text: string }[] = [
    { wert: 'KEINER', text: 'Kein Datum' },
    { wert: 'JAHR', text: 'Jahr (2026)' },
    { wert: 'JAHR_MONAT', text: 'Jahr und Monat (2026-10)' },
];
const RUECK: { wert: Ruecksetzung; text: string }[] = [
    { wert: 'NIE', text: 'Fortlaufend, nie zurück auf 1' },
    { wert: 'JAEHRLICH', text: 'Jedes Jahr wieder ab Start' },
    { wert: 'MONATLICH', text: 'Jeden Monat wieder ab Start' },
];

/** Die Nummer, wie sie mit diesen Einstellungen aussähe (nur zur Anzeige beim Bearbeiten; die echte Nummer vergibt der Server). */
function beispiel(z: Zeile, praefix: string, datum: Datumsteil, stellen: number, start: number): string {
    const jetzt = new Date();
    const d = datum === 'KEINER' ? '' : datum === 'JAHR' ? `${jetzt.getFullYear()}-` : `${jetzt.getFullYear()}-${String(jetzt.getMonth() + 1).padStart(2, '0')}-`;
    return `${praefix || z.art.slice(0, 2)}-${d}${String(Math.max(start, 1)).padStart(stellen, '0')}`;
}

const SELECT = 'min-h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm';

/**
 * Nummernkreise: je Dokumentart Präfix, Datumsteil, Zähler und Stellen einstellen. Ohne eigene Einstellung gilt das bisherige Format. Eine vergebene Nummer ändert
 * sich nie, eine Änderung gilt nur für neue Dokumente.
 */
export function NummernkreiseSection() {
    const toast = useToast();
    const [zeilen, setZeilen] = useState<Zeile[] | null>(null);
    const [fehler, setFehler] = useState('');
    const [offen, setOffen] = useState<string | null>(null);
    const [praefix, setPraefix] = useState('');
    const [datum, setDatum] = useState<Datumsteil>('JAHR');
    const [rueck, setRueck] = useState<Ruecksetzung>('JAEHRLICH');
    const [stellen, setStellen] = useState('4');
    const [start, setStart] = useState('1');
    const [meldung, setMeldung] = useState('');
    const [speichert, setSpeichert] = useState(false);

    const laden = useCallback(async () => {
        try {
            const res = await fetch('/api/nummernkreise');
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Nummernkreise konnten nicht geladen werden.'));
            setZeilen(await res.json() as Zeile[]);
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Nummernkreise konnten nicht geladen werden.');
        }
    }, []);

    useEffect(() => { void laden(); }, [laden]);

    const oeffne = (z: Zeile) => {
        setOffen(z.art); setPraefix(z.praefix); setDatum(z.datumsteil); setRueck(z.ruecksetzung); setStellen(String(z.stellen)); setStart(String(z.startnummer)); setMeldung('');
    };

    const speichere = async (z: Zeile) => {
        const s = validateDecimalInput(stellen, { label: 'Stellen', required: true, min: 3, max: 8, integer: true });
        if (!s.valid) { setMeldung(s.message); return; }
        const n = validateDecimalInput(start, { label: 'Startnummer', required: true, min: 1, integer: true });
        if (!n.valid) { setMeldung(n.message); return; }
        setSpeichert(true); setMeldung('');
        try {
            const res = await fetch(`/api/nummernkreise/${z.art}`, {
                method: 'PUT', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ praefix, datumsteil: datum, ruecksetzung: rueck, stellen: s.value, startnummer: n.value }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Nummernkreis konnte nicht gespeichert werden.'));
            const neu = await res.json() as Zeile;
            toast.success(`Nummernkreis ${z.bezeichnung} gespeichert. Die nächste Nummer: ${neu.naechsteNummer}`);
            setOffen(null);
            await laden();
        } catch (e) {
            setMeldung(e instanceof Error ? e.message : 'Der Nummernkreis konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const zurueck = async (z: Zeile) => {
        setSpeichert(true); setMeldung('');
        try {
            const res = await fetch(`/api/nummernkreise/${z.art}`, { method: 'DELETE' });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Das Zurücksetzen hat nicht geklappt.'));
            toast.success(`${z.bezeichnung}: wieder das bisherige Format. Vergebene Nummern bleiben unverändert.`);
            setOffen(null);
            await laden();
        } catch (e) {
            setMeldung(e instanceof Error ? e.message : 'Das Zurücksetzen hat nicht geklappt.');
        } finally {
            setSpeichert(false);
        }
    };

    if (!zeilen && !fehler) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<Hash className="w-5 h-5 text-rose-600" />}
            title="Nummernkreise"
            description={<p>So sehen die Nummern Ihrer Rechnungen, Angebote und Lieferscheine aus. Eine einmal vergebene Nummer ändert sich nie; eine Änderung gilt nur für neue Dokumente. Rechnungen laufen ohne Lücke.</p>}
        >
            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {zeilen && (
                <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200" data-testid="nummernkreise">
                    {zeilen.map(z => (
                        <li key={z.art} className="p-3" data-testid={`nummernkreis-${z.art}`}>
                            <div className="flex flex-wrap items-center gap-3">
                                <span className="w-44 shrink-0 text-sm font-medium text-slate-800">{z.bezeichnung}</span>
                                <span className="flex-1 font-mono text-sm text-slate-700">{z.naechsteNummer}</span>
                                {z.eigenesFormat && <span className="rounded-full bg-rose-50 px-2 py-0.5 text-xs text-rose-700">eigenes Format</span>}
                                <Button variant="outline" size="sm" onClick={() => (offen === z.art ? setOffen(null) : oeffne(z))} aria-expanded={offen === z.art} aria-label={`${z.bezeichnung} ändern`}>
                                    {offen === z.art ? 'Schließen' : 'Ändern'}
                                </Button>
                            </div>
                            {z.warnung && <p className="mt-2 text-xs text-amber-800">{z.warnung}</p>}
                            {offen === z.art && (
                                <div className="mt-3 grid gap-3 rounded-lg bg-slate-50 p-3 md:grid-cols-2">
                                    <div>
                                        <Label htmlFor={`nk-praefix-${z.art}`}>Präfix</Label>
                                        <Input id={`nk-praefix-${z.art}`} value={praefix} onChange={e => setPraefix(e.target.value)} maxLength={10} placeholder="RE" />
                                    </div>
                                    <div>
                                        <Label htmlFor={`nk-datum-${z.art}`}>Datum in der Nummer</Label>
                                        <select id={`nk-datum-${z.art}`} className={SELECT} value={datum} onChange={e => setDatum(e.target.value as Datumsteil)}>
                                            {DATUM.map(d => <option key={d.wert} value={d.wert}>{d.text}</option>)}
                                        </select>
                                    </div>
                                    <div>
                                        <Label htmlFor={`nk-rueck-${z.art}`}>Zähler</Label>
                                        <select id={`nk-rueck-${z.art}`} className={SELECT} value={rueck} onChange={e => setRueck(e.target.value as Ruecksetzung)}>
                                            {RUECK.map(d => <option key={d.wert} value={d.wert}>{d.text}</option>)}
                                        </select>
                                    </div>
                                    <div className="grid grid-cols-2 gap-3">
                                        <DecimalInput id={`nk-stellen-${z.art}`} label="Stellen" integer min={3} max={8} value={stellen} onChange={setStellen} />
                                        <DecimalInput id={`nk-start-${z.art}`} label="Startnummer" integer min={1} value={start} onChange={setStart} />
                                    </div>
                                    <p className="text-sm text-slate-700 md:col-span-2" data-testid="nk-vorschau">
                                        So sähe die erste Nummer aus: <span className="font-mono">{beispiel(z, praefix, datum, Number(stellen) || 4, Number(start) || 1)}</span>
                                    </p>
                                    {meldung && <p role="alert" className="rounded-lg bg-rose-50 p-2 text-sm text-rose-800 md:col-span-2">{meldung}</p>}
                                    <div className="flex flex-wrap gap-2 md:col-span-2">
                                        <Button size="sm" onClick={() => void speichere(z)} disabled={speichert}><Save className="mr-2 h-4 w-4" />Speichern</Button>
                                        {z.eigenesFormat && (
                                            <Button variant="outline" size="sm" onClick={() => void zurueck(z)} disabled={speichert}><RotateCcw className="mr-2 h-4 w-4" />Wieder das bisherige Format</Button>
                                        )}
                                    </div>
                                </div>
                            )}
                        </li>
                    ))}
                </ul>
            )}
        </SettingsCard>
    );
}
