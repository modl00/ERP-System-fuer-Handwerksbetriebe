import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Card } from '../../ui/card';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { ladePersonen, ladeRhythmus, speichereRhythmus, type Person, type Rhythmus } from '../../../features/plantafel/plantafelApi';

const WOCHENTAGE: { nr: number; kurz: string; name: string }[] = [
    { nr: 1, kurz: 'Mo', name: 'Montag' }, { nr: 2, kurz: 'Di', name: 'Dienstag' }, { nr: 3, kurz: 'Mi', name: 'Mittwoch' }, { nr: 4, kurz: 'Do', name: 'Donnerstag' },
    { nr: 5, kurz: 'Fr', name: 'Freitag' }, { nr: 6, kurz: 'Sa', name: 'Samstag' }, { nr: 7, kurz: 'So', name: 'Sonntag' },
];

const alsText = (h: number) => String(h).replace('.', ',');

/** Arbeitstag in Stunden (Ersatz ohne Zeitkonto) und Bürotage je Mitarbeiter: An einem Bürotag hat die Person keine Stunden für Fertigung und Montage. */
export function RhythmusKarte() {
    const toast = useToast();
    const [stunden, setStunden] = useState('8');
    const [buerotage, setBuerotage] = useState<Record<string, number[]>>({});
    const [personen, setPersonen] = useState<Person[]>([]);
    const [geladen, setGeladen] = useState(false);
    const [fehler, setFehler] = useState('');
    const [arbeitet, setArbeitet] = useState(false);

    useEffect(() => {
        let aktiv = true;
        Promise.all([ladeRhythmus(), ladePersonen()]).then(([r, p]) => {
            if (!aktiv) return;
            setStunden(alsText(Number.isFinite(r?.arbeitstagStunden) ? r.arbeitstagStunden : 8));
            setBuerotage(r?.buerotage && typeof r.buerotage === 'object' ? r.buerotage : {});
            setPersonen(Array.isArray(p) ? p : []);
            setGeladen(true);
        }, (e) => { if (aktiv) setFehler(e instanceof Error ? e.message : 'Der Wochenrhythmus konnte nicht geladen werden.'); });
        return () => { aktiv = false; };
    }, []);

    if (fehler) return <Card className="mt-4 p-4 sm:p-6"><p role="alert" className="text-sm text-rose-800">{fehler}</p></Card>;
    if (!geladen) return <Card className="mt-4 p-4 sm:p-6"><p className="flex items-center gap-2 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />Wird geladen …</p></Card>;

    const umschalten = (id: number, tag: number) => setBuerotage((alt) => {
        const jetzt = alt[String(id)] ?? [];
        const neu = jetzt.includes(tag) ? jetzt.filter((n) => n !== tag) : [...jetzt, tag].sort();
        return { ...alt, [String(id)]: neu };
    });

    const speichern = async () => {
        const h = Number(stunden.trim().replace(',', '.'));
        if (!Number.isFinite(h) || h <= 0 || h > 24) {
            toast.error('Der Arbeitstag muss zwischen mehr als 0 und 24 Stunden lang sein, zum Beispiel 8 oder 7,5.');
            return;
        }
        setArbeitet(true);
        try {
            const r: Rhythmus = await speichereRhythmus({ arbeitstagStunden: h, buerotage });
            setStunden(alsText(r.arbeitstagStunden));
            setBuerotage(r.buerotage);
            toast.success('Arbeitstag und Wochenrhythmus sind gespeichert. Die Plantafel hat sich neu gerechnet.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Wochenrhythmus konnte nicht gespeichert werden.');
        } finally {
            setArbeitet(false);
        }
    };

    return (
        <Card className="mt-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold text-slate-900">Arbeitstag und Bürotage</h2>
            <p className="mt-0.5 text-sm text-slate-500">Damit rechnet die Plantafel, wie viele Stunden jemand am Tag für Aufträge hat.</p>

            <div className="mt-4">
                <label htmlFor="arbeitstag-stunden" className="text-sm font-medium text-slate-700">Standard-Arbeitstag</label>
                <div className="mt-1 flex items-center gap-2 text-sm text-slate-600">
                    <input id="arbeitstag-stunden" inputMode="decimal" value={stunden} disabled={arbeitet} onChange={(e) => setStunden(e.target.value)}
                        className="w-20 rounded-lg border border-slate-300 px-2 py-1.5 text-right focus:border-rose-500 focus:outline-none focus:ring-1 focus:ring-rose-500" />
                    Stunden
                </div>
                <p className="mt-1 text-xs text-slate-500">Gilt für Mitarbeiter ohne Zeitkonto und für Vorgänge, bei denen noch niemand zugewiesen ist. Wer ein Zeitkonto hat, arbeitet nach seinen Sollstunden.</p>
            </div>

            <fieldset className="mt-5" disabled={arbeitet}>
                <legend className="text-sm font-medium text-slate-700">Bürotage</legend>
                <p className="text-xs text-slate-500">An einem Bürotag hat die Person keine Stunden für Fertigung und Montage. Büroarbeit (zum Beispiel Planung) zählt trotzdem.</p>
                {personen.length === 0 ? <p className="mt-2 text-sm text-slate-500">Es gibt noch keine Mitarbeiter.</p> : (
                    <ul className="mt-2 divide-y divide-slate-100 rounded-lg border border-slate-200">
                        {personen.map((p) => (
                            <li key={p.id} className="flex flex-wrap items-center gap-2 px-3 py-2">
                                <span className="min-w-40 flex-1 text-sm text-slate-800">{p.name}</span>
                                <div className="flex flex-wrap gap-1.5">
                                    {WOCHENTAGE.slice(0, 5).map((t) => (
                                        <label key={t.nr} className="flex cursor-pointer items-center gap-1 rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-700 has-[:checked]:border-rose-300 has-[:checked]:bg-rose-50">
                                            <input type="checkbox" aria-label={`${p.name} ${t.name} im Büro`} className="accent-rose-600" checked={(buerotage[String(p.id)] ?? []).includes(t.nr)} onChange={() => umschalten(p.id, t.nr)} />
                                            {t.kurz}
                                        </label>
                                    ))}
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </fieldset>

            <div className="mt-6 flex justify-end">
                <Button type="button" onClick={() => void speichern()} disabled={arbeitet}>{arbeitet ? 'Wird gespeichert …' : 'Speichern'}</Button>
            </div>
        </Card>
    );
}
