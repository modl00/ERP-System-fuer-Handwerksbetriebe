import { useState } from 'react';
import { Check, Pencil, Trash2, X } from 'lucide-react';
import { cn } from '../../lib/utils';
import { Input } from '../../components/ui/input';
import { Select } from '../../components/ui/select-custom';
import { optionenAusText } from './optionen';
import type { ProtokollDatenfeld } from './types';

interface DatenfeldEingabeProps {
    feld: ProtokollDatenfeld;
    bearbeitbar: boolean;
    onSetzen: (feld: ProtokollDatenfeld, wert: string) => void;
    /** Ändert Bezeichnung (und bei einer Auswahl die Möglichkeiten) nur in diesem Feldeinsatz. Ohne diese Funktion gibt es keinen Stift. */
    onUmbenennen?: (feld: ProtokollDatenfeld, label: string, optionen: string[] | null) => Promise<void> | void;
    /** Entfernt das Feld aus diesem Feldeinsatz. Ohne diese Funktion gibt es keinen Papierkorb. */
    onLoeschen?: (feld: ProtokollDatenfeld) => void;
}

const ICON_KNOPF = 'inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-800 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-50';

/**
 * Ein Datenfeld der Datenaufnahme. Text wird beim Verlassen des Feldes gespeichert, Ja/Nein und Auswahl
 * sofort. Ein zweiter Klick auf die gewählte Ja/Nein-Schaltfläche leert das Feld wieder.
 */
