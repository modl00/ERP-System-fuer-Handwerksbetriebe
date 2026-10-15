import { ChevronDown, ChevronUp, Trash2 } from 'lucide-react';
import { cn } from '../../../lib/utils';

interface Props {
    /** Wofür die Knöpfe sind, für die Beschriftung für Screenreader: "Prüfpunkt 3". */
    bezeichnung: string;
    onHoch?: () => void;
    onRunter?: () => void;
    onEntfernen?: () => void;
    entfernenGesperrtGrund?: string;
    className?: string;
}

const knopf = 'inline-flex h-9 w-9 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-800 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent';

/** Nach oben, nach unten, entfernen: ohne Ziehen, damit es auch mit Tastatur und am Handy geht. */
export function ZeilenKnoepfe({ bezeichnung, onHoch, onRunter, onEntfernen, entfernenGesperrtGrund, className }: Props) {
    return (
        <div className={cn('flex shrink-0 items-center', className)}>
            <button type="button" className={knopf} onClick={onHoch} disabled={!onHoch} aria-label={`${bezeichnung} nach oben`}>
                <ChevronUp className="h-4 w-4" aria-hidden="true" />
            </button>
            <button type="button" className={knopf} onClick={onRunter} disabled={!onRunter} aria-label={`${bezeichnung} nach unten`}>
                <ChevronDown className="h-4 w-4" aria-hidden="true" />
            </button>
            {onEntfernen && (
                <button type="button" className={cn(knopf, 'hover:bg-rose-50 hover:text-rose-700')} onClick={onEntfernen}
                    disabled={entfernenGesperrtGrund !== undefined} title={entfernenGesperrtGrund} aria-label={`${bezeichnung} entfernen`}>
                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                </button>
            )}
        </div>
    );
}
