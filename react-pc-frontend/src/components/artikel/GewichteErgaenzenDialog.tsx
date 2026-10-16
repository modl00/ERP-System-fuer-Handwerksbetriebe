import { useEffect, useState } from 'react';
import { Loader2, Scale } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';
import { parseErrorMessage } from '../settings/settingsApi';

interface Zeile { id: number; bezeichnung: string; form: string; status: 'BERECHENBAR' | 'MASSE_FEHLEN'; hinweis: string | null; kg: number | null; flaeche: number | null; einheit: string | null; dichte: number | null }
interface Vorschau { gesamt: number; zeilen: Zeile[]; gekuerzt: boolean }

const zahl = (n: number | null, stellen = 3) => (n == null ? '–' : n.toLocaleString('de-DE', { minimumFractionDigits: 0, maximumFractionDigits: stellen }));

interface Props { offen: boolean; onOpenChange: (offen: boolean) => void; onFertig: () => void }

/**
 * Gewicht und Außenfläche für Artikel ergänzen, die eine Form, aber noch kein Gewicht haben (Issue #252): aus den Maßen und der Dichte des Werkstoffs berechnet,
 * erst Vorschau, dann Auswahl. Artikel mit eigenem Gewicht werden nie überschrieben.
 */
export function GewichteErgaenzenDialog({ offen, onOpenChange, onFertig }: Props) {
    return (
        <Dialog open={offen} onOpenChange={onOpenChange} className="w-full max-w-4xl max-h-[90vh]" aria-labelledby="gewichte-ergaenzen-titel">
            <DialogContent>{offen && <Inhalt onSchliessen={() => onOpenChange(false)} onFertig={onFertig} />}</DialogContent>
        </Dialog>
    );
}

function Inhalt({ onSchliessen, onFertig }: { onSchliessen: () => void; onFertig: () => void }) {
    const toast = useToast();
    const [daten, setDaten] = useState<Vorschau | null>(null);
    const [fehler, setFehler] = useState('');
    const [auswahl, setAuswahl] = useState<Set<number>>(new Set());
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        let aktuell = true;
        (async () => {
            try {
                const res = await fetch('/api/artikel/gewichte-ergaenzen/vorschau');
                if (res.status === 403) throw new Error('Das Ergänzen der Gewichte ist nur für Administratoren.');
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Vorschau konnte nicht berechnet werden.'));
                const v = await res.json() as Vorschau;
                if (!aktuell) return;
                setDaten(v);
                setAuswahl(new Set(v.zeilen.filter(z => z.status === 'BERECHENBAR').map(z => z.id)));
            } catch (e) {
                if (aktuell) setFehler(e instanceof Error ? e.message : 'Die Vorschau konnte nicht berechnet werden.');
            }
        })();
        return () => { aktuell = false; };
    }, []);

    const umschalten = (id: number) => setAuswahl(alt => {
        const neu = new Set(alt);
        if (neu.has(id)) neu.delete(id); else neu.add(id);
        return neu;
    });

    const uebernehmen = async () => {
        setSpeichert(true);
        try {
            const res = await fetch('/api/artikel/gewichte-ergaenzen/uebernehmen', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ artikelIds: [...auswahl] }) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Gewichte konnten nicht übernommen werden.'));
            const r = await res.json() as { ergaenzt: number; uebersprungen: number };
            toast.success(r.ergaenzt === 1 ? 'Bei 1 Artikel wurde das Gewicht ergänzt.' : `Bei ${r.ergaenzt} Artikeln wurde das Gewicht ergänzt.`);
            if (r.uebersprungen > 0) toast.info(`${r.uebersprungen} Artikel wurden übersprungen (hatten inzwischen ein Gewicht oder reichen die Maße nicht).`);
            onFertig();
            onSchliessen();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Gewichte konnten nicht übernommen werden.');
            setSpeichert(false);
        }
    };

    const berechenbar = daten?.zeilen.filter(z => z.status === 'BERECHENBAR').length ?? 0;
    return (
        <>
            <DialogHeader>
                <DialogTitle id="gewichte-ergaenzen-titel" className="flex items-center gap-2"><Scale className="h-5 w-5 text-rose-600" aria-hidden="true" /> Gewichte ergänzen</DialogTitle>
                <DialogDescription>Für Artikel mit gewählter Form, aber ohne Gewicht, rechnet das Programm Gewicht und Außenfläche aus den Maßen und der Dichte des Werkstoffs (ohne Werkstoff: Stahl 7,85). Nichts wird überschrieben, was schon ein Gewicht hat.</DialogDescription>
            </DialogHeader>
            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {!daten && !fehler && <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird berechnet …</p>}
            {daten && (
                <div className="min-h-0 flex-1 overflow-y-auto" data-testid="gewichte-vorschau">
                    <p className="mb-2 text-sm text-slate-700">{daten.gesamt === 0 ? 'Alle Artikel mit Form haben schon ein Gewicht.' : `${daten.gesamt} Artikel ohne Gewicht, davon ${berechenbar} berechenbar${daten.gekuerzt ? ` (angezeigt werden die ersten ${daten.zeilen.length})` : ''}.`}</p>
                    {daten.zeilen.length > 0 && (
                        <table className="w-full text-sm">
                            <thead><tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                                <th scope="col" className="w-8 py-2"><span className="sr-only">Übernehmen</span></th>
                                <th scope="col" className="py-2 pr-3 font-semibold">Artikel</th>
                                <th scope="col" className="px-2 py-2 font-semibold">Form</th>
                                <th scope="col" className="px-2 py-2 text-right font-semibold">Gewicht</th>
                                <th scope="col" className="pl-2 py-2 text-right font-semibold">Außenfläche</th>
                            </tr></thead>
                            <tbody>
                                {daten.zeilen.map(z => (
                                    <tr key={z.id} className="border-b border-slate-100 align-top" data-testid={`gewicht-${z.id}`}>
                                        <td className="py-2"><input type="checkbox" className="h-4 w-4 accent-rose-600" aria-label={`${z.bezeichnung} übernehmen`} disabled={z.status !== 'BERECHENBAR'} checked={auswahl.has(z.id)} onChange={() => umschalten(z.id)} /></td>
                                        <td className="py-2 pr-3 font-medium text-slate-900 break-words">{z.bezeichnung}</td>
                                        <td className="px-2 py-2 text-slate-600">{z.form}</td>
                                        {z.status === 'BERECHENBAR' ? (
                                            <>
                                                <td className="px-2 py-2 text-right tabular-nums">{zahl(z.kg)} kg {z.einheit}</td>
                                                <td className="pl-2 py-2 text-right tabular-nums">{zahl(z.flaeche)} m² {z.einheit}</td>
                                            </>
                                        ) : <td colSpan={2} className="px-2 py-2 text-xs text-amber-800">{z.hinweis}</td>}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>
            )}
            <DialogFooter>
                <Button variant="outline" onClick={onSchliessen} disabled={speichert}>Abbrechen</Button>
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void uebernehmen()} disabled={speichert || auswahl.size === 0}>
                    {speichert && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Ausgewählte übernehmen ({auswahl.size})
                </Button>
            </DialogFooter>
        </>
    );
}
