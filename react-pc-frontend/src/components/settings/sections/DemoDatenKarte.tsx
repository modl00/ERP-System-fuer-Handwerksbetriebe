import { useEffect, useState } from 'react';
import { FlaskConical, Loader2 } from 'lucide-react';
import { Card } from '../../ui/card';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { useConfirm } from '../../ui/confirm-dialog';
import { entferneDemoDaten, ladeDemoStatus, type DemoEntfernt } from '../../../features/demo/demoApi';

/** „3 Mitarbeiter, 1 Kunde …": nur nennen, was entfernt wurde. */
export function entferntText(e: DemoEntfernt): string {
    const teile: string[] = [];
    const nenne = (n: number, eins: string, viele: string) => { if (n > 0) teile.push(`${n} ${n === 1 ? eins : viele}`); };
    nenne(e.mitarbeiter, 'Mitarbeiter', 'Mitarbeiter');
    nenne(e.kunden, 'Kunde', 'Kunden');
    nenne(e.lieferanten, 'Lieferant', 'Lieferanten');
    nenne(e.leistungen, 'Leistung', 'Leistungen');
    nenne(e.auftraege, 'Auftrag', 'Aufträge');
    nenne(e.termine, 'Termin', 'Termine');
    return teile.length === 0 ? 'Es gab nichts zu entfernen.' : `Entfernt: ${teile.join(', ')}.`;
}

/**
 * Die früheren Demo-Daten (erfundene Mitarbeiter, Kunden, Lieferanten, Leistungen, Aufträge, Termine und Abwesenheiten) wieder entfernen. Entfernt wird nur, was die Demo-Daten selbst
 * angelegt haben; echte Daten bleiben. Als Ausgangspunkt für eigene Leistungen dienen die fertigen Metallbau-Leistungen (Leistungen → Vorlagen übernehmen).
 */
export function DemoDatenKarte() {
    const toast = useToast();
    const confirm = useConfirm();
    const [vorhanden, setVorhanden] = useState<boolean | null>(null);
    const [arbeitet, setArbeitet] = useState(false);
    const [ergebnis, setErgebnis] = useState<DemoEntfernt | null>(null);

    useEffect(() => {
        let aktiv = true;
        ladeDemoStatus().then(
            v => { if (aktiv) setVorhanden(v); },
            e => { if (aktiv) toast.error(e instanceof Error ? e.message : 'Der Stand der Demo-Daten konnte nicht geladen werden.'); },
        );
        return () => { aktiv = false; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const entfernen = async () => {
        const ja = await confirm({
            title: 'Demo-Daten entfernen?',
            message: 'Das entfernt die erfundenen Demo-Daten (Mitarbeiter mit „@demo.invalid", Kunden „DEMO-K…", Aufträge „DEMO-…", Leistungen „(Demo)", die Demo-Lieferanten und Demo-Termine) mit allem, was daran hängt, zum Beispiel ihrem Plan. Echte Daten bleiben. Das lässt sich nicht rückgängig machen.',
            confirmLabel: 'Demo-Daten entfernen',
            variant: 'danger',
        });
        if (!ja) return;
        setArbeitet(true);
        try {
            const e = await entferneDemoDaten();
            setErgebnis(e);
            setVorhanden(false);
            toast.success(entferntText(e));
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Demo-Daten konnten nicht entfernt werden.');
        } finally {
            setArbeitet(false);
        }
    };

    return (
        <Card className="mt-6 p-5">
            <h3 className="flex items-center gap-2 text-base font-semibold text-slate-900"><FlaskConical className="h-4 w-4 text-rose-600" aria-hidden="true" />Demo-Daten entfernen</h3>
            <p className="mt-1 text-sm text-slate-600">
                Die früheren Demo-Daten (erfundene Mitarbeiter, Kunden, Lieferanten, Leistungen, Aufträge mit Plan, Termine, Urlaub und Krankmeldungen) lassen sich hier wieder entfernen. Es wird nur entfernt,
                was die Demo-Daten selbst angelegt haben.
            </p>
            <p className="mt-2 text-sm text-slate-600">
                Als Ausgangspunkt gibt es jetzt <b>fertige Leistungen für Metallbau</b> mit erfundenen Zeiten, vollständig verschachtelt mit Artikeln, Arbeitsgängen, Maschinen und Fremdleistungen:
                unter <b>Leistungen → Vorlagen übernehmen</b>.
            </p>
            {vorhanden === false && !ergebnis && <p role="status" className="mt-3 text-sm font-medium text-emerald-700" data-testid="keine-demo">Es sind keine Demo-Daten vorhanden.</p>}
            {ergebnis && <p role="status" className="mt-3 text-sm font-medium text-emerald-700" data-testid="demo-entfernt">{entferntText(ergebnis)}</p>}
            {vorhanden && (
                <div className="mt-4">
                    <Button onClick={() => void entfernen()} disabled={arbeitet} className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700">
                        {arbeitet && <Loader2 className="mr-2 h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />}
                        Demo-Daten entfernen
                    </Button>
                </div>
            )}
        </Card>
    );
}
