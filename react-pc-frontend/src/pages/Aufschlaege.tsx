import { useCallback, useEffect, useState } from 'react';
import { Percent, Save } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { DecimalInput } from '../components/ui/decimal-input';
import { useToast } from '../components/ui/toast';
import { formatDecimalInput, validateDecimalInput } from '../lib/numberInput';
import { parseErrorMessage } from '../components/settings/settingsApi';

type Quelle = 'ARTIKEL' | 'KATEGORIE' | 'ALLGEMEIN' | 'KEINER';

interface KategorieZeile {
    id: number;
    oberkategorieId: number | null;
    pfad: string;
    prozent: number | null;
    wirksamProzent: number | null;
    wirksamQuelle: Quelle;
    wirksamVon: string | null;
}

interface Uebersicht {
    allgemein: number | null;
    kategorien: KategorieZeile[];
}

const BASIS = '/api/aufschlaege';
const json = { 'Content-Type': 'application/json' };
const alsText = (p: number | null) => (p == null ? '' : formatDecimalInput(p));

/** Was für die Artikel einer Kategorie gilt, in einem Satz. */
function wirkung(z: KategorieZeile): string {
    if (z.wirksamQuelle === 'KATEGORIE') return z.prozent != null ? `${alsText(z.wirksamProzent)} %` : `${alsText(z.wirksamProzent)} % (von „${z.wirksamVon}")`;
    if (z.wirksamQuelle === 'ALLGEMEIN') return `${alsText(z.wirksamProzent)} % (allgemein)`;
    return 'kein Aufschlag';
}

/**
 * Aufschläge einstellen: ein allgemeiner Aufschlag für alle Artikel und ein eigener je Kategorie. Es gilt immer der genaueste: erst der Aufschlag am Artikel,
 * dann der der Kategorie (sonst der der Oberkategorie), dann der allgemeine. Ist nirgends einer eingestellt, übernimmt das Programm den Einkaufspreis unverändert.
 */
