import { useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, FileUp, Info, Loader2 } from 'lucide-react';

import { Button } from '../../../components/ui/button';
import { Card } from '../../../components/ui/card';
import { cn } from '../../../lib/utils';
import { AKTION_TEXT, QUELLE_TEXT, erstelleVorschau, uebernehme, type Aktion, type Ergebnisbericht, type Vorschau } from './uebernahmeApi';

const AKTION_FARBE: Record<Aktion, string> = {
    ZUGEORDNET: 'bg-emerald-100 text-emerald-800',
    LEER: 'bg-slate-100 text-slate-600',
    NEU_IN_LISTE: 'bg-amber-100 text-amber-800',
    FREITEXT: 'bg-slate-100 text-slate-600',
    UNGUELTIG: 'bg-rose-100 text-rose-800',
};

function Kennzahl({ wert, text, warnung }: { wert: number; text: string; warnung?: boolean }) {
    return (
        <div className={cn('rounded-lg border p-3', warnung && wert > 0 ? 'border-amber-300 bg-amber-50' : 'border-slate-200 bg-white')}>
            <p className="text-2xl font-semibold text-slate-900">{wert}</p>
            <p className="text-xs text-slate-600">{text}</p>
        </div>
    );
}

/**
 * Übernahme aus HandwerkPro: Die Exportdatei wählen, die <b>Vorschau</b> ansehen und danach übernehmen. Die Vorschau zeigt, wohin jeder alte Wert
 * kommt, welche Module ergänzt werden, was noch eine Entscheidung braucht und was neu in die Auswahllisten käme.
 */
