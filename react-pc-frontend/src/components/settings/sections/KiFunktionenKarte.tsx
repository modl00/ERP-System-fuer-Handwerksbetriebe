import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, Cloud, Download, Loader2, Lock, ShieldCheck, XCircle } from 'lucide-react';
import { Button } from '../../ui/button';
import { Select } from '../../ui/select-custom';
import { useToast } from '../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

type Anbieter = 'GEMINI' | 'LOKAL' | 'CLAUDE' | 'MISTRAL';
type Datenschutz = 'NUR_LOKAL' | 'CLOUD_ERLAUBT';

interface Funktion {
    funktion: string;
    anzeige: string;
    anbieter: Anbieter;
    datenschutz: Datenschutz;
    /** Je Anbieter: das gewählte Modell dieser Funktion, leer = keine Wahl. */
    auswahl: Record<string, string>;
    /** Je Anbieter: das Modell, das ohne Wahl gilt. */
    standards: Record<string, string>;
    benoetigtBild: boolean;
    benoetigtPdf: boolean;
}
interface AnbieterStand { typ: Anbieter; anzeige: string; lokal: boolean; eingerichtet: boolean; kannBild: boolean; kannPdf: boolean }
interface Uebersicht { funktionen: Funktion[]; anbieter: AnbieterStand[] }
interface GeminiFunktion { schluessel: string; anzeige: string; modell: string; standard: string; gewaehlt: boolean }
interface GeminiUebersicht { funktionen: GeminiFunktion[]; websuche: boolean }
interface Testergebnis { ok: boolean; art: string; meldung: string }
interface Modell { name: string; anzeigename: string }

const ANBIETER = '/api/settings/ki-anbieter';
const MODELLE = '/api/settings/ki-modelle';
const json = { 'Content-Type': 'application/json' };

/**
 * Gemini-Schlüssel, die unterdrückt werden, weil die Funktion jetzt über KiFunktion/KiAnbieterService läuft.
 * 'dokument-analyse' → DOKUMENT_ERKENNEN, 'scanner' → SCANNER, 'spracheingabe' → SPRACHEINGABE (alle in KiFunktion).
 * 'pro' (Schwierige Fälle) war ein Gemini-internes Modell-Tier, das durch die Modellauswahl pro Funktion abgelöst wurde.
 */
const MIT_ANBIETERWAHL = new Set([
    'ki-hilfe', 'assistent', 'email-classification', 'email-text', 'pdf-extractor', 'plantafel', 'testbox',
    'dokument-analyse', 'scanner', 'pro', 'spracheingabe',
]);

const DATENSCHUTZ = [
    { value: 'NUR_LOKAL', label: 'Nur lokal' },
    { value: 'CLOUD_ERLAUBT', label: 'Cloud erlaubt' },
];

/** Zeile der Tabelle: entweder eine Funktion mit Anbieterwahl oder eine, die noch fest bei Gemini läuft. */
interface Zeile {
    id: string;
    anzeige: string;
    fest: boolean;
}

interface Entwurf { anbieter: Anbieter; datenschutz: Datenschutz; modell: Record<string, string> }

/**
 * Eine Tabelle für alles, was je KI-Funktion einzustellen ist: wer antwortet (Anbieter), mit welchem Modell und wohin die Daten gehen dürfen (Datenschutz).
 * Funktionen, die noch fest bei Gemini laufen, stehen mit in der Tabelle; Anbieter und Datenschutz sind dort gesperrt. Die Modelle je Anbieter lassen sich
 * mit einem Klick laden. „Nur lokal“ sperrt Cloud-Anbieter, auch der Server prüft das bei jedem Aufruf.
 */