/** {@code eingebettet}: ohne Seitenkopf und Seitenrand, zum Einsetzen in ein Fenster (zum Beispiel in der Artikelübersicht). */
export default function Aufschlaege({ eingebettet = false }: { eingebettet?: boolean } = {}) {
    const toast = useToast();
    const [daten, setDaten] = useState<Uebersicht | null>(null);
    const [fehler, setFehler] = useState('');
    const [allgemein, setAllgemein] = useState('');
    const [eingabe, setEingabe] = useState<Record<number, string>>({});
    const [speichert, setSpeichert] = useState<string | null>(null);

    const uebernehmen = useCallback((u: Uebersicht) => {
        setDaten(u);
        setAllgemein(alsText(u.allgemein));
        setEingabe(Object.fromEntries(u.kategorien.map(k => [k.id, alsText(k.prozent)])));
    }, []);

    useEffect(() => {
        let abgebrochen = false;
        fetch(BASIS)
            .then(async res => {
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Aufschläge konnten nicht geladen werden.'));
                return res.json() as Promise<Uebersicht>;
            })
            .then(u => { if (!abgebrochen) uebernehmen(u); })
            .catch(e => { if (!abgebrochen) setFehler(e instanceof Error ? e.message : 'Die Aufschläge konnten nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [uebernehmen]);

    const speichere = async (schluessel: string, url: string, text: string, name: string) => {
        const p = validateDecimalInput(text, { label: name, min: 0, max: 1000 });
        if (!p.valid) { toast.error(p.message); return; }
        setSpeichert(schluessel);
        try {
            const res = await fetch(url, { method: 'PUT', headers: json, body: JSON.stringify({ prozent: p.value }) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Aufschlag konnte nicht gespeichert werden.'));
            uebernehmen(await res.json() as Uebersicht);
            toast.success(p.value == null ? `${name}: kein Aufschlag eingestellt.` : `${name}: ${formatDecimalInput(p.value)} % gespeichert.`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Aufschlag konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(null);
        }
    };

    return (
        <PageLayout
            {...(eingebettet
                ? { className: 'max-w-none px-0 sm:px-0 lg:px-0 py-0' }
                : { ribbonCategory: 'Artikelverwaltung', title: 'Aufschläge', subtitle: 'Wie viel Prozent auf den Einkaufspreis kommen, damit der Verkaufspreis stimmt.' })}
        >
            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {!daten && !fehler && <p className="text-sm text-slate-500">Die Aufschläge werden geladen …</p>}
            {daten && (
                <>
                    <Card className="p-6">
                        <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900"><Percent className="h-5 w-5 text-rose-600" aria-hidden="true" />Allgemeiner Aufschlag</h2>
                        <p className="mt-1 text-sm text-slate-600">Gilt für alle Artikel, die weder einen eigenen Aufschlag noch eine Kategorie mit Aufschlag haben. Leer lassen heißt: kein allgemeiner Aufschlag.</p>
                        <div className="mt-4 flex flex-wrap items-end gap-3">
                            <div className="w-40">
                                <DecimalInput id="aufschlag-allgemein" label="Aufschlag in Prozent" value={allgemein} onChange={setAllgemein} min={0} max={1000} placeholder="zum Beispiel 25" />
                            </div>
                            <Button onClick={() => void speichere('allgemein', `${BASIS}/allgemein`, allgemein, 'Allgemeiner Aufschlag')}
                                disabled={speichert !== null || allgemein.trim() === alsText(daten.allgemein)} aria-label="Allgemeinen Aufschlag speichern">
                                <Save className="mr-2 h-4 w-4" />Speichern
                            </Button>
                        </div>
                    </Card>

                    <Card className="p-6">
                        <h2 className="text-lg font-semibold text-slate-900">Aufschlag je Kategorie</h2>
                        <p className="mt-1 text-sm text-slate-600">
                            Hat eine Kategorie keinen eigenen Aufschlag, gilt der der Oberkategorie, sonst der allgemeine. Ein Aufschlag direkt am Artikel geht immer vor.
                            Das Programm zeigt zu jeder Kategorie, was für ihre Artikel gilt.
                        </p>
                        {daten.kategorien.length === 0 && <p className="mt-4 text-sm text-slate-500">Es gibt noch keine Artikelkategorien.</p>}
                        {daten.kategorien.length > 0 && (
                            <div className="mt-4 overflow-x-auto rounded-lg border border-slate-200">
                                <table className="w-full text-sm" data-testid="aufschlag-kategorien">
                                    <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                                        <tr>
                                            <th scope="col" className="px-3 py-2 font-medium">Kategorie</th>
                                            <th scope="col" className="px-3 py-2 font-medium">Eigener Aufschlag</th>
                                            <th scope="col" className="px-3 py-2 font-medium">Es gilt</th>
                                            <th scope="col" className="px-3 py-2"><span className="sr-only">Speichern</span></th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-slate-100">
                                        {daten.kategorien.map(k => {
                                            const text = eingabe[k.id] ?? '';
                                            return (
                                                <tr key={k.id} data-testid={`aufschlag-kategorie-${k.id}`}>
                                                    <th scope="row" className="px-3 py-2 text-left font-medium text-slate-800">{k.pfad}</th>
                                                    <td className="px-3 py-2">
                                                        <div className="w-28">
                                                            <DecimalInput aria-label={`Aufschlag für ${k.pfad}`} value={text} min={0} max={1000} placeholder="kein eigener"
                                                                onChange={v => setEingabe(alt => ({ ...alt, [k.id]: v }))} />
                                                        </div>
                                                    </td>
                                                    <td className="px-3 py-2 text-slate-700">{wirkung(k)}</td>
                                                    <td className="px-3 py-2 text-right">
                                                        <Button variant="outline" size="sm" aria-label={`Aufschlag für ${k.pfad} speichern`}
                                                            disabled={speichert !== null || text.trim() === alsText(k.prozent)}
                                                            onClick={() => void speichere(`k${k.id}`, `${BASIS}/kategorien/${k.id}`, text, `Aufschlag für ${k.pfad}`)}>
                                                            Speichern
                                                        </Button>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </Card>
                </>
            )}
        </PageLayout>
    );
}
