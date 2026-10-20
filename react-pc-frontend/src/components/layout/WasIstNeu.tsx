import { useEffect, useRef, useState } from 'react';
import { Sparkles, X } from 'lucide-react';
import { cn } from '../../lib/utils';
import { AKTUELLE_VERSION, CHANGELOG } from './changelog';

const STORAGE_KEY = 'erp_neuigkeiten_version';

function leseGeleseneVersion(): string {
    try {
        return localStorage.getItem(STORAGE_KEY) ?? '';
    } catch {
        return '';
    }
}

function speichereVersion(version: string): void {
    try {
        localStorage.setItem(STORAGE_KEY, version);
    } catch {
        // localStorage nicht verfügbar – kein Problem
    }
}

/** Sparkles-Button oben rechts; öffnet ein Panel mit den letzten Neuerungen. */
export function WasIstNeu() {
    const [offen, setOffen] = useState(false);
    // Lazy-Initializer: kein Effect nötig, liest localStorage einmalig beim Mount
    const [hatNeu, setHatNeu] = useState(() => leseGeleseneVersion() !== AKTUELLE_VERSION);
    const panelRef = useRef<HTMLDivElement>(null);

    // Klick außerhalb schließt das Panel
    useEffect(() => {
        if (!offen) return;
        function onOutside(e: MouseEvent) {
            if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
                setOffen(false);
            }
        }
        document.addEventListener('mousedown', onOutside);
        return () => document.removeEventListener('mousedown', onOutside);
    }, [offen]);

    function togglePanel() {
        const wirdGeoeffnet = !offen;
        setOffen(wirdGeoeffnet);
        if (wirdGeoeffnet && hatNeu) {
            speichereVersion(AKTUELLE_VERSION);
            setHatNeu(false);
        }
    }

    return (
        <div className="relative" ref={panelRef}>
            <button
                type="button"
                aria-label="Was ist neu?"
                aria-expanded={offen}
                onClick={togglePanel}
                className={cn(
                    'relative flex items-center gap-1.5 rounded-lg p-2 text-sm transition-colors',
                    offen
                        ? 'bg-rose-50 text-rose-700'
                        : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900',
                )}
            >
                <Sparkles className="w-4 h-4" />
                <span className="hidden lg:inline font-medium">Was ist neu?</span>
                {hatNeu && (
                    <span
                        aria-label="Neue Einträge vorhanden"
                        className="absolute right-1 top-1 h-2 w-2 rounded-full bg-rose-500 ring-2 ring-white"
                    />
                )}
            </button>

            {offen && (
                <div
                    role="dialog"
                    aria-label="Neuigkeiten"
                    className="absolute right-0 top-full z-50 mt-2 w-80 rounded-xl border border-slate-200 bg-white shadow-lg"
                >
                    <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
                        <div className="flex items-center gap-2">
                            <Sparkles className="w-4 h-4 text-rose-600" />
                            <span className="font-semibold text-slate-900 text-sm">Was ist neu?</span>
                        </div>
                        <button
                            type="button"
                            aria-label="Schließen"
                            onClick={() => setOffen(false)}
                            className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                        >
                            <X className="w-4 h-4" />
                        </button>
                    </div>

                    <div className="max-h-96 overflow-y-auto">
                        {CHANGELOG.map((eintrag, i) => (
                            <div key={eintrag.version} className={cn('px-4 py-3', i > 0 && 'border-t border-slate-100')}>
                                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
                                    {eintrag.datum}
                                </p>
                                <ul className="space-y-1.5">
                                    {eintrag.eintraege.map((text) => (
                                        <li key={text} className="flex items-start gap-2 text-sm text-slate-700">
                                            <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-rose-400" />
                                            {text}
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}
