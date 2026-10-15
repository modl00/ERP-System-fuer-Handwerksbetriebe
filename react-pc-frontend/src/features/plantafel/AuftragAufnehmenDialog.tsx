import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { SearchableSelect, type SearchableSelectOption } from '../../components/ui/searchable-select';
import { useToast } from '../../components/ui/toast';
import { AiButton } from '../../components/ui/ai-button';
import { ladeKiVorschlag, nimmAuftragAuf, uebernehmeKiVorschlag, type Auftrag, type KiKettenVorschlag } from './plantafelApi';

interface Props { offen: boolean; bereitsDrin: number[]; onSchliessen: () => void; onAuftrag: (a: Auftrag) => void }

async function sucheAuftraege(term: string): Promise<SearchableSelectOption[]> {
    try {
        const res = await fetch(`/api/projekte/simple?q=${encodeURIComponent(term)}&size=50&nurOffene=true`);
        if (!res.ok) return [];
        const daten = await res.json();
        return (Array.isArray(daten) ? daten : []).map((p: { id: number; bauvorhaben?: string; auftragsnummer?: string; kunde?: string }) => ({
            value: String(p.id), label: p.bauvorhaben || `Auftrag ${p.id}`, sublabel: `${p.auftragsnummer ?? ''}${p.kunde ? ' • ' + p.kunde : ''}`,
        }));
    } catch {
        return [];
    }
}

/** Nimmt einen offenen Auftrag mit einer Standardkette (Planung bis Montage) in die Plantafel auf. */
export function AuftragAufnehmenDialog({ offen, bereitsDrin, onSchliessen, onAuftrag }: Props) {
    return (
        <Dialog open={offen} onOpenChange={o => { if (!o) onSchliessen(); }} className="w-full max-w-lg" aria-labelledby="aufnehmen-titel">
            <DialogContent>{offen && <Inhalt bereitsDrin={bereitsDrin} onSchliessen={onSchliessen} onAuftrag={onAuftrag} />}</DialogContent>
        </Dialog>
    );
}

