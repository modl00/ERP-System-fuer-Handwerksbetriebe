import { useEffect, useState } from 'react';
import { Calculator, ChevronRight, Factory, Hammer, Layers, Loader2, Package } from 'lucide-react';
import { cn } from '../../lib/utils';
import { formatCurrency } from '../artikel/formatCurrency';
import type { Kalkulation, KalkulationPosition } from '../../features/leistungen/kalkulationApi';

interface Props {
    leistungId: number;
    /** Menge der Position im Angebot: Rüstzeit, Mindestpreis und einmalige Kosten verteilen sich darauf. */
    menge: number;
    /** Preis je Einheit, wie er im Angebot steht. */
    preis: number;
    gesperrt: boolean;
    /** Schreibt den kalkulierten Preis in die Position. */
    onPreisUebernehmen: (preis: number) => void;
}

const ICON = { ARTIKEL: Package, ARBEIT: Hammer, BAUGRUPPE: Layers, FREMDLEISTUNG: Factory } as const;

const mengeText = (z: KalkulationPosition) => `${z.menge.toLocaleString('de-DE', { maximumFractionDigits: 3 })} ${z.einheit}`;

/**
 * Die Kalkulation hinter einer Angebotsposition, <b>nur intern</b> (steht nicht im Kundendokument): die Zeilen der Leistung mit Material, Arbeit, Maschine
 * und Fremdleistung, gerechnet für die Menge dieser Position, dazu der kalkulierte Preis im Vergleich mit dem Preis im Angebot. Der Preis wird nie von selbst
 * geändert; „Kalkulierten Preis übernehmen“ schreibt ihn nur auf Klick in die Position.
 */
