import { useState } from 'react';
import { Input } from '../../../components/ui/input';
import { Label } from '../../../components/ui/label';
import { Select } from '../../../components/ui/select-custom';
import type { WartungFeldtyp } from '../types';
import { optionenAusText } from './modulBearbeiten';
import { ZeilenKnoepfe } from './ZeilenKnoepfe';

export interface DatenfeldWerte {
    label: string;
    feldtyp: WartungFeldtyp;
    optionen: string[];
}

const FELDTYPEN = [
    { value: 'TEXT', label: 'Freitext' },
    { value: 'JA_NEIN', label: 'Ja / Nein' },
    { value: 'AUSWAHL', label: 'Auswahl aus Liste' },
    { value: 'JAHR', label: 'Jahreszahl' },
];

interface Props<T extends DatenfeldWerte> {
    /** Eindeutig je Zeile, für die Beschriftung der Felder. */
    idPrefix: string;
    nummer: number;
    /** Wo das Feld steht ( "Datenfeld-Abschnitt 2" ): macht die Beschriftung für Screenreader eindeutig. */
    kontext?: string;
    feld: T;
    onChange: (neu: T) => void;
    onHoch?: () => void;
    onRunter?: () => void;
    onEntfernen: () => void;
}

/** Eine Zeile für ein Datenfeld: Bezeichnung, Art und bei einer Auswahl die Möglichkeiten (eine pro Zeile). */
export function DatenfeldZeile<T extends DatenfeldWerte>({ idPrefix, nummer, kontext, feld, onChange, onHoch, onRunter, onEntfernen }: Props<T>) {
    // Der Text wird hier gehalten: Würde jede Eingabe sofort in eine Liste zerlegt, ginge der Zeilenumbruch am Ende verloren.
    const [optionenText, setOptionenText] = useState(feld.optionen.join('\n'));
    const name = `Datenfeld ${nummer}${kontext ? ` in ${kontext}` : ''}`;

    return (
        <div className="rounded-lg border border-slate-200 bg-white p-3">
            <div className="flex flex-col gap-3 md:flex-row md:items-end">
                <div className="min-w-0 flex-1">
                    <Label htmlFor={`${idPrefix}-label`}>Bezeichnung</Label>
                    <Input id={`${idPrefix}-label`} aria-label={`Bezeichnung von ${name}`} value={feld.label} maxLength={200} placeholder="z. B. Hersteller"
                        onChange={(e) => onChange({ ...feld, label: e.target.value })} />
                </div>
                <div className="md:w-56">
                    <Label htmlFor={`${idPrefix}-art`}>Art</Label>
                    <Select id={`${idPrefix}-art`} aria-label={`Art von ${name}`} value={feld.feldtyp} options={FELDTYPEN}
                        // Die Möglichkeiten bleiben beim Wechsel der Art stehen, damit sie nach "zurück zu Auswahl" nicht verschwunden sind; gespeichert werden sie nur bei einer Auswahl.
                        onChange={(wert) => onChange({ ...feld, feldtyp: wert as WartungFeldtyp })} />
                </div>
                <ZeilenKnoepfe bezeichnung={name} onHoch={onHoch} onRunter={onRunter} onEntfernen={onEntfernen} />
            </div>
            {feld.feldtyp === 'AUSWAHL' && (
                <div className="mt-3">
                    <Label htmlFor={`${idPrefix}-optionen`}>Möglichkeiten (eine pro Zeile)</Label>
                    <textarea
                        id={`${idPrefix}-optionen`}
                        aria-label={`Möglichkeiten von ${name}`}
                        className="min-h-[80px] w-full rounded-lg border border-slate-200 p-3 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                        value={optionenText}
                        placeholder={'gut\nmittel\nschlecht'}
                        onChange={(e) => { setOptionenText(e.target.value); onChange({ ...feld, optionen: optionenAusText(e.target.value) }); }}
                    />
                </div>
            )}
        </div>
    );
}
