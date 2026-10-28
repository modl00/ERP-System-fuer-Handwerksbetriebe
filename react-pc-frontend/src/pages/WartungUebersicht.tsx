import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, ClipboardCheck, Plus, Search } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { DatePicker } from '../components/ui/datepicker';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { SearchableSelect } from '../components/ui/searchable-select';
import { kundeName as bekannterKundenname, sucheKunden } from '../features/wartung/kundenSuche';
import { useToast } from '../components/ui/toast';
import { cn } from '../lib/utils';
import { aendereProtokollart, sucheProtokolle } from '../features/wartung/api';
import { formatDatum, PROTOKOLLARTEN, STATUS_ANZEIGE } from '../features/wartung/format';
import { fortschrittProzent } from '../features/wartung/gruppierung';
import { NeueWartungDialog } from '../features/wartung/NeueWartungDialog';
import { WartungObjekteTab } from '../features/wartung/WartungObjekteTab';
import { SammelberichtTab } from '../features/wartung/SammelberichtTab';
import { WartungEinstellungenTab } from '../features/wartung/WartungEinstellungenTab';
import { WartungUebernahmeTab } from '../features/wartung/uebernahme/WartungUebernahmeTab';
import { useAuth } from '../auth/AuthContext';
import type { Seite, WartungListeneintrag, WartungProtokoll, WartungStatus } from '../features/wartung/types';

const SEITENGROESSE = 20;


type Reiter = 'protokolle' | 'objekte' | 'sammelbericht' | 'einstellungen' | 'uebernahme';

const STATUS_FILTER = [
    { value: '', label: 'Alle (ohne Archiv)' },
    { value: 'OFFEN', label: STATUS_ANZEIGE.OFFEN.label },
    { value: 'IN_BEARBEITUNG', label: STATUS_ANZEIGE.IN_BEARBEITUNG.label },
    { value: 'ABGESCHLOSSEN', label: STATUS_ANZEIGE.ABGESCHLOSSEN.label },
    { value: 'ARCHIVIERT', label: STATUS_ANZEIGE.ARCHIVIERT.label },
];

function ProtokollKarte({ eintrag }: { eintrag: WartungListeneintrag }) {
    const status = STATUS_ANZEIGE[eintrag.status];
    const prozent = fortschrittProzent({ gesamt: eintrag.pruefpunkteGesamt, bewertet: eintrag.pruefpunkteBewertet });
    const ort = [eintrag.kundeName, eintrag.objektBezeichnung, eintrag.tuerBezeichnung].filter(Boolean).join(' · ');
    return (
        <Link
            to={`/wartung/${eintrag.id}`}
            data-testid={`wartung-${eintrag.id}`}
            className="block rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
            aria-label={`Wartung ${eintrag.nummer} öffnen`}
        >
            <Card className="p-4 transition-shadow hover:shadow-md">
                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div className="flex min-w-0 items-start gap-3">
                        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-rose-100 text-rose-600" aria-hidden="true">
                            <ClipboardCheck className="h-5 w-5" />
                        </span>
                        <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                                <span className="text-base font-bold text-slate-900">{eintrag.nummer}</span>
                                <span className={cn('inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold', status.klasse)}>{status.label}</span>
                                {eintrag.maengel > 0 && (
                                    <span className="inline-flex items-center rounded-full border border-rose-200 bg-rose-50 px-2 py-0.5 text-xs font-semibold text-rose-700">
                                        {eintrag.maengel} {eintrag.maengel === 1 ? 'Mangel' : 'Mängel'}
                                    </span>
                                )}
                            </div>
                            {eintrag.titel && <p className="mt-0.5 text-sm font-medium text-slate-800 break-words">{eintrag.titel}</p>}
                            <p className="mt-0.5 text-sm text-slate-500 break-words">{ort}</p>
                        </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-4 md:flex-col md:items-end md:gap-1.5">
                        <p className="text-sm text-slate-500">{eintrag.protokollTyp} vom {formatDatum(eintrag.datum)}</p>
                        <div className="flex items-center gap-2">
                            <div
                                role="progressbar"
                                aria-label={`Fortschritt ${eintrag.nummer}`}
                                aria-valuemin={0}
                                aria-valuemax={100}
                                aria-valuenow={prozent}
                                className="h-1.5 w-24 overflow-hidden rounded-full bg-slate-100"
                            >
                                <div className={cn('h-full rounded-full', prozent === 100 ? 'bg-emerald-500' : 'bg-rose-600')} style={{ width: `${prozent}%` }} />
                            </div>
                            <span className="text-xs tabular-nums text-slate-500">{eintrag.pruefpunkteBewertet}/{eintrag.pruefpunkteGesamt}</span>
                        </div>
                    </div>
                </div>
            </Card>
        </Link>
    );
}

