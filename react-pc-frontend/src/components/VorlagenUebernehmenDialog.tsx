import { useEffect, useMemo, useState } from 'react';
import { Library, Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { Button } from './ui/button';
import { useToast } from './ui/toast';
import { parseErrorMessage } from './settings/settingsApi';

type Art = 'arbeitsgaenge' | 'profile';

interface ArbeitsgangZeile { name: string; abteilung: string; einheit: string; erklaerung: string; vorhanden: boolean; felder: number; beispielMinuten: number | null }
interface Abteilung { id: number; name: string }
interface ArbeitsgangVorschau { hinweis: string; zeilen: ArbeitsgangZeile[]; abteilungen: Abteilung[] }
interface ProfilZeile { nummer: string; name: string; form: string; vorhanden: boolean; kg: number; flaeche: number; einheit: string; herkunft: string }
interface ProfilVorschau { zeilen: ProfilZeile[]; werkstoffGefunden: boolean }

const zahl = (n: number | null, stellen = 2) => (n == null ? '–' : n.toLocaleString('de-DE', { minimumFractionDigits: 0, maximumFractionDigits: stellen }));

const TEXTE: Record<Art, { titel: string; beschreibung: string; knopf: string; erfolg: (n: number) => string }> = {
    arbeitsgaenge: {
        titel: 'Arbeitsgänge für Metallbau übernehmen',
        beschreibung: 'Fertige Arbeitsgänge mit Eingabemaske und Formel (Sägen, Bohren, Schweißen, Abkanten, Montage …). Es werden nur Name, Abteilung und Formel angelegt – keine Stundensätze. Die Richtwerte in den Feldern passen Sie an Ihre Maschinen an.',
        knopf: 'Arbeitsgänge',
        erfolg: n => (n === 1 ? '1 Arbeitsgang wurde angelegt.' : `${n} Arbeitsgänge wurden angelegt.`),
    },
    profile: {
        titel: 'Stahlprofile und Bleche übernehmen',
        beschreibung: 'Gängige Rohre, Stäbe, Flachstahl, Winkel, Träger und Bleche als Lagerartikel mit Maßen, Gewicht und Oberfläche. Gewicht und Fläche sind aus den Maßen berechnet (Stahl 7,85 kg/dm³) oder stammen aus der Profiltabelle. Es wird kein Preis und kein Lieferant eingetragen.',
        knopf: 'Artikel',
        erfolg: n => (n === 1 ? '1 Artikel wurde angelegt.' : `${n} Artikel wurden angelegt.`),
    },
};

interface Props { art: Art; offen: boolean; onOpenChange: (offen: boolean) => void; onFertig: () => void }

/** Mitgelieferte Metallbau-Vorlagen übernehmen: erst Vorschau (neu oder schon vorhanden), dann Auswahl, dann anlegen. Ein zweiter Lauf legt nichts doppelt an. */
export function VorlagenUebernehmenDialog({ art, offen, onOpenChange, onFertig }: Props) {
    return (
        <Dialog open={offen} onOpenChange={onOpenChange} className="w-full max-w-4xl max-h-[90vh]" aria-labelledby="vorlagen-titel">
            <DialogContent>{offen && <Inhalt art={art} onSchliessen={() => onOpenChange(false)} onFertig={onFertig} />}</DialogContent>
        </Dialog>
    );
}

function Inhalt({ art, onSchliessen, onFertig }: { art: Art; onSchliessen: () => void; onFertig: () => void }) {
    const toast = useToast();
    const text = TEXTE[art];
    const [ag, setAg] = useState<ArbeitsgangVorschau | null>(null);
    const [pr, setPr] = useState<ProfilVorschau | null>(null);
    const [fehler, setFehler] = useState('');
    const [auswahl, setAuswahl] = useState<Set<string>>(new Set());
    const [filter, setFilter] = useState('');
    const [ersatzAbteilung, setErsatzAbteilung] = useState('');
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        let aktuell = true;
        (async () => {
            try {
                const res = await fetch(`/api/vorlagen/metallbau/${art}`);
                if (res.status === 403) throw new Error('Die Vorlagen übernehmen dürfen nur Administratoren.');
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Vorlagen konnten nicht geladen werden.'));
                const daten = await res.json();
                if (!aktuell) return;
                if (art === 'arbeitsgaenge') {
                    const v = daten as ArbeitsgangVorschau;
                    setAg(v);
                    setAuswahl(new Set(v.zeilen.filter(z => !z.vorhanden).map(z => z.name)));
                } else {
                    const v = daten as ProfilVorschau;
                    setPr(v);
                    setAuswahl(new Set());
                }
            } catch (e) {
                if (aktuell) setFehler(e instanceof Error ? e.message : 'Die Vorlagen konnten nicht geladen werden.');
            }
        })();
        return () => { aktuell = false; };
    }, [art]);

    const zeilen = useMemo(() => {
        const alle: { schluessel: string; name: string; vorhanden: boolean }[] = art === 'arbeitsgaenge'
            ? (ag?.zeilen ?? []).map(z => ({ schluessel: z.name, name: z.name, vorhanden: z.vorhanden }))
            : (pr?.zeilen ?? []).map(z => ({ schluessel: z.nummer, name: z.name, vorhanden: z.vorhanden }));
        const f = filter.trim().toLowerCase();
        return f ? alle.filter(z => z.name.toLowerCase().includes(f)) : alle;
    }, [art, ag, pr, filter]);

    const umschalten = (k: string) => setAuswahl(alt => {
        const neu = new Set(alt);
        if (neu.has(k)) neu.delete(k); else neu.add(k);
        return neu;
    });
    const sichtbareNeue = zeilen.filter(z => !z.vorhanden).map(z => z.schluessel);
    const alleSichtbarenGewaehlt = sichtbareNeue.length > 0 && sichtbareNeue.every(k => auswahl.has(k));
    const sichtbareUmschalten = () => setAuswahl(alt => {
        const neu = new Set(alt);
        for (const k of sichtbareNeue) { if (alleSichtbarenGewaehlt) neu.delete(k); else neu.add(k); }
        return neu;
    });

    const uebernehmen = async () => {
        setSpeichert(true);
        try {
            const body = art === 'arbeitsgaenge'
                ? { namen: [...auswahl], abteilungId: ersatzAbteilung ? Number(ersatzAbteilung) : null }
                : { nummern: [...auswahl] };
            const res = await fetch(`/api/vorlagen/metallbau/${art}/uebernehmen`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Vorlagen konnten nicht übernommen werden.'));
            const r = await res.json() as { angelegt: number; uebersprungen: number };
            toast.success(text.erfolg(r.angelegt));
            if (r.uebersprungen > 0) toast.info(`${r.uebersprungen} waren schon vorhanden und wurden übersprungen.`);
            onFertig();
            onSchliessen();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Vorlagen konnten nicht übernommen werden.');
            setSpeichert(false);
        }
    };

    const geladen = art === 'arbeitsgaenge' ? ag : pr;
    const neu = zeilen.filter(z => !z.vorhanden).length;
    return (
        <>
            <DialogHeader>
                <DialogTitle id="vorlagen-titel" className="flex items-center gap-2"><Library className="h-5 w-5 text-rose-600" aria-hidden="true" /> {text.titel}</DialogTitle>
                <DialogDescription>{text.beschreibung}</DialogDescription>
            </DialogHeader>
            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {!geladen && !fehler && <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …</p>}
            {geladen && (
                <div className="min-h-0 flex-1 overflow-y-auto" data-testid="vorlagen-vorschau">
                    {art === 'arbeitsgaenge' && ag?.hinweis && <p className="mb-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">{ag.hinweis}</p>}
                    {art === 'profile' && pr && !pr.werkstoffGefunden && <p className="mb-2 rounded-lg bg-amber-50 p-3 text-xs text-amber-900">Der Werkstoff S235JR fehlt im Programm. Die Artikel werden ohne Werkstoff angelegt, das Gewicht gilt für Stahl (7,85 kg/dm³).</p>}
                    <div className="mb-2 flex flex-wrap items-center gap-3">
                        <input type="search" value={filter} onChange={e => setFilter(e.target.value)} placeholder="Suchen, zum Beispiel „Rundrohr“ oder „Bohren“" aria-label="Vorlagen durchsuchen" className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm" />
                        <Button variant="outline" size="sm" onClick={sichtbareUmschalten} disabled={sichtbareNeue.length === 0}>{alleSichtbarenGewaehlt ? 'Auswahl aufheben' : 'Alle neuen auswählen'}</Button>
                    </div>
                    {art === 'arbeitsgaenge' && ag && (
                        <div className="mb-2 flex flex-wrap items-center gap-2 text-sm text-slate-700">
                            <label htmlFor="vorlagen-ersatz-abteilung">Falls es die Abteilung (Werkstatt, Montage, Büro) bei Ihnen nicht gibt:</label>
                            <select id="vorlagen-ersatz-abteilung" value={ersatzAbteilung} onChange={e => setErsatzAbteilung(e.target.value)} className="rounded-lg border border-slate-300 px-2 py-1 text-sm">
                                <option value="">Abbrechen und melden</option>
                                {ag.abteilungen.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                            </select>
                        </div>
                    )}
                    <p className="mb-2 text-sm text-slate-700">{neu} neu, {zeilen.length - neu} schon vorhanden{filter ? ' (gefiltert)' : ''}.</p>
                    <table className="w-full text-sm">
                        <thead><tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                            <th scope="col" className="w-8 py-2"><span className="sr-only">Übernehmen</span></th>
                            <th scope="col" className="py-2 pr-3 font-semibold">{art === 'arbeitsgaenge' ? 'Arbeitsgang' : 'Artikel'}</th>
                            {art === 'arbeitsgaenge'
                                ? (<><th scope="col" className="px-2 py-2 font-semibold">Abteilung</th><th scope="col" className="px-2 py-2 font-semibold">Eingabefelder</th><th scope="col" className="px-2 py-2 text-right font-semibold">Beispiel</th></>)
                                : (<><th scope="col" className="px-2 py-2 text-right font-semibold">Gewicht</th><th scope="col" className="px-2 py-2 text-right font-semibold">Außenfläche</th></>)}
                            <th scope="col" className="pl-2 py-2 font-semibold">Stand</th>
                        </tr></thead>
                        <tbody>
                            {art === 'arbeitsgaenge' && (ag?.zeilen ?? []).filter(z => zeilen.some(x => x.schluessel === z.name)).map(z => (
                                <tr key={z.name} className="border-b border-slate-100 align-top">
                                    <td className="py-2"><input type="checkbox" className="h-4 w-4 accent-rose-600" aria-label={`${z.name} übernehmen`} disabled={z.vorhanden} checked={auswahl.has(z.name)} onChange={() => umschalten(z.name)} /></td>
                                    <td className="py-2 pr-3"><span className="font-medium text-slate-900 break-words">{z.name}</span><span className="block text-xs text-slate-500">{z.erklaerung}</span></td>
                                    <td className="px-2 py-2 text-slate-600">{z.abteilung}</td>
                                    <td className="px-2 py-2 text-slate-600">{z.felder > 0 ? <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-xs text-blue-700">{z.felder} Variable{z.felder !== 1 ? 'n' : ''}</span> : <span className="text-slate-400">–</span>}</td>
                                    <td className="px-2 py-2 text-right tabular-nums">{zahl(z.beispielMinuten)} min {z.einheit}</td>
                                    <td className="pl-2 py-2 text-xs">{z.vorhanden ? <span className="text-slate-500">vorhanden</span> : <span className="font-medium text-emerald-700">neu</span>}</td>
                                </tr>
                            ))}
                            {art === 'profile' && (pr?.zeilen ?? []).filter(z => zeilen.some(x => x.schluessel === z.nummer)).map(z => (
                                <tr key={z.nummer} className="border-b border-slate-100 align-top">
                                    <td className="py-2"><input type="checkbox" className="h-4 w-4 accent-rose-600" aria-label={`${z.name} übernehmen`} disabled={z.vorhanden} checked={auswahl.has(z.nummer)} onChange={() => umschalten(z.nummer)} /></td>
                                    <td className="py-2 pr-3 font-medium text-slate-900 break-words">{z.name}<span className="block text-xs font-normal text-slate-500">{z.form} · {z.nummer}</span></td>
                                    <td className="px-2 py-2 text-right tabular-nums">{zahl(z.kg, 3)} kg {z.einheit}</td>
                                    <td className="px-2 py-2 text-right tabular-nums">{zahl(z.flaeche, 3)} m² {z.einheit}</td>
                                    <td className="pl-2 py-2 text-xs">{z.vorhanden ? <span className="text-slate-500">vorhanden</span> : <span className="font-medium text-emerald-700">neu</span>}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
            <DialogFooter>
                <Button variant="outline" onClick={onSchliessen} disabled={speichert}>Abbrechen</Button>
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void uebernehmen()} disabled={speichert || auswahl.size === 0}>
                    {speichert && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} {text.knopf} übernehmen ({auswahl.size})
                </Button>
            </DialogFooter>
        </>
    );
}