function Inhalt({ bereitsDrin, onSchliessen, onAuftrag }: Omit<Props, 'offen'>) {
    const toast = useToast();
    const [auswahl, setAuswahl] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');
    const [fragtKi, setFragtKi] = useState(false);
    const [vorschlag, setVorschlag] = useState<KiKettenVorschlag | null>(null);
    /** je Vorschlagszeile: übernehmen? und die (änderbare) Dauer */
    const [gewaehlt, setGewaehlt] = useState<boolean[]>([]);
    const [dauern, setDauern] = useState<string[]>([]);

    const pruefeAuswahl = (): number | null => {
        const id = Number(auswahl);
        if (!id) return null;
        if (bereitsDrin.includes(id)) {
            setFehler('Dieser Auftrag ist schon in der Plantafel.');
            return null;
        }
        return id;
    };

    const kiFragen = async () => {
        const id = pruefeAuswahl();
        if (id == null) return;
        setFragtKi(true);
        setFehler('');
        try {
            const v = await ladeKiVorschlag(id);
            setVorschlag(v);
            setGewaehlt(v.schritte.map(() => true));
            setDauern(v.schritte.map(x => String(x.dauerTage)));
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die KI konnte keinen Vorschlag machen.');
        } finally {
            setFragtKi(false);
        }
    };

    const vorschlagUebernehmen = async () => {
        const id = pruefeAuswahl();
        if (id == null || !vorschlag) return;
        const schritte = vorschlag.schritte.flatMap((x, i) => (gewaehlt[i] ? [{ typId: x.typId, dauerTage: Number(dauern[i]) }] : []));
        if (schritte.length === 0) {
            setFehler('Bitte mindestens einen Vorgang auswählen.');
            return;
        }
        if (schritte.some(x => !Number.isInteger(x.dauerTage) || x.dauerTage < 1)) {
            setFehler('Die Dauer muss eine ganze Zahl ab 1 (Arbeitstage) sein.');
            return;
        }
        setSpeichert(true);
        setFehler('');
        try {
            onAuftrag(await uebernehmeKiVorschlag(id, schritte));
            toast.success('Der Auftrag ist jetzt in der Plantafel. Dauern und Reihenfolge lassen sich anpassen.');
            onSchliessen();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Der Vorschlag konnte nicht übernommen werden.');
            setSpeichert(false);
        }
    };

    const aufnehmen = async () => {
        const id = Number(auswahl);
        if (!id) return;
        if (bereitsDrin.includes(id)) {
            setFehler('Dieser Auftrag ist schon in der Plantafel.');
            return;
        }
        setSpeichert(true);
        setFehler('');
        try {
            onAuftrag(await nimmAuftragAuf(id));
            toast.success('Der Auftrag ist jetzt in der Plantafel. Dauern und Reihenfolge lassen sich anpassen.');
            onSchliessen();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Der Auftrag konnte nicht aufgenommen werden.');
            setSpeichert(false);
        }
    };

    return (
        <>
            <DialogHeader>
                <DialogTitle id="aufnehmen-titel">Auftrag in die Plantafel aufnehmen</DialogTitle>
                <DialogDescription>Das Programm legt die Vorgänge nacheinander an (Planung, Materialbeschaffung, Fertigung, Veredelung, Montage) mit geschätzten Dauern. Danach lässt sich alles anpassen. Oder die KI schlägt nach den Positionen des Angebots vor, welche Vorgänge der Auftrag braucht.</DialogDescription>
            </DialogHeader>
            <div className="space-y-3 py-1">
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                <SearchableSelect value={auswahl} onChange={v => { setAuswahl(v); setFehler(''); setVorschlag(null); }} onSearch={sucheAuftraege}
                    placeholder="Auftrag suchen …" searchPlaceholder="Name oder Auftragsnummer" />
                {vorschlag && (
                    <section aria-label="Vorschlag der KI" className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
                        <p className="text-sm text-slate-700">
                            Vorschlag der KI nach den Positionen {vorschlag.quelle === 'AUFTRAGSBESTAETIGUNG' ? 'der Auftragsbestätigung' : 'des Angebots'}. Nicht Passendes einfach abwählen, die Dauer lässt sich ändern.
                        </p>
                        <ul className="space-y-2">
                            {vorschlag.schritte.map((x, i) => (
                                <li key={x.typId} className="flex items-start gap-2">
                                    <input type="checkbox" className="mt-1 h-4 w-4 accent-rose-600" checked={gewaehlt[i] ?? false} aria-label={`${x.typName} übernehmen`}
                                        onChange={() => setGewaehlt(alt => alt.map((g, j) => (j === i ? !g : g)))} />
                                    <span className="mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: x.farbe }} aria-hidden="true" />
                                    <div className="min-w-0 flex-1">
                                        <p className="text-sm font-medium text-slate-900">{x.typName}</p>
                                        {x.begruendung && <p className="text-xs text-slate-600">{x.begruendung}</p>}
                                    </div>
                                    <label className="flex shrink-0 items-center gap-1 text-xs text-slate-600">
                                        <input inputMode="numeric" aria-label={`Dauer von ${x.typName} in Arbeitstagen`} value={dauern[i] ?? ''} onChange={e => setDauern(alt => alt.map((d, j) => (j === i ? e.target.value : d)))}
                                            className="w-14 rounded border border-slate-300 px-2 py-1 text-right text-sm" /> Tage
                                    </label>
                                </li>
                            ))}
                        </ul>
                        {vorschlag.hinweise.length > 0 && (
                            <ul className="list-disc pl-5 text-xs text-amber-800">{vorschlag.hinweise.map(h => <li key={h}>{h}</li>)}</ul>
                        )}
                    </section>
                )}
            </div>
            <DialogFooter>
                <Button variant="outline" onClick={onSchliessen} disabled={speichert}>Abbrechen</Button>
                {!vorschlag && <AiButton label="Mit KI vorschlagen" isLoading={fragtKi} disabled={speichert || !auswahl} onClick={() => void kiFragen()} />}
                {vorschlag ? (
                    <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void vorschlagUebernehmen()} disabled={speichert}>
                        {speichert && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Auswahl übernehmen
                    </Button>
                ) : (
                    <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void aufnehmen()} disabled={speichert || fragtKi || !auswahl}>
                        {speichert && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Aufnehmen
                    </Button>
                )}
            </DialogFooter>
        </>
    );
}