export default function WartungUebersicht() {
    const toast = useToast();
    const navigate = useNavigate();
    const [suchParameter, setSuchParameter] = useSearchParams();
    const { isAdmin } = useAuth();
    const angefragt = suchParameter.get('tab');
    // Die Einstellungen sind Admin-Sache: Wer sie nicht öffnen darf, sieht die normale Liste.
    const reiter: Reiter = angefragt === 'objekte' || angefragt === 'sammelbericht' || ((angefragt === 'einstellungen' || angefragt === 'uebernahme') && isAdmin) ? angefragt : 'protokolle';

    const [suche, setSuche] = useState('');
    const [suchtext, setSuchtext] = useState('');
    const [status, setStatus] = useState('');
    const [kundeId, setKundeId] = useState('');
    const [kundeName, setKundeName] = useState('');
    const [datumVon, setDatumVon] = useState('');
    const [datumBis, setDatumBis] = useState('');
    const [seite, setSeite] = useState(0);
    const [ergebnis, setErgebnis] = useState<Seite<WartungListeneintrag> | null>(null);
    const [ladeStatus, setLadeStatus] = useState<'laedt' | 'ok' | 'fehler'>('laedt');
    const [dialog, setDialog] = useState<{ offen: boolean; vorbelegung?: { kundeId: number; kundeName: string; objektId?: number; tuerId?: number } }>({ offen: false });
    const anfrageNummer = useRef(0);
    const [ausgewaehlt, setAusgewaehlt] = useState<Set<number>>(new Set());
    const [neueArt, setNeueArt] = useState('');
    const [aendert, setAendert] = useState(false);

    // Der Suchtext wird erst nach einer kurzen Pause abgeschickt, damit nicht jeder Tastendruck eine Anfrage auslöst.
    useEffect(() => {
        const timer = setTimeout(() => { setSuchtext(suche); setSeite(0); }, 350);
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
                seite,
                groesse: SEITENGROESSE,
            });
            // Eine langsame ältere Antwort darf eine neuere nicht überschreiben.
            if (nummer !== anfrageNummer.current) return;
            setErgebnis(antwort);
            setLadeStatus('ok');
        } catch (e) {
            if (nummer !== anfrageNummer.current) return;
            setLadeStatus('fehler');
            toast.error(e instanceof Error ? e.message : 'Die Wartungen konnten nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [kundeId, status, datumVon, datumBis, suchtext, seite, datumFalsch]);

    useEffect(() => { if (reiter === 'protokolle') void laden(); }, [laden, reiter]);

    const waehleReiter = (neu: Reiter) => {
        setSuchParameter(neu === 'protokolle' ? {} : { tab: neu }, { replace: true });
    };
    const reiterListe: [Reiter, string][] = [['protokolle', 'Wartungen'], ['objekte', 'Objekte und Türen'], ['sammelbericht', 'Sammelbericht'], ...(isAdmin ? [['einstellungen', 'Einstellungen'] as [Reiter, string], ['uebernahme', 'Übernahme HandwerkPro'] as [Reiter, string]] : [])];

    const zuruecksetzen = () => {
        setSuche(''); setSuchtext(''); setStatus(''); setKundeId(''); setKundeName(''); setDatumVon(''); setDatumBis(''); setSeite(0);
    };
    const filterAktiv = suche !== '' || status !== '' || kundeId !== '' || datumVon !== '' || datumBis !== '';

    const angelegt = (protokoll: WartungProtokoll) => navigate(`/wartung/${protokoll.id}`);
    const eintraege = ergebnis?.content ?? [];
    const alleGewaehlt = eintraege.length > 0 && eintraege.every((e) => ausgewaehlt.has(e.id));
    const umschalten = (id: number) => setAusgewaehlt((alt) => { const neu = new Set(alt); if (neu.has(id)) neu.delete(id); else neu.add(id); return neu; });
    const alleUmschalten = () => setAusgewaehlt(alleGewaehlt ? new Set() : new Set(eintraege.map((e) => e.id)));

    /** Ändert die Protokollart der gewählten Wartungen und meldet, was nicht geändert werden konnte (zum Beispiel abgeschlossene). */
    const artAendern = async () => {
        if (neueArt === '' || ausgewaehlt.size === 0) return;
        setAendert(true);
        try {
            const r = await aendereProtokollart([...ausgewaehlt], neueArt);
            if (r.geaendert > 0) toast.success(`${r.geaendert} ${r.geaendert === 1 ? 'Wartung ist' : 'Wartungen sind'} jetzt als „${neueArt}" eingetragen.`);
            if (r.uebersprungen.length > 0) {
                toast.warning(`Nicht geändert: ${r.uebersprungen.map((u) => `${u.nummer ?? 'Nr. ' + u.id} (${u.grund})`).join(' · ')}`);
            } else if (r.geaendert === 0) {
                toast.info('Alle gewählten Wartungen hatten diese Protokollart schon.');
            }
            setAusgewaehlt(new Set());
            setNeueArt('');
            void laden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Protokollart konnte nicht geändert werden.');
        } finally {
            setAendert(false);
        }
    };

    return (
        <PageLayout
            ribbonCategory="Projektmanagement"
            title="Wartung"
            subtitle="Wartungsprotokolle, Objekte und Türen im Überblick."
            actions={
                <Button onClick={() => setDialog({ offen: true })}>
                    <Plus className="h-4 w-4" aria-hidden="true" /> Neue Wartung
                </Button>
            }
        >
            <div role="tablist" aria-label="Bereiche der Wartung" className="flex flex-wrap gap-1 border-b border-slate-200">
                {reiterListe.map(([wert, beschriftung]) => (
                    <button
                        key={wert}
                        type="button"
                        role="tab"
                        id={`wartung-tab-${wert}`}
                        aria-selected={reiter === wert}
                        aria-controls={`wartung-panel-${wert}`}
                        onClick={() => waehleReiter(wert)}
                        className={cn(
                            'px-3 py-2 text-sm font-medium rounded-t-lg transition-colors whitespace-nowrap focus:outline-none focus:ring-2 focus:ring-rose-500',
                            reiter === wert ? 'bg-rose-50 text-rose-700 border-b-2 border-rose-600' : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50',
                        )}
                    >
                        {beschriftung}
                    </button>
                ))}
            </div>

            {reiter === 'sammelbericht' && <SammelberichtTab />}

            {reiter === 'einstellungen' && <WartungEinstellungenTab />}

            {reiter === 'uebernahme' && isAdmin && <WartungUebernahmeTab />}

            {reiter === 'objekte' && (
                <section role="tabpanel" id="wartung-panel-objekte" aria-labelledby="wartung-tab-objekte">
                    <WartungObjekteTab />
                </section>
            )}

            {reiter === 'protokolle' && (
                <section role="tabpanel" id="wartung-panel-protokolle" aria-labelledby="wartung-tab-protokolle" className="space-y-6">
                    <Card className="p-4">
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-5">
                            <div className="xl:col-span-2">
                                <Label htmlFor="wartung-suche">Suche</Label>
                                <div className="relative">
                                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                                    <Input id="wartung-suche" className="pl-9" value={suche} maxLength={100} placeholder="Nummer, Kunde, Objekt oder Tür"
                                        onChange={(e) => setSuche(e.target.value)} />
                                </div>
                            </div>
                            <div>
                                <Label htmlFor="wartung-status">Status</Label>
                                <Select id="wartung-status" value={status} onChange={(wert) => { setStatus(wert); setSeite(0); }} options={STATUS_FILTER} />
                            </div>
                            <div>
                                <Label id="wartung-filter-kunde-label">Kunde</Label>
                                <div role="group" aria-labelledby="wartung-filter-kunde-label">
                                    <SearchableSelect
                                        value={kundeId}
                                        selectedLabel={kundeId === '' ? 'Alle Kunden' : kundeName}
                                        onChange={(wert) => { setKundeId(wert); setKundeName(bekannterKundenname(wert)); setSeite(0); }}
                                        onSearch={(term) => sucheKunden(term, true)}
                                        placeholder="Alle Kunden"
                                        searchPlaceholder="Name, Kundennummer oder Ort"
                                    />
                                </div>
                            </div>
                            <div className="flex items-end">
                                <Button variant="outline" onClick={zuruecksetzen} disabled={!filterAktiv} className="w-full"
                                    title={filterAktiv ? undefined : 'Es sind keine Filter gesetzt'}>
                                    Zurücksetzen
                                </Button>
                            </div>
                            <div>
                                <Label id="wartung-von-label">Datum von</Label>
                                <div role="group" aria-labelledby="wartung-von-label">
                                    <DatePicker value={datumVon} onChange={(w) => { setDatumVon(w); setSeite(0); }} placeholder="Beginn" max={datumBis || undefined} />
                                </div>
                            </div>
                            <div>
                                <Label id="wartung-bis-label">Datum bis</Label>
                                <div role="group" aria-labelledby="wartung-bis-label">
                                    <DatePicker value={datumBis} onChange={(w) => { setDatumBis(w); setSeite(0); }} placeholder="Ende" min={datumVon || undefined} />
                                </div>
                            </div>
                        </div>
                        {datumFalsch && <p role="alert" className="mt-3 text-sm text-rose-700">Das Datum „von" liegt nach dem Datum „bis".</p>}
                    </Card>

                    {ladeStatus === 'laedt' && (
                        <div className="flex flex-col gap-3" aria-busy="true" aria-label="Wartungen werden geladen">
                            {[0, 1, 2].map((i) => <div key={i} className="motion-safe:animate-pulse bg-white border border-slate-200 rounded-lg shadow-sm h-20" />)}
                        </div>
                    )}

                    {ladeStatus === 'fehler' && (
                        <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg">
                            <p>Die Wartungen konnten nicht geladen werden.</p>
                            <Button variant="outline" size="sm" className="mt-3" onClick={() => void laden()}>Erneut versuchen</Button>
                        </div>
                    )}

                    {ladeStatus === 'ok' && eintraege.length === 0 && (
                        <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg">
                            <ClipboardCheck className="mx-auto mb-2 h-6 w-6 text-slate-400" aria-hidden="true" />
                            <p>{filterAktiv ? 'Keine Wartungen zu diesen Filtern gefunden.' : 'Noch keine Wartungen angelegt.'}</p>
                            {!filterAktiv && (
                                <Button size="sm" className="mt-3" onClick={() => setDialog({ offen: true })}>
                                    <Plus className="h-4 w-4" aria-hidden="true" /> Erste Wartung anlegen
                                </Button>
                            )}
                        </div>
                    )}

                    {ladeStatus === 'ok' && eintraege.length > 0 && (
                        <>
                            <ul className="flex flex-col gap-3">
                                {eintraege.map((e) => (
                                    <li key={e.id} className="flex items-start gap-3">
                                        <input type="checkbox" className="mt-6 h-5 w-5 shrink-0 accent-rose-600" checked={ausgewaehlt.has(e.id)} onChange={() => umschalten(e.id)}
                                            aria-label={`Wartung ${e.nummer} auswählen`} />
                                        <div className="min-w-0 flex-1"><ProtokollKarte eintrag={e} /></div>
                                    </li>
                                ))}
                            </ul>
                            <div className="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-white p-3" data-testid="sammelaenderung">
                                <label className="flex items-center gap-2 text-sm text-slate-700">
                                    <input type="checkbox" className="h-5 w-5 accent-rose-600" checked={alleGewaehlt} onChange={alleUmschalten} aria-label="Alle Wartungen dieser Seite auswählen" />
                                    Alle auf dieser Seite
                                </label>
                                {ausgewaehlt.size > 0 && (
                                    <>
                                        <span className="text-sm font-medium text-slate-900">{ausgewaehlt.size} ausgewählt</span>
                                        <div className="min-w-[14rem]">
                                            <Select id="sammel-art" aria-label="Neue Protokollart" value={neueArt} onChange={setNeueArt}
                                                options={[{ value: '', label: 'Protokollart wählen …' }, ...PROTOKOLLARTEN.map((a) => ({ value: a, label: a }))]} />
                                        </div>
                                        <Button size="sm" onClick={() => void artAendern()} disabled={neueArt === '' || aendert}>Protokollart ändern</Button>
                                        <Button variant="ghost" size="sm" onClick={() => setAusgewaehlt(new Set())}>Auswahl aufheben</Button>
                                    </>
                                )}
                            </div>
                            <div className="flex items-center justify-between gap-3">
                                <p className="text-sm text-slate-500">
                                    {ergebnis!.totalElements} {ergebnis!.totalElements === 1 ? 'Wartung' : 'Wartungen'}
                                    {ergebnis!.totalPages > 1 && ` · Seite ${ergebnis!.number + 1} von ${ergebnis!.totalPages}`}
                                </p>
                                {ergebnis!.totalPages > 1 && (
                                    <div className="flex gap-2">
                                        <Button variant="outline" size="sm" disabled={seite === 0} onClick={() => setSeite((s) => Math.max(0, s - 1))}>
                                            <ChevronLeft className="h-4 w-4" aria-hidden="true" /> Zurück
                                        </Button>
                                        <Button variant="outline" size="sm" disabled={seite + 1 >= ergebnis!.totalPages} onClick={() => setSeite((s) => s + 1)}>
                                            Weiter <ChevronRight className="h-4 w-4" aria-hidden="true" />
                                        </Button>
                                    </div>
                                )}
                            </div>
                        </>
                    )}
                </section>
            )}

            <NeueWartungDialog
                open={dialog.offen}
                onOpenChange={(offen) => setDialog((alt) => ({ ...alt, offen }))}
                vorbelegung={dialog.vorbelegung}
                onAngelegt={angelegt}
            />
        </PageLayout>
    );
}
