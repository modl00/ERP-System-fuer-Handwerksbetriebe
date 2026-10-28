import { useState } from 'react';
import { Calculator, Loader2 } from 'lucide-react';
import { Button } from '../ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { useToast } from '../ui/toast';
import { LeistungKalkulation } from '../../features/leistungen/LeistungKalkulation';
import type { DocBlock } from './types';
import { unitMap } from './helpers';

const EINHEIT_AUS_KUERZEL: Record<string, string> = Object.fromEntries(Object.entries(unitMap).map(([wert, kurz]) => [kurz.toLowerCase(), wert]));

/**
 * Eine einmalige Position kalkulieren (#55, #205): Beim ersten Mal entsteht im Hintergrund eine temporäre Leistung (nicht im Katalog), an der Material, Arbeit, Maschine
 * und Fremdleistung wie im Leistungseditor kalkuliert werden. Der Preis der Position folgt der gespeicherten Kalkulation; die Aufstellung steht danach „nur intern" an der
 * Position. Die temporäre Leistung lässt sich später samt Kalkulation in den Leistungsstamm übernehmen.
 */
export function EigenePositionKalkulieren({ block, onUpdate }: { block: DocBlock; onUpdate: (updates: Partial<DocBlock>) => void }) {
    const toast = useToast();
    const [offen, setOffen] = useState(false);
    const [legtAn, setLegtAn] = useState(false);
    // Die Nummer der temporären Leistung merkt sich der Dialog selbst, bis das Dokument die Änderung übernommen hat
    const [neueId, setNeueId] = useState<number | null>(null);
    const leistungId = block.leistungId ?? neueId;
    const vorhanden = leistungId != null && (block.temporaereLeistung === true || neueId != null);
    const gefuellt = (block.title ?? '').trim().length > 0;

    const oeffne = async () => {
        if (vorhanden) { setOffen(true); return; }
        setLegtAn(true);
        try {
            const res = await fetch('/api/leistungen/temporaer', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: (block.title ?? '').trim(), description: block.description ?? '', price: block.price ?? 0, unit: EINHEIT_AUS_KUERZEL[(block.unit ?? '').trim().toLowerCase()] ?? 'STUECK' }),
            });
            const d = await res.json().catch(() => ({})) as { id?: number; message?: string };
            if (!res.ok || d.id == null) { toast.error(d.message ?? 'Die Kalkulation konnte nicht vorbereitet werden.'); return; }
            setNeueId(d.id);
            onUpdate({ leistungId: d.id, temporaereLeistung: true });
            setOffen(true);
        } catch {
            toast.error('Die Kalkulation konnte nicht vorbereitet werden. Ist der Server erreichbar?');
        } finally {
            setLegtAn(false);
        }
    };

    return (
        <>
            <Button type="button" variant="outline" size="sm" className="mt-1 h-7 gap-1 border-rose-300 px-2 text-[11px] text-rose-700 hover:bg-rose-50" disabled={!gefuellt || legtAn} onClick={() => void oeffne()}
                title={gefuellt ? undefined : 'Zuerst einen Titel für die Position eintragen'}>
                {legtAn ? <Loader2 className="h-3 w-3 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Calculator className="h-3 w-3" aria-hidden="true" />}
                {vorhanden ? 'Kalkulation bearbeiten' : 'Kalkulieren'}
            </Button>
            <Dialog open={offen} onOpenChange={o => { if (!o) setOffen(false); }}>
                <DialogContent className="sm:max-w-[900px] max-h-[90vh] overflow-y-auto" aria-label="Position kalkulieren">
                    <DialogHeader>
                        <DialogTitle>Position kalkulieren: {block.title}</DialogTitle>
                        <DialogDescription>Die Kalkulation gehört nur zu dieser Position und steht nicht im Katalog. Der Preis der Position folgt der gespeicherten Kalkulation (nur intern sichtbar, nie im Kundendokument).</DialogDescription>
                    </DialogHeader>
                    {leistungId != null && (
                        <LeistungKalkulation leistungId={leistungId} onPreisGeaendert={(preis, kalkuliert) => { if (kalkuliert) onUpdate({ price: preis }); }} />
                    )}
                    <DialogFooter>
                        <Button onClick={() => setOffen(false)} className="bg-rose-600 text-white hover:bg-rose-700">Fertig</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </>
    );
}
