import { Check } from 'lucide-react';
import { cn } from '../../lib/utils';
import { Select } from '../../components/ui/select-custom';
import { ModulIcon } from './ModulIcon';
import {
    basisModule,
    gewaehltesBasis,
    passendeZusatzModule,
    schalteZusatz,
    setzeBauart,
    waehleBasis,
} from './modulAuswahl';
import type { ModulAuswahl, WartungModul } from './types';

interface ModulWahlProps {
    module: WartungModul[];
    auswahl: ModulAuswahl[];
    onChange: (auswahl: ModulAuswahl[]) => void;
    disabled?: boolean;
}

/** Auswahl der Bauart, wenn das Modul welche kennt. */
function BauartWahl({ modul, eintrag, onChange, disabled }: {
    modul: WartungModul;
    eintrag: ModulAuswahl;
    onChange: (schluessel: string | null) => void;
    disabled?: boolean;
}) {
    if (modul.untertypen.length === 0) return null;
    return (
        <div className="mt-3">
            <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">
                Bauart
            </label>
            <Select
                aria-label={`Bauart für ${modul.name}`}
                value={eintrag.untertypSchluessel ?? ''}
                onChange={(wert) => onChange(wert === '' ? null : wert)}
                options={modul.untertypen.map((u) => ({ value: u.schluessel, label: u.label }))}
                placeholder="Bauart wählen"
                disabled={disabled}
                error={eintrag.untertypSchluessel ? undefined : 'Bitte die Bauart wählen.'}
            />
        </div>
    );
}

/**
 * Wahl der Module für eine Tür oder ein Protokoll. Zuerst das Basis-Element (genau eines), dann die
 * Zusatzmodule, die dazu passen. So lässt sich keine Kombination wählen, die das Backend ablehnt.
 */
export function ModulWahl({ module, auswahl, onChange, disabled }: ModulWahlProps) {
    const basis = gewaehltesBasis(module, auswahl);
    const zusatz = passendeZusatzModule(module, basis);
    const basisEintrag = basis ? auswahl.find((a) => a.modulId === basis.id) : undefined;

    return (
        <div className="space-y-5">
            <fieldset>
                <legend className="text-sm font-semibold text-slate-700 mb-2">Was wird geprüft?</legend>
                <div role="radiogroup" aria-label="Basis-Element" className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {basisModule(module).map((m) => {
                        const gewaehlt = basis?.id === m.id;
                        return (
                            <button
                                key={m.id}
                                type="button"
                                role="radio"
                                aria-checked={gewaehlt}
                                disabled={disabled}
                                onClick={() => onChange(waehleBasis(module, auswahl, m.id))}
                                className={cn(
                                    'flex items-start gap-3 rounded-lg border p-3 text-left transition-colors min-h-[3.5rem]',
                                    'focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-50 disabled:cursor-not-allowed',
                                    gewaehlt ? 'border-rose-300 bg-rose-50' : 'border-slate-200 bg-white hover:bg-slate-50',
                                )}
                            >
                                <span
                                    className={cn(
                                        'flex h-9 w-9 shrink-0 items-center justify-center rounded-full',
                                        gewaehlt ? 'bg-rose-100 text-rose-600' : 'bg-slate-100 text-slate-500',
                                    )}
                                >
                                    <ModulIcon name={m.icon} />
                                </span>
                                <span className="min-w-0 flex-1">
                                    <span className="block text-sm font-semibold text-slate-900 break-words">{m.name}</span>
                                    {m.beschreibung && (
                                        <span className="mt-0.5 block text-xs text-slate-500 line-clamp-2">{m.beschreibung}</span>
                                    )}
                                </span>
                                {gewaehlt && <Check className="h-4 w-4 shrink-0 text-rose-600" aria-hidden="true" />}
                            </button>
                        );
                    })}
                </div>
                {basis && basisEintrag && (
                    <BauartWahl
                        modul={basis}
                        eintrag={basisEintrag}
                        disabled={disabled}
                        onChange={(s) => onChange(setzeBauart(auswahl, basis.id, s))}
                    />
                )}
            </fieldset>

            {zusatz.length > 0 && (
                <fieldset>
                    <legend className="text-sm font-semibold text-slate-700 mb-2">Zusätzlich vorhanden</legend>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                        {zusatz.map((m) => {
                            const eintrag = auswahl.find((a) => a.modulId === m.id);
                            const gewaehlt = eintrag !== undefined;
                            return (
                                <div
                                    key={m.id}
                                    className={cn(
                                        'rounded-lg border p-3',
                                        gewaehlt ? 'border-rose-300 bg-rose-50' : 'border-slate-200 bg-white',
                                    )}
                                >
                                    <button
                                        type="button"
                                        role="checkbox"
                                        aria-checked={gewaehlt}
                                        disabled={disabled}
                                        onClick={() => onChange(schalteZusatz(module, auswahl, m.id))}
                                        className="flex w-full items-start gap-3 text-left rounded focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-50 disabled:cursor-not-allowed"
                                    >
                                        <span
                                            className={cn(
                                                'flex h-9 w-9 shrink-0 items-center justify-center rounded-full',
                                                gewaehlt ? 'bg-rose-100 text-rose-600' : 'bg-slate-100 text-slate-500',
                                            )}
                                        >
                                            <ModulIcon name={m.icon} />
                                        </span>
                                        <span className="min-w-0 flex-1">
                                            <span className="block text-sm font-semibold text-slate-900 break-words">{m.name}</span>
                                            {m.beschreibung && (
                                                <span className="mt-0.5 block text-xs text-slate-500 line-clamp-2">{m.beschreibung}</span>
                                            )}
                                        </span>
                                        <span
                                            className={cn(
                                                'mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border',
                                                gewaehlt ? 'border-rose-600 bg-rose-600 text-white' : 'border-slate-300 bg-white',
                                            )}
                                            aria-hidden="true"
                                        >
                                            {gewaehlt && <Check className="h-3.5 w-3.5" />}
                                        </span>
                                    </button>
                                    {eintrag && (
                                        <BauartWahl
                                            modul={m}
                                            eintrag={eintrag}
                                            disabled={disabled}
                                            onChange={(s) => onChange(setzeBauart(auswahl, m.id, s))}
                                        />
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </fieldset>
            )}
        </div>
    );
}
