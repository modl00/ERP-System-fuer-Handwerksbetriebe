import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { CheckCircle2, Download, FileText, Image, Loader2, Plug, XCircle } from 'lucide-react';
import { Button } from '../../ui/button';
import { Input } from '../../ui/input';
import { Select } from '../../ui/select-custom';
import { useToast } from '../../ui/toast';
import { useOptionalAuth } from '../../../auth/AuthContext';
import { SaveButton, SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

type Anbieter = 'GEMINI' | 'LOKAL' | 'CLAUDE' | 'MISTRAL';
interface AnbieterStand { typ: Anbieter; anzeige: string; lokal: boolean; eingerichtet: boolean; modell: string; schluesselGesetzt: boolean; kannBild: boolean; kannPdf: boolean }
interface Uebersicht { anbieter: AnbieterStand[] }
interface Testergebnis { ok: boolean; art: string; meldung: string }
interface Modell { name: string; anzeigename: string }

const BASIS = '/api/settings/ki-anbieter';
const json = { 'Content-Type': 'application/json' };
const CLOUD: Anbieter[] = ['CLAUDE', 'MISTRAL'];

/**
 * Zugang zu den KI-Anbietern (Gemini, lokal, Claude, Mistral): Schlüssel eintragen, Standardmodell wählen, Modelle laden, testen. Welche Funktion welchen Anbieter und welches
 * Modell nutzt und wohin die Daten gehen dürfen, steht in der Tabelle darunter ({@code KiFunktionenKarte}). Zugangsdaten werden gespeichert, aber nie wieder angezeigt.
 */
export function KiAnbieterKarte({ geminiZugang = null, lokalZugang = null }: { geminiZugang?: ReactNode; lokalZugang?: ReactNode } = {}) {
    const toast = useToast();
    // Nicht-werfende Variante: Die Karte wird in Tests auch ohne Anmelde-Rahmen gebaut.
    const auth = useOptionalAuth();
    const [daten, setDaten] = useState<Uebersicht | null>(null);
    const [ladefehler, setLadefehler] = useState('');
    const [modell, setModell] = useState<Record<string, string>>({});
    const [schluessel, setSchluessel] = useState<Record<string, string>>({});
    const [loeschen, setLoeschen] = useState<Record<string, boolean>>({});
    const [speichert, setSpeichert] = useState(false);
    const [tests, setTests] = useState<Record<string, Testergebnis | 'laeuft'>>({});
    const [listen, setListen] = useState<Record<string, Modell[]>>({});
    const [laedt, setLaedt] = useState<Record<string, boolean>>({});
    // Welcher Anbieter oben gerade bearbeitet wird (Auswahlliste)
    const [sicht, setSicht] = useState<Anbieter>('GEMINI');

    const uebernehmen = useCallback((u: Uebersicht) => {
        setDaten(u);
        setModell(Object.fromEntries(u.anbieter.filter(a => CLOUD.includes(a.typ)).map(a => [a.typ, a.modell])));
        setSchluessel({});
        setLoeschen({});
    }, []);

    useEffect(() => {
        let abgebrochen = false;
        fetch(BASIS)
            .then(async res => {
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die KI-Anbieter konnten nicht geladen werden.'));
                return res.json() as Promise<Uebersicht>;
            })
            .then(u => { if (!abgebrochen) uebernehmen(u); })
            .catch(e => { if (!abgebrochen) setLadefehler(e instanceof Error ? e.message : 'Die KI-Anbieter konnten nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [uebernehmen]);

    const geaendert = !!daten && (
        daten.anbieter.some(a => CLOUD.includes(a.typ) && (modell[a.typ] ?? '') !== a.modell)
        || Object.values(schluessel).some(s => s !== '') || Object.values(loeschen).some(Boolean)
    );

    const speichern = async () => {
        if (!daten) return;
        setSpeichert(true);
        try {
            const eingabe = (typ: Anbieter) => ({ modell: (modell[typ] ?? '').trim(), schluessel: loeschen[typ] ? '' : (schluessel[typ] ? schluessel[typ] : null) });
            const res = await fetch(BASIS, {
                method: 'PUT', headers: json,
                body: JSON.stringify({
                    claude: eingabe('CLAUDE'), mistral: eingabe('MISTRAL'),
                }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellungen konnten nicht gespeichert werden.'));
            uebernehmen(await res.json() as Uebersicht);
            setTests({});
            toast.success('Die KI-Anbieter wurden gespeichert.');
            // Anmeldedaten neu holen, damit „kiAktiv" app-weit sofort stimmt (KI-Hilfe in
            // der Menüleiste, KI-Knöpfe) – auch wenn Claude/Mistral statt Gemini aktiv wird.
            await auth?.refreshMe();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Einstellungen konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const teste = async (typ: Anbieter) => {
        setTests(t => ({ ...t, [typ]: 'laeuft' }));
        try {
            const res = await fetch(`${BASIS}/test`, { method: 'POST', headers: json, body: JSON.stringify({ anbieter: typ }) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Test ist fehlgeschlagen.'));
            const ergebnis = await res.json() as Testergebnis;
            setTests(t => ({ ...t, [typ]: ergebnis }));
        } catch (e) {
            setTests(t => ({ ...t, [typ]: { ok: false, art: 'FEHLER', meldung: e instanceof Error ? e.message : 'Der Test ist fehlgeschlagen.' } }));
        }
    };

    /** Fragt den Anbieter nach seinen Modellen; sie erscheinen als Vorschläge im Feld „Modell". */
    const ladeModelle = async (typ: Anbieter) => {
        setLaedt(l => ({ ...l, [typ]: true }));
        try {
            const res = await fetch(`${BASIS}/${typ}/modelle`);
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Modelle konnten nicht geladen werden.'));
            const liste = await res.json() as Modell[];
            setListen(l => ({ ...l, [typ]: liste }));
            toast.success(`${liste.length} Modelle gefunden.`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Modelle konnten nicht geladen werden.');
        } finally {
            setLaedt(l => ({ ...l, [typ]: false }));
        }
    };

    if (!daten && !ladefehler) return <SectionLoading />;

    const stand = (typ: Anbieter) => daten?.anbieter.find(a => a.typ === typ);

    const anbieterOptionen = (daten?.anbieter ?? []).map(a => ({ value: a.typ, label: `${a.anzeige} (${a.eingerichtet ? 'eingerichtet' : 'noch nicht eingerichtet'})` }));

    return (
        <div className="space-y-8" data-testid="ki-anbieter-karte">
            {ladefehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{ladefehler}</div>}

            <section className="space-y-3" aria-labelledby="ki-schritt-1">
                <h3 id="ki-schritt-1" className="text-base font-bold text-slate-900">1. Anbieter und Zugang</h3>
                <p className="text-sm text-slate-500">
                    Wählen Sie den Anbieter und tragen Sie seinen Zugang ein: Gemini (Google), Ihre lokale KI, Claude (Anthropic) oder Mistral. Schlüssel werden gespeichert und nie wieder angezeigt.
                </p>
                {daten && (
                    <div className="max-w-md">
                        <Select aria-label="Anbieter wählen" value={sicht} onChange={v => setSicht(v as Anbieter)} options={anbieterOptionen} />
                    </div>
                )}
                {sicht === 'GEMINI' && geminiZugang}
                {sicht === 'LOKAL' && lokalZugang}
                {daten && CLOUD.includes(sicht) && (
                    <SettingsCard icon={<Plug className="w-5 h-5 text-rose-600" />} title={sicht === 'CLAUDE' ? 'Claude (Anthropic)' : 'Mistral'}>
                        <div className="space-y-5">
                            {CLOUD.filter(typ => typ === sicht).map(typ => {
                                const s = stand(typ);
                                const test = tests[typ];
                                const name = typ === 'CLAUDE' ? 'Claude (Anthropic)' : 'Mistral';
                                return (
                                    <fieldset key={typ} className="rounded-lg border border-slate-200 p-3 space-y-2" data-testid={`ki-zugang-${typ}`}>
                                        <legend className="px-1 text-sm font-semibold text-slate-800 flex items-center gap-2">
                                            {name}
                                            {s?.kannBild && <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-normal text-slate-600" title="Kann Bilder verarbeiten"><Image className="w-3 h-3" aria-hidden="true" /> Bilder</span>}
                                            {s?.kannPdf && <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-normal text-slate-600" title="Kann PDFs nativ lesen"><FileText className="w-3 h-3" aria-hidden="true" /> PDF</span>}
                                        </legend>
                                        <div className="grid gap-2 sm:grid-cols-[9rem_1fr] sm:items-center">
                                            <label htmlFor={`ki-${typ}-schluessel`} className="text-sm text-slate-700">Schlüssel</label>
                                            <Input id={`ki-${typ}-schluessel`} type="password" autoComplete="new-password" value={schluessel[typ] ?? ''} className="font-mono text-sm"
                                                placeholder={s?.schluesselGesetzt && !loeschen[typ] ? 'Gespeichert, zum Ersetzen neu eingeben' : 'API-Schlüssel'}
                                                onChange={e => { setSchluessel(a => ({ ...a, [typ]: e.target.value })); setLoeschen(a => ({ ...a, [typ]: false })); }} />
                                            <label htmlFor={`ki-${typ}-modell`} className="text-sm text-slate-700">Modell</label>
                                            <Input id={`ki-${typ}-modell`} value={modell[typ] ?? ''} spellCheck={false} autoComplete="off" className="font-mono text-sm" list={`ki-${typ}-modelle`}
                                                onChange={e => setModell(a => ({ ...a, [typ]: e.target.value }))} />
                                            <datalist id={`ki-${typ}-modelle`}>
                                                {(listen[typ] ?? []).map(m => <option key={m.name} value={m.name}>{m.anzeigename}</option>)}
                                            </datalist>
                                        </div>
                                        <div className="flex flex-wrap items-center gap-3">
                                            {s?.schluesselGesetzt && (
                                                <label className="flex items-center gap-2 text-xs text-slate-600 cursor-pointer select-none">
                                                    <input type="checkbox" checked={!!loeschen[typ]} className="h-3.5 w-3.5 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                                        onChange={e => { setLoeschen(a => ({ ...a, [typ]: e.target.checked })); if (e.target.checked) setSchluessel(a => ({ ...a, [typ]: '' })); }} />
                                                    Gespeicherten Schlüssel entfernen
                                                </label>
                                            )}
                                            <Button variant="outline" size="sm" onClick={() => void ladeModelle(typ)} disabled={!s?.schluesselGesetzt || laedt[typ]}
                                                title={s?.schluesselGesetzt ? undefined : 'Zuerst einen Schlüssel eintragen und speichern'} aria-label={`Verfügbare Modelle von ${name} laden`}>
                                                {laedt[typ] ? <Loader2 className="w-3.5 h-3.5 animate-spin motion-reduce:animate-none" /> : <Download className="w-3.5 h-3.5" />} Modelle laden
                                            </Button>
                                            {listen[typ] && <span className="text-xs text-slate-500">{listen[typ].length} Modelle zur Auswahl im Feld „Modell".</span>}
                                            <Button variant="outline" size="sm" onClick={() => void teste(typ)} disabled={geaendert || !s?.eingerichtet || test === 'laeuft'}
                                                title={geaendert ? 'Zuerst speichern, getestet wird der gespeicherte Stand' : !s?.eingerichtet ? 'Zuerst einen Schlüssel eintragen und speichern' : undefined}
                                                aria-label={`${name} testen`}>
                                                {test === 'laeuft' ? <Loader2 className="w-3.5 h-3.5 animate-spin motion-reduce:animate-none" /> : null} Testen
                                            </Button>
                                        </div>
                                        {test && test !== 'laeuft' && (
                                            <div role="status" className={`flex items-start gap-2 text-sm rounded-lg p-2 ${test.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'}`}>
                                                {test.ok ? <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" /> : <XCircle className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />}
                                                <span>{test.meldung}</span>
                                            </div>
                                        )}
                                    </fieldset>
                                );
                            })}
        
                            <SaveButton onClick={() => void speichern()} saving={speichert} disabled={!geaendert}>Zugang speichern</SaveButton>
                        </div>
                    </SettingsCard>
                )}
            </section>
        </div>
    );
}
