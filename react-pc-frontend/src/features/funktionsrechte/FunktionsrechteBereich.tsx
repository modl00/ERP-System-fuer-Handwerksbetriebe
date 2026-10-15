import { useCallback, useEffect, useState } from 'react';
import { Check, Loader2, Save, Smartphone, Users } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { useToast } from '../../components/ui/toast';
import { setzeFunktionsrechteZurueck, type Rechte } from './useFunktionsrechte';

interface FunktionInfo { name: string; anzeige: string; beschreibung: string }
interface AbteilungRechte { abteilungId: number; abteilungName: string; rechte: Record<string, Rechte> }
interface Uebersicht { funktionen: FunktionInfo[]; aktionen: string[]; abteilungen: AbteilungRechte[] }

const SPALTEN: { feld: keyof Rechte; text: string }[] = [
    { feld: 'ansehen', text: 'Sehen' },
    { feld: 'anlegen', text: 'Anlegen' },
    { feld: 'aendern', text: 'Ändern' },
    { feld: 'loeschen', text: 'Löschen' },
];

async function meldung(res: Response, fallback: string): Promise<string> {
    if (res.status === 403) return 'Nur Administratoren dürfen Rechte ändern.';
    try {
        const body = await res.json() as { message?: string };
        if (body?.message) return body.message;
    } catch { /* kein JSON */ }
    return fallback;
}

/**
 * Rechte an den Funktionen des Programms je Abteilung: Sehen, Anlegen, Ändern, Löschen. Sie gelten gleich für das Programm am PC und für die Handy-App.
 * Ohne Einstellung ist alles erlaubt. Ein Mitarbeiter in mehreren Abteilungen hat die Summe der Rechte seiner Abteilungen.
 */
