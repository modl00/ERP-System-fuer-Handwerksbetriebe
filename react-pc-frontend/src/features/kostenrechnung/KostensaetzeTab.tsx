import { useEffect, useState } from 'react';
import { Euro, Gem, Info } from 'lucide-react';
import { Card } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { DecimalInput } from '../../components/ui/decimal-input';
import { formatCurrency } from '../../components/artikel/formatCurrency';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { ladeInterneZeitSchaetzung, ladeKostensaetze, ladeVorgaben, speichereVorgaben, type InterneZeitSchaetzung, type Kostensaetze, type SatzZeile, type Vorgaben } from './kostensaetzeApi';
import { SatzUebernahmeKarte } from './SatzUebernahmeKarte';
import { GemeinkostenKarte } from './GemeinkostenKarte';

const euro = (n: number | null) => (n == null ? '–' : formatCurrency(n));
const aktuellesJahr = new Date().getFullYear();

function Tabelle({ titel, zeilen, beschreibung }: { titel: string; zeilen: SatzZeile[]; beschreibung?: string }) {
    if (zeilen.length === 0) return null;
    return (
        <Card className="p-4" data-testid={`tabelle-${titel}`}>
            <h2 className="text-base font-semibold text-slate-900">{titel}</h2>
            {beschreibung && <p className="mb-2 text-xs text-slate-500">{beschreibung}</p>}
            <div className="overflow-x-auto">
                <table className="w-full text-sm">
                    <thead>
                        <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                            <th scope="col" className="py-2 pr-3 font-semibold">Name</th>
                            <th scope="col" className="px-2 py-2 text-right font-semibold">Personal je Std.</th>
                            <th scope="col" className="px-2 py-2 text-right font-semibold">Umlage</th>
                            <th scope="col" className="px-2 py-2 text-right font-semibold">Kostensatz</th>
                            <th scope="col" className="px-2 py-2 text-right font-semibold">+ Wagnis</th>
                            <th scope="col" className="px-2 py-2 text-right font-semibold">+ Gewinn</th>
                            <th scope="col" className="pl-2 py-2 text-right font-semibold text-rose-700">Verrechnungssatz</th>
                        </tr>
                    </thead>
                    <tbody>
                        {zeilen.map(z => (
                            <tr key={`${z.art}-${z.name}`} className="border-b border-slate-100 align-top" data-testid={`zeile-${z.name}`}>
                                <td className="py-2 pr-3">
                                    <span className="font-medium text-slate-900">{z.name}</span>
                                    {z.art !== 'PERSON' && <span className="ml-1 text-xs text-slate-500">({z.anzahl} {z.anzahl === 1 ? 'Person' : 'Personen'})</span>}
                                    {z.hinweis && <p className="text-xs text-amber-800">{z.hinweis}</p>}
                                </td>
                                <td className="px-2 py-2 text-right tabular-nums">{euro(z.personalkostensatz)}</td>
                                <td className="px-2 py-2 text-right tabular-nums">{euro(z.umlage)}</td>
                                <td className="px-2 py-2 text-right font-semibold tabular-nums">{euro(z.kostensatz)}</td>
                                <td className="px-2 py-2 text-right tabular-nums text-slate-600">{euro(z.wagnisBetrag)}</td>
                                <td className="px-2 py-2 text-right tabular-nums text-slate-600">{euro(z.gewinnBetrag)}</td>
                                <td className="pl-2 py-2 text-right font-semibold tabular-nums text-rose-700">{euro(z.verrechnungssatz)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </Card>
    );
}

/**
 * Was kostet mich eine Stunde, und was verlange ich? Kostensatz (Selbstkosten) und Verrechnungssatz für den ganzen Betrieb, nach Qualifikation und je Person, mit
 * sichtbarer Herleitung. Das ist eine Vorschau: Es wird nichts verändert. Löhne sind personenbezogen, deshalb nur für Administratoren.
 */
export function KostensaetzeTab() {
    const [jahr, setJahr] = useState(aktuellesJahr);
    const [wagnis, setWagnis] = useState('0');
    const [gewinn, setGewinn] = useState('10');
    const [interne, setInterne] = useState('');
    const [daten, setDaten] = useState<Kostensaetze | null>(null);
    const [fehler, setFehler] = useState('');
    const [laedt, setLaedt] = useState(false);
    const [neuLaden, setNeuLaden] = useState(0);
    const toast = useToast();
    /** Erst wenn die gespeicherten Vorgaben da sind (oder nicht geladen werden konnten), wird zum ersten Mal gerechnet. */
    const [bereit, setBereit] = useState(false);
    const [gespeichert, setGespeichert] = useState<Vorgaben | null>(null);
    const [speichert, setSpeichert] = useState(false);
    const [schaetzung, setSchaetzung] = useState<InterneZeitSchaetzung | null>(null);
    const [schaetztGerade, setSchaetztGerade] = useState(false);

    const w = validateDecimalInput(wagnis, { label: 'Das Wagnis', required: true, min: 0, max: 100 });
    const g = validateDecimalInput(gewinn, { label: 'Der Gewinn', required: true, min: 0, max: 200 });
    const i = validateDecimalInput(interne, { label: 'Die interne Zeit', required: false, min: 0, max: 100, integer: true });
    const eingabeFehler = !w.valid ? w.message : !g.valid ? g.message : !i.valid ? i.message : '';
    const wagnisWert = w.valid ? w.value ?? 0 : 0;
    const gewinnWert = g.valid ? g.value ?? 0 : 0;
    const interneWert = i.valid ? i.value ?? null : null;

    useEffect(() => {
        let aktuell = true;
        ladeVorgaben().then(v => {
            if (!aktuell) return;
            setGespeichert(v);
            if (v.wagnis != null) setWagnis(formatDecimalInput(v.wagnis));
            if (v.gewinn != null) setGewinn(formatDecimalInput(v.gewinn));
            if (v.interneZeit != null) setInterne(String(v.interneZeit));
        }).catch(() => { /* ohne gespeicherte Vorgaben gelten die Standardwerte der Seite */ }).finally(() => { if (aktuell) setBereit(true); });
        return () => { aktuell = false; };
    }, []);

    useEffect(() => {
        if (eingabeFehler || !bereit) return;
        const abbruch = new AbortController();
        const t = window.setTimeout(() => {
            setLaedt(true);
            ladeKostensaetze(jahr, wagnisWert, gewinnWert, interneWert, abbruch.signal)
                .then(d => { setDaten(d); setFehler(''); })
                .catch(e => { if (!abbruch.signal.aborted) setFehler(e instanceof Error ? e.message : 'Die Sätze konnten nicht berechnet werden.'); })
                .finally(() => { if (!abbruch.signal.aborted) setLaedt(false); });
        }, 400);
        return () => { window.clearTimeout(t); abbruch.abort(); };
    }, [jahr, wagnisWert, gewinnWert, interneWert, eingabeFehler, neuLaden, bereit]);

    const schaetzJahr = jahr < aktuellesJahr ? jahr : aktuellesJahr - 1;
    const unveraendert = gespeichert != null && gespeichert.wagnis === wagnisWert && gespeichert.gewinn === gewinnWert && gespeichert.interneZeit === interneWert;

    const vorgabeSpeichern = () => {
        setSpeichert(true);
        speichereVorgaben({ wagnis: wagnisWert, gewinn: gewinnWert, interneZeit: interneWert })
            .then(v => { setGespeichert(v); toast.success('Gespeichert. Diese Werte stehen beim nächsten Öffnen wieder da. An den Sätzen der Arbeitsgänge ändert sich dadurch nichts.'); })
            .catch(e => toast.error(e instanceof Error ? e.message : 'Die Vorgaben konnten nicht gespeichert werden.'))
            .finally(() => setSpeichert(false));
    };

    const interneSchaetzen = () => {
        setSchaetztGerade(true);
        ladeInterneZeitSchaetzung(schaetzJahr)
            .then(setSchaetzung)
            .catch(e => setSchaetzung({ jahr: schaetzJahr, prozent: null, interneStunden: null, sollstunden: null, hinweis: e instanceof Error ? e.message : 'Die interne Zeit konnte nicht geschätzt werden.' }))
            .finally(() => setSchaetztGerade(false));
    };

    return (
        <div className="space-y-4" data-testid="kostensaetze">
            <Card className="p-4">
                <div className="grid gap-3 sm:grid-cols-4">
                    <div>
                        <label htmlFor="ks-jahr" className="mb-1 block text-sm font-medium text-slate-700">Jahr</label>
                        <select id="ks-jahr" value={jahr} onChange={e => setJahr(Number(e.target.value))} className="min-h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm">
                            {[aktuellesJahr + 1, aktuellesJahr, aktuellesJahr - 1, aktuellesJahr - 2].map(j => <option key={j} value={j}>{j}</option>)}
                        </select>
                    </div>
                    <DecimalInput label="Wagnis (%)" value={wagnis} onChange={setWagnis} min={0} max={100} required />
                    <DecimalInput label="Gewinn (%)" value={gewinn} onChange={setGewinn} min={0} max={200} required />
                    <DecimalInput label="Interne Zeit (%)" value={interne} onChange={setInterne} min={0} max={100} integer />
                </div>
                <p className="mt-2 flex items-start gap-1.5 text-xs text-slate-500"><Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                    Interne Zeit (Werkstatt, Wartung, Büro) nur für das laufende Jahr: leer = Standardwert des Rechners. Verrechnungssatz = Kostensatz + Wagnis, dann + Gewinn. Das ist eine <strong className="mx-1">Vorschau</strong>: Es wird nichts verändert.</p>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                    <Button type="button" variant="outline" size="sm" onClick={vorgabeSpeichern} disabled={speichert || !!eingabeFehler || unveraendert}>
                        {unveraendert ? 'Als Vorgabe gespeichert' : 'Als Vorgabe speichern'}
                    </Button>
                    <Button type="button" variant="ghost" size="sm" className="text-rose-600 hover:bg-rose-100" onClick={interneSchaetzen} disabled={schaetztGerade}>
                        <Gem className="mr-2 h-4 w-4" aria-hidden="true" /> Interne Zeit aus gebuchten Stunden schätzen
                    </Button>
                </div>
                {schaetzung && (
                    <div className="mt-2 rounded-lg bg-slate-50 p-3 text-sm text-slate-700" role="status" data-testid="interne-schaetzung">
                        {schaetzung.prozent != null ? (
                            <p>
                                Aus den Buchungen {schaetzung.jahr}: <strong>{schaetzung.prozent} %</strong> interne Zeit ({(schaetzung.interneStunden ?? 0).toLocaleString('de-DE', { maximumFractionDigits: 0 })} von {(schaetzung.sollstunden ?? 0).toLocaleString('de-DE', { maximumFractionDigits: 0 })} Sollstunden).{' '}
                                <button type="button" className="font-medium text-rose-700 underline" onClick={() => { setInterne(String(schaetzung.prozent)); setSchaetzung(null); }}>In das Feld übernehmen</button>
                            </p>
                        ) : <p>{schaetzung.hinweis}</p>}
                        {schaetzung.prozent != null && schaetzung.hinweis && <p className="mt-1 text-amber-800">{schaetzung.hinweis}</p>}
                    </div>
                )}
                {eingabeFehler && <p role="alert" className="mt-2 rounded-lg bg-rose-50 p-2 text-sm text-rose-800">{eingabeFehler}</p>}
            </Card>

            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {laedt && !daten && <p role="status" className="text-sm text-slate-500">Die Sätze werden berechnet …</p>}

            {daten && (
                <>
                    <div className="grid gap-3 sm:grid-cols-4" data-testid="summen">
                        <Card className="p-3"><p className="text-xs text-slate-500">Lohnkosten im Jahr</p><p className="text-lg font-semibold tabular-nums">{euro(daten.lohnkostenGesamt)}</p></Card>
                        <Card className="p-3"><p className="text-xs text-slate-500">Verkäufliche Stunden</p><p className="text-lg font-semibold tabular-nums">{formatDecimalInput(daten.stundenGesamt)}</p></Card>
                        <Card className="p-3"><p className="text-xs text-slate-500">Gemeinkosten im Jahr</p><p className="text-lg font-semibold tabular-nums">{euro(daten.gemeinkostenGesamt)}</p></Card>
                        <Card className="p-3"><p className="text-xs text-slate-500">Umlage je verkäufliche Stunde</p><p className="text-lg font-semibold tabular-nums">{euro(daten.umlage)}</p></Card>
                    </div>
                    <p className="text-xs text-slate-500">{daten.modus === 'RUECKWIRKEND' ? `Rückblick ${daten.jahr}: echte Zeitbuchungen und Löhne.` : `Hochrechnung ${daten.jahr}: Soll-Stunden abzüglich Urlaub, Krankheit und interner Zeit.`}</p>
                    {daten.hinweise.map(h => <p key={h} role="status" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">{h}</p>)}
                    <GemeinkostenKarte gemeinkosten={daten.gemeinkosten} rueckblick={daten.jahr < aktuellesJahr} onGeaendert={() => setNeuLaden(n => n + 1)} />
                    <Tabelle titel="Ganzer Betrieb" zeilen={[daten.betrieb]} beschreibung="Das ist der Selbstkostensatz des Rechners „Was muss meine Stunde kosten?“, plus Wagnis und Gewinn." />
                    <Tabelle titel="Nach Qualifikation" zeilen={daten.gruppen} beschreibung="Die Qualifikation steht bei jedem Mitarbeiter in den Stammdaten (Auszubildender, Facharbeiter, Meister)." />
                    <Tabelle titel="Je Person" zeilen={daten.personen} beschreibung="Lohn der Person durch ihre verkäuflichen Stunden. Personen ohne verkäufliche Stunden (zum Beispiel Büro) haben keinen eigenen Satz: Ihr Lohn steckt im Durchschnitt." />
                    <SatzUebernahmeKarte parameter={{ jahr, wagnis: wagnisWert, gewinn: gewinnWert, interneQuote: interneWert }} moeglich={daten.betrieb.verrechnungssatz != null} />
                    {daten.betrieb.kostensatz == null && daten.hinweise.length === 0 && <Card className="p-6 text-center text-slate-500"><Euro className="mx-auto mb-2 h-8 w-8 text-rose-200" aria-hidden="true" />Für dieses Jahr gibt es noch keine Sätze.</Card>}
                </>
            )}
        </div>
    );
}
