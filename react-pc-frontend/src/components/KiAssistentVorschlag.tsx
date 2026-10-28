import { Check, Loader2, X } from 'lucide-react';

export interface AssistentVorschlag {
    id: string;
    werkzeug: string;
    beschreibung: string;
}

export type VorschlagStatus = { art: 'offen' } | { art: 'laeuft' } | { art: 'bestaetigt' | 'verworfen' | 'fehler'; meldung: string };

interface Props {
    vorschlag: AssistentVorschlag;
    status: VorschlagStatus;
    onBestaetigen: () => void;
    onVerwerfen: () => void;
}

/**
 * Eine Änderung, die die KI vorschlägt. Sie geschieht erst, wenn der Benutzer „Ja, ausführen" klickt: Vorher steht hier genau, was passieren würde.
 */
export function KiAssistentVorschlag({ vorschlag, status, onBestaetigen, onVerwerfen }: Props) {
    const offen = status.art === 'offen' || status.art === 'laeuft';
    return (
        <div className="mt-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-slate-800" data-testid={`vorschlag-${vorschlag.id}`}>
            <p className="text-[11px] font-semibold uppercase tracking-wide text-amber-800">Vorschlag der KI</p>
            <p className="mt-1 max-h-72 overflow-y-auto whitespace-pre-wrap break-words text-sm" data-testid="vorschlag-text">{vorschlag.beschreibung}</p>
            {offen ? (
                <>
                    <p className="mt-1 text-xs text-slate-600">Es wird nichts geändert, bevor Sie bestätigen.</p>
                    <div className="mt-2 flex gap-2">
                        <button type="button" onClick={onBestaetigen} disabled={status.art === 'laeuft'}
                            className="inline-flex items-center gap-1.5 rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-rose-700 disabled:opacity-50">
                            {status.art === 'laeuft' ? <Loader2 className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Check className="h-3.5 w-3.5" aria-hidden="true" />}
                            Ja, ausführen
                        </button>
                        <button type="button" onClick={onVerwerfen} disabled={status.art === 'laeuft'}
                            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50">
                            <X className="h-3.5 w-3.5" aria-hidden="true" /> Verwerfen
                        </button>
                    </div>
                </>
            ) : (
                <p role="status" className={`mt-2 text-xs font-medium ${status.art === 'bestaetigt' ? 'text-emerald-700' : status.art === 'fehler' ? 'text-rose-700' : 'text-slate-600'}`}>
                    {status.meldung}
                </p>
            )}
        </div>
    );
}