export function FunktionsrechteBereich() {
    const toast = useToast();
    const [daten, setDaten] = useState<Uebersicht | null>(null);
    const [fehler, setFehler] = useState('');
    const [speichert, setSpeichert] = useState<number | null>(null);
    const [gespeichert, setGespeichert] = useState<number | null>(null);

    const laden = useCallback(async () => {
        setFehler('');
        try {
            const res = await fetch('/api/abteilungen/funktionsrechte');
            if (!res.ok) throw new Error(await meldung(res, 'Die Rechte für Funktionen konnten nicht geladen werden.'));
            const d = await res.json() as Uebersicht;
            if (!Array.isArray(d?.abteilungen) || !Array.isArray(d?.funktionen)) throw new Error('Die Rechte für Funktionen konnten nicht gelesen werden.');
            setDaten(d);
        } catch (e) {
            const text = e instanceof Error ? e.message : 'Die Rechte für Funktionen konnten nicht geladen werden.';
            setFehler(text);
            toast.error(text);
        }
    }, [toast]);

    useEffect(() => { void laden(); }, [laden]);

    const aendere = (abteilungId: number, funktion: string, feld: keyof Rechte) => {
        setDaten(alt => alt && {
            ...alt,
            abteilungen: alt.abteilungen.map(a => {
                if (a.abteilungId !== abteilungId) return a;
                const r = a.rechte[funktion];
                const neu = !r[feld];
                // Ohne Sehen geht nichts anderes; wer Anlegen, Ändern oder Löschen erlaubt, darf auch sehen
                const geaendert: Rechte = feld === 'ansehen'
                    ? (neu ? { ...r, ansehen: true } : { ansehen: false, anlegen: false, aendern: false, loeschen: false })
                    : { ...r, [feld]: neu, ansehen: neu ? true : r.ansehen };
                return { ...a, rechte: { ...a.rechte, [funktion]: geaendert } };
            }),
        });
        setGespeichert(null);
    };

    const speichern = async (abteilung: AbteilungRechte) => {
        setSpeichert(abteilung.abteilungId);
        try {
            const res = await fetch(`/api/abteilungen/${encodeURIComponent(String(abteilung.abteilungId))}/funktionsrechte`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rechte: abteilung.rechte }),
            });
            if (!res.ok) throw new Error(await meldung(res, 'Die Rechte konnten nicht gespeichert werden.'));
            const neu = await res.json() as AbteilungRechte;
            setDaten(alt => alt && { ...alt, abteilungen: alt.abteilungen.map(a => (a.abteilungId === neu.abteilungId ? neu : a)) });
            // Die eigenen Rechte können sich geändert haben (Menü)
            setzeFunktionsrechteZurueck();
            setGespeichert(abteilung.abteilungId);
            toast.success(`Rechte für ${abteilung.abteilungName} gespeichert.`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Rechte konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(null);
        }
    };

    return (
        <section className="mb-10" aria-labelledby="funktionsrechte-titel" data-testid="funktionsrechte">
            <div className="mb-4 flex items-start gap-3">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-rose-100"><Smartphone className="h-5 w-5 text-rose-600" aria-hidden="true" /></span>
                <div>
                    <h2 id="funktionsrechte-titel" className="text-xl font-bold text-slate-900">Funktionen in Programm und Handy-App</h2>
                    <p className="text-sm text-slate-600">
                        Was darf eine Abteilung sehen, anlegen, ändern und löschen? Die Rechte gelten gleich am PC und in der Handy-App.
                        Ohne Einstellung ist alles erlaubt. Wer in mehreren Abteilungen ist, bekommt die Summe der Rechte. Administratoren am PC sind nicht eingeschränkt.
                    </p>
                </div>
            </div>

            {fehler && (
                <div role="alert" className="mb-4 rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800">
                    {fehler}
                    <Button variant="outline" size="sm" onClick={() => void laden()} className="ml-4">Erneut laden</Button>
                </div>
            )}
            {!daten && !fehler && <div className="flex items-center gap-2 py-6 text-slate-500"><Loader2 className="h-4 w-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> Rechte werden geladen …</div>}

            {daten && daten.abteilungen.length === 0 && <p className="text-sm text-slate-500">Es gibt noch keine Abteilungen.</p>}

            {daten && (
                <div className="space-y-4">
                    {daten.abteilungen.map(abt => (
                        <Card key={abt.abteilungId} className="p-5" data-testid={`funktionsrechte-${abt.abteilungId}`}>
                            <div className="mb-3 flex items-center justify-between gap-3">
                                <div className="flex items-center gap-3">
                                    <Users className="h-5 w-5 text-rose-600" aria-hidden="true" />
                                    <h3 className="text-lg font-semibold text-slate-900">{abt.abteilungName}</h3>
                                </div>
                                <Button size="sm" onClick={() => void speichern(abt)} disabled={speichert === abt.abteilungId}
                                    className="bg-rose-600 text-white hover:bg-rose-700">
                                    {speichert === abt.abteilungId ? <Loader2 className="mr-2 h-4 w-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
                                        : gespeichert === abt.abteilungId ? <Check className="mr-2 h-4 w-4" aria-hidden="true" /> : <Save className="mr-2 h-4 w-4" aria-hidden="true" />}
                                    {gespeichert === abt.abteilungId ? 'Gespeichert' : 'Speichern'}
                                </Button>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="border-b border-slate-200 text-left text-slate-700">
                                            <th scope="col" className="px-3 py-2 font-semibold">Funktion</th>
                                            {SPALTEN.map(s => <th key={s.feld} scope="col" className="w-24 px-3 py-2 text-center font-semibold">{s.text}</th>)}
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {daten.funktionen.map(f => {
                                            const r = abt.rechte[f.name] ?? { ansehen: true, anlegen: true, aendern: true, loeschen: true };
                                            return (
                                                <tr key={f.name} className="border-b border-slate-100 last:border-0">
                                                    <th scope="row" className="px-3 py-3 text-left font-normal">
                                                        <div className="font-medium text-slate-900">{f.anzeige}</div>
                                                        <div className="text-xs text-slate-500">{f.beschreibung}</div>
                                                    </th>
                                                    {SPALTEN.map(s => (
                                                        <td key={s.feld} className="px-3 py-3 text-center">
                                                            <input type="checkbox" checked={r[s.feld]} onChange={() => aendere(abt.abteilungId, f.name, s.feld)}
                                                                disabled={speichert === abt.abteilungId}
                                                                aria-label={`${abt.abteilungName}: ${f.anzeige} ${s.text}`}
                                                                className="h-4 w-4 accent-rose-600 focus:ring-2 focus:ring-rose-500" />
                                                        </td>
                                                    ))}
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        </Card>
                    ))}
                </div>
            )}
        </section>
    );
}
