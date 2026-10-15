import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, ArrowRightLeft, BellRing, ClipboardList, History, Loader2, PackageMinus, PackagePlus, Warehouse } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { useLagerAktiv } from '../modulschalter/useFeatures';
import { BuchungsDialog, MindestbestandDialog, UmbuchungsDialog, VerlaufDialog } from './LagerDialoge';
import {
    ladeBestandVon, ladeOrte, ladeVerlauf, mengeText,
    type Buchungsart, type LagerBestand, type LagerBuchung, type Lagerort,
} from './lagerApi';

const ART_TEXT: Record<Buchungsart, string> = { ZUGANG: 'Zugang', ABGANG: 'Entnahme', KORREKTUR: 'Inventur' };
const datum = (s: string) => new Date(s).toLocaleDateString('de-DE');

/**
 * Das Lager eines Artikels auf der Artikelseite: Bestand, Lagerorte, Mindestbestand und letzte Buchungen, dazu Zugang, Entnahme, Inventur,
 * Umbuchen und Mindestbestand mit denselben Dialogen wie auf der Lager-Seite. Der Bestand ändert sich nur über Buchungen.
 * Ist das Lagermodul ausgeschaltet, erscheint der Bereich nicht.
 */
export function ArtikelLagerBereich({ artikelId, einheitGeaendertStand }: { artikelId: number; einheitGeaendertStand?: unknown }) {
    const aktiv = useLagerAktiv();
    const toast = useToast();
    const [bestand, setBestand] = useState<LagerBestand | null>(null);
    const [buchungen, setBuchungen] = useState<LagerBuchung[]>([]);
    const [orte, setOrte] = useState<Lagerort[]>([]);
    const [fehler, setFehler] = useState('');
    const [stand, setStand] = useState(0);
    const [buchung, setBuchung] = useState<Buchungsart | null>(null);
    const [umbuchung, setUmbuchung] = useState(false);
    const [mindest, setMindest] = useState(false);
    const [verlauf, setVerlauf] = useState(false);
    const neuLaden = useCallback(() => setStand(n => n + 1), []);

    useEffect(() => {
        if (aktiv !== true) return;
        let abgebrochen = false;
        Promise.all([ladeBestandVon(artikelId), ladeVerlauf(artikelId), ladeOrte()])
            .then(([b, v, o]) => {
                if (abgebrochen) return;
                setBestand(b); setBuchungen(v.slice(0, 5)); setOrte(o); setFehler('');
            })
            .catch(e => {
                if (abgebrochen) return;
                const text = e instanceof Error ? e.message : 'Der Lagerbestand konnte nicht geladen werden.';
                setFehler(text);
                toast.error(text);
            });
        return () => { abgebrochen = true; };
    }, [aktiv, artikelId, stand, einheitGeaendertStand, toast]);

    if (aktiv !== true) return null;

    const leer = bestand != null && bestand.orte.length === 0 && buchungen.length === 0 && bestand.mindestbestand == null;

    return (
        <section className="bg-white border border-slate-200 rounded-lg p-5" aria-label="Lager" data-testid="artikel-lager">
            <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
                <div>
                    <h2 className="text-lg font-semibold text-slate-900">Lager</h2>
                    <p className="text-sm text-slate-500">Bestand ändert sich nur über Buchungen, so bleibt nachvollziehbar, woher er kommt.</p>
                </div>
                <Link to="/lager" className="text-sm text-rose-700 hover:underline inline-flex items-center gap-1.5">
                    <Warehouse className="w-4 h-4" aria-hidden="true" /> Im Lager öffnen
                </Link>
            </div>

            {fehler ? (
                <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>
            ) : bestand == null ? (
                <div className="flex items-center gap-2 text-slate-500 text-sm py-4"><Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> Lagerbestand wird geladen …</div>
            ) : (
                <div className="space-y-4">
                    {leer ? (
                        <div className="rounded-lg border border-dashed border-slate-200 p-5 text-center">
                            <p className="text-slate-700 font-medium">Dieser Artikel ist noch nicht im Lager.</p>
                            <p className="text-sm text-slate-500 mt-1">Buchen Sie den ersten Zugang, zum Beispiel die Ware vom letzten Lieferschein.</p>
                            <Button size="sm" className="mt-3 bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => setBuchung('ZUGANG')}>
                                <PackagePlus className="w-4 h-4 mr-2" aria-hidden="true" /> Ersten Zugang buchen
                            </Button>
                        </div>
                    ) : (
                        <>
                            <div className="grid grid-cols-2 md:grid-cols-3 gap-x-6 gap-y-4 text-sm">
                                <div>
                                    <div className="text-xs uppercase tracking-wide text-slate-500">Bestand</div>
                                    <div className={cn('text-xl font-semibold tabular-nums', bestand.negativ ? 'text-rose-700' : 'text-slate-900')} data-testid="lager-gesamt">{mengeText(bestand.bestand, bestand.einheit)}</div>
                                </div>
                                <div>
                                    <div className="text-xs uppercase tracking-wide text-slate-500">Mindestbestand</div>
                                    <div className="tabular-nums text-slate-800">{bestand.mindestbestand != null ? mengeText(bestand.mindestbestand, bestand.einheit) : '–'}</div>
                                </div>
                                <div className="col-span-2 md:col-span-1">
                                    <div className="text-xs uppercase tracking-wide text-slate-500 mb-1">Lagerorte</div>
                                    <div className="flex flex-wrap gap-1">
                                        {bestand.orte.length === 0 ? <span className="text-slate-400">–</span> : bestand.orte.map(o => (
                                            <span key={o.lagerortId} className="inline-flex rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700">{o.lagerortName}: {mengeText(o.bestand, bestand.einheit)}</span>
                                        ))}
                                    </div>
                                </div>
                            </div>

                            {bestand.negativ && (
                                <div role="status" className="flex items-start gap-2 text-sm text-rose-800 bg-rose-50 border border-rose-200 rounded-lg p-3">
                                    <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />
                                    Es wurde mehr entnommen als eingebucht. Bitte Zugang oder Inventur buchen.
                                </div>
                            )}
                            {!bestand.negativ && bestand.unterMindestbestand && bestand.mindestbestand != null && (
                                <div role="status" className="flex items-start gap-2 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg p-3">
                                    <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />
                                    Unter dem Mindestbestand: es fehlen {mengeText(bestand.mindestbestand - bestand.bestand, bestand.einheit)}.
                                </div>
                            )}

                            <div className="flex flex-wrap gap-2">
                                <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => setBuchung('ZUGANG')}><PackagePlus className="w-4 h-4 mr-2" aria-hidden="true" /> Zugang</Button>
                                <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => setBuchung('ABGANG')}><PackageMinus className="w-4 h-4 mr-2" aria-hidden="true" /> Entnahme</Button>
                                <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => setBuchung('KORREKTUR')} title="Gezählten Bestand eintragen"><ClipboardList className="w-4 h-4 mr-2" aria-hidden="true" /> Inventur</Button>
                                <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => setUmbuchung(true)} disabled={bestand.orte.length === 0} title={bestand.orte.length === 0 ? 'Erst Ware einbuchen' : 'An einen anderen Lagerort legen'}><ArrowRightLeft className="w-4 h-4 mr-2" aria-hidden="true" /> Umbuchen</Button>
                                <Button variant="outline" size="sm" onClick={() => setMindest(true)}><BellRing className="w-4 h-4 mr-2" aria-hidden="true" /> Mindestbestand</Button>
                            </div>

                            {buchungen.length > 0 && (
                                <div>
                                    <div className="flex items-center justify-between mb-1">
                                        <h3 className="text-sm font-semibold text-slate-800">Letzte Buchungen</h3>
                                        <button type="button" className="text-sm text-rose-700 hover:underline inline-flex items-center gap-1" onClick={() => setVerlauf(true)}>
                                            <History className="w-4 h-4" aria-hidden="true" /> Ganzer Verlauf
                                        </button>
                                    </div>
                                    <ul className="divide-y divide-slate-100 text-sm" data-testid="lager-buchungen">
                                        {buchungen.map(b => (
                                            <li key={b.id} className="py-1.5 flex flex-wrap items-center gap-x-3 gap-y-0.5">
                                                <span className="text-slate-500 w-24">{datum(b.gebuchtAm)}</span>
                                                <span className="font-medium text-slate-800 w-20">{ART_TEXT[b.art]}</span>
                                                <span className={cn('tabular-nums w-28', b.menge < 0 ? 'text-rose-700' : 'text-emerald-700')}>{b.menge > 0 ? '+' : ''}{mengeText(b.menge, bestand.einheit)}</span>
                                                <span className="text-slate-600">{b.lagerortName}{b.projekt ? ` · ${b.projekt}` : ''}{b.notiz ? ` · ${b.notiz}` : ''}</span>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            )}
                        </>
                    )}
                </div>
            )}

            <BuchungsDialog offen={buchung != null} art={buchung ?? 'ZUGANG'} artikel={bestand} orte={orte}
                onSchliessen={() => setBuchung(null)}
                onGebucht={(zeile, art) => {
                    toast.success(`${ART_TEXT[art]} gebucht. Bestand: ${mengeText(zeile.bestand, zeile.einheit)}`);
                    neuLaden();
                }} />
            <MindestbestandDialog artikel={mindest ? bestand : null} onSchliessen={() => setMindest(false)} onGespeichert={neuLaden} />
            <VerlaufDialog artikel={verlauf ? bestand : null} onSchliessen={() => setVerlauf(false)} />
            <UmbuchungsDialog artikel={umbuchung ? bestand : null} orte={orte} onSchliessen={() => setUmbuchung(false)} onGebucht={neuLaden} />
        </section>
    );
}
