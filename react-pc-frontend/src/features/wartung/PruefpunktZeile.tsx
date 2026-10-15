import { useId, useState } from 'react';
import { Check, History, MessageSquareText, Pencil, Trash2, X } from 'lucide-react';
import { cn } from '../../lib/utils';
import { ERGEBNIS_ANZEIGE } from './format';
import type { ProtokollPruefpunkt, WartungErgebnis } from './types';

interface PruefpunktZeileProps {
    punkt: ProtokollPruefpunkt;
    bearbeitbar: boolean;
    onBewerten: (punkt: ProtokollPruefpunkt, ergebnis: WartungErgebnis | null, bemerkung: string) => void;
    /** Ändert den Wortlaut des Prüfpunkts (nur in diesem Feldeinsatz). Ohne diese Funktion gibt es keinen Stift. */
    onTextAendern?: (punkt: ProtokollPruefpunkt, label: string) => Promise<void> | void;
    /** Entfernt den Prüfpunkt aus diesem Feldeinsatz. Ohne diese Funktion gibt es keinen Papierkorb. */
    onLoeschen?: (punkt: ProtokollPruefpunkt) => void;
}

const REIHENFOLGE: WartungErgebnis[] = ['IO', 'NIO', 'NA'];

const AKTIV_KLASSE: Record<WartungErgebnis, string> = {
    IO: 'bg-emerald-600 border-emerald-600 text-white',
    NIO: 'bg-rose-600 border-rose-600 text-white',
    NA: 'bg-slate-600 border-slate-600 text-white',
};

const GRUND_KLASSE = 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50';

const ICON_KNOPF = 'inline-flex h-11 w-11 items-center justify-center rounded-lg text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-800 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-50';

/**
 * Ein Prüfpunkt der Checkliste: drei große Schaltflächen (i.O., n.i.O., n.a.), die auch mit Handschuhen
 * und auf dem Handy gut zu treffen sind. Ein zweiter Klick auf die gewählte Schaltfläche nimmt die
 * Bewertung zurück. Zu jedem Ergebnis gibt es ein optionales Hinweisfeld: bei i.O. eine Bemerkung, bei n.i.O. die
 * Fehlerbeschreibung, bei n.a. eine Begründung. Es klappt auf Wunsch auf, bei n.i.O. und bei vorhandenem Text von selbst.
 */
