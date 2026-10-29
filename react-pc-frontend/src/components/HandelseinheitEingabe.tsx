/**
 * Hilfs-Eingabe für Artikel mit Handelseinheit (z.B. Stangen à 6m):
 * Wechsel zwischen Direkt-Eingabe (in Verrechnungseinheit) und
 * Stangen-Modus (Anzahl × Länge → kg/m).
 *
 * Gibt immer die Menge in der Verrechnungseinheit des Artikels aus.
 */
import { useState } from 'react';
import { DecimalInput } from './ui/decimal-input';
import { InfoButton } from './ui/info-button';

export interface HandelseinheitArtikel {
    einheit: string;
    kgProMeter?: number | null;
    handelseinheitBezeichnung?: string | null;
    handelseinheitMeter?: number | null;
    handelseinheitVarianten?: string | null;
}

interface Props {
    artikel: HandelseinheitArtikel;
    value: string;
    onChange: (val: string) => void;
    label?: string;
    min?: number;
    max?: number;
}

export function hatHandelseinheit(a: HandelseinheitArtikel): boolean {
    return !!(
        a.kgProMeter && a.kgProMeter > 0 &&
        a.handelseinheitBezeichnung &&
        a.handelseinheitMeter && a.handelseinheitMeter > 0
    );
}

/** Parst kommagetrennte Variantenliste; leeres Array = keine Varianten */
function parseVarianten(v?: string | null): number[] {
    if (!v) return [];
    return v.split(',').map(s => parseFloat(s.trim())).filter(n => isFinite(n) && n > 0);
}

export function HandelseinheitEingabe({ artikel, value, onChange, label, min = 0.001, max }: Props) {
    const [stangenModus, setStangenModus] = useState(false);
    const [anzahl, setAnzahl] = useState('1');
    const varianten = parseVarianten(artikel.handelseinheitVarianten);
    const standardLaenge = artikel.handelseinheitMeter ?? 6;
    const [laenge, setLaenge] = useState(String(varianten.length > 0 ? varianten[0] : standardLaenge));

    const hatHE = hatHandelseinheit(artikel);
    const hatVarianten = varianten.length > 0;

    const berechneKg = (anz: string, laengeM: string): number | null => {
        if (!artikel.kgProMeter) return null;
        const a = parseFloat(anz.replace(',', '.'));
        const l = parseFloat(laengeM.replace(',', '.'));
        if (!isFinite(a) || !isFinite(l) || a <= 0 || l <= 0) return null;
        return a * l * artikel.kgProMeter;
    };

    const stangenZuKg = (anz: string, laengeM: string) => {
        const kg = berechneKg(anz, laengeM);
        if (kg != null) {
            onChange(kg.toLocaleString('de-DE', { maximumFractionDigits: 6, useGrouping: false }));
        }
    };

    if (!hatHE || !stangenModus) {
        return (
            <div className="space-y-1">
                <div className="flex items-center gap-1">
                    <label className="block text-xs text-slate-600">{label ?? `Menge (${artikel.einheit})`}</label>
                    {hatHE && (
                        <InfoButton titel="Handelseinheit">
                            Dieser Artikel wird in {artikel.handelseinheitBezeichnung} gehandelt
                            {hatVarianten
                                ? ` (${varianten.map(v => `${v} m`).join(' oder ')}).`
                                : ` (je ${standardLaenge} m).`}
                            {' '}Über „in {artikel.handelseinheitBezeichnung}" können Sie die Anzahl und Länge direkt eingeben –
                            das Gewicht wird dann automatisch berechnet.
                        </InfoButton>
                    )}
                </div>
                <div className="flex gap-2 items-center">
                    <div className="flex-1">
                        <DecimalInput value={value} onChange={onChange} min={min} max={max} />
                    </div>
                    <span className="text-sm text-slate-500 shrink-0">{artikel.einheit}</span>
                    {hatHE && (
                        <button
                            type="button"
                            onClick={() => setStangenModus(true)}
                            className="text-xs text-rose-600 hover:underline shrink-0"
                            title={`Eingabe in ${artikel.handelseinheitBezeichnung} umschalten`}
                        >
                            in {artikel.handelseinheitBezeichnung}
                        </button>
                    )}
                </div>
            </div>
        );
    }

    const bez = artikel.handelseinheitBezeichnung!;
    const kg = berechneKg(anzahl, laenge);

    return (
        <div className="space-y-2">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-1">
                    <label className="text-xs text-slate-600">{label ?? `Menge in ${bez}`}</label>
                    <InfoButton titel="Automatische Umrechnung">
                        Anzahl × Länge × {artikel.kgProMeter} kg/m wird automatisch in kg umgerechnet
                        und in das Mengenfeld eingetragen. Am Lager und in der Bestellung wird immer in kg gebucht.
                    </InfoButton>
                </div>
                <button
                    type="button"
                    onClick={() => setStangenModus(false)}
                    className="text-xs text-slate-400 hover:text-slate-600"
                >
                    direkt in {artikel.einheit}
                </button>
            </div>

            {/* Schnellwahl für Varianten */}
            {hatVarianten && (
                <div className="flex gap-1 flex-wrap">
                    <span className="text-xs text-slate-500 self-center">Länge:</span>
                    {varianten.map(v => (
                        <button
                            key={v}
                            type="button"
                            onClick={() => { const s = String(v); setLaenge(s); stangenZuKg(anzahl, s); }}
                            className={`text-xs px-2 py-0.5 rounded border transition-colors ${
                                parseFloat(laenge.replace(',', '.')) === v
                                    ? 'bg-rose-600 text-white border-rose-600'
                                    : 'bg-white text-slate-600 border-slate-300 hover:border-rose-400'
                            }`}
                        >
                            {v} m
                        </button>
                    ))}
                </div>
            )}

            <div className="grid grid-cols-2 gap-2">
                <div>
                    <label className="block text-xs text-slate-500 mb-0.5">Anzahl {bez}</label>
                    <DecimalInput value={anzahl} onChange={v => { setAnzahl(v); stangenZuKg(v, laenge); }} min={0.001} placeholder="1" />
                </div>
                <div>
                    <label className="block text-xs text-slate-500 mb-0.5">Länge je {bez} (m)</label>
                    <DecimalInput value={laenge} onChange={v => { setLaenge(v); stangenZuKg(anzahl, v); }} min={0.001} placeholder={String(standardLaenge)} />
                </div>
            </div>
            {kg != null && (
                <p className="text-xs text-slate-500 bg-slate-50 rounded px-2 py-1">
                    {anzahl} {bez} × {laenge} m × {artikel.kgProMeter} kg/m{' = '}
                    <span className="font-medium text-slate-800">
                        {kg.toLocaleString('de-DE', { maximumFractionDigits: 3 })} kg
                    </span>
                </p>
            )}
        </div>
    );
}
