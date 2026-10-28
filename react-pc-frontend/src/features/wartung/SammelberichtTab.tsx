import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CircleAlert, Download, FileText, Loader2, Search } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { DatePicker } from '../../components/ui/datepicker';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { SearchableSelect } from '../../components/ui/searchable-select';
import { Select } from '../../components/ui/select-custom';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { ladeSammelbericht, sucheProtokolle } from './api';
import { formatDatum, STATUS_ANZEIGE } from './format';
import { kundeName as bekannterKundenname, sucheKunden } from './kundenSuche';
import { oeffneWartefenster, schliesseFenster, speicherePdf, zeigePdf } from './pdfAnzeige';
import { hinweiseZurAuswahl, MAX_PROTOKOLLE } from './sammelbericht';
import type { Seite, WartungListeneintrag, WartungStatus } from './types';

/** So viele Treffer lädt die Auswahl auf einmal. Mehr zeigt sie nicht; dann hilft ein engerer Filter. */
const TREFFER = 100;

const STATUS_AUSWAHL = [
    { value: 'ABGESCHLOSSEN', label: STATUS_ANZEIGE.ABGESCHLOSSEN.label },
    { value: '', label: 'Alle (ohne Archiv)' },
    { value: 'OFFEN', label: STATUS_ANZEIGE.OFFEN.label },
    { value: 'IN_BEARBEITUNG', label: STATUS_ANZEIGE.IN_BEARBEITUNG.label },
    { value: 'ARCHIVIERT', label: STATUS_ANZEIGE.ARCHIVIERT.label },
];

