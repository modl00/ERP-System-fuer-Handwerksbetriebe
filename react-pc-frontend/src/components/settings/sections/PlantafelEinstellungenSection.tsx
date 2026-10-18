import { useEffect, useState } from 'react';
import { Loader2, Plus, Trash2 } from 'lucide-react';
import { Card } from '../../ui/card';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { ladePlanungEinstellungen, speicherePlanungEinstellungen, type PlanungEinstellungen } from '../../../features/plantafel/planungEinstellungenApi';

const WOCHENTAGE: { nr: number; name: string }[] = [
    { nr: 1, name: 'Montag' }, { nr: 2, name: 'Dienstag' }, { nr: 3, name: 'Mittwoch' }, { nr: 4, name: 'Donnerstag' },
    { nr: 5, name: 'Freitag' }, { nr: 6, name: 'Samstag' }, { nr: 7, name: 'Sonntag' },
];

/** Arbeitswoche, Betriebsurlaub und Ampel-Schwelle für die Plantafel. */
export function PlantafelEinstellungenSection() {
    const toast = useToast();
    const [werte, setWerte] = useState<PlanungEinstellungen | null>(null);
    const [arbeitet, setArbeitet] = useState(false);

    useEffect(() => {
        let aktiv = true;
        ladePlanungEinstellungen().then(
            (e) => { if (aktiv) setWerte(e); },
            (err) => { if (aktiv) toast.error(err instanceof Error ? err.message : 'Die Planungs-Einstellungen konnten nicht geladen werden.'); },
        );
        return () => { aktiv = false; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const speichern = async () => {
        if (!werte) return;
        setArbeitet(true);
        try {
            setWerte(await speicherePlanungEinstellungen(werte));
            toast.success('Die Planungs-Einstellungen sind gespeichert. Die Pläne rechnen sich neu, sobald ein Auftrag geändert wird.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Planungs-Einstellungen konnten nicht gespeichert werden.');
        } finally {
            setArbeitet(false);
        }
    };

    if (!werte) {
        return <Card className="p-4 sm:p-6"><p className="flex items-center gap-2 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />Wird geladen …</p></Card>;
    }

    const umschalten = (nr: number) => setWerte({ ...werte, arbeitstage: werte.arbeitstage.includes(nr) ? werte.arbeitstage.filter((n) => n !== nr) : [...werte.arbeitstage, nr].sort() });
    const urlaubAendern = (index: number, teil: Partial<PlanungEinstellungen['betriebsurlaub'][number]>) =>
        setWerte({ ...werte, betriebsurlaub: werte.betriebsurlaub.map((u, i) => (i === index ? { ...u, ...teil } : u)) });

    return (
        <Card className="p-4 sm:p-6">
            <h2 className="text-lg font-semibold text-slate-900">Plantafel</h2>
            <p className="mt-0.5 text-sm text-slate-500">Was die Plantafel bei der Terminrechnung als Arbeitstag zählt. Es gilt für den ganzen Betrieb.</p>

            <fieldset className="mt-5" disabled={arbeitet}>
                <legend className="text-sm font-medium text-slate-700">Arbeitswoche</legend>
                <div className="mt-2 flex flex-wrap gap-2">
                    {WOCHENTAGE.map((t) => (
                        <label key={t.nr} className="flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-700 has-[:checked]:border-rose-300 has-[:checked]:bg-rose-50">
                            <input type="checkbox" checked={werte.arbeitstage.includes(t.nr)} onChange={() => umschalten(t.nr)} className="accent-rose-600" />
                            {t.name}
                        </label>
                    ))}
                </div>
                <p className="mt-1 text-xs text-slate-500">Feiertage kommen dazu und zählen nie als Arbeitstag.</p>
            </fieldset>

            <div className="mt-5">
                <label htmlFor="ampel-schwelle" className="text-sm font-medium text-slate-700">Ampel „Knapp" ab</label>
                <div className="mt-1 flex items-center gap-2 text-sm text-slate-600">
                    <input id="ampel-schwelle" type="number" min={0} max={30} value={werte.ampelSchwelleTage} disabled={arbeitet}
                        onChange={(e) => setWerte({ ...werte, ampelSchwelleTage: Number(e.target.value) })}
                        className="w-20 rounded-lg border border-slate-300 px-2 py-1.5 focus:border-rose-500 focus:outline-none focus:ring-1 focus:ring-rose-500" />
                    Arbeitstagen Luft bis zum Zieltermin
                </div>
            </div>

            <fieldset className="mt-5" disabled={arbeitet}>
                <legend className="text-sm font-medium text-slate-700">Betriebsurlaub</legend>
                <p className="text-xs text-slate-500">In diesen Zeiträumen arbeitet niemand. Die Plantafel plant darüber hinweg.</p>
                <ul className="mt-2 space-y-2">
                    {werte.betriebsurlaub.map((u, i) => (
                        <li key={i} className="flex flex-wrap items-center gap-2">
                            <input type="date" aria-label={`Betriebsurlaub ${i + 1} von`} value={u.von} onChange={(e) => urlaubAendern(i, { von: e.target.value })} className="rounded-lg border border-slate-300 px-2 py-1.5 text-sm" />
                            <span className="text-sm text-slate-500">bis</span>
                            <input type="date" aria-label={`Betriebsurlaub ${i + 1} bis`} value={u.bis} onChange={(e) => urlaubAendern(i, { bis: e.target.value })} className="rounded-lg border border-slate-300 px-2 py-1.5 text-sm" />
                            <input type="text" aria-label={`Betriebsurlaub ${i + 1} Bezeichnung`} placeholder="Bezeichnung (zum Beispiel Sommerpause)" maxLength={80} value={u.bezeichnung}
                                onChange={(e) => urlaubAendern(i, { bezeichnung: e.target.value })} className="min-w-[12rem] flex-1 rounded-lg border border-slate-300 px-2 py-1.5 text-sm" />
                            <button type="button" aria-label={`Betriebsurlaub ${i + 1} entfernen`} onClick={() => setWerte({ ...werte, betriebsurlaub: werte.betriebsurlaub.filter((_, k) => k !== i) })}
                                className="rounded p-1.5 text-slate-500 hover:bg-slate-100 hover:text-rose-700"><Trash2 className="h-4 w-4" aria-hidden="true" /></button>
                        </li>
                    ))}
                </ul>
                <Button type="button" variant="outline" size="sm" className="mt-2"
                    onClick={() => { const heute = new Date().toISOString().slice(0, 10); setWerte({ ...werte, betriebsurlaub: [...werte.betriebsurlaub, { von: heute, bis: heute, bezeichnung: '' }] }); }}>
                    <Plus className="mr-1 h-4 w-4" aria-hidden="true" />Zeitraum hinzufügen
                </Button>
            </fieldset>

            <div className="mt-6 flex justify-end">
                <Button type="button" onClick={speichern} disabled={arbeitet}>{arbeitet ? 'Wird gespeichert …' : 'Speichern'}</Button>
            </div>
        </Card>
    );
}