export function DatenfeldEingabe({ feld, bearbeitbar, onSetzen, onUmbenennen, onLoeschen }: DatenfeldEingabeProps) {
    const [text, setText] = useState(feld.wert ?? '');
    const [umbenennen, setUmbenennen] = useState(false);
    const [neuerLabel, setNeuerLabel] = useState(feld.label);
    const [neueOptionen, setNeueOptionen] = useState(feld.optionen.join('\n'));
    const [speichert, setSpeichert] = useState(false);
    const [optionenFehler, setOptionenFehler] = useState<string | undefined>();
    // Neuer Wert vom Server: in den Entwurf übernehmen (beim Rendern statt in einem Effekt).
    const [gespeicherterWert, setGespeicherterWert] = useState(feld.wert);
    if (feld.wert !== gespeicherterWert) {
        setGespeicherterWert(feld.wert);
        setText(feld.wert ?? '');
    }

    const grund = bearbeitbar ? undefined : 'Das Protokoll ist abgeschlossen. Zum Ändern zuerst wieder öffnen.';
    const eingabeId = `datenfeld-${feld.id}`;
    const istAuswahl = feld.feldtyp === 'AUSWAHL';

    const beenden = () => {
        setUmbenennen(false);
        setNeuerLabel(feld.label);
        setNeueOptionen(feld.optionen.join('\n'));
        setOptionenFehler(undefined);
    };

    const speichereDefinition = async () => {
        const label = neuerLabel.trim();
        if (label === '' || !onUmbenennen) return;
        let optionen: string[] | null = null;
        if (istAuswahl) {
            optionen = optionenAusText(neueOptionen);
            if (optionen.length < 2) {
                setOptionenFehler('Bitte mindestens zwei Möglichkeiten eintragen, je eine pro Zeile.');
                return;
            }
        }
        const unveraendert = label === feld.label && (optionen === null || optionen.join('\n') === feld.optionen.join('\n'));
        if (unveraendert) {
            beenden();
            return;
        }
        setSpeichert(true);
        try {
            await onUmbenennen(feld, label, optionen);
            setUmbenennen(false);
            setOptionenFehler(undefined);
        } catch {
            // Die Meldung hat die Seite schon gezeigt; das Feld bleibt zum Bearbeiten offen
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <div className="min-w-0" data-testid={eingabeId}>
            {umbenennen ? (
                <div className="mb-2 flex flex-col gap-2">
                    <div className="flex items-center gap-1">
                        <input
                            type="text"
                            value={neuerLabel}
                            maxLength={200}
                            aria-label={`Datenfeld umbenennen: ${feld.label}`}
                            autoFocus
                            onChange={(e) => setNeuerLabel(e.target.value)}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter' && !istAuswahl) { e.preventDefault(); void speichereDefinition(); }
                                if (e.key === 'Escape') beenden();
                            }}
                            className="min-h-11 w-full min-w-0 flex-1 rounded-lg border border-slate-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                        <button type="button" className={ICON_KNOPF} onClick={() => void speichereDefinition()} disabled={speichert || neuerLabel.trim() === ''} aria-label="Änderung übernehmen">
                            <Check className="h-4 w-4" aria-hidden="true" />
                        </button>
                        <button type="button" className={ICON_KNOPF} onClick={beenden} disabled={speichert} aria-label="Umbenennen abbrechen">
                            <X className="h-4 w-4" aria-hidden="true" />
                        </button>
                    </div>
                    {istAuswahl && (
                        <div>
                            <label htmlFor={`${eingabeId}-optionen`} className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">Möglichkeiten (eine pro Zeile)</label>
                            <textarea
                                id={`${eingabeId}-optionen`}
                                value={neueOptionen}
                                rows={Math.min(8, Math.max(3, neueOptionen.split('\n').length))}
                                aria-invalid={optionenFehler ? true : undefined}
                                aria-describedby={optionenFehler ? `${eingabeId}-optionen-fehler` : undefined}
                                onChange={(e) => { setNeueOptionen(e.target.value); setOptionenFehler(undefined); }}
                                className="w-full rounded-lg border border-slate-200 p-3 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                            />
                            {optionenFehler && <p id={`${eingabeId}-optionen-fehler`} role="alert" className="mt-1 text-sm text-rose-700">{optionenFehler}</p>}
                            <p className="mt-1 text-xs text-slate-500">Ein Wert, der danach nicht mehr zur Auswahl steht, wird geleert.</p>
                        </div>
                    )}
                </div>
            ) : (
                <div className="mb-1 flex items-start justify-between gap-1">
                    <label htmlFor={eingabeId} className="block min-w-0 break-words pt-1.5 text-sm text-slate-800">{feld.label}</label>
                    {bearbeitbar && (onUmbenennen || onLoeschen) && (
                        <div className="-mt-1 flex shrink-0">
                            {onUmbenennen && (
                                <button type="button" className={ICON_KNOPF} onClick={() => { setNeuerLabel(feld.label); setNeueOptionen(feld.optionen.join('\n')); setUmbenennen(true); }} aria-label={`Datenfeld umbenennen: ${feld.label}`}>
                                    <Pencil className="h-4 w-4" aria-hidden="true" />
                                </button>
                            )}
                            {onLoeschen && (
                                <button type="button" className={cn(ICON_KNOPF, 'hover:bg-rose-50 hover:text-rose-700')} onClick={() => onLoeschen(feld)} aria-label={`Datenfeld löschen: ${feld.label}`}>
                                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                                </button>
                            )}
                        </div>
                    )}
                </div>
            )}

            {(feld.feldtyp === 'TEXT' || feld.feldtyp === 'JAHR') && (
                <Input
                    id={eingabeId}
                    value={text}
                    maxLength={feld.feldtyp === 'JAHR' ? 4 : 5000}
                    inputMode={feld.feldtyp === 'JAHR' ? 'numeric' : undefined}
                    placeholder={feld.feldtyp === 'JAHR' ? 'JJJJ' : undefined}
                    disabled={!bearbeitbar}
                    title={grund}
                    onChange={(e) => setText(e.target.value)}
                    onBlur={() => { if ((feld.wert ?? '') !== text) onSetzen(feld, text); }}
                    onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur(); }}
                />
            )}

            {feld.feldtyp === 'JA_NEIN' && (
                <div id={eingabeId} role="group" aria-label={feld.label} className="grid grid-cols-2 gap-2 max-w-xs" title={grund}>
                    {['Ja', 'Nein'].map((antwort) => {
                        const gewaehlt = feld.wert === antwort;
                        return (
                            <button
                                key={antwort}
                                type="button"
                                aria-pressed={gewaehlt}
                                disabled={!bearbeitbar}
                                onClick={() => onSetzen(feld, gewaehlt ? '' : antwort)}
                                className={cn(
                                    'min-h-11 rounded-lg border px-3 text-sm font-semibold transition-colors',
                                    'focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-60',
                                    gewaehlt ? 'border-rose-600 bg-rose-600 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
                                )}
                            >
                                {antwort}
                            </button>
                        );
                    })}
                </div>
            )}

            {feld.feldtyp === 'AUSWAHL' && (
                <Select
                    id={eingabeId}
                    aria-label={feld.label}
                    value={feld.wert ?? ''}
                    disabled={!bearbeitbar}
                    onChange={(wert) => onSetzen(feld, wert)}
                    options={[{ value: '', label: 'Nicht angegeben' }, ...feld.optionen.map((o) => ({ value: o, label: o }))]}
                />
            )}
        </div>
    );
}
