import { Calculator } from 'lucide-react';

import { DecimalInput } from '../../components/ui/decimal-input';
import type { ArbeitsgangFormelFeld } from '../../types';

interface Props {
    /** Bezeichnung des Arbeitsgangs, nur für die Beschriftung der Felder */
    arbeitsgang: string;
    felder: ArbeitsgangFormelFeld[];
    /** Eingegebene Werte als Text, Schlüssel = Feldname in Kleinbuchstaben */
    werte: Record<string, string>;
    onChange: (feldname: string, text: string) => void;
    /** Das Ergebnis der Formel in Minuten (aus der Vorschau der Kalkulation), solange noch keines da ist leer */
    bearbeitungMinuten: number | null | undefined;
    ruestMinuten: number | null | undefined;
}

const minuten = (m: number | null | undefined) => (m == null ? '…' : m.toLocaleString('de-DE', { maximumFractionDigits: 2 }));

/** Die Eingabemaske eines Arbeitsgangs mit Formel (#216): Man trägt Durchmesser, Länge usw. ein, die Zeit rechnet das Programm. */
export function FormelEingabe({ arbeitsgang, felder, werte, onChange, bearbeitungMinuten, ruestMinuten }: Props) {
    return (
        <div className="space-y-1.5 min-w-[13rem]" data-testid="formel-eingabe">
            <div className="flex items-center gap-1 text-xs font-medium text-slate-600"><Calculator className="w-3 h-3 text-rose-600" aria-hidden="true" /> Zeit aus Formel</div>
            {felder.map(f => (
                <div key={f.name} className="flex items-center gap-1.5">
                    <DecimalInput aria-label={`${f.bezeichnung} ${arbeitsgang}`} value={werte[f.name.toLowerCase()] ?? ''} min={-1000000000} max={1000000000}
                        placeholder={f.standard == null ? '' : String(f.standard).replace('.', ',')} onChange={wert => onChange(f.name.toLowerCase(), wert)} />
                    <span className="text-xs text-slate-500">{f.bezeichnung}{f.einheit ? ` (${f.einheit})` : ''}</span>
                </div>
            ))}
            <div className="text-xs text-slate-700" data-testid="formel-ergebnis">
                = {minuten(bearbeitungMinuten)} min je Einheit{ruestMinuten != null ? `, ${minuten(ruestMinuten)} min rüsten` : ''}
            </div>
        </div>
    );
}