export function PruefpunktZeile({ punkt, bearbeitbar, onBewerten, onTextAendern, onLoeschen }: PruefpunktZeileProps) {
    const feldId = useId();
    const [bemerkung, setBemerkung] = useState(punkt.bemerkung ?? '');
    // Kommt vom Server ein neuer Wert, übernehmen wir ihn in den Entwurf. Das passiert beim Rendern statt in
    // einem Effekt, damit es keinen zweiten Durchlauf mit dem alten Wert gibt.
    const [gespeicherteBemerkung, setGespeicherteBemerkung] = useState(punkt.bemerkung);
    if (punkt.bemerkung !== gespeicherteBemerkung) {
        setGespeicherteBemerkung(punkt.bemerkung);
        setBemerkung(punkt.bemerkung ?? '');
    }
    const [hinweisOffen, setHinweisOffen] = useState(false);
    const [textAendern, setTextAendern] = useState(false);
    const [neuerText, setNeuerText] = useState(punkt.label);
    const [textSpeichert, setTextSpeichert] = useState(false);

    const grund = bearbeitbar ? undefined : 'Der Feldeinsatz ist abgeschlossen. Zum Ändern zuerst wieder öffnen.';
    const hinweisSichtbar = hinweisOffen || punkt.ergebnis === 'NIO' || bemerkung.trim() !== '';
    const hatHinweis = bemerkung.trim() !== '';

    const waehle = (ergebnis: WartungErgebnis) => {
        if (!bearbeitbar) return;
        onBewerten(punkt, punkt.ergebnis === ergebnis ? null : ergebnis, bemerkung);
    };

    const speichereBemerkung = () => {
        if (!bearbeitbar || (punkt.bemerkung ?? '') === bemerkung) return;
        onBewerten(punkt, punkt.ergebnis, bemerkung);
    };

    const speichereText = async () => {
        const text = neuerText.trim();
        if (text === '' || !onTextAendern) return;
        if (text === punkt.label) {
            setTextAendern(false);
            return;
        }
        setTextSpeichert(true);
        try {
            await onTextAendern(punkt, text);
            setTextAendern(false);
        } catch {
            // Die Meldung hat die Seite schon gezeigt; das Feld bleibt zum Bearbeiten offen
        } finally {
            setTextSpeichert(false);
        }
    };

    return (
        <li
            data-testid={`pruefpunkt-${punkt.id}`}
            className={cn(
                'rounded-lg border bg-white p-3 sm:p-4',
                punkt.ergebnis === 'NIO' ? 'border-rose-200' : 'border-slate-200',
            )}
        >
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0 flex-1">
                    {textAendern ? (
                        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                            <input
                                type="text"
                                value={neuerText}
                                maxLength={500}
                                aria-label={`Prüfpunkt umformulieren: ${punkt.label}`}
                                autoFocus
                                onChange={(e) => setNeuerText(e.target.value)}
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter') { e.preventDefault(); void speichereText(); }
                                    if (e.key === 'Escape') { setTextAendern(false); setNeuerText(punkt.label); }
                                }}
                                className="min-h-11 w-full min-w-0 flex-1 rounded-lg border border-slate-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                            />
                            <div className="flex gap-1">
                                <button type="button" className={ICON_KNOPF} onClick={() => void speichereText()} disabled={textSpeichert || neuerText.trim() === ''} aria-label="Neuen Wortlaut übernehmen">
                                    <Check className="h-4 w-4" aria-hidden="true" />
                                </button>
                                <button type="button" className={ICON_KNOPF} onClick={() => { setTextAendern(false); setNeuerText(punkt.label); }} disabled={textSpeichert} aria-label="Umformulieren abbrechen">
                                    <X className="h-4 w-4" aria-hidden="true" />
                                </button>
                            </div>
                        </div>
                    ) : (
                        <p className="text-sm font-medium text-slate-900 break-words">{punkt.label}</p>
                    )}
                    {punkt.vorherigesErgebnis && (
                        <p className="mt-1 inline-flex items-center gap-1 text-xs text-slate-500">
                            <History className="h-3.5 w-3.5" aria-hidden="true" />
                            Letztes Mal: <span className="font-semibold">{ERGEBNIS_ANZEIGE[punkt.vorherigesErgebnis].kurz}</span>
                        </p>
                    )}
                </div>
                <div role="group" aria-label={`Bewertung: ${punkt.label}`} className="grid grid-cols-3 gap-2 sm:w-72 shrink-0" title={grund}>
                    {REIHENFOLGE.map((ergebnis) => {
                        const gewaehlt = punkt.ergebnis === ergebnis;
                        return (
                            <button
                                key={ergebnis}
                                type="button"
                                aria-pressed={gewaehlt}
                                aria-label={`${ERGEBNIS_ANZEIGE[ergebnis].lang}: ${punkt.label}`}
                                disabled={!bearbeitbar}
                                onClick={() => waehle(ergebnis)}
                                className={cn(
                                    'min-h-11 rounded-lg border px-2 text-sm font-semibold transition-colors',
                                    'focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-60',
                                    gewaehlt ? AKTIV_KLASSE[ergebnis] : GRUND_KLASSE,
                                )}
                            >
                                {ERGEBNIS_ANZEIGE[ergebnis].kurz}
                            </button>
                        );
                    })}
                </div>
            </div>

            <div className="mt-2 flex flex-wrap items-center gap-1">
                <button
                    type="button"
                    aria-expanded={hinweisSichtbar}
                    aria-controls={`${feldId}-hinweis`}
                    // Bei n.i.O. oder vorhandenem Text bleibt das Feld offen: Zuklappen würde Eingegebenes verstecken
                    disabled={punkt.ergebnis === 'NIO' || hatHinweis}
                    onClick={() => setHinweisOffen((v) => !v)}
                    className={cn(
                        'inline-flex min-h-11 items-center gap-1.5 rounded-lg px-2 text-sm transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500',
                        hatHinweis ? 'font-medium text-slate-700' : 'text-slate-500 hover:bg-slate-100 hover:text-slate-800',
                        'disabled:cursor-default disabled:hover:bg-transparent',
                    )}
                >
                    <MessageSquareText className="h-4 w-4" aria-hidden="true" />
                    {hatHinweis || hinweisSichtbar ? 'Hinweis' : 'Hinweis schreiben'}
                </button>
                {bearbeitbar && onTextAendern && !textAendern && (
                    <button type="button" className={ICON_KNOPF} onClick={() => { setNeuerText(punkt.label); setTextAendern(true); }} aria-label={`Prüfpunkt umformulieren: ${punkt.label}`}>
                        <Pencil className="h-4 w-4" aria-hidden="true" />
                    </button>
                )}
                {bearbeitbar && onLoeschen && (
                    <button type="button" className={cn(ICON_KNOPF, 'hover:bg-rose-50 hover:text-rose-700')} onClick={() => onLoeschen(punkt)} aria-label={`Prüfpunkt löschen: ${punkt.label}`}>
                        <Trash2 className="h-4 w-4" aria-hidden="true" />
                    </button>
                )}
            </div>

            {hinweisSichtbar && (
                <div id={`${feldId}-hinweis`} className="mt-1">
                    <label htmlFor={`bemerkung-${punkt.id}`} className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">
                        {punkt.ergebnis === 'NIO' ? 'Was ist nicht in Ordnung?' : punkt.ergebnis === 'NA' ? 'Begründung (optional)' : 'Hinweis / Bemerkung (optional)'}
                    </label>
                    <textarea
                        id={`bemerkung-${punkt.id}`}
                        value={bemerkung}
                        maxLength={10000}
                        disabled={!bearbeitbar}
                        rows={2}
                        placeholder={punkt.ergebnis === 'NIO' ? 'z. B. Dichtung gerissen, Schließer zu schwach eingestellt' : punkt.ergebnis === 'NA' ? 'z. B. Gerät nicht vorhanden' : 'z. B. Wert notiert, Besonderheit'}
                        onChange={(e) => setBemerkung(e.target.value)}
                        onBlur={speichereBemerkung}
                        className="w-full rounded-lg border border-slate-200 p-3 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:bg-slate-50"
                    />
                </div>
            )}
        </li>
    );
}
