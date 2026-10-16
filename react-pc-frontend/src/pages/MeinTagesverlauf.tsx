import { useCallback, useEffect, useState } from 'react';
import { BookCheck, CheckCircle2, ChevronLeft, ChevronRight, Info, Link2, Loader2, Pencil, Plus, Send, Trash2 } from 'lucide-react';

import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { cn } from '../lib/utils';
import { aufbewahrungText, ereignisText, heute, ladeMeinenTag, tagVerschieben, uhrzeit, type Art, type MeinTag } from '../features/aktivitaet/aktivitaetApi';

const SYMBOL: Record<Art, typeof Plus> = { ANGELEGT: Plus, GEAENDERT: Pencil, GELOESCHT: Trash2, VERSENDET: Send, GEBUCHT: BookCheck, ZUGEORDNET: Link2, ABGESCHLOSSEN: CheckCircle2 };

/** "Mein Tagesverlauf": die eigenen Ereignisse eines Tages, nur Art, Objekt und Uhrzeit. Jeder sieht nur seinen eigenen Verlauf. */
const MeinTagesverlauf = () => {
    const [datum, setDatum] = useState(heute());
    const [tag, setTag] = useState<{ datum: string; daten: MeinTag } | null>(null);
    const [fehler, setFehler] = useState('');

    const laden = useCallback((d: string) => {
        ladeMeinenTag(d)
            .then((daten) => { setTag({ datum: d, daten }); setFehler(''); })
            .catch((e: unknown) => setFehler(e instanceof Error ? e.message : 'Der Tagesverlauf konnte nicht geladen werden.'));
    }, []);
    useEffect(() => { laden(datum); }, [datum, laden]);

    const aktuell = tag && tag.datum === datum ? tag.daten : null;
    const istHeute = datum === heute();

    return (
        <PageLayout ribbonCategory="Mein Bereich" title="MEIN TAGESVERLAUF" subtitle="Was Sie an einem Tag im Programm gemacht haben. Das sehen nur Sie.">
            <div className="space-y-4">
                <div className="flex flex-wrap items-center gap-2">
                    <Button variant="outline" size="sm" onClick={() => setDatum(tagVerschieben(datum, -1))} aria-label="Vorheriger Tag"><ChevronLeft className="h-4 w-4" /></Button>
                    <input type="date" value={datum} max={heute()} onChange={(e) => { if (e.target.value) setDatum(e.target.value); }} aria-label="Tag wählen"
                        className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                    <Button variant="outline" size="sm" onClick={() => setDatum(tagVerschieben(datum, 1))} disabled={istHeute} aria-label="Nächster Tag"><ChevronRight className="h-4 w-4" /></Button>
                    {!istHeute && <Button variant="ghost" size="sm" onClick={() => setDatum(heute())}>Heute</Button>}
                </div>

                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                {!aktuell && !fehler && <p className="flex items-center gap-2 text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Der Tagesverlauf wird geladen …</p>}

                {aktuell && !aktuell.aktiv && (
                    <p className="flex items-start gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-900"><Info className="mt-0.5 h-4 w-4 shrink-0" />
                        Für Sie wird im Moment nichts aufgezeichnet: Der Tagesverlauf ist für Mitarbeiter nicht eingeschaltet. Das entscheidet der Administrator.</p>
                )}

                {aktuell && (
                    aktuell.ereignisse.length === 0 ? (
                        <Card className="p-6 text-center text-slate-600">{aktuell.aktiv ? 'Für diesen Tag gibt es keine Einträge.' : 'Es gibt keine Einträge.'}</Card>
                    ) : (
                        <Card className="p-0">
                            <ol className="divide-y divide-slate-100">
                                {aktuell.ereignisse.map((e) => {
                                    const Symbol = SYMBOL[e.art] ?? Pencil;
                                    return (
                                        <li key={e.id} className="flex items-center gap-3 px-4 py-3">
                                            <span className="w-14 shrink-0 text-sm font-medium tabular-nums text-slate-500">{uhrzeit(e.zeitpunkt)}</span>
                                            <span className={cn('flex h-8 w-8 shrink-0 items-center justify-center rounded-full', e.art === 'GELOESCHT' ? 'bg-rose-50 text-rose-600' : 'bg-slate-100 text-slate-600')}><Symbol className="h-4 w-4" /></span>
                                            <span className="min-w-0 flex-1 text-sm text-slate-900">{ereignisText(e)}</span>
                                        </li>
                                    );
                                })}
                            </ol>
                        </Card>
                    )
                )}

                {aktuell && (
                    <p className="text-xs text-slate-500">
                        Gespeichert werden nur Art, Objekt und Uhrzeit, keine Inhalte wie E-Mail-Texte oder Beträge. Aufbewahrung: {aufbewahrungText(aktuell.aufbewahrungTage)}.
                    </p>
                )}
            </div>
        </PageLayout>
    );
};

export default MeinTagesverlauf;
