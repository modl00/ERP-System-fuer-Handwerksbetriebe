import { useState, useCallback, useRef } from 'react';
import { GitMerge, Search, X, AlertTriangle } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { useToast } from './ui/toast';
import type { Lieferant } from '../types';

interface Props {
    quell: Lieferant;
    onErfolgreich: () => void;
    onAbbrechen: () => void;
}

export function LieferantZusammenfuehrenDialog({ quell, onErfolgreich, onAbbrechen }: Props) {
    const toast = useToast();
    const [suche, setSuche] = useState('');
    const [ergebnisse, setErgebnisse] = useState<Lieferant[]>([]);
    const [laedt, setLaedt] = useState(false);
    const [ziel, setZiel] = useState<Lieferant | null>(null);
    const [fuehrtZusammen, setFuehrtZusammen] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const suchen = useCallback(async (term: string) => {
        setSuche(term);
        if (debounceRef.current) clearTimeout(debounceRef.current);
        if (term.trim().length < 2) { setErgebnisse([]); return; }
        debounceRef.current = setTimeout(async () => {
            setLaedt(true);
            try {
                const res = await fetch(`/api/lieferanten?q=${encodeURIComponent(term)}&size=15`);
                if (!res.ok) throw new Error();
                const data = await res.json();
                const liste: Lieferant[] = Array.isArray(data) ? data : (data.lieferanten ?? []);
                setErgebnisse(liste.filter(l => l.id !== quell.id && !l.archiviert));
            } catch {
                toast.error('Suche fehlgeschlagen.');
            } finally {
                setLaedt(false);
            }
        }, 300);
    }, [quell.id, toast]);

    const zusammenfuehren = async () => {
        if (!ziel) return;
        setFuehrtZusammen(true);
        try {
            const res = await fetch(
                `/api/lieferanten/${quell.id}/zusammenfuehren?zielId=${ziel.id}`,
                { method: 'POST' }
            );
            if (!res.ok) {
                let msg = 'Zusammenführen fehlgeschlagen.';
                try {
                    const body = await res.json();
                    if (body?.message) msg = body.message;
                } catch { /* JSON parse fehlgeschlagen, Standardmeldung beibehalten */ }
                throw new Error(msg);
            }
            toast.success(`„${quell.lieferantenname}" wurde mit „${ziel.lieferantenname}" zusammengeführt.`);
            onErfolgreich();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Zusammenführen fehlgeschlagen.');
        } finally {
            setFuehrtZusammen(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
            <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg flex flex-col">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
                    <div className="flex items-center gap-2">
                        <GitMerge className="h-5 w-5 text-rose-600" aria-hidden />
                        <h2 className="font-semibold text-slate-800">Dopplungen zusammenführen</h2>
                    </div>
                    <button
                        onClick={fuehrtZusammen ? undefined : onAbbrechen}
                        disabled={fuehrtZusammen}
                        className="text-slate-400 hover:text-slate-600 rounded-lg p-1 disabled:opacity-40"
                        aria-label="Schließen"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <div className="p-6 space-y-5">
                    {/* Info */}
                    <div className="flex gap-3 rounded-lg bg-amber-50 border border-amber-200 p-3 text-sm text-amber-800">
                        <AlertTriangle className="h-4 w-4 mt-0.5 shrink-0 text-amber-600" aria-hidden />
                        <p>
                            <span className="font-medium">„{quell.lieferantenname}"</span> wird gelöscht.
                            Notizen, Dokumente, Kontakte und Artikel-Preise werden in den Ziel-Lieferanten
                            übernommen. Belege und Bestellungen dürfen nicht vorhanden sein.
                        </p>
                    </div>

                    {/* Ziel-Suche */}
                    {!ziel ? (
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-slate-700">
                                Zusammenführen mit …
                            </label>
                            <div className="relative">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" aria-hidden />
                                <Input
                                    className="pl-9"
                                    placeholder="Lieferantenname eingeben …"
                                    value={suche}
                                    onChange={e => suchen(e.target.value)}
                                    autoFocus
                                />
                            </div>
                            {laedt && (
                                <p className="text-sm text-slate-500 px-1">Suche …</p>
                            )}
                            {ergebnisse.length > 0 && (
                                <ul className="border border-slate-200 rounded-lg overflow-hidden divide-y divide-slate-100 max-h-52 overflow-y-auto">
                                    {ergebnisse.map(l => (
                                        <li key={l.id}>
                                            <button
                                                className="w-full text-left px-4 py-2.5 hover:bg-slate-50 text-sm"
                                                onClick={() => { setZiel(l); setErgebnisse([]); }}
                                            >
                                                <span className="font-medium text-slate-800">{l.lieferantenname}</span>
                                                {l.ort && (
                                                    <span className="ml-2 text-slate-500">{l.ort}</span>
                                                )}
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            )}
                            {suche.length >= 2 && !laedt && ergebnisse.length === 0 && (
                                <p className="text-sm text-slate-500 px-1">Keine aktiven Lieferanten gefunden.</p>
                            )}
                        </div>
                    ) : (
                        <div className="space-y-1">
                            <p className="text-sm font-medium text-slate-700">Ziel-Lieferant</p>
                            <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
                                <div>
                                    <p className="font-medium text-slate-800">{ziel.lieferantenname}</p>
                                    {ziel.ort && <p className="text-sm text-slate-500">{ziel.ort}</p>}
                                </div>
                                <button
                                    onClick={() => setZiel(null)}
                                    className="text-slate-400 hover:text-slate-600 rounded p-1"
                                    aria-label="Auswahl aufheben"
                                >
                                    <X className="h-4 w-4" />
                                </button>
                            </div>
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="flex justify-end gap-2 px-6 py-4 border-t border-slate-100">
                    <Button variant="outline" onClick={onAbbrechen} disabled={fuehrtZusammen}>
                        Abbrechen
                    </Button>
                    <Button
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                        disabled={!ziel || fuehrtZusammen}
                        onClick={zusammenfuehren}
                    >
                        {fuehrtZusammen ? 'Wird zusammengeführt …' : 'Zusammenführen'}
                    </Button>
                </div>
            </div>
        </div>
    );
}
