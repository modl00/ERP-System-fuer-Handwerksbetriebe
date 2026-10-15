import { Ruler } from 'lucide-react';

import { DecimalInput } from '../../components/ui/decimal-input';
import type { ArbeitsgangFormelFeld } from '../../types';

interface Props {
    /** Bezeichnung des Materials, nur für die Beschriftung der Felder */
    material: string;
    felder: ArbeitsgangFormelFeld[];
    /** Eingegebene Maße als Text, Schlüssel = Feldname in Kleinbuchstaben */
    werte: Record<string, string>;
    onChange: (feldname: string, text: string) => void;
}

/** Die Maße eines Zuschnitts (#228): Länge, Breite, Dicke usw. eintragen; Gewicht, Fläche und Preis je Stück rechnet das Programm. */
export function MassEingabe({ material, felder, werte, onChange }: Props) {
    return (
        <div className="space-y-1.5 min-w-[13rem]" data-testid="mass-eingabe">
            <div className="flex items-center gap-1 text-xs font-medium text-slate-600"><Ruler className="w-3 h-3 text-rose-600" aria-hidden="true" /> Maße je Stück</div>
            {felder.map(f => (
                <div key={f.name} className="flex items-center gap-1.5">
                    <DecimalInput aria-label={`${f.bezeichnung} ${material}`} value={werte[f.name.toLowerCase()] ?? ''} min={-1000000000} max={1000000000}
                        placeholder={f.standard == null ? '' : String(f.standard).replace('.', ',')} onChange={wert => onChange(f.name.toLowerCase(), wert)} />
                    <span className="text-xs text-slate-500">{f.bezeichnung}{f.einheit ? ` (${f.einheit})` : ''}</span>
                </div>
            ))}
        </div>
    );
}
