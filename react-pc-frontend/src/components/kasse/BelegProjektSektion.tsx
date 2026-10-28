import { useCallback, useEffect, useState } from 'react';
import { Briefcase, Loader2 } from 'lucide-react';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';
import { ProjectSelectModal } from '../ProjectSelectModal';
import { formatEuro } from './belegFormat';

interface Zuordnung {
    id: number;
    projektId?: number | null;
    projektName?: string | null;
    kostenstelleId?: number | null;
    kostenstelleName?: string | null;
    betrag?: number | null;
    prozentanteil?: number | null;
}

interface Props {
    /** Nummer der Eingangsrechnung zum Beleg. Ohne Eingangsrechnung gibt es noch nichts, dem ein Projekt zugeordnet werden kann. */
    eingangsrechnungId: number | null;
}

/**
 * „Für welches Projekt?" im Beleg-Fenster (#178/#243): dieselbe Zuordnung wie in den Bestellungen, damit die Kosten im Projekt (Material, Nachkalkulation) ankommen.
 * Hier lässt sich ein Projekt wählen, wenn die Rechnung noch keinem Ziel zugeordnet ist. Ist sie es schon, steht die Zuordnung da; aufgeteilt wird weiter in den
 * Bestellungen, damit hier keine bestehende Aufteilung überschrieben wird.
 */
export function BelegProjektSektion({ eingangsrechnungId }: Props) {
    const toast = useToast();
    const [zuordnungen, setZuordnungen] = useState<Zuordnung[] | null>(null);
    const [fehler, setFehler] = useState(false);
    const [waehlen, setWaehlen] = useState(false);
    const [speichert, setSpeichert] = useState(false);

    const laden = useCallback(async () => {
        if (eingangsrechnungId == null) return;
        try {
            const res = await fetch(`/api/bestellungen-uebersicht/zuordnungen/${eingangsrechnungId}`);
            if (!res.ok) throw new Error();
            const daten = await res.json();
            setZuordnungen(Array.isArray(daten) ? daten : []);
            setFehler(false);
        } catch {
            setFehler(true);
        }
    }, [eingangsrechnungId]);

    useEffect(() => { void laden(); }, [laden]);

    const zuordnen = async (projekt: { id: number; bauvorhaben: string }) => {
        setWaehlen(false);
        if (eingangsrechnungId == null) return;
        setSpeichert(true);
        try {
            const res = await fetch('/api/bestellungen-uebersicht/zuordnen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ geschaeftsdokumentId: eingangsrechnungId, projektAnteile: [{ projektId: projekt.id, prozentanteil: 100 }] }),
            });
            if (!res.ok) {
                const b = await res.json().catch(() => null);
                toast.error(b?.error ?? b?.message ?? 'Die Zuordnung zum Projekt konnte nicht gespeichert werden.');
                return;
            }
            toast.success(`Der Beleg gehört jetzt zum Projekt „${projekt.bauvorhaben}".`);
            await laden();
        } catch {
            toast.error('Die Zuordnung zum Projekt konnte nicht gespeichert werden (keine Verbindung).');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <section data-testid="beleg-projekt">
            <h3 className="text-base font-bold text-slate-900">Für welches Projekt?</h3>
            {eingangsrechnungId == null ? (
                <p className="mt-1 text-sm text-slate-600">Ein Projekt lässt sich wählen, sobald der Beleg als Rechnung mit Lieferant erfasst ist.</p>
            ) : fehler ? (
                <p className="mt-1 text-sm text-slate-600" role="status">Die Zuordnung konnte nicht geladen werden.</p>
            ) : zuordnungen === null ? (
                <p className="mt-1 flex items-center gap-2 text-sm text-slate-500"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …</p>
            ) : zuordnungen.length === 0 ? (
                <>
                    <p className="mt-1 text-sm text-slate-600">Die Kosten dieser Rechnung landen im gewählten Projekt (wie in den Bestellungen).</p>
                    <Button type="button" variant="outline" className="mt-3 gap-2" disabled={speichert} onClick={() => setWaehlen(true)}>
                        {speichert ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Briefcase className="h-4 w-4" aria-hidden="true" />} Projekt wählen …
                    </Button>
                </>
            ) : (
                <>
                    <ul className="mt-2 space-y-1 text-sm text-slate-800">
                        {zuordnungen.map(z => (
                            <li key={z.id} className="flex flex-wrap items-center gap-x-2">
                                <Briefcase className="h-3.5 w-3.5 text-slate-400" aria-hidden="true" />
                                <span className="font-medium">{z.projektName ?? z.kostenstelleName ?? 'Ziel'}</span>
                                <span className="text-slate-500">
                                    {z.prozentanteil != null ? `${z.prozentanteil} %` : ''}{z.prozentanteil != null && z.betrag != null ? ' · ' : ''}{z.betrag != null ? `${formatEuro(z.betrag)} €` : ''}
                                </span>
                            </li>
                        ))}
                    </ul>
                    <p className="mt-2 text-sm text-slate-600">Zum Aufteilen oder Ändern: Bestellungen → Rechnung → Projekten zuordnen.</p>
                </>
            )}
            {waehlen && (
                // Über dem Beleg-Fenster: Die Auswahl hat sonst dieselbe Ebene und läge verdeckt dahinter
                <div className="relative z-[60]">
                    <ProjectSelectModal onSelect={p => void zuordnen(p)} onClose={() => setWaehlen(false)} />
                </div>
            )}
        </section>
    );
}
