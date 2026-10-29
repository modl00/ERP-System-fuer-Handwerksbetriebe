import { useEffect, useRef, useState } from 'react';
import { Loader2, Search } from 'lucide-react';

import { Input } from '../../components/ui/input';
import { cn } from '../../lib/utils';
import { einheitAusArtikel, type LagerEinheit } from './lagerApi';

/** Ein Treffer der Artikelsuche, wie ihn das Lager braucht. */
export interface GewaehlterArtikel {
    id: number;
    titel: string;
    nummer: string | null;
    einheit: LagerEinheit;
    kgProMeter?: number | null;
    handelseinheitBezeichnung?: string | null;
    handelseinheitMeter?: number | null;
    handelseinheitVarianten?: string | null;
}

interface ArtikelTreffer {
    id: number;
    produktname?: string;
    kurzbeschreibung?: string;
    artikelnummer?: string;
    abmessung?: string;
    verrechnungseinheit?: string | { name: string };
    kgProMeter?: number | null;
    handelseinheitBezeichnung?: string | null;
    handelseinheitMeter?: number | null;
    handelseinheitVarianten?: string | null;
}

const titel = (a: ArtikelTreffer) =>
    a.kurzbeschreibung?.trim() || [a.produktname, a.abmessung].filter(Boolean).join(' ').trim() || `Artikel ${a.id}`;

interface Props {
    onWaehlen: (artikel: GewaehlterArtikel) => void;
}

/**
 * Artikelsuche für das Lager, direkt im Buchungsdialog. Jeder Treffer zeigt Artikelnummer und die <b>Einheit</b> (Meter, Liter, Stück, Kilogramm ...),
 * in der der Bestand geführt wird. Bewusst keine zweite Dialogebene: Ein Dialog über einem Dialog war schon einmal verdeckt und nicht bedienbar.
 */
export function LagerArtikelWahl({ onWaehlen }: Props) {
    const [suche, setSuche] = useState('');
    const [treffer, setTreffer] = useState<ArtikelTreffer[] | null>(null);
    const [fehler, setFehler] = useState('');
    const nr = useRef(0);

    useEffect(() => {
        const mein = ++nr.current;
        const timer = setTimeout(() => {
            const params = new URLSearchParams({ page: '0', size: '12' });
            if (suche.trim()) params.set('q', suche.trim());
            fetch(`/api/artikel?${params.toString()}`)
                .then(res => { if (!res.ok) throw new Error(); return res.json() as Promise<{ artikel?: ArtikelTreffer[] }>; })
                .then(daten => { if (mein === nr.current) { setTreffer(Array.isArray(daten?.artikel) ? daten.artikel : []); setFehler(''); } })
                .catch(() => { if (mein === nr.current) { setFehler('Die Artikel konnten nicht geladen werden.'); setTreffer([]); } });
        }, suche ? 300 : 0);
        return () => clearTimeout(timer);
    }, [suche]);

    return (
        <div className="space-y-2" data-testid="lager-artikelwahl">
            <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <Input value={suche} onChange={e => setSuche(e.target.value)} placeholder="Artikel oder Artikelnummer suchen…" aria-label="Artikel suchen" className="pl-9" autoFocus />
            </div>
            <div className="max-h-56 overflow-y-auto rounded-lg border border-slate-200 bg-white" role="listbox" aria-label="Gefundene Artikel">
                {treffer == null ? (
                    <div className="flex items-center gap-2 text-sm text-slate-500 p-3"><Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> Wird geladen…</div>
                ) : fehler ? (
                    <p role="alert" className="p-3 text-sm text-rose-700">{fehler}</p>
                ) : treffer.length === 0 ? (
                    <p className="p-3 text-sm text-slate-500">Kein Artikel gefunden.</p>
                ) : treffer.map(a => {
                    const einheit = einheitAusArtikel(a.verrechnungseinheit);
                    return (
                        <button key={a.id} type="button" role="option" aria-selected={false} aria-label={`${titel(a)} auswählen`}
                            className={cn('w-full text-left px-3 py-2 border-b border-slate-100 last:border-0 hover:bg-rose-50 focus-visible:outline-none focus-visible:bg-rose-50 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-300 cursor-pointer flex items-center justify-between gap-3')}
                            onClick={() => onWaehlen({ id: a.id, titel: titel(a), nummer: a.artikelnummer ?? null, einheit, kgProMeter: a.kgProMeter ?? null, handelseinheitBezeichnung: a.handelseinheitBezeichnung ?? null, handelseinheitMeter: a.handelseinheitMeter ?? null, handelseinheitVarianten: a.handelseinheitVarianten ?? null })}>
                            <span className="min-w-0">
                                <span className="block text-sm text-slate-900 truncate">{titel(a)}</span>
                                {a.artikelnummer && <span className="block text-xs text-slate-500">{a.artikelnummer}</span>}
                            </span>
                            <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700" title={`Der Bestand wird in ${einheit.lang} geführt`}>{einheit.lang} ({einheit.kurz})</span>
                        </button>
                    );
                })}
            </div>
        </div>
    );
}
