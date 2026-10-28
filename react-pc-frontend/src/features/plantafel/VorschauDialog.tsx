import { useCallback, useRef, useState, type ReactNode } from 'react';
import { ArrowRight, AlertTriangle } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { ladeVorschau, type Ampel, type AuftragAenderung, type Probe, type Vorschau } from './plantafelApi';
import { anzeigeKurz } from './zeitachse';

const AMPEL_TEXT: Record<Ampel, string> = { OK: 'im Plan', KNAPP: 'knapp', VERSPAETET: 'verspätet', OHNE_ZIEL: 'ohne Zieltermin' };
const MAX_VORGAENGE = 8;

/** „+10 Tage", „−2 Tage", „gleich". */
export function tageText(tage: number | null): string {
    if (tage == null) return 'neu';
    if (tage === 0) return 'gleich';
    return `${tage > 0 ? '+' : '−'}${Math.abs(tage)} ${Math.abs(tage) === 1 ? 'Tag' : 'Tage'}`;
}

/** Eine Zeile der Vorschau: „Plantermin rückt vom 20.11.26 auf den 04.12.26 (+14 Tage)". */
export function auftragZeile(a: AuftragAenderung): string {
    const liefer = a.altLiefertermin
        ? (a.altLiefertermin === a.neuLiefertermin ? `Plantermin bleibt am ${anzeigeKurz(a.neuLiefertermin)}` : `Plantermin rückt vom ${anzeigeKurz(a.altLiefertermin)} auf den ${anzeigeKurz(a.neuLiefertermin)} (${tageText(a.tage)})`)
        : `Plantermin ${anzeigeKurz(a.neuLiefertermin)}`;
    const ampel = a.altAmpel !== a.neuAmpel || a.neuAmpel === 'VERSPAETET'
        ? `, ${AMPEL_TEXT[a.neuAmpel]}${a.neuAmpel === 'VERSPAETET' && a.neuVerspaetungTage ? ` +${a.neuVerspaetungTage} Arbeitstage` : ''}`
        : '';
    return `${liefer}${ampel}`;
}

/** Ohne Änderung der Lieferzeit und ohne Warnung gibt es nichts zu fragen. */
export function braucht(v: Vorschau): boolean {
    return v.warnungen.length > 0 || v.auftraege.some(a => a.tage !== 0 || a.altAmpel !== a.neuAmpel);
}

interface Props { offen: boolean; titel: string; vorschau: Vorschau | null; onJa: () => void; onNein: () => void }

/** Zeigt, was sich durch eine Änderung verschiebt, bevor sie gespeichert wird: Lieferzeiten, Ampel, betroffene Vorgänge. */
export function VorschauDialog({ offen, titel, vorschau, onJa, onNein }: Props) {
    const weitere = vorschau ? Math.max(0, vorschau.vorgaenge.length - MAX_VORGAENGE) : 0;
    return (
        <Dialog open={offen} onOpenChange={o => { if (!o) onNein(); }} className="w-full max-w-xl max-h-[90vh]" aria-labelledby="vorschau-titel">
            <DialogContent>
                <DialogHeader>
                    <DialogTitle id="vorschau-titel">Das ändert sich</DialogTitle>
                    <DialogDescription>{titel}</DialogDescription>
                </DialogHeader>
                {vorschau && (
                    <div className="min-h-0 flex-1 space-y-3 overflow-y-auto py-1 text-sm" data-testid="vorschau">
                        {vorschau.warnungen.map(w => (
                            <p key={w} role="alert" className="flex items-start gap-2 rounded-lg bg-amber-50 p-3 text-amber-900"><AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />{w}</p>
                        ))}
                        {vorschau.auftraege.length === 0 && <p className="text-slate-600">Kein Auftrag verschiebt sich.</p>}
                        <ul className="space-y-2">
                            {vorschau.auftraege.map(a => (
                                <li key={a.projektId} className="rounded-lg border border-slate-200 p-3" data-testid={`vorschau-auftrag-${a.projektId}`}>
                                    <p className="font-medium text-slate-900">{a.bezeichnung}{a.eigener ? '' : ' (anderer Auftrag)'}</p>
                                    <p className="text-slate-700">{auftragZeile(a)}</p>
                                </li>
                            ))}
                        </ul>
                        {vorschau.vorgaenge.length > 0 && (
                            <div>
                                <p className="mb-1 font-medium text-slate-900">Betroffene Vorgänge</p>
                                <ul className="space-y-1 text-xs text-slate-700">
                                    {vorschau.vorgaenge.slice(0, MAX_VORGAENGE).map(v => (
                                        <li key={v.vorgangId} className="flex flex-wrap items-center gap-1">
                                            <span className="font-medium">{v.bezeichnung}</span>
                                            <span className="text-slate-500">({v.auftrag})</span>
                                            <span>{v.altStart ? `${anzeigeKurz(v.altStart)} bis ${anzeigeKurz(v.altEnde)}` : 'neu'}</span>
                                            <ArrowRight className="h-3 w-3" aria-hidden="true" />
                                            <span>{anzeigeKurz(v.neuStart)} bis {anzeigeKurz(v.neuEnde)}</span>
                                        </li>
                                    ))}
                                </ul>
                                {weitere > 0 && <p className="mt-1 text-xs text-slate-500">… und {weitere} weitere</p>}
                            </div>
                        )}
                    </div>
                )}
                <DialogFooter>
                    <Button variant="outline" onClick={onNein}>Abbrechen</Button>
                    <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={onJa}>Übernehmen</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

interface Offen { titel: string; vorschau: Vorschau; aufloesen: (ja: boolean) => void }

/**
 * Fragt vor einer Änderung nach: Das Programm rechnet sie durch und zeigt, was sich verschiebt. Ändert sich nichts Wesentliches (keine neue Lieferzeit, keine Warnung),
 * geht es ohne Rückfrage weiter; ist die Vorschau nicht erreichbar, auch (gespeichert wird dann trotzdem erst durch den Server geprüft).
 * Der Aufrufer muss {@code dialog} irgendwo darstellen.
 */
export function useVorschauFrage(): { frage: (vorgangId: number, probe: Probe, titel: string) => Promise<boolean>; dialog: ReactNode } {
    const [offen, setOffen] = useState<Offen | null>(null);
    const lauf = useRef(0);
    const frage = useCallback(async (vorgangId: number, probe: Probe, titel: string): Promise<boolean> => {
        const mein = ++lauf.current;
        let vorschau: Vorschau;
        try {
            vorschau = await ladeVorschau(vorgangId, probe);
        } catch {
            return true; // keine Vorschau: nicht im Weg stehen
        }
        if (!vorschau || !Array.isArray(vorschau.auftraege) || !braucht(vorschau) || mein !== lauf.current) return true;
        return new Promise<boolean>(aufloesen => setOffen({ titel, vorschau, aufloesen }));
    }, []);
    const schliesse = (ja: boolean) => {
        offen?.aufloesen(ja);
        setOffen(null);
    };
    const dialog = <VorschauDialog offen={offen != null} titel={offen?.titel ?? ''} vorschau={offen?.vorschau ?? null} onJa={() => schliesse(true)} onNein={() => schliesse(false)} />;
    return { frage, dialog };
}
