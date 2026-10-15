import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { ladeDauerVorschlag, uebernehmeDauerVorschlag, type Auftrag, type DauerVorschlagAntwort } from './plantafelApi';

interface Props { offen: boolean; auftrag: Auftrag; onSchliessen: () => void; onAuftrag: (a: Auftrag) => void }

const zahl = (n: number | null) => (n == null ? '–' : n.toLocaleString('de-DE', { maximumFractionDigits: 2 }));

/**
 * Schlägt die Dauern der Vorgänge aus der Arbeitszeit des Angebots (oder der Auftragsbestätigung) vor: Stunden der Abteilung ÷ Stunden pro Arbeitstag, aufgerundet.
 * Das Programm ändert nichts von selbst: Der Anwender wählt aus, was übernommen wird. Von Hand gesetzte Dauern sind nicht vorgewählt.
 */
export function DauerVorschlagDialog(props: Props) {
    return (
        <Dialog open={props.offen} onOpenChange={o => { if (!o) props.onSchliessen(); }} className="w-full max-w-3xl" aria-labelledby="dauer-titel">
            <DialogContent>{props.offen && <Inhalt {...props} />}</DialogContent>
        </Dialog>
    );
}

type Zustand = { art: 'laedt' } | { art: 'fehler'; meldung: string } | { art: 'da'; daten: DauerVorschlagAntwort };

function Inhalt({ auftrag, onSchliessen, onAuftrag }: Props) {
    const toast = useToast();
    const [zustand, setZustand] = useState<Zustand>({ art: 'laedt' });
    const [gewaehlt, setGewaehlt] = useState<Set<number>>(new Set());
    const [speichert, setSpeichert] = useState(false);
    const projektId = auftrag.projektId;

    useEffect(() => {
        let aktuell = true;
        ladeDauerVorschlag(projektId).then(
            daten => {
                if (!aktuell) return;
                setZustand({ art: 'da', daten });
                // Vorgewählt: was sich ändert und nicht von Hand gesetzt wurde
                setGewaehlt(new Set(daten.zeilen.filter(z => z.vorgeschlageneDauer != null && z.vorgeschlageneDauer !== z.aktuelleDauer && z.aktuelleHerkunft !== 'VON_HAND').map(z => z.vorgangId)));
            },
            e => { if (aktuell) setZustand({ art: 'fehler', meldung: e instanceof Error ? e.message : 'Der Vorschlag konnte nicht berechnet werden.' }); },
        );
        return () => { aktuell = false; };
    }, [projektId]);

    const umschalten = (id: number) => setGewaehlt(alt => { const neu = new Set(alt); if (neu.has(id)) neu.delete(id); else neu.add(id); return neu; });

    const uebernehmen = async () => {
        setSpeichert(true);
        try {
            onAuftrag(await uebernehmeDauerVorschlag(projektId, [...gewaehlt]));
            toast.success('Die Dauern wurden übernommen und die Termine neu gerechnet.');
            onSchliessen();
        } catch (e) {
            setZustand({ art: 'fehler', meldung: e instanceof Error ? e.message : 'Der Vorschlag konnte nicht übernommen werden.' });
            setSpeichert(false);
        }
    };

    return (
        <>
            <DialogHeader>
                <DialogTitle id="dauer-titel">Dauern aus dem Angebot vorschlagen</DialogTitle>
                <DialogDescription>
                    {auftrag.bezeichnung}. Das Programm nimmt die Arbeitszeit der Leistungen (Kalkulation) und teilt sie durch die Stunden pro Arbeitstag der Vorgangsart. Es ändert nichts, bevor Sie übernehmen.
                </DialogDescription>
            </DialogHeader>
            <div className="min-h-0 flex-1 space-y-3 overflow-y-auto py-1">
                {zustand.art === 'laedt' && <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Der Vorschlag wird berechnet …</p>}
                {zustand.art === 'fehler' && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{zustand.meldung}</p>}
                {zustand.art === 'da' && (
                    <>
                        <p className="text-sm text-slate-600">Grundlage: {zustand.daten.quelle === 'AUFTRAGSBESTAETIGUNG' ? 'Auftragsbestätigung' : zustand.daten.quelle === 'ANGEBOT' ? 'Angebot' : 'kein Angebot und keine Auftragsbestätigung'}.</p>
                        {zustand.daten.hinweise.map(h => <p key={h} className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">{h}</p>)}
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                                    <th scope="col" className="w-8 py-2" />
                                    <th scope="col" className="py-2 pr-3 font-semibold">Vorgang</th>
                                    <th scope="col" className="px-2 py-2 text-right font-semibold">Jetzt</th>
                                    <th scope="col" className="px-2 py-2 text-right font-semibold">Vorschlag</th>
                                    <th scope="col" className="py-2 pl-3 font-semibold">Rechnung</th>
                                </tr>
                            </thead>
                            <tbody>
                                {zustand.daten.zeilen.map(z => (
                                    <tr key={z.vorgangId} className="border-b border-slate-100 align-top" data-testid={`dauer-${z.vorgangId}`}>
                                        <td className="py-2">
                                            <input type="checkbox" className="h-4 w-4 accent-rose-600" disabled={z.vorgeschlageneDauer == null} checked={gewaehlt.has(z.vorgangId)} onChange={() => umschalten(z.vorgangId)} aria-label={`${z.bezeichnung} übernehmen`} />
                                        </td>
                                        <td className="py-2 pr-3"><span className="font-medium text-slate-900">{z.bezeichnung}</span></td>
                                        <td className="px-2 py-2 text-right tabular-nums">{z.aktuelleDauer} Tg.{z.aktuelleHerkunft === 'VON_HAND' && <span className="block text-xs text-slate-500">von Hand</span>}</td>
                                        <td className="px-2 py-2 text-right font-semibold tabular-nums">{z.vorgeschlageneDauer == null ? '–' : `${z.vorgeschlageneDauer} Tg.`}</td>
                                        <td className="py-2 pl-3 text-xs text-slate-600">
                                            {z.vorgeschlageneDauer != null ? `${zahl(z.stunden)} Std. ${z.abteilung ?? ''} ÷ ${zahl(z.stundenProTag)} Std. pro Tag` : z.hinweis}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </>
                )}
            </div>
            <DialogFooter>
                <Button variant="outline" onClick={onSchliessen} disabled={speichert}>Abbrechen</Button>
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void uebernehmen()} disabled={speichert || gewaehlt.size === 0}>
                    {speichert && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Auswahl übernehmen
                </Button>
            </DialogFooter>
        </>
    );
}
