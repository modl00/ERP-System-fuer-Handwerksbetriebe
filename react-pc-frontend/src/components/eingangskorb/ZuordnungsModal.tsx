import { useCallback, useEffect, useRef, useState } from 'react';
import { Loader2, Search, X } from 'lucide-react';
import { Button } from '../ui/button';
import { Input } from '../ui/input';

interface ProjektTreffer {
    id: number;
    bauvorhaben: string;
    auftragsnummer: string | null;
    kunde: string | null;
    abgeschlossen: boolean;
}

interface EingangEintrag {
    id: number;
    dateiname: string | null;
    lieferantName: string | null;
}

interface ZuordnungsModalProps {
    eintrag: EingangEintrag | null;
    onClose: () => void;
    onZugeordnet: () => void;
}

export function ZuordnungsModal({ eintrag, onClose, onZugeordnet }: ZuordnungsModalProps) {
    const [suchbegriff, setSuchbegriff] = useState('');
    const [projekte, setProjekte] = useState<ProjektTreffer[]>([]);
    const [laden, setLaden] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);
    const abortRef = useRef<AbortController | null>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    const sucheProjekte = useCallback((q: string) => {
        if (q.trim().length < 2) {
            setProjekte([]);
            setLaden(false);
            return;
        }
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;
        setLaden(true);
        fetch(`/api/projekte/suche?q=${encodeURIComponent(q.trim())}`, { signal: controller.signal })
            .then(r => r.json())
            .then(data => {
                setProjekte(Array.isArray(data) ? data : []);
                setLaden(false);
            })
            .catch(e => {
                if (!(e instanceof DOMException && e.name === 'AbortError')) {
                    setProjekte([]);
                    setLaden(false);
                }
            });
    }, []);

    useEffect(() => {
        if (eintrag) {
            setSuchbegriff('');
            setProjekte([]);
            setFehler(null);
            setTimeout(() => inputRef.current?.focus(), 50);
        }
        return () => abortRef.current?.abort();
    }, [eintrag]);

    useEffect(() => {
        const t = setTimeout(() => sucheProjekte(suchbegriff), 250);
        return () => clearTimeout(t);
    }, [suchbegriff, sucheProjekte]);

    const zuordnen = async (projektId: number) => {
        if (!eintrag) return;
        setFehler(null);
        try {
            const res = await fetch(`/api/eingangskorb/${eintrag.id}/zuordnen`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ projektId, prozent: 100 }),
            });
            if (!res.ok) throw new Error('Zuordnung fehlgeschlagen');
            onZugeordnet();
        } catch {
            setFehler('Zuordnung fehlgeschlagen. Bitte erneut versuchen.');
        }
    };

    if (!eintrag) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 flex flex-col max-h-[80vh]">
                <div className="flex items-center justify-between px-6 py-4 border-b">
                    <div>
                        <p className="text-xs font-semibold text-rose-600 uppercase tracking-wide">Eingangskorb</p>
                        <h2 className="text-lg font-bold text-slate-900">Dokument zuordnen</h2>
                        <p className="text-sm text-slate-500 mt-0.5 truncate max-w-xs">
                            {eintrag.dateiname ?? 'Dokument'} · {eintrag.lieferantName}
                        </p>
                    </div>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600 ml-4 flex-shrink-0">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <div className="px-6 py-4 border-b">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                        <Input
                            ref={inputRef}
                            value={suchbegriff}
                            onChange={e => setSuchbegriff(e.target.value)}
                            placeholder="Projekt suchen (mind. 2 Zeichen)…"
                            className="pl-9"
                        />
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto px-6 py-3 min-h-0">
                    {laden && (
                        <div className="flex items-center justify-center py-8 text-slate-400">
                            <Loader2 className="w-5 h-5 animate-spin mr-2" />
                            <span className="text-sm">Suche läuft…</span>
                        </div>
                    )}
                    {!laden && suchbegriff.trim().length < 2 && (
                        <p className="text-sm text-slate-400 py-8 text-center">Projektname oder Auftragsnummer eingeben</p>
                    )}
                    {!laden && suchbegriff.trim().length >= 2 && projekte.length === 0 && (
                        <p className="text-sm text-slate-400 py-8 text-center">Keine Projekte gefunden</p>
                    )}
                    {projekte.map(p => (
                        <button
                            key={p.id}
                            onClick={() => zuordnen(p.id)}
                            className="w-full text-left px-4 py-3 rounded-lg hover:bg-rose-50 transition-colors border border-transparent hover:border-rose-200 mb-1"
                        >
                            <p className="font-medium text-slate-800">{p.bauvorhaben}</p>
                            <p className="text-sm text-slate-500">
                                {[p.auftragsnummer, p.kunde].filter(Boolean).join(' · ')}
                                {p.abgeschlossen && <span className="ml-2 text-xs text-amber-600">(abgeschlossen)</span>}
                            </p>
                        </button>
                    ))}
                </div>

                {fehler && (
                    <div className="px-6 py-2 bg-red-50 text-red-700 text-sm border-t">{fehler}</div>
                )}

                <div className="px-6 py-4 border-t flex justify-end">
                    <Button variant="outline" onClick={onClose}>Abbrechen</Button>
                </div>
            </div>
        </div>
    );
}
