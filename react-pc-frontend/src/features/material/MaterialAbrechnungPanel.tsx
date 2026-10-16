import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle2, Hourglass, Link2 } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { useToast } from '../../components/ui/toast';
import { useAuth } from '../../auth/AuthContext';
import { cn } from '../../lib/utils';
import { MaterialZuordnenDialog } from './MaterialZuordnenDialog';
import { euro, ladeMaterialAbrechnung, mengeText, setzeSchwelle, speichereZuordnung, type MaterialAbrechnung, type ZeileEingabe } from './materialAbrechnungApi';

interface Props {
    projektId: number;
    /** Wird nach dem Speichern aufgerufen, damit die Seite ihre Rechnungsliste neu lädt. */
    onGeaendert?: () => void;
}

/**
 * Plan und Rechnung des Projektmaterials: welches geplante Material ist durch welche Eingangsrechnung bezahlt (mit tatsächlichem Preis und Markierung bei
 * Abweichung), was ist noch offen ("Material erwartet, Rechnung fehlt noch") und wie viel von jeder Rechnung ist schon zugeordnet. Zeigt nichts, wenn es weder
 * Material noch Rechnungen gibt.
 */
export function MaterialAbrechnungPanel({ projektId, onGeaendert }: Props) {
    const toast = useToast();
    const { isAdmin } = useAuth();
    const [daten, setDaten] = useState<MaterialAbrechnung | null>(null);
    const [fehler, setFehler] = useState('');
    const [dokumentId, setDokumentId] = useState<number | null>(null);
    const [schwelle, setSchwelle] = useState('');

    const laden = useCallback(() => {
        ladeMaterialAbrechnung(projektId)
            .then((d) => { setDaten(d); setSchwelle(String(d.schwelleProzent)); setFehler(''); })
            .catch((e: unknown) => setFehler(e instanceof Error ? e.message : 'Plan und Rechnung konnten nicht geladen werden.'));
    }, [projektId]);
    useEffect(() => { laden(); }, [laden]);

    const speichern = async (zeilen: ZeileEingabe[]) => {
        if (dokumentId === null) return;
        const neu = await speichereZuordnung(projektId, dokumentId, zeilen);
        setDaten(neu);
        setDokumentId(null);
        toast.success(zeilen.length === 0 ? 'Die Zuordnung ist aufgehoben.' : 'Das Material ist der Rechnung zugeordnet.');
        onGeaendert?.();
    };

    const schwelleSpeichern = async () => {
        const p = Number(schwelle.replace(',', '.'));
        if (!Number.isInteger(p) || p < 0 || p > 100) { toast.error('Bitte eine ganze Zahl zwischen 0 und 100 eintragen.'); return; }
        try {
            await setzeSchwelle(p);
            toast.success('Die Schwelle ist gespeichert.');
            laden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Schwelle konnte nicht gespeichert werden.');
        }
    };

    if (fehler) return <p role="alert" className="mt-6 rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>;
    if (!daten || (daten.positionen.length === 0 && daten.rechnungen.length === 0)) return null;
    const rechnung = daten.rechnungen.find((r) => r.dokumentId === dokumentId) ?? null;

    return (
        <div className="mt-8" data-testid="material-abrechnung">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
                <h3 className="text-lg font-medium text-slate-900">Material: Plan und Rechnung</h3>
                {daten.offenAnzahl > 0 && (
                    <span className="flex items-center gap-1 text-sm text-amber-800"><Hourglass className="h-4 w-4" /> Material erwartet, Rechnung fehlt noch: {daten.offenAnzahl} {daten.offenAnzahl === 1 ? 'Position' : 'Positionen'} (Plan {euro(daten.offenGeplant)})</span>
                )}
            </div>

            {daten.rechnungen.length > 0 && (
                <div className="mb-4 space-y-2">
                    {daten.rechnungen.map((r) => (
                        <Card key={r.dokumentId} className="flex flex-wrap items-center gap-3 p-3">
                            <div className="min-w-0 flex-1">
                                <p className="text-sm font-medium text-slate-900">Rechnung {r.dokumentNummer ?? 'ohne Nummer'}{r.lieferantName ? ` · ${r.lieferantName}` : ''}</p>
                                <p className="text-xs text-slate-500">Das Projekt trägt {euro(r.anteil)} netto, zugeordnet {euro(r.zugeordnet)}</p>
                            </div>
                            <span className={cn('rounded-lg px-2 py-1 text-xs font-medium', r.rest > 0.01 ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800')}>
                                {r.rest > 0.01 ? `noch offen ${euro(r.rest)}` : 'alles zugeordnet'}
                            </span>
                            <Button variant="outline" size="sm" onClick={() => setDokumentId(r.dokumentId)} aria-label={`Material zur Rechnung ${r.dokumentNummer ?? ''} zuordnen`}><Link2 className="h-4 w-4" /> Material zuordnen</Button>
                        </Card>
                    ))}
                </div>
            )}

            {daten.positionen.length > 0 && (
                <Card className="overflow-x-auto p-0">
                    <table className="w-full text-left text-sm">
                        <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                            <tr><th className="px-4 py-3">Material</th><th className="px-4 py-3 text-right">Plan</th><th className="px-4 py-3 text-right">Rechnung</th><th className="px-4 py-3">Stand</th></tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {daten.positionen.map((p) => (
                                <tr key={p.artikelInProjektId}>
                                    <td className="px-4 py-3">
                                        <p className="font-medium text-slate-900">{p.bezeichnung}</p>
                                        <p className="text-xs text-slate-500">{mengeText(p.menge)} {p.einheit}{p.lieferantName ? ` · ${p.lieferantName}` : ''}</p>
                                    </td>
                                    <td className="px-4 py-3 text-right text-slate-700">{euro(p.geplant)}</td>
                                    <td className="px-4 py-3 text-right text-slate-900">{euro(p.ist)}</td>
                                    <td className="px-4 py-3">
                                        {p.ist === null ? (
                                            <span className="text-slate-500">{p.bestellt ? 'bestellt, Rechnung fehlt' : 'noch nicht bestellt'}</span>
                                        ) : (
                                            <span className="inline-flex flex-wrap items-center gap-2">
                                                <span className="inline-flex items-center gap-1 text-emerald-700"><CheckCircle2 className="h-4 w-4" /> per Rechnung {p.rechnungen.join(', ')}</span>
                                                {p.abweichung && p.abweichungProzent !== null && (
                                                    <span className={cn('inline-flex items-center gap-1 rounded-lg px-2 py-0.5 text-xs font-medium', p.abweichung === 'TEURER' ? 'bg-rose-100 text-rose-800' : 'bg-emerald-100 text-emerald-800')}
                                                        title={`Plan ${euro(p.geplant)}, Rechnung ${euro(p.ist)}`}>
                                                        <AlertTriangle className="h-3.5 w-3.5" /> {p.abweichung === 'TEURER' ? 'teurer' : 'günstiger'}: {Math.abs(p.abweichungProzent).toLocaleString('de-DE')} %
                                                    </span>
                                                )}
                                            </span>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </Card>
            )}

            {isAdmin && (
                <div className="mt-3 flex flex-wrap items-center gap-2 text-sm text-slate-600">
                    <label htmlFor="material-schwelle">Abweichung markieren ab</label>
                    <input id="material-schwelle" inputMode="numeric" value={schwelle} onChange={(e) => setSchwelle(e.target.value)} className="w-16 rounded-lg border border-slate-300 px-2 py-1 text-right focus:outline-none focus:ring-2 focus:ring-rose-500" />
                    <span>Prozent</span>
                    <Button variant="outline" size="sm" onClick={() => void schwelleSpeichern()} disabled={schwelle === String(daten.schwelleProzent)}>Speichern</Button>
                </div>
            )}

            {rechnung && (
                <MaterialZuordnenDialog rechnung={rechnung} positionen={daten.positionen} schwelleProzent={daten.schwelleProzent} onSpeichern={speichern} onSchliessen={() => setDokumentId(null)} />
            )}
        </div>
    );
}
