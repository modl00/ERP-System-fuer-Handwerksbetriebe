import { useMemo, useState } from 'react';
import { Download, FileText, Loader2, Files } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { useToast } from '../../components/ui/toast';
import { ladeSammelbericht } from './api';
import { formatDatum } from './format';
import { oeffneWartefenster, schliesseFenster, speicherePdf, zeigePdf } from './pdfAnzeige';
import { MAX_PROTOKOLLE } from './sammelbericht';

export interface ProjektBericht {
    protokollId: number;
    nummer: string;
    datum: string;
    anlage: string;
}

interface Props {
    berichte: ProjektBericht[];
    /** Vorschlag für den Titel auf dem Deckblatt, zum Beispiel der Projektname. */
    titelVorschlag?: string;
}

/**
 * Sammelbericht im Projekt (#238/#244): Die fertigen Prüfberichte dieses Projekts ankreuzen und als ein PDF mit Deckblatt öffnen oder speichern.
 * Es sind dieselben Berichte wie in der Projektmappe; der Sammelbericht entsteht beim Klick und wird nicht abgelegt.
 */
export function ProjektSammelbericht({ berichte, titelVorschlag = '' }: Props) {
    const toast = useToast();
    // Abgewählte merken statt Gewählte: Neu hinzukommende Berichte sind gleich mit dabei
    const [abgewaehlt, setAbgewaehlt] = useState<Set<number>>(new Set());
    const [titel, setTitel] = useState(titelVorschlag);
    const [mitDeckblatt, setMitDeckblatt] = useState(true);
    const [laeuft, setLaeuft] = useState<'oeffnen' | 'speichern' | null>(null);

    const gewaehlt = useMemo(() => berichte.filter((b) => !abgewaehlt.has(b.protokollId)), [berichte, abgewaehlt]);
    const zuViele = gewaehlt.length > MAX_PROTOKOLLE;

    if (berichte.length === 0) return null;

    const umschalten = (id: number) => setAbgewaehlt((alt) => {
        const neu = new Set(alt);
        if (neu.has(id)) neu.delete(id); else neu.add(id);
        return neu;
    });

    const erstelle = async (art: 'oeffnen' | 'speichern') => {
        if (gewaehlt.length === 0 || zuViele || laeuft) return;
        setLaeuft(art);
        // Das Fenster muss beim Klick aufgehen, erst nach dem Laden wäre es für den Browser ein Pop-up
        const fenster = art === 'oeffnen' ? oeffneWartefenster('Der Sammelbericht wird erstellt') : null;
        try {
            const { blob, dateiname } = await ladeSammelbericht({ protokollIds: gewaehlt.map((b) => b.protokollId), titel, mitDeckblatt });
            if (art === 'speichern') {
                speicherePdf(blob, dateiname);
            } else if (!zeigePdf(blob, fenster)) {
                speicherePdf(blob, dateiname);
                toast.info('Der Browser hat das neue Fenster blockiert. Der Sammelbericht wurde stattdessen heruntergeladen.');
            }
        } catch (e) {
            schliesseFenster(fenster);
            toast.error(e instanceof Error ? e.message : 'Der Sammelbericht konnte nicht erstellt werden.');
        } finally {
            setLaeuft(null);
        }
    };

    return (
        <section className="rounded-xl border border-slate-200 bg-white p-4" aria-labelledby="projekt-sammelbericht-titel" data-testid="projekt-sammelbericht">
            <h3 id="projekt-sammelbericht-titel" className="mb-1 flex items-center gap-2 text-sm font-medium text-slate-900">
                <Files className="h-4 w-4 text-rose-500" aria-hidden="true" /> Sammelbericht
            </h3>
            <p className="mb-3 text-sm text-slate-600">Fassen Sie die Prüfberichte dieses Projekts in einem PDF zusammen, zum Beispiel für den Kunden.</p>
            <ul className="mb-3 max-h-56 space-y-1 overflow-y-auto text-sm">
                {berichte.map((b) => (
                    <li key={b.protokollId}>
                        <label className="flex cursor-pointer items-start gap-2 rounded px-1 py-0.5 hover:bg-slate-50">
                            <input type="checkbox" className="mt-0.5 h-4 w-4 accent-rose-600" checked={!abgewaehlt.has(b.protokollId)} onChange={() => umschalten(b.protokollId)} />
                            <FileText className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden="true" />
                            <span className="break-words">{formatDatum(b.datum)} · {b.anlage} ({b.nummer})</span>
                        </label>
                    </li>
                ))}
            </ul>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
                <div>
                    <Label htmlFor="projekt-sammel-titel">Titel auf dem Deckblatt</Label>
                    <Input id="projekt-sammel-titel" value={titel} maxLength={200} placeholder="z. B. Wartung Brandschutztüren 2026" onChange={(e) => setTitel(e.target.value)} />
                </div>
                <label className="flex items-center gap-2 pb-2 text-sm text-slate-700">
                    <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={mitDeckblatt} onChange={(e) => setMitDeckblatt(e.target.checked)} /> Mit Deckblatt
                </label>
            </div>
            {zuViele && <p className="mt-2 text-sm text-rose-700" role="alert">Ein Sammelbericht kann höchstens {MAX_PROTOKOLLE} Berichte enthalten.</p>}
            <div className="mt-3 flex flex-wrap items-center gap-2">
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" disabled={gewaehlt.length === 0 || zuViele || laeuft !== null} onClick={() => void erstelle('oeffnen')}>
                    {laeuft === 'oeffnen' ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <FileText className="h-4 w-4" aria-hidden="true" />} Sammelbericht öffnen
                </Button>
                <Button variant="outline" disabled={gewaehlt.length === 0 || zuViele || laeuft !== null} onClick={() => void erstelle('speichern')}>
                    {laeuft === 'speichern' ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Download className="h-4 w-4" aria-hidden="true" />} Als PDF speichern
                </Button>
                <span className="text-sm text-slate-500">{gewaehlt.length} von {berichte.length} gewählt</span>
            </div>
        </section>
    );
}