export function KiFunktionenKarte() {
    const toast = useToast();
    const [daten, setDaten] = useState<Uebersicht | null>(null);
    const [gemini, setGemini] = useState<GeminiUebersicht | null>(null);
    const [ladefehler, setLadefehler] = useState('');
    const [entwurf, setEntwurf] = useState<Record<string, Entwurf>>({});
    const [festModell, setFestModell] = useState<Record<string, string>>({});
    const [websuche, setWebsuche] = useState(true);
    const [listen, setListen] = useState<Partial<Record<Anbieter, Modell[]>>>({});
    const [laedt, setLaedt] = useState<Partial<Record<Anbieter, boolean>>>({});
    const [tests, setTests] = useState<Record<string, Testergebnis | 'laeuft'>>({});
    const [speichert, setSpeichert] = useState(false);

    const uebernehmen = useCallback((u: Uebersicht, g: GeminiUebersicht) => {
        setDaten(u);
        setGemini(g);
        setEntwurf(Object.fromEntries(u.funktionen.map(f => [f.funktion, { anbieter: f.anbieter, datenschutz: f.datenschutz, modell: { ...(f.auswahl ?? {}) } }])));
        setFestModell(Object.fromEntries(g.funktionen.filter(f => !MIT_ANBIETERWAHL.has(f.schluessel)).map(f => [f.schluessel, f.gewaehlt ? f.modell : ''])));
        setWebsuche(g.websuche);
    }, []);

    useEffect(() => {
        let abgebrochen = false;
        Promise.all([
            fetch(ANBIETER).then(async res => {
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die KI-Funktionen konnten nicht geladen werden.'));
                return res.json() as Promise<Uebersicht>;
            }),
            fetch(MODELLE).then(async res => {
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die KI-Funktionen konnten nicht geladen werden.'));
                return res.json() as Promise<GeminiUebersicht>;
            }),
        ])
            .then(([u, g]) => { if (!abgebrochen) uebernehmen(u, g); })
            .catch(e => { if (!abgebrochen) setLadefehler(e instanceof Error ? e.message : 'Die KI-Funktionen konnten nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [uebernehmen]);

    const festeFunktionen = useMemo(() => (gemini?.funktionen ?? []).filter(f => !MIT_ANBIETERWAHL.has(f.schluessel)), [gemini]);
    const zeilen = useMemo<Zeile[]>(() => [
        ...(daten?.funktionen ?? []).map(f => ({ id: f.funktion, anzeige: f.anzeige, fest: false })),
        ...festeFunktionen.map(f => ({ id: f.schluessel, anzeige: f.anzeige, fest: true })),
    ], [daten, festeFunktionen]);

    const stand = (typ: Anbieter) => daten?.anbieter.find(a => a.typ === typ);
    const anbieterName = (typ: Anbieter) => stand(typ)?.anzeige ?? typ;

    const geaendert = !!daten && !!gemini && (
        daten.funktionen.some(f => {
            const e = entwurf[f.funktion];
            return !!e && (e.anbieter !== f.anbieter || e.datenschutz !== f.datenschutz || (e.modell[e.anbieter] ?? '') !== (f.auswahl?.[e.anbieter] ?? ''));
        })
        || festeFunktionen.some(f => (festModell[f.schluessel] ?? '') !== (f.gewaehlt ? f.modell : ''))
        || websuche !== gemini.websuche
    );

    const aendere = (id: string, teil: Partial<Entwurf>) => {
        setEntwurf(alt => {
            const neu = { ...alt[id], ...teil };
            // Nur lokal passt nur zur lokalen KI: Wer „nur lokal“ wählt, bekommt die lokale KI
            if (neu.datenschutz === 'NUR_LOKAL' && neu.anbieter !== 'LOKAL') neu.anbieter = 'LOKAL';
            return { ...alt, [id]: neu };
        });
        setTests(t => { const { [id]: _weg, ...rest } = t; void _weg; return rest; });
    };

    const setzeModell = (zeile: Zeile, wert: string) => {
        if (zeile.fest) setFestModell(alt => ({ ...alt, [zeile.id]: wert }));
        else setEntwurf(alt => ({ ...alt, [zeile.id]: { ...alt[zeile.id], modell: { ...alt[zeile.id].modell, [alt[zeile.id].anbieter]: wert } } }));
        setTests(t => { const { [zeile.id]: _weg, ...rest } = t; void _weg; return rest; });
    };

    const ladeModelle = async (typ: Anbieter) => {
        setLaedt(l => ({ ...l, [typ]: true }));
        try {
            const res = await fetch(`${ANBIETER}/${typ}/modelle`);
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Modelle konnten nicht geladen werden.'));
            const liste = await res.json() as Modell[];
            setListen(l => ({ ...l, [typ]: liste }));
            toast.success(`${liste.length} Modelle von ${anbieterName(typ)} gefunden.`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Modelle konnten nicht geladen werden.');
        } finally {
            setLaedt(l => ({ ...l, [typ]: false }));
        }
    };

    /** Anbieter, Modell, Standard und die Möglichkeit, es zu ändern: alles, was eine Zeile über ihr Modell wissen muss. */
    const modellInfo = (zeile: Zeile) => {
        if (zeile.fest) {
            const f = festeFunktionen.find(x => x.schluessel === zeile.id);
            return { anbieter: 'GEMINI' as Anbieter, wert: festModell[zeile.id] ?? '', standard: f?.standard ?? '' };
        }
        const e = entwurf[zeile.id];
        const f = daten?.funktionen.find(x => x.funktion === zeile.id);
        return { anbieter: e.anbieter, wert: e.modell[e.anbieter] ?? '', standard: f?.standards?.[e.anbieter] ?? '' };
    };

    const teste = async (zeile: Zeile) => {
        const { anbieter, wert, standard } = modellInfo(zeile);
        setTests(t => ({ ...t, [zeile.id]: 'laeuft' }));
        try {
            const res = await fetch(`${ANBIETER}/test`, { method: 'POST', headers: json, body: JSON.stringify({ anbieter, modell: wert || standard || null }) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Test ist fehlgeschlagen.'));
            const ergebnis = await res.json() as Testergebnis;
            setTests(t => ({ ...t, [zeile.id]: ergebnis }));
        } catch (e) {
            setTests(t => ({ ...t, [zeile.id]: { ok: false, art: 'FEHLER', meldung: e instanceof Error ? e.message : 'Der Test ist fehlgeschlagen.' } }));
        }
    };

    const speichern = async () => {
        if (!daten || !gemini) return;
        setSpeichert(true);
        try {
            const res = await fetch(ANBIETER, {
                method: 'PUT', headers: json,
                body: JSON.stringify({
                    funktionen: Object.fromEntries(daten.funktionen.map(f => {
                        const e = entwurf[f.funktion];
                        return [f.funktion, { anbieter: e.anbieter, datenschutz: e.datenschutz, modell: (e.modell[e.anbieter] ?? '').trim() }];
                    })),
                }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellungen konnten nicht gespeichert werden.'));
            const neuA = await res.json() as Uebersicht;
            const resG = await fetch(MODELLE, {
                method: 'PUT', headers: json,
                body: JSON.stringify({ modelle: Object.fromEntries(festeFunktionen.map(f => [f.schluessel, (festModell[f.schluessel] ?? '').trim()])), websuche }),
            });
            if (!resG.ok) throw new Error(await parseErrorMessage(resG, 'Die Modelle konnten nicht gespeichert werden.'));
            uebernehmen(neuA, await resG.json() as GeminiUebersicht);
            setTests({});
            toast.success('Die KI-Funktionen wurden gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Einstellungen konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    if (!daten && !ladefehler) return <SectionLoading />;

    const modellOptionen = (typ: Anbieter, wert: string, standard: string) => {
        const liste = listen[typ] ?? [];
        const opts = [{ value: '', label: standard ? `Standard (${standard})` : 'Standard' }];
        for (const m of liste) opts.push({ value: m.name, label: m.anzeigename && m.anzeigename !== m.name ? `${m.anzeigename} (${m.name})` : m.name });
        if (wert && !opts.some(o => o.value === wert)) opts.push({ value: wert, label: wert });
        return opts;
    };

    return (
        <SettingsCard
            icon={<ShieldCheck className="w-5 h-5 text-rose-600" />}
            title="KI-Funktionen"
            description={
                <p>
                    Für jede Funktion: Welcher Anbieter antwortet, mit welchem Modell, und dürfen die Fragen ins Internet? Bei „Nur lokal“ sind Cloud-Anbieter gesperrt, Ihre Kunden- und
                    Firmendaten verlassen dann das Haus nicht. Ohne eigene Modellwahl gilt der Standard.
                </p>
            }
        >
            {ladefehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{ladefehler}</div>}
            {daten && gemini && (
                <div className="space-y-4" data-testid="ki-funktionen-karte">
                    <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm text-slate-600">Modelle laden von:</span>
                        {daten.anbieter.map(a => (
                            <Button key={a.typ} variant="outline" size="sm" onClick={() => void ladeModelle(a.typ)} disabled={!a.eingerichtet || laedt[a.typ]}
                                title={a.eingerichtet ? undefined : `${a.anzeige} ist noch nicht eingerichtet (oben Zugang eintragen und speichern)`}
                                aria-label={`Verfügbare Modelle von ${a.anzeige} laden`}>
                                {laedt[a.typ] ? <Loader2 className="w-3.5 h-3.5 animate-spin motion-reduce:animate-none" /> : <Download className="w-3.5 h-3.5" />}
                                {a.anzeige.replace(/ \(.*\)$/, '')}
                                {listen[a.typ] && <span className="text-xs text-slate-500">({listen[a.typ]!.length})</span>}
                            </Button>
                        ))}
                    </div>

                    <div className="-m-1 overflow-x-auto p-1">
                        <table className="w-full min-w-[56rem] border-collapse text-left text-sm">
                            <thead>
                                <tr className="border-b border-slate-200 text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    <th scope="col" className="py-2 pr-3 font-semibold">Funktion</th>
                                    <th scope="col" className="px-2 py-2 font-semibold">Anbieter</th>
                                    <th scope="col" className="px-2 py-2 font-semibold">Modell</th>
                                    <th scope="col" className="px-2 py-2 font-semibold">Datenschutz</th>
                                    <th scope="col" className="py-2 pl-2 font-semibold"><span className="sr-only">Modell prüfen</span></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {zeilen.map(zeile => {
                                    const { anbieter, wert, standard } = modellInfo(zeile);
                                    const e = zeile.fest ? null : entwurf[zeile.id];
                                    const funk = daten?.funktionen.find(f => f.funktion === zeile.id);
                                    const s = stand(anbieter);
                                    const test = tests[zeile.id];
                                    const lokalGesperrt = e?.datenschutz === 'NUR_LOKAL';
                                    return (
                                        <Fragment key={zeile.id}>
                                        <tr className="align-top" data-testid={zeile.fest ? `ki-modell-${zeile.id}` : `ki-anbieter-${zeile.id}`}>
                                            <th scope="row" className="py-3 pr-3 align-top font-medium text-slate-800">
                                                <span className="block">{zeile.anzeige}</span>
                                                <span className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs font-normal text-slate-500">
                                                    {lokalGesperrt || anbieter === 'LOKAL'
                                                        ? <span className="inline-flex items-center gap-1 text-emerald-700"><ShieldCheck className="w-3.5 h-3.5" aria-hidden="true" /> {lokalGesperrt ? 'Daten bleiben im Haus' : 'Lokale KI, Daten bleiben im Haus'}</span>
                                                        : <span className="inline-flex items-center gap-1 text-amber-700"><Cloud className="w-3.5 h-3.5" aria-hidden="true" /> Fragen gehen an den Anbieter</span>}
                                                    {s && !s.eingerichtet && <span className="text-rose-700">{s.anzeige} ist noch nicht eingerichtet</span>}
                                                    {funk?.benoetigtPdf && s && !s.kannPdf && <span className="inline-flex items-center gap-1 text-amber-700" title="Dieser Anbieter liest PDFs nicht nativ – die Seiten werden automatisch als Bilder übertragen"><AlertTriangle className="w-3.5 h-3.5 shrink-0" aria-hidden="true" /> PDF wird als Bilder gesendet</span>}
                                                </span>
                                            </th>
                                            <td className="px-2 py-2.5 w-52">
                                                {zeile.fest || !e ? (
                                                    <span className="inline-flex items-center gap-1.5 py-2 text-slate-600" title="Diese Funktion nutzt noch fest Gemini">
                                                        <Lock className="w-3.5 h-3.5 text-slate-400" aria-hidden="true" /> Gemini (fest)
                                                    </span>
                                                ) : (
                                                    <Select aria-label={`Anbieter für ${zeile.anzeige}`} value={e.anbieter} onChange={v => aendere(zeile.id, { anbieter: v as Anbieter })}
                                                        options={daten.anbieter.map(a => ({ value: a.typ, label: a.anzeige, disabled: lokalGesperrt && !a.lokal }))} />
                                                )}
                                            </td>
                                            <td className="px-2 py-2.5 w-72">
                                                <Select aria-label={`Modell für ${zeile.anzeige}`} value={wert} onChange={v => setzeModell(zeile, v)} options={modellOptionen(anbieter, wert, standard)} />
                                            </td>
                                            <td className="px-2 py-2.5 w-44">
                                                {zeile.fest || !e ? (
                                                    <span className="inline-flex items-center gap-1.5 py-2 text-slate-600" title="Läuft über Google (Gemini)">
                                                        <Lock className="w-3.5 h-3.5 text-slate-400" aria-hidden="true" /> Cloud (Google)
                                                    </span>
                                                ) : (
                                                    <Select aria-label={`Datenschutz für ${zeile.anzeige}`} value={e.datenschutz} onChange={v => aendere(zeile.id, { datenschutz: v as Datenschutz })} options={DATENSCHUTZ} />
                                                )}
                                            </td>
                                            <td className="py-2.5 pl-2 w-24">
                                                <Button variant="outline" size="sm" onClick={() => void teste(zeile)} disabled={!s?.eingerichtet || test === 'laeuft'}
                                                    title={s?.eingerichtet ? undefined : `${anbieterName(anbieter)} ist noch nicht eingerichtet`} aria-label={`Modell für ${zeile.anzeige} testen`}>
                                                    {test === 'laeuft' ? <Loader2 className="w-3.5 h-3.5 animate-spin motion-reduce:animate-none" /> : null} Testen
                                                </Button>
                                            </td>
                                        </tr>
                                        {test && test !== 'laeuft' && (
                                            <tr>
                                                <td colSpan={5} className="pb-3">
                                                    <div role="status" className={`flex items-start gap-2 rounded-lg p-2 text-sm ${test.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'}`}>
                                                        {test.ok ? <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" /> : <XCircle className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />}
                                                        <span>{test.meldung}</span>
                                                    </div>
                                                </td>
                                            </tr>
                                        )}
                                        </Fragment>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>

                    <label className="flex items-start gap-3 cursor-pointer select-none">
                        <input type="checkbox" checked={websuche} onChange={ev => setWebsuche(ev.target.checked)}
                            className="mt-1 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500" />
                        <span>
                            <span className="font-medium text-slate-900">KI-Hilfe darf im Internet suchen (Google-Suche)</span>
                            <span className="block text-xs text-slate-500">Die Suche hat ein eigenes, kleineres Google-Kontingent. Bei Fehlern „Kontingent erschöpft“ hilft oft, sie auszuschalten.</span>
                        </span>
                    </label>

                    <SaveButton onClick={() => void speichern()} saving={speichert} disabled={!geaendert}>Speichern</SaveButton>
                </div>
            )}
        </SettingsCard>
    );
}