export function WartungUebernahmeTab() {
    const [datei, setDatei] = useState<File | null>(null);
    const [laedt, setLaedt] = useState(false);
    const [fehler, setFehler] = useState('');
    const [vorschau, setVorschau] = useState<Vorschau | null>(null);
    const [nurOffene, setNurOffene] = useState(true);
    const [neueWerte, setNeueWerte] = useState(true);
    const [frage, setFrage] = useState(false);
    const [laeuft, setLaeuft] = useState(false);
    const [bericht, setBericht] = useState<Ergebnisbericht | null>(null);

    const starten = async () => {
        if (!datei) return;
        setLaedt(true);
        setFehler('');
        try {
            setBericht(null);
            setFrage(false);
            setVorschau(await erstelleVorschau(await datei.text()));
        } catch (e) {
            setVorschau(null);
            setFehler(e instanceof Error ? e.message : 'Die Vorschau konnte nicht erstellt werden.');
        } finally {
            setLaedt(false);
        }
    };

    const uebernehmen = async () => {
        if (!datei) return;
        setLaeuft(true);
        setFehler('');
        try {
            setBericht(await uebernehme(await datei.text(), neueWerte));
            setFrage(false);
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Übernahme ist fehlgeschlagen. Es wurde nichts übernommen.');
        } finally {
            setLaeuft(false);
        }
    };

    const mitFragen = useMemo(() => (vorschau?.protokolle ?? []).filter((p) => p.fragen.length > 0), [vorschau]);
    const werte = useMemo(() => (vorschau?.werte ?? []).filter((w) => !nurOffene || w.aktion === 'NEU_IN_LISTE' || w.aktion === 'UNGUELTIG'), [vorschau, nurOffene]);
    const z = vorschau?.zusammenfassung;

    return (
        <section role="tabpanel" id="wartung-panel-uebernahme" aria-labelledby="wartung-tab-uebernahme" className="space-y-6 pt-4">
            <Card className="space-y-3 p-4">
                <h2 className="text-lg font-semibold text-slate-900">Übernahme aus HandwerkPro</h2>
                <p className="flex items-start gap-2 text-sm text-slate-600"><Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    Die <b className="mx-1">Vorschau</b> ändert nichts; sie zeigt nur, was die Übernahme tun würde. Erst der Knopf „Protokolle übernehmen“ legt Daten an. Die Exportdatei erzeugen Sie mit <code>tools/wartung-export-handwerkpro.mjs</code>;
                    sie enthält Kundendaten und gehört nicht ins Repository.</p>
                <div className="flex flex-wrap items-center gap-3">
                    <input type="file" accept=".json,application/json" aria-label="Exportdatei wählen" onChange={(e) => { setDatei(e.target.files?.[0] ?? null); setVorschau(null); setFehler(''); }}
                        className="text-sm file:mr-3 file:rounded-lg file:border file:border-slate-300 file:bg-white file:px-3 file:py-2 file:text-sm" />
                    <Button onClick={() => void starten()} disabled={!datei || laedt}>
                        {laedt ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <FileUp className="h-4 w-4" aria-hidden="true" />} Vorschau erstellen
                    </Button>
                </div>
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            </Card>

            {vorschau && z && (
                <>
                    <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-6" data-testid="kennzahlen">
                        <Kennzahl wert={z.wartungen} text="Wartungen" />
                        <Kennzahl wert={z.abgeschlossen} text="abgeschlossen" />
                        <Kennzahl wert={z.offen} text="offen" />
                        <Kennzahl wert={z.modulErgaenzt} text="Module nachgereicht" />
                        <Kennzahl wert={z.modulUnklar} text="Modul unklar" warnung />
                        <Kennzahl wert={z.kundenNeu} text="Kunden neu" warnung />
                        <Kennzahl wert={z.objekteNeu} text="Objekte neu" />
                        <Kennzahl wert={z.tuerenNeu} text="Türen neu" />
                        <Kennzahl wert={z.pruefpunkteZugeordnet} text={`von ${z.pruefpunkteGesamt} Prüfpunkten zugeordnet`} />
                        <Kennzahl wert={z.pruefpunkteEigene} text="Prüfpunkte als eigene Punkte" />
                        <Kennzahl wert={z.werteNeuInListe} text="Werte neu in Listen" warnung />
                        <Kennzahl wert={z.werteUngueltig} text="ungültige Werte" warnung />
                    </div>

                    <Card className="space-y-3 p-4" data-testid="uebernahme-karte">
                        <h3 className="text-sm font-semibold text-slate-900">Jetzt übernehmen</h3>
                        {bericht ? (
                            <div role="status" className="space-y-2 text-sm text-slate-700">
                                <p className="flex items-center gap-2 font-medium text-emerald-800"><CheckCircle2 className="h-4 w-4" aria-hidden="true" /> Die Übernahme ist fertig.</p>
                                <ul className="list-disc pl-5">
                                    <li>{bericht.protokolleNeu} {bericht.protokolleNeu === 1 ? 'Protokoll' : 'Protokolle'} übernommen{bericht.protokolleSchonDa > 0 ? `, ${bericht.protokolleSchonDa} waren schon da` : ''}</li>
                                    <li>{bericht.kundenNeu} Kunden, {bericht.objekteNeu} Objekte, {bericht.tuerenNeu} Türen neu angelegt</li>
                                    <li>{bericht.pruefpunkteEigene} Prüfpunkte als eigene Punkte, {bericht.katalogErgaenzt} Ergänzungen im Katalog</li>
                                </ul>
                                {bericht.uebersprungen.length > 0 && (
                                    <div className="rounded-lg bg-amber-50 p-3"><p className="font-medium text-amber-900">{bericht.uebersprungen.length} {bericht.uebersprungen.length === 1 ? 'Protokoll wurde' : 'Protokolle wurden'} ausgelassen:</p>
                                        <ul className="list-disc pl-5 text-amber-900">{bericht.uebersprungen.map((u) => <li key={u}>{u}</li>)}</ul></div>
                                )}
                                {bericht.hinweise.length > 0 && <ul className="list-disc pl-5 text-slate-600">{bericht.hinweise.map((h) => <li key={h}>{h}</li>)}</ul>}
                                <p className="text-slate-500">Die PDFs der Protokolle entstehen wie immer aus den Daten, sobald Sie ein Protokoll drucken. Die Exportdatei können Sie jetzt löschen.</p>
                            </div>
                        ) : (
                            <>
                                <label className="flex items-center gap-2 text-sm text-slate-700"><input type="checkbox" className="h-4 w-4 accent-rose-600" checked={neueWerte} onChange={(e) => setNeueWerte(e.target.checked)} /> Neue Werte (zum Beispiel neue Hersteller) in die Auswahllisten aufnehmen</label>
                                {!frage ? (
                                    <Button onClick={() => setFrage(true)} disabled={laeuft}>Protokolle übernehmen …</Button>
                                ) : (
                                    <div className="space-y-2 rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
                                        <p>Es werden bis zu <b>{z.wartungen} Protokolle</b> mit ihren alten Nummern angelegt{mitFragen.length > 0 ? `; ${mitFragen.length} davon mit offener Frage werden ausgelassen` : ''}. Alles geschieht in einem Schritt: Geht etwas schief, wird nichts übernommen. Ein zweiter Lauf legt nichts doppelt an.</p>
                                        <div className="flex gap-2">
                                            <Button onClick={() => void uebernehmen()} disabled={laeuft}>{laeuft && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />} Ja, jetzt übernehmen</Button>
                                            <Button variant="outline" onClick={() => setFrage(false)} disabled={laeuft}>Abbrechen</Button>
                                        </div>
                                    </div>
                                )}
                            </>
                        )}
                    </Card>

                    {vorschau.hinweise.length > 0 && (
                        <Card className="space-y-1 p-4"><h3 className="text-sm font-semibold text-slate-900">Der Katalog wird ergänzt</h3>
                            <ul className="list-disc pl-5 text-sm text-slate-700">{vorschau.hinweise.map((h) => <li key={h}>{h}</li>)}</ul></Card>
                    )}

                    <Card className="space-y-3 p-4">
                        <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                            {mitFragen.length > 0 && <AlertTriangle className="h-4 w-4 text-amber-600" aria-hidden="true" />}
                            {mitFragen.length === 0 ? 'Keine offenen Fragen zu den Protokollen' : `${mitFragen.length} ${mitFragen.length === 1 ? 'Protokoll braucht' : 'Protokolle brauchen'} eine Entscheidung`}
                        </h3>
                        {mitFragen.length > 0 && (
                            <ul className="divide-y divide-slate-100 text-sm">
                                {mitFragen.map((p) => (
                                    <li key={p.altId} className="py-2"><span className="font-medium text-slate-900">{p.altNummer}</span> <span className="text-slate-500">{p.titel}</span>
                                        <ul className="mt-1 list-disc pl-5 text-slate-700">{p.fragen.map((f) => <li key={f}>{f}</li>)}</ul></li>
                                ))}
                            </ul>
                        )}
                    </Card>

                    <Card className="overflow-x-auto p-0">
                        <h3 className="px-4 pt-4 text-sm font-semibold text-slate-900">Protokolle: Module und Bauart</h3>
                        <table className="mt-2 w-full text-left text-sm">
                            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-2">Protokoll</th><th className="px-4 py-2">Art</th><th className="px-4 py-2">Module</th><th className="px-4 py-2">Bauart</th><th className="px-4 py-2 text-right">Prüfpunkte</th></tr></thead>
                            <tbody className="divide-y divide-slate-100">
                                {vorschau.protokolle.map((p) => (
                                    <tr key={p.altId}>
                                        <td className="px-4 py-2"><span className="font-medium text-slate-900">{p.altNummer}</span><br /><span className="text-xs text-slate-500">{p.status}</span></td>
                                        <td className="px-4 py-2 text-slate-700">{p.protokollart ?? '–'}</td>
                                        <td className="px-4 py-2 text-slate-700">{p.module.join(', ') || '–'}<br /><span className="text-xs text-slate-500">{QUELLE_TEXT[p.modulQuelle]}{p.ergaenzteModule.length > 0 ? `, ergänzt: ${p.ergaenzteModule.join(', ')}` : ''}</span></td>
                                        <td className="px-4 py-2 text-slate-700">{p.bauart ?? '–'}</td>
                                        <td className="px-4 py-2 text-right text-slate-700">{p.pruefpunkteZugeordnet} von {p.pruefpunkte}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </Card>

                    <Card className="overflow-x-auto p-0">
                        <div className="flex flex-wrap items-center justify-between gap-2 px-4 pt-4">
                            <h3 className="text-sm font-semibold text-slate-900">Abbildungstabelle: alter Wert → neuer Wert</h3>
                            <label className="flex items-center gap-2 text-sm text-slate-700"><input type="checkbox" className="h-4 w-4 accent-rose-600" checked={nurOffene} onChange={(e) => setNurOffene(e.target.checked)} /> Nur Werte, die eine Entscheidung brauchen</label>
                        </div>
                        <table className="mt-2 w-full text-left text-sm">
                            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-2">Feld</th><th className="px-4 py-2">Alter Wert</th><th className="px-4 py-2">Neuer Wert</th><th className="px-4 py-2 text-right">Anzahl</th><th className="px-4 py-2">Was passiert</th></tr></thead>
                            <tbody className="divide-y divide-slate-100">
                                {werte.map((w) => (
                                    <tr key={`${w.feld}|${w.altWert}|${w.neuerWert}|${w.aktion}`}>
                                        <td className="px-4 py-2 text-slate-900">{w.feld}</td>
                                        <td className="px-4 py-2 text-slate-700">{w.altWert === '' ? <span className="text-slate-400">(leer)</span> : w.altWert}</td>
                                        <td className="px-4 py-2 text-slate-700">{w.neuerWert === '' ? <span className="text-slate-400">(leer)</span> : w.neuerWert}</td>
                                        <td className="px-4 py-2 text-right text-slate-700">{w.anzahl}</td>
                                        <td className="px-4 py-2"><span className={cn('rounded-full px-2 py-0.5 text-xs font-medium', AKTION_FARBE[w.aktion])}>{AKTION_TEXT[w.aktion]}</span></td>
                                    </tr>
                                ))}
                                {werte.length === 0 && <tr><td colSpan={5} className="px-4 py-4 text-center text-slate-500">Alle Werte sind eindeutig zugeordnet.</td></tr>}
                            </tbody>
                        </table>
                    </Card>

                    <Card className="overflow-x-auto p-0">
                        <h3 className="px-4 pt-4 text-sm font-semibold text-slate-900">Kunden</h3>
                        <table className="mt-2 w-full text-left text-sm">
                            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-2">In HandwerkPro</th><th className="px-4 py-2">Im ERP</th><th className="px-4 py-2 text-right">Wartungen</th></tr></thead>
                            <tbody className="divide-y divide-slate-100">
                                {vorschau.kunden.map((k) => (
                                    <tr key={k.altId}>
                                        <td className="px-4 py-2 text-slate-900">{k.name}</td>
                                        <td className="px-4 py-2">{k.erpName ? <span className="text-slate-700">{k.erpName}</span> : <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">wird neu angelegt</span>}</td>
                                        <td className="px-4 py-2 text-right text-slate-700">{k.wartungen}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </Card>

                    {vorschau.eigenePruefpunkte.length > 0 && (
                        <Card className="space-y-2 p-4">
                            <h3 className="text-sm font-semibold text-slate-900">Prüfpunkte, die es im Katalog nicht gibt ({vorschau.eigenePruefpunkte.length} verschiedene)</h3>
                            <p className="text-xs text-slate-500">Sie bleiben als eigene Punkte im jeweiligen Protokoll erhalten, nichts geht verloren.</p>
                            <ul className="max-h-64 divide-y divide-slate-100 overflow-y-auto text-sm">
                                {vorschau.eigenePruefpunkte.map((p) => <li key={p.label} className="flex justify-between gap-3 py-1.5"><span className="text-slate-800">{p.label}</span><span className="text-slate-500">{p.anzahl}×</span></li>)}
                            </ul>
                        </Card>
                    )}
                </>
            )}
        </section>
    );
}
