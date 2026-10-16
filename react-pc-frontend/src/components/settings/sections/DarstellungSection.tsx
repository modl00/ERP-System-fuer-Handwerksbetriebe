import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Check, Loader2, RotateCcw } from 'lucide-react';
import { Card } from '../../ui/card';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { cn } from '../../../lib/utils';
import { FARB_PRESETS, STANDARD_FARBE, kontrastZuWeiss, normalisiereFarbe } from '../../../features/darstellung/farbpalette';
import { ladeDarstellung, merkeFarbe, speichereFarbe, wendeFarbeAn } from '../../../features/darstellung/darstellung';

/** Programmfarbe der ganzen Firma wählen. Die Auswahl wirkt sofort als Vorschau, gilt aber erst nach „Speichern“. */
export function DarstellungSection() {
    const toast = useToast();
    /** Was auf dem Server steht ({@code null} = Standard-Rosé); {@code undefined} solange es geladen wird. */
    const [gespeichert, setGespeichert] = useState<string | null | undefined>(undefined);
    const [auswahl, setAuswahl] = useState<string>(STANDARD_FARBE);
    const [eingabe, setEingabe] = useState('');
    const [eingabeFehler, setEingabeFehler] = useState<string | null>(null);
    const [arbeitet, setArbeitet] = useState(false);
    const gespeichertRef = useRef<string | null>(null);

    useEffect(() => {
        let aktiv = true;
        ladeDarstellung().then(
            (d) => {
                if (!aktiv) return;
                gespeichertRef.current = d.farbe;
                setGespeichert(d.farbe);
                setAuswahl(d.farbe ?? STANDARD_FARBE);
            },
            (e) => {
                if (!aktiv) return;
                toast.error(e instanceof Error ? e.message : 'Die Programmfarbe konnte nicht geladen werden.');
                gespeichertRef.current = null;
                setGespeichert(null);
            },
        );
        return () => { aktiv = false; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Beim Verlassen der Seite ohne Speichern gilt wieder die gespeicherte Farbe
    useEffect(() => () => wendeFarbeAn(gespeichertRef.current), []);

    const vorschau = (farbe: string) => {
        setAuswahl(farbe);
        setEingabeFehler(null);
        wendeFarbeAn(farbe === STANDARD_FARBE ? null : farbe);
    };

    const eingabeUebernehmen = () => {
        const farbe = normalisiereFarbe(eingabe);
        if (farbe === null) {
            setEingabeFehler('Bitte eine Farbe wie #0f766e angeben (sechs Zeichen aus 0-9 und a-f).');
            return;
        }
        vorschau(farbe);
    };

    const speichern = async (farbe: string | null) => {
        setArbeitet(true);
        try {
            const neu = await speichereFarbe(farbe);
            gespeichertRef.current = neu.farbe;
            setGespeichert(neu.farbe);
            setAuswahl(neu.farbe ?? STANDARD_FARBE);
            wendeFarbeAn(neu.farbe);
            merkeFarbe(neu.farbe);
            toast.success(neu.farbe === null ? 'Das Standard-Rosé ist wiederhergestellt.' : 'Die Programmfarbe ist gespeichert und gilt für alle Benutzer.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Programmfarbe konnte nicht gespeichert werden.');
        } finally {
            setArbeitet(false);
        }
    };

    const geladen = gespeichert !== undefined;
    const aktuell = gespeichert ?? STANDARD_FARBE;
    const geaendert = geladen && auswahl !== aktuell;
    const kontrastSchwach = kontrastZuWeiss(auswahl) < 4.5;

    return (
        <Card className="p-4 sm:p-6">
            <h2 className="text-lg font-semibold text-slate-900">Programmfarbe</h2>
            <p className="mt-0.5 text-sm text-slate-500">
                Die Farbe für Schaltflächen, Reiter und Markierungen. Sie gilt für alle Benutzer. Rot für Fehler und Grün für Erfolg bleiben, wie sie sind.
            </p>

            <fieldset className="mt-5" disabled={!geladen || arbeitet}>
                <legend className="text-sm font-medium text-slate-700">Farbe wählen</legend>
                <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
                    {FARB_PRESETS.map((p) => {
                        const gewaehlt = auswahl === p.farbe;
                        return (
                            <button
                                key={p.farbe}
                                type="button"
                                aria-pressed={gewaehlt}
                                onClick={() => vorschau(p.farbe)}
                                className={cn(
                                    'flex items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60',
                                    gewaehlt ? 'border-slate-900 bg-slate-50 font-semibold text-slate-900' : 'border-slate-200 text-slate-700 hover:bg-slate-50',
                                )}
                            >
                                <span className="h-5 w-5 shrink-0 rounded-full border border-black/10" style={{ backgroundColor: p.farbe }} aria-hidden="true" />
                                <span className="min-w-0 flex-1 truncate">{p.name}</span>
                                {gewaehlt && <Check className="h-4 w-4 shrink-0" aria-hidden="true" />}
                            </button>
                        );
                    })}
                </div>

                <div className="mt-4 max-w-sm">
                    <label htmlFor="darstellung-farbe-eingabe" className="text-sm font-medium text-slate-700">Eigene Farbe</label>
                    <div className="mt-1 flex gap-2">
                        <input
                            id="darstellung-farbe-eingabe"
                            type="text"
                            inputMode="text"
                            autoComplete="off"
                            maxLength={7}
                            value={eingabe}
                            placeholder="#0f766e"
                            aria-invalid={eingabeFehler !== null}
                            aria-describedby="darstellung-farbe-hinweis"
                            onChange={(e) => setEingabe(e.target.value)}
                            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); eingabeUebernehmen(); } }}
                            className="min-w-0 flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                        <Button type="button" variant="outline" size="sm" onClick={eingabeUebernehmen}>Ausprobieren</Button>
                    </div>
                    <p id="darstellung-farbe-hinweis" className={cn('mt-1 text-xs', eingabeFehler ? 'text-red-600' : 'text-slate-500')} role={eingabeFehler ? 'alert' : undefined}>
                        {eingabeFehler ?? 'Sechs Zeichen mit Raute, zum Beispiel #0f766e. Dunklere Farben lesen sich mit weißer Schrift besser.'}
                    </p>
                </div>
            </fieldset>

            <div className="mt-5 rounded-lg border border-slate-200 bg-slate-50 p-4">
                <p className="text-sm font-medium text-slate-700">Vorschau</p>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                    <span className="rounded-md border border-rose-600 bg-rose-600 px-3 py-1.5 text-sm font-medium text-white">Speichern</span>
                    <span className="rounded-md border border-rose-300 px-3 py-1.5 text-sm font-medium text-rose-700">Abbrechen</span>
                    <span className="rounded-full bg-rose-100 px-2.5 py-1 text-xs font-semibold text-rose-700">Neu</span>
                    <span className="border-b-2 border-rose-600 px-1 pb-0.5 text-sm font-semibold text-rose-600">Aktiver Reiter</span>
                </div>
                {kontrastSchwach && (
                    <p className="mt-3 flex items-start gap-2 text-sm text-amber-700" role="status">
                        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                        Weiße Schrift auf dieser Farbe ist schwer zu lesen. Wähle eine dunklere Farbe.
                    </p>
                )}
            </div>

            <div className="mt-5 flex flex-wrap items-center gap-2">
                <Button type="button" size="sm" disabled={!geaendert || arbeitet} onClick={() => void speichern(auswahl === STANDARD_FARBE ? null : auswahl)}>
                    {arbeitet ? <Loader2 className="mr-1.5 h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Check className="mr-1.5 h-4 w-4" aria-hidden="true" />}
                    Speichern
                </Button>
                <Button type="button" variant="outline" size="sm" disabled={!geaendert || arbeitet} onClick={() => vorschau(aktuell)}>
                    Verwerfen
                </Button>
                <Button type="button" variant="ghost" size="sm" disabled={!geladen || arbeitet || (gespeichert ?? null) === null} onClick={() => void speichern(null)}>
                    <RotateCcw className="mr-1.5 h-4 w-4" aria-hidden="true" />
                    Standard-Rosé wiederherstellen
                </Button>
            </div>
        </Card>
    );
}