export function ServiceKalkulation({ leistungId, menge, preis, gesperrt, onPreisUebernehmen }: Props) {
    const [offen, setOffen] = useState(false);
    const [daten, setDaten] = useState<Kalkulation | null>(null);
    const [fehler, setFehler] = useState('');
    const [laedt, setLaedt] = useState(false);
    const gueltig = menge > 0;

    useEffect(() => {
        if (!offen || !gueltig) return;
        let abgebrochen = false;
        const timer = setTimeout(() => {
            setLaedt(true);
            fetch(`/api/leistungen/${leistungId}/kalkulation/angebot?menge=${encodeURIComponent(String(menge))}`)
                .then(async res => {
                    if (!res.ok) {
                        let text = 'Die Kalkulation konnte nicht geladen werden.';
                        try { const b = await res.json() as { message?: string }; if (b?.message) text = b.message; } catch { /* kein JSON */ }
                        throw new Error(text);
                    }
                    return res.json() as Promise<Kalkulation>;
                })
                .then(k => { if (!abgebrochen) { setDaten(k); setFehler(''); } })
                .catch(e => { if (!abgebrochen) setFehler(e instanceof Error ? e.message : 'Die Kalkulation konnte nicht geladen werden.'); })
                .finally(() => { if (!abgebrochen) setLaedt(false); });
        }, 300);
        return () => { abgebrochen = true; clearTimeout(timer); };
    }, [offen, gueltig, leistungId, menge]);

    const berechnet = daten?.preisBerechnet;
    const abweichung = berechnet != null ? Math.round((preis - berechnet) * 100) / 100 : null;
    const hatZeilen = (daten?.positionen.length ?? 0) > 0;

    return (
        <div className="mt-2 pt-2 border-t border-slate-100" data-testid="service-kalkulation">
            <button type="button" aria-expanded={offen} onClick={e => { e.stopPropagation(); setOffen(o => !o); }}
                className="flex items-center gap-1.5 text-[11px] font-medium text-slate-500 hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 rounded">
                <ChevronRight className={cn('w-3 h-3 transition-transform', offen && 'rotate-90')} aria-hidden="true" />
                <Calculator className="w-3 h-3" aria-hidden="true" /> Kalkulation ansehen <span className="font-normal text-slate-400">(nur intern)</span>
            </button>
            {offen && (
                <div className="mt-2 rounded-lg border border-slate-200 bg-white p-3 text-xs" onClick={e => e.stopPropagation()}>
                    {!gueltig && <p className="text-slate-500">Bitte zuerst eine Menge eintragen.</p>}
                    {gueltig && laedt && !daten && <p className="flex items-center gap-1.5 text-slate-500"><Loader2 className="w-3 h-3 animate-spin motion-reduce:animate-none" aria-hidden="true" /> Kalkulation wird berechnet …</p>}
                    {fehler && <p role="alert" className="text-rose-700">{fehler}</p>}
                    {gueltig && daten && !hatZeilen && <p className="text-slate-500">Diese Leistung hat noch keine Kalkulation. Sie lässt sich im Leistungskatalog anlegen.</p>}
                    {gueltig && daten && hatZeilen && (
                        <>
                            <p className="mb-2 text-slate-500">Für <strong>{menge.toLocaleString('de-DE', { maximumFractionDigits: 3 })}</strong> Einheiten gerechnet, je Einheit. Steht nicht im Dokument für den Kunden.</p>
                            <ul className="divide-y divide-slate-100" aria-label="Zeilen der Kalkulation">
                                {daten.positionen.map((z, i) => {
                                    const Symbol = ICON[z.typ];
                                    return (
                                        <li key={z.id ?? i} className="flex items-start gap-2 py-1.5">
                                            <Symbol className="w-3.5 h-3.5 mt-0.5 text-slate-400 shrink-0" aria-hidden="true" />
                                            <div className="min-w-0 flex-1">
                                                <p className="text-slate-800">{z.bezeichnung}{z.veredler ? <span className="text-slate-500"> bei {z.veredler}</span> : null}</p>
                                                <p className="text-slate-500">{mengeText(z)}{z.hinweis !== 'OK' ? ' · Preis unvollständig' : ''}</p>
                                            </div>
                                            <span className="tabular-nums font-medium text-slate-800">{formatCurrency(z.summe)}</span>
                                        </li>
                                    );
                                })}
                            </ul>
                            <dl className="mt-2 grid grid-cols-[1fr_auto] gap-x-6 gap-y-0.5 border-t border-slate-200 pt-2">
                                <dt className="text-slate-600">Material</dt><dd className="text-right tabular-nums">{formatCurrency(daten.materialSumme)}</dd>
                                <dt className="text-slate-600">Arbeit (Lohn)</dt><dd className="text-right tabular-nums">{formatCurrency(daten.arbeitSumme)}</dd>
                                {(daten.maschineSumme ?? 0) > 0 && (<><dt className="text-slate-600">Maschine</dt><dd className="text-right tabular-nums">{formatCurrency(daten.maschineSumme ?? 0)}</dd></>)}
                                {(daten.fremdleistungSumme ?? 0) > 0 && (<><dt className="text-slate-600">Fremdleistung</dt><dd className="text-right tabular-nums">{formatCurrency(daten.fremdleistungSumme ?? 0)}</dd></>)}
                                <dt className="font-medium text-slate-700">Selbstkosten je Einheit</dt><dd className="text-right font-medium tabular-nums" data-testid="selbstkosten">{formatCurrency(daten.zwischensumme)}</dd>
                                {daten.zuschlag !== 0 && (<><dt className="text-slate-600">Zuschlag{daten.zuschlagProzent ? ` (${daten.zuschlagProzent.toLocaleString('de-DE')} %)` : ''}</dt><dd className="text-right tabular-nums">{formatCurrency(daten.zuschlag)}</dd></>)}
                                <dt className="font-semibold text-slate-900">Kalkulierter Preis je Einheit</dt><dd className="text-right font-semibold tabular-nums" data-testid="kalkuliert">{formatCurrency(daten.preisBerechnet)}</dd>
                            </dl>
                            {!daten.vollstaendig && <p role="alert" className="mt-2 text-amber-800">Für mindestens eine Zeile fehlt ein Preis. Der kalkulierte Preis wäre zu niedrig.</p>}
                            {abweichung != null && abweichung !== 0 && (
                                <div className="mt-2 flex flex-wrap items-center justify-between gap-2 rounded-md bg-amber-50 p-2 text-amber-900" data-testid="abweichung">
                                    <span>Im Angebot steht {formatCurrency(preis)}, kalkuliert sind {formatCurrency(daten.preisBerechnet)} ({abweichung > 0 ? '+' : ''}{formatCurrency(abweichung)} je Einheit).</span>
                                    {!gesperrt && daten.vollstaendig && (
                                        <button type="button" onClick={() => onPreisUebernehmen(daten.preisBerechnet)} className="rounded-md border border-amber-400 bg-white px-2 py-1 font-medium hover:bg-amber-100">Kalkulierten Preis übernehmen</button>
                                    )}
                                </div>
                            )}
                            {abweichung === 0 && <p className="mt-2 text-emerald-700">Der Preis im Angebot entspricht der Kalkulation.</p>}
                        </>
                    )}
                </div>
            )}
        </div>
    );
}
