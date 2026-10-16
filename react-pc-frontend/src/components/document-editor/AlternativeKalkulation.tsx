import { useState } from 'react';
import { Calculator, Loader2, Undo2 } from 'lucide-react';
import { Button } from '../ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { useOptionalToast } from '../ui/toast';
import { LeistungKalkulation } from '../../features/leistungen/LeistungKalkulation';
import { EigenePositionUebernehmen } from './EigenePositionUebernehmen';
import type { DocBlock } from './types';
import { unitMap } from './helpers';

const EINHEIT_AUS_KUERZEL: Record<string, string> = Object.fromEntries(Object.entries(unitMap).map(([wert, kurz]) => [kurz.toLowerCase(), wert]));

interface Props { block: DocBlock; onUpdate: (updates: Partial<DocBlock>) => void }

/**
 * Alternative Kalkulation direkt an einer Position: Bei einer Katalogleistung wird ihre Kalkulation kopiert, bei einem Artikel entsteht eine Leistung mit dem Artikel als Material.
 * Daran lassen sich Material, Arbeit, Maschine und Fremdleistung wie im Leistungseditor ändern, um kurzfristig eine individuelle Leistung zu erstellen; der Preis der Position
 * folgt der Kalkulation. Das Original bleibt unberührt und lässt sich über „Zurück zum Original“ wieder einsetzen. Die Alternative steht nur in diesem Dokument, bis man sie in
 * den Leistungsstamm übernimmt.
 */
export function AlternativeKalkulation({ block, onUpdate }: Props) {
    const toast = useOptionalToast(); // ohne Meldungsfläche (zum Beispiel in einem Test) läuft der Baustein trotzdem
    const [offen, setOffen] = useState(false);
    const [legtAn, setLegtAn] = useState(false);
    const aktiv = block.alternativeVon != null && block.leistungId != null && block.temporaereLeistung === true;

    const erzeugen = async () => {
        setLegtAn(true);
        try {
            const aus = block.leistungId != null;
            const res = aus
                ? await fetch(`/api/leistungen/${block.leistungId}/alternative`, { method: 'POST' })
                : await fetch('/api/leistungen/temporaer/aus-artikel', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ artikelId: block.artikelId, name: (block.title ?? '').trim(), unit: EINHEIT_AUS_KUERZEL[(block.unit ?? '').trim().toLowerCase()] ?? 'STUECK', price: block.price ?? 0 }),
                });
            const d = await res.json().catch(() => ({})) as { id?: number; message?: string };
            if (!res.ok || d.id == null) { toast?.error(d.message ?? 'Die alternative Kalkulation konnte nicht angelegt werden.'); return; }
            onUpdate({ leistungId: d.id, temporaereLeistung: true, alternativeVon: { leistungId: aus ? (block.leistungId as number) : null, preis: block.price ?? 0 } });
            setOffen(true);
        } catch {
            toast?.error('Die alternative Kalkulation konnte nicht angelegt werden. Ist der Server erreichbar?');
        } finally {
            setLegtAn(false);
        }
    };

    const zurueck = () => {
        const v = block.alternativeVon;
        if (!v) return;
        onUpdate({ leistungId: v.leistungId ?? undefined, temporaereLeistung: false, price: v.preis, alternativeVon: undefined });
        toast?.success('Das Original ist wieder eingesetzt. Die Alternative bleibt nirgends gespeichert, außer im Hintergrund als temporäre Leistung.');
    };

    return (
        <div className="mt-2 border-t border-slate-100 pt-2" data-testid="alternative-kalkulation">
            {!aktiv ? (
                <>
                    <p className="text-[11px] text-slate-500">Eine abweichende Kalkulation nur für diese Position erstellen, ohne den Katalog zu ändern.</p>
                    <Button type="button" variant="outline" size="sm" className="mt-1 h-7 gap-1 border-rose-300 px-2 text-[11px] text-rose-700 hover:bg-rose-50" disabled={legtAn} onClick={() => void erzeugen()}>
                        {legtAn ? <Loader2 className="h-3 w-3 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Calculator className="h-3 w-3" aria-hidden="true" />}
                        Alternative kalkulieren
                    </Button>
                </>
            ) : (
                <>
                    <p className="text-[11px] text-slate-500" data-testid="alternative-aktiv">Alternative Kalkulation, nur in diesem Dokument. Der Preis folgt der Kalkulation.</p>
                    <div className="flex flex-wrap gap-2">
                        <Button type="button" variant="outline" size="sm" className="mt-1 h-7 gap-1 border-rose-300 px-2 text-[11px] text-rose-700 hover:bg-rose-50" onClick={() => setOffen(true)}>
                            <Calculator className="h-3 w-3" aria-hidden="true" /> Alternative bearbeiten
                        </Button>
                        <Button type="button" variant="outline" size="sm" className="mt-1 h-7 gap-1 px-2 text-[11px]" onClick={zurueck}>
                            <Undo2 className="h-3 w-3" aria-hidden="true" /> Zurück zum Original
                        </Button>
                        <EigenePositionUebernehmen block={block} onUebernommen={(leistungId, kategorieId) => onUpdate({ leistungId, kategorieId, temporaereLeistung: false, alternativeVon: undefined })} />
                    </div>
                </>
            )}
            <Dialog open={offen} onOpenChange={o => { if (!o) setOffen(false); }}>
                <DialogContent className="sm:max-w-[900px] max-h-[90vh] overflow-y-auto" aria-label="Alternative Kalkulation">
                    <DialogHeader>
                        <DialogTitle>Alternative Kalkulation: {block.title}</DialogTitle>
                        <DialogDescription>Die Änderungen gelten nur für diese Position, der Katalog bleibt unberührt. Der Preis der Position folgt der gespeicherten Kalkulation (nur intern sichtbar, nie im Kundendokument).</DialogDescription>
                    </DialogHeader>
                    {block.leistungId != null && (
                        <LeistungKalkulation leistungId={block.leistungId} onPreisGeaendert={(preis, kalkuliert) => { if (kalkuliert) onUpdate({ price: preis }); }} />
                    )}
                    <DialogFooter>
                        <Button onClick={() => setOffen(false)} className="bg-rose-600 text-white hover:bg-rose-700">Fertig</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