/** Mehrere Wartungen ankreuzen und als ein PDF mit optionalem Deckblatt öffnen oder speichern. */
export function SammelberichtTab() {
    const toast = useToast();
    const [suche, setSuche] = useState('');
    const [suchtext, setSuchtext] = useState('');
    const [status, setStatus] = useState('ABGESCHLOSSEN');
    const [kundeId, setKundeId] = useState('');
    const [kundeName, setKundeName] = useState('');
    const [datumVon, setDatumVon] = useState('');
    const [datumBis, setDatumBis] = useState('');
    const [ergebnis, setErgebnis] = useState<Seite<WartungListeneintrag> | null>(null);
    const [ladeStatus, setLadeStatus] = useState<'laedt' | 'ok' | 'fehler'>('laedt');
    // Die Auswahl bleibt beim Wechsel der Filter erhalten: So lassen sich Protokolle verschiedener Kunden oder Monate sammeln.
    const [auswahl, setAuswahl] = useState<Map<number, WartungListeneintrag>>(new Map());
    const [titel, setTitel] = useState('');
    const [mitDeckblatt, setMitDeckblatt] = useState(true);
    const [laeuft, setLaeuft] = useState<'oeffnen' | 'speichern' | null>(null);
    const anfrageNummer = useRef(0);

    useEffect(() => {
        const timer = setTimeout(() => setSuchtext(suche), 350);
        return () => clearTimeout(timer);
    }, [suche]);

    const datumFalsch = datumVon !== '' && datumBis !== '' && datumVon > datumBis;

    const laden = useCallback(async () => {
        if (datumFalsch) return;
        const nummer = ++anfrageNummer.current;
        setLadeStatus('laedt');
        try {
            const antwort = await sucheProtokolle({
                kundeId: kundeId === '' ? undefined : Number(kundeId),
                status: status === '' ? undefined : (status as WartungStatus),
                datumVon: datumVon || undefined,
                datumBis: datumBis || undefined,
                suche: suchtext,
                seite: 0,
                groesse: TREFFER,
            });
            if (nummer !== anfrageNummer.current) return;
            setErgebnis(antwort);
            // Die gemerkte Auswahl zeigt sonst einen alten Stand (Status, Mängel), wenn sich ein Protokoll inzwischen geändert hat
            setAuswahl((alt) => new Map([...alt].map(([id, e]) => [id, antwort.content.find((c) => c.id === id) ?? e])));
            setLadeStatus('ok');
        } catch (e) {
            if (nummer !== anfrageNummer.current) return;
            // Die Liste der vorherigen Filter wäre jetzt falsch: lieber die Fehlerkarte mit neuem Versuch
            setErgebnis(null);
            setLadeStatus('fehler');
            toast.error(e instanceof Error ? e.message : 'Die Wartungen konnten nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [kundeId, status, datumVon, datumBis, suchtext, datumFalsch]);

    useEffect(() => { void laden(); }, [laden]);

    const sichtbar = useMemo(() => ergebnis?.content ?? [], [ergebnis]);
    const alleSichtbarGewaehlt = sichtbar.length > 0 && sichtbar.every((e) => auswahl.has(e.id));
    const gewaehlt = useMemo(() => [...auswahl.values()], [auswahl]);
    const hinweise = hinweiseZurAuswahl(gewaehlt);

    const umschalten = (eintrag: WartungListeneintrag) => setAuswahl((alt) => {
        const neu = new Map(alt);
        if (neu.has(eintrag.id)) neu.delete(eintrag.id); else neu.set(eintrag.id, eintrag);
        return neu;
    });

    const alleUmschalten = () => setAuswahl((alt) => {
        const neu = new Map(alt);
        if (alleSichtbarGewaehlt) sichtbar.forEach((e) => neu.delete(e.id)); else sichtbar.forEach((e) => neu.set(e.id, e));
        return neu;
    });

    const zuruecksetzen = () => {
        setSuche(''); setSuchtext(''); setStatus('ABGESCHLOSSEN'); setKundeId(''); setKundeName(''); setDatumVon(''); setDatumBis('');
    };
    const filterGeaendert = suche !== '' || status !== 'ABGESCHLOSSEN' || kundeId !== '' || datumVon !== '' || datumBis !== '';

    const erstelle = async (art: 'oeffnen' | 'speichern') => {
        if (gewaehlt.length === 0 || hinweise.zuViele || laeuft) return;
        setLaeuft(art);
        // Das Fenster muss jetzt, beim Klick, aufgehen. Erst nach dem Laden wäre es für den Browser ein Pop-up.
        const fenster = art === 'oeffnen' ? oeffneWartefenster('Der Sammelbericht wird erstellt') : null;
        try {
            const { blob, dateiname } = await ladeSammelbericht({ protokollIds: gewaehlt.map((e) => e.id), titel, mitDeckblatt });
            if (art === 'speichern') {
                speicherePdf(blob, dateiname);
            } else if (!zeigePdf(blob, fenster)) {
                speicherePdf(blob, dateiname);
                toast.info('Der Browser hat das neue Fenster blockiert. Der Sammelbericht wurde stattdessen heruntergeladen.');
            }
        } catch (e) {
            schliesseFenster(fenster);
            toast.error(e instanceof Error ? e.message : 'Der Sammelbericht konnte nicht erstellt werden.');
        } finally {
            setLaeuft(null);
        }
    };

    return (
        <section role="tabpanel" id="wartung-panel-sammelbericht" aria-labelledby="wartung-tab-sammelbericht" className="space-y-6">
            <Card className="p-4">
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-[minmax(0,2fr)_minmax(0,1.1fr)_minmax(0,1.3fr)_minmax(0,1fr)_minmax(0,1fr)_auto]">
                    <div>
                        <Label htmlFor="sammel-suche">Suche</Label>
                        <div className="relative">
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                            <Input id="sammel-suche" className="pl-9" value={suche} maxLength={100} placeholder="Nummer, Kunde, Objekt oder Tür"
                                onChange={(e) => setSuche(e.target.value)} />
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="sammel-status">Status</Label>
                        <Select id="sammel-status" value={status} onChange={setStatus} options={STATUS_AUSWAHL} />
                    </div>
                    <div>
                        <Label id="sammel-kunde-label">Kunde</Label>
                        <div role="group" aria-labelledby="sammel-kunde-label">
                            <SearchableSelect
                                value={kundeId}
                                selectedLabel={kundeId === '' ? 'Alle Kunden' : kundeName}
                                onChange={(wert) => { setKundeId(wert); setKundeName(bekannterKundenname(wert)); }}
                                onSearch={(term) => sucheKunden(term, true)}
                                placeholder="Alle Kunden"
                                searchPlaceholder="Name, Kundennummer oder Ort"
                            />
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="sammel-von">Datum von</Label>
                        <DatePicker id="sammel-von" value={datumVon} onChange={setDatumVon} placeholder="Beginn" />
                    </div>
                    <div>
                        <Label htmlFor="sammel-bis">Datum bis</Label>
                        <DatePicker id="sammel-bis" value={datumBis} onChange={setDatumBis} placeholder="Ende" />
                    </div>
                    <div className="flex items-end">
                        <Button variant="outline" onClick={zuruecksetzen} disabled={!filterGeaendert} className="w-full xl:w-auto">Zurücksetzen</Button>
                    </div>
                </div>
                {datumFalsch && <p role="alert" className="mt-2 text-sm text-rose-700">Das Datum „von" liegt nach dem Datum „bis".</p>}
            </Card>

            <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
                <div className="min-w-0 space-y-3">
                    {ladeStatus === 'laedt' && !ergebnis && (
                        <div className="flex items-center justify-center gap-2 py-12 text-slate-500" role="status">
                            <Loader2 className="h-5 w-5 motion-safe:animate-spin" aria-hidden="true" /> Wartungen werden geladen …
                        </div>
                    )}
                    {ladeStatus === 'fehler' && !ergebnis && (
                        <Card className="p-6 text-center">
                            <p className="text-slate-700">Die Wartungen konnten nicht geladen werden.</p>
                            <Button size="sm" className="mt-3" onClick={() => void laden()}>Noch einmal versuchen</Button>
                        </Card>
                    )}
                    {ergebnis && sichtbar.length === 0 && (
                        <Card className="p-8 text-center">
                            <FileText className="mx-auto h-8 w-8 text-slate-300" aria-hidden="true" />
                            <p className="mt-2 font-medium text-slate-800">Keine passenden Wartungen</p>
                            <p className="mt-1 text-sm text-slate-500">Mit anderen Filtern erscheinen hier die Wartungen, die in den Bericht sollen.</p>
                        </Card>
                    )}
                    {sichtbar.length > 0 && (
                        <>
                            <div className="flex items-center justify-between gap-3 px-1">
                                <label className="flex cursor-pointer items-center gap-2 text-sm font-medium text-slate-700">
                                    <input type="checkbox" className="h-4 w-4 rounded border-slate-300 accent-rose-600 focus:ring-2 focus:ring-rose-500"
                                        checked={alleSichtbarGewaehlt} onChange={alleUmschalten} />
                                    Alle {sichtbar.length} angezeigten auswählen
                                </label>
                                {ladeStatus === 'laedt' && <Loader2 className="h-4 w-4 text-slate-400 motion-safe:animate-spin" aria-label="Wird aktualisiert" />}
                            </div>
                            <ul className="flex flex-col gap-2">
                                {sichtbar.map((e) => {
                                    const st = STATUS_ANZEIGE[e.status];
                                    const ort = [e.kundeName, e.objektBezeichnung, e.tuerBezeichnung].filter(Boolean).join(' · ');
                                    const gewaehltJa = auswahl.has(e.id);
                                    return (
                                        <li key={e.id}>
                                            <label
                                                className={cn(
                                                    'flex cursor-pointer items-start gap-3 rounded-lg border bg-white p-3 transition-colors focus-within:ring-2 focus-within:ring-rose-500',
                                                    gewaehltJa ? 'border-rose-300 bg-rose-50/40' : 'border-slate-200 hover:border-slate-300',
                                                )}
                                            >
                                                <input type="checkbox" className="mt-1 h-4 w-4 shrink-0 rounded border-slate-300 accent-rose-600"
                                                    checked={gewaehltJa} onChange={() => umschalten(e)} aria-label={`${e.nummer} in den Bericht aufnehmen`} />
                                                <span className="min-w-0 flex-1">
                                                    <span className="flex flex-wrap items-center gap-2">
                                                        <span className="font-semibold text-slate-900">{e.nummer}</span>
                                                        <span className={cn('inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold', st.klasse)}>{st.label}</span>
                                                        {e.maengel > 0 && (
                                                            <span className="inline-flex items-center rounded-full border border-rose-200 bg-rose-50 px-2 py-0.5 text-xs font-semibold text-rose-700">
                                                                {e.maengel} {e.maengel === 1 ? 'Mangel' : 'Mängel'}
                                                            </span>
                                                        )}
                                                    </span>
                                                    <span className="mt-0.5 block text-sm text-slate-500 break-words">{ort}</span>
                                                </span>
                                                <span className="shrink-0 text-sm text-slate-500">{formatDatum(e.datum)}</span>
                                            </label>
                                        </li>
                                    );
                                })}
                            </ul>
                            {ergebnis && ergebnis.totalElements > sichtbar.length && (
                                <p role="status" className="px-1 text-sm text-amber-700">
                                    Es gibt {ergebnis.totalElements} passende Wartungen, angezeigt werden die ersten {sichtbar.length}. Bitte mit Kunde oder Zeitraum eingrenzen.
                                </p>
                            )}
                        </>
                    )}
                </div>

                <Card className="h-fit space-y-4 p-4 xl:sticky xl:top-4">
                    <div>
                        <h3 className="text-base font-semibold text-slate-900">Bericht</h3>
                        <p className="mt-0.5 text-sm text-slate-500" aria-live="polite">
                            {gewaehlt.length === 0 ? 'Noch nichts ausgewählt.' : `${gewaehlt.length} ${gewaehlt.length === 1 ? 'Wartung' : 'Wartungen'} ausgewählt.`}
                            {gewaehlt.length > 0 && (
                                <button type="button" className="ml-2 text-rose-700 underline underline-offset-2 hover:text-rose-800" onClick={() => setAuswahl(new Map())}>
                                    Auswahl leeren
                                </button>
                            )}
                        </p>
                    </div>
                    <div className="flex flex-col gap-2">
                        <Button onClick={() => void erstelle('oeffnen')} disabled={gewaehlt.length === 0 || hinweise.zuViele || laeuft !== null}>
                            {laeuft === 'oeffnen'
                                ? <><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird erstellt …</>
                                : <><FileText className="h-4 w-4" aria-hidden="true" /> PDF öffnen</>}
                        </Button>
                        <Button variant="outline" onClick={() => void erstelle('speichern')} disabled={gewaehlt.length === 0 || hinweise.zuViele || laeuft !== null}>
                            {laeuft === 'speichern'
                                ? <><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird erstellt …</>
                                : <><Download className="h-4 w-4" aria-hidden="true" /> Herunterladen</>}
                        </Button>
                    </div>
                    {gewaehlt.length > 0 && (hinweise.entwuerfe > 0 || hinweise.mitOffenenPunkten > 0 || hinweise.zuViele) && (
                        <div role="status" className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                            <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                            <div className="space-y-1">
                                {hinweise.zuViele && <p>Ein Bericht kann höchstens {MAX_PROTOKOLLE} Wartungen enthalten.</p>}
                                {hinweise.entwuerfe > 0 && <p>{hinweise.entwuerfe} {hinweise.entwuerfe === 1 ? 'Wartung ist' : 'Wartungen sind'} noch nicht abgeschlossen und erscheint als Entwurf.</p>}
                                {hinweise.mitOffenenPunkten > 0 && <p>{hinweise.mitOffenenPunkten} {hinweise.mitOffenenPunkten === 1 ? 'Wartung hat' : 'Wartungen haben'} noch offene Prüfpunkte.</p>}
                            </div>
                        </div>
                    )}

                    <div>
                        <Label htmlFor="sammel-titel">Titel (optional)</Label>
                        <Input id="sammel-titel" value={titel} maxLength={200} placeholder="z. B. Jahreswartung Werk Nord 2026" onChange={(e) => setTitel(e.target.value)} />
                    </div>
                    <label className="flex cursor-pointer items-start gap-2 text-sm text-slate-700">
                        <input type="checkbox" className="mt-0.5 h-4 w-4 rounded border-slate-300 accent-rose-600" checked={mitDeckblatt} onChange={(e) => setMitDeckblatt(e.target.checked)} />
                        <span>
                            <span className="font-medium">Deckblatt mit Übersicht</span>
                            <span className="block text-slate-500">Zeitraum, Kennzahlen und Mängelliste vor den Protokollen.</span>
                        </span>
                    </label>


                </Card>
            </div>
        </section>
    );
}
