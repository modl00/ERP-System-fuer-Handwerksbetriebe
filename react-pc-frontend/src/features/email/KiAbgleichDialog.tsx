import { useEffect, useState } from 'react';
import { Briefcase, FileText, Loader2, Sparkles, Truck } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { parseErrorMessage } from '../../components/settings/settingsApi';

export interface KiVorschlag {
    emailId: number;
    betreff: string | null;
    von: string | null;
    art: 'PROJEKT' | 'ANFRAGE' | 'LIEFERANT' | 'KEINE';
    zielId: number | null;
    ziel: string | null;
    hinweis: string | null;
}

const ART: Record<string, { label: string; icon: typeof Briefcase }> = {
    PROJEKT: { label: 'Projekt', icon: Briefcase },
    ANFRAGE: { label: 'Anfrage', icon: FileText },
    LIEFERANT: { label: 'Lieferant', icon: Truck },
};

interface Props {
    open: boolean;
    emailIds: number[];
    onOpenChange: (open: boolean) => void;
    /** Nach dem Übernehmen: die Liste neu laden. */
    onUebernommen: () => void;
}

/**
 * Ausgewählte E-Mails mit der KI (und den Regeln des Programms) abgleichen: erst ein Vorschlag je Mail, dann übernehmen Sie, was stimmt. Nichts wird ohne Ihren Klick zugeordnet.
 */
export function KiAbgleichDialog({ open, emailIds, onOpenChange, onUebernommen }: Props) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[720px]">
                {open && <Inhalt emailIds={emailIds} onSchliessen={() => onOpenChange(false)} onUebernommen={onUebernommen} />}
            </DialogContent>
        </Dialog>
    );
}

function Inhalt({ emailIds, onSchliessen, onUebernommen }: { emailIds: number[]; onSchliessen: () => void; onUebernommen: () => void }) {
    const toast = useToast();
    const [vorschlaege, setVorschlaege] = useState<KiVorschlag[] | null>(null);
    const [fehler, setFehler] = useState('');
    const [auswahl, setAuswahl] = useState<Set<number>>(new Set());
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        let aktuell = true;
        (async () => {
            try {
                const res = await fetch('/api/emails/ki-abgleich/vorschlaege', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ emailIds }) });
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Abgleich ist fehlgeschlagen.'));
                const daten = await res.json() as KiVorschlag[];
                if (!aktuell) return;
                setVorschlaege(daten);
                setAuswahl(new Set(daten.filter(v => v.art !== 'KEINE').map(v => v.emailId)));
            } catch (e) {
                if (aktuell) setFehler(e instanceof Error ? e.message : 'Der Abgleich ist fehlgeschlagen.');
            }
        })();
        return () => { aktuell = false; };
    }, [emailIds]);

    const umschalten = (id: number) => setAuswahl(alt => {
        const neu = new Set(alt);
        if (neu.has(id)) neu.delete(id); else neu.add(id);
        return neu;
    });

    const uebernehmen = async () => {
        if (!vorschlaege) return;
        setSpeichert(true);
        try {
            const body = vorschlaege.filter(v => auswahl.has(v.emailId) && v.art !== 'KEINE').map(v => ({ emailId: v.emailId, art: v.art, zielId: v.zielId }));
            const res = await fetch('/api/emails/ki-abgleich/uebernehmen', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Zuordnung konnte nicht gespeichert werden.'));
            const r = await res.json() as { zugeordnet: number; abgelehnt: string[] };
            toast.success(r.zugeordnet === 1 ? '1 E-Mail wurde zugeordnet.' : `${r.zugeordnet} E-Mails wurden zugeordnet.`);
            if (r.abgelehnt.length > 0) toast.warning(`Nicht übernommen: ${r.abgelehnt.join('; ')}`);
            onUebernommen();
            onSchliessen();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Zuordnung konnte nicht gespeichert werden.');
            setSpeichert(false);
        }
    };

    const treffer = vorschlaege?.filter(v => v.art !== 'KEINE').length ?? 0;
    return (
        <>
            <DialogHeader>
                <DialogTitle className="flex items-center gap-2"><Sparkles className="h-5 w-5 text-rose-600" aria-hidden="true" /> Mit KI abgleichen</DialogTitle>
                <DialogDescription>Das Programm schlägt je E-Mail ein Ziel vor (Lieferant, Kunde, Schlagwörter, KI). Zugeordnet wird nur, was Sie angekreuzt lassen und bestätigen.</DialogDescription>
            </DialogHeader>
            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {!vorschlaege && !fehler && <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> {emailIds.length} E-Mails werden abgeglichen … (das kann einen Moment dauern)</p>}
            {vorschlaege && (
                <ul className="max-h-[50vh] space-y-2 overflow-y-auto" data-testid="ki-vorschlaege">
                    {vorschlaege.map(v => {
                        const a = ART[v.art];
                        return (
                            <li key={v.emailId} className="flex items-start gap-3 rounded-lg border border-slate-200 p-3">
                                <input type="checkbox" className="mt-1 h-4 w-4 accent-rose-600" aria-label={`${v.betreff ?? 'E-Mail'} zuordnen`} checked={auswahl.has(v.emailId)} disabled={v.art === 'KEINE'} onChange={() => umschalten(v.emailId)} />
                                <div className="min-w-0 flex-1">
                                    <p className="truncate text-sm font-medium text-slate-900">{v.betreff || '(ohne Betreff)'}</p>
                                    <p className="truncate text-xs text-slate-500">{v.von}</p>
                                    {a ? (
                                        <p className="mt-1 inline-flex items-center gap-1.5 text-sm text-rose-700"><a.icon className="h-4 w-4" aria-hidden="true" /> {a.label}: <strong>{v.ziel}</strong></p>
                                    ) : (
                                        <p className="mt-1 text-sm text-slate-500">{v.hinweis ?? 'Kein Vorschlag.'}</p>
                                    )}
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}
            <DialogFooter>
                <Button variant="outline" onClick={onSchliessen} disabled={speichert}>Abbrechen</Button>
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void uebernehmen()} disabled={speichert || !vorschlaege || auswahl.size === 0}>
                    {speichert && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Übernehmen ({auswahl.size}{vorschlaege ? ` von ${treffer}` : ''})
                </Button>
            </DialogFooter>
        </>
    );
}
