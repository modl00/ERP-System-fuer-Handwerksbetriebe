import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Info, Loader2, Plus, Save } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { useConfirm } from '../components/ui/confirm-dialog';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { useToast } from '../components/ui/toast';
import { cn } from '../lib/utils';
import { ladeModulDetail, ladeModulUebersicht, legeModulAn, speichereModul } from '../features/wartung/api';
import { DatenfeldZeile } from '../features/wartung/editor/DatenfeldZeile';
import {
    bereinige, entferne, entferneBauart, ersetze, fehlerImModul, istGeaendert, kopieAlsNeu, leererAbschnitt, leeresDatenfeld, leeresModul,
    neueZeile, schluesselVergeben, verschiebe, verschiebeImBereich,
} from '../features/wartung/editor/modulBearbeiten';
import { ZeilenKnoepfe } from '../features/wartung/editor/ZeilenKnoepfe';
import { INTERVALL_OPTIONEN } from '../features/wartung/format';
import { ModulIcon } from '../features/wartung/ModulIcon';
import { MODUL_ICON_BEZEICHNUNG, MODUL_ICON_NAMEN } from '../features/wartung/modulIcons';
import type { ModulAbschnitt, ModulDetail, ModulUebersicht, WartungBereich } from '../features/wartung/types';

const ZURUECK = '/wartung?tab=einstellungen';

const KATEGORIEN = [
    { value: 'BASIS', label: 'Basis-Element (bestimmt, was geprüft wird)' },
    { value: 'ZUSATZ', label: 'Zusatzmodul (kommt zu einem Basis-Element dazu)' },
];

function intervallOptionen(aktuell: number) {
    const werte = INTERVALL_OPTIONEN.includes(aktuell) ? INTERVALL_OPTIONEN : [...INTERVALL_OPTIONEN, aktuell].sort((a, b) => a - b);
    return werte.map((m) => ({ value: String(m), label: m === 1 ? '1 Monat' : `${m} Monate` }));
}

function Bereichskarte({ titel, hinweis, children }: { titel: string; hinweis?: string; children: React.ReactNode }) {
    return (
        <Card className="space-y-4 p-4 sm:p-6">
            <div>
                <h3 className="text-base font-semibold text-slate-900">{titel}</h3>
                {hinweis && <p className="mt-0.5 text-sm text-slate-500">{hinweis}</p>}
            </div>
            {children}
        </Card>
    );
}

/** Ein Modul des Wartungskatalogs anlegen oder bearbeiten (nur Admin). */
export default function WartungModulEditor() {
    const { id } = useParams();
    const [suchParameter] = useSearchParams();
    const navigate = useNavigate();
    const toast = useToast();
    const confirm = useConfirm();
    const istNeu = id === 'neu';
    const kopieVon = suchParameter.get('kopie');

    const [modul, setModul] = useState<ModulDetail | null>(null);
    const [original, setOriginal] = useState<ModulDetail | null>(null);
    const [alleModule, setAlleModule] = useState<ModulUebersicht[]>([]);
    const [ladeStatus, setLadeStatus] = useState<'laedt' | 'ok' | 'fehler'>('laedt');
    const [speichert, setSpeichert] = useState(false);
    const [versucht, setVersucht] = useState(false);

    const laden = useCallback(async () => {
        setLadeStatus('laedt');
        try {
            const [geladen, liste] = await Promise.all([
                istNeu
                    ? (kopieVon ? ladeModulDetail(Number(kopieVon)).then(kopieAlsNeu) : Promise.resolve(leeresModul()))
                    : ladeModulDetail(Number(id)),
                ladeModulUebersicht(),
            ]);
            const mitSchluesseln = schluesselVergeben(geladen);
            setModul(mitSchluesseln);
            // Eine Kopie gilt als geändert, denn sie ist noch nicht gespeichert. Ein geladenes oder leeres neues Modul nicht.
            setOriginal(istNeu && kopieVon ? null : mitSchluesseln);
            setAlleModule(liste);
            setLadeStatus('ok');
        } catch (e) {
            setLadeStatus('fehler');
            toast.error(e instanceof Error ? e.message : 'Das Modul konnte nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [id, istNeu, kopieVon]);

    useEffect(() => { void laden(); }, [laden]);

    // Der Browser warnt beim Schließen des Tabs, solange etwas ungespeichert ist.
    const geaendert = modul !== null && istGeaendert(original, modul);
    useEffect(() => {
        if (!geaendert) return;
        const warnen = (e: BeforeUnloadEvent) => { e.preventDefault(); };
        window.addEventListener('beforeunload', warnen);
        return () => window.removeEventListener('beforeunload', warnen);
    }, [geaendert]);

    const aendere = (fn: (alt: ModulDetail) => ModulDetail) => setModul((alt) => (alt ? schluesselVergeben(fn(alt)) : alt));

    const basisModule = useMemo(() => alleModule.filter((m) => m.kategorie === 'BASIS' && m.id !== modul?.id), [alleModule, modul?.id]);

    const zurueck = async () => {
        if (geaendert) {
            const ja = await confirm({
                title: 'Änderungen verwerfen?',
                message: 'Es gibt Änderungen, die noch nicht gespeichert sind. Sie gehen verloren, wenn Sie jetzt gehen.',
                confirmLabel: 'Verwerfen',
                variant: 'warning',
            });
            if (!ja) return;
        }
        navigate(ZURUECK);
    };

    const speichern = async () => {
        if (!modul) return;
        setVersucht(true);
        const bereinigt = bereinige(modul);
        const problem = fehlerImModul(bereinigt);
        if (problem) { toast.error(problem); return; }
        setSpeichert(true);
        try {
            const gespeichert = schluesselVergeben(modul.id === null ? await legeModulAn(bereinigt) : await speichereModul(bereinigt));
            toast.success(`„${gespeichert.name}“ gespeichert. Es gilt für neue Wartungen.`);
            setModul(gespeichert);
            setOriginal(gespeichert);
            setVersucht(false);
            if (modul.id === null) navigate(`/wartung/module/${gespeichert.id}`, { replace: true });
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Modul konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const speicherknopf = (
        <Button onClick={() => void speichern()} disabled={!modul || speichert || (!geaendert && modul?.id !== null)}>
            {speichert ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Save className="h-4 w-4" aria-hidden="true" />}
            Speichern
        </Button>
    );

    return (
        <PageLayout
            ribbonCategory="Wartung"
            title={istNeu ? 'Neues Modul' : 'Modul bearbeiten'}
            subtitle={modul && !istNeu ? modul.name : 'Bausteine der Checkliste festlegen'}
            actions={
                <div className="flex flex-wrap gap-2">
                    <Button variant="outline" onClick={() => void zurueck()}>
                        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Zu den Einstellungen
                    </Button>
                    {ladeStatus === 'ok' && speicherknopf}
                </div>
            }
        >
            {ladeStatus === 'laedt' && (
                <div className="flex items-center justify-center gap-2 py-16 text-slate-500" role="status">
                    <Loader2 className="h-5 w-5 motion-safe:animate-spin" aria-hidden="true" /> Modul wird geladen …
                </div>
            )}
            {ladeStatus === 'fehler' && (
                <Card className="p-8 text-center">
                    <p className="font-medium text-slate-800">Das Modul konnte nicht geladen werden.</p>
                    <div className="mt-3 flex justify-center gap-2">
                        <Button size="sm" onClick={() => void laden()}>Noch einmal versuchen</Button>
                        <Link to={ZURUECK} className="inline-flex items-center rounded border border-rose-200 bg-white px-3 py-1.5 text-sm text-rose-700 hover:bg-rose-50">Zurück</Link>
                    </div>
                </Card>
            )}
            {ladeStatus === 'ok' && modul && (
                <div className="space-y-6">
                    <div role="note" className="flex gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
                        <Info className="mt-0.5 h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
                        <p>
                            Änderungen gelten nur für <strong className="font-semibold">neue Wartungen</strong>. Bestehende Protokolle behalten ihren Inhalt,
                            auch wenn hier Prüfpunkte geändert oder gelöscht werden.
                            {modul.anzahlTueren > 0 && <> Dieses Modul ist an {modul.anzahlTueren} {modul.anzahlTueren === 1 ? 'Tür' : 'Türen'} im Einsatz.</>}
                        </p>
                    </div>

                    <Bereichskarte titel="Allgemein">
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                            <div className="md:col-span-2">
                                <Label htmlFor="modul-name">Name</Label>
                                <Input id="modul-name" value={modul.name} maxLength={120} placeholder="z. B. Brandschutztür" aria-invalid={versucht && modul.name.trim() === ''}
                                    onChange={(e) => aendere((m) => ({ ...m, name: e.target.value }))} />
                                {versucht && modul.name.trim() === '' && <p role="alert" className="mt-1 text-sm text-rose-700">Bitte einen Namen eintragen.</p>}
                            </div>
                            <div className="md:col-span-2">
                                <Label htmlFor="modul-beschreibung">Beschreibung (optional)</Label>
                                <textarea id="modul-beschreibung" className="min-h-[70px] w-full rounded-lg border border-slate-200 p-3 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                                    value={modul.beschreibung ?? ''} maxLength={2000} placeholder="Wofür ist das Modul gedacht?"
                                    onChange={(e) => aendere((m) => ({ ...m, beschreibung: e.target.value === '' ? null : e.target.value }))} />
                            </div>
                            <div>
                                <Label htmlFor="modul-art">Art</Label>
                                <Select id="modul-art" value={modul.kategorie} options={KATEGORIEN} disabled={modul.anzahlTueren > 0}
                                    onChange={(wert) => aendere((m) => ({ ...m, kategorie: wert as ModulDetail['kategorie'], andockbarAn: wert === 'BASIS' ? [] : m.andockbarAn }))} />
                                {modul.anzahlTueren > 0 && <p className="mt-1 text-sm text-slate-500">Die Art lässt sich nicht ändern, solange Türen das Modul verwenden.</p>}
                            </div>
                            <div>
                                <Label htmlFor="modul-intervall">Prüfintervall (Vorschlag)</Label>
                                <Select id="modul-intervall" value={String(modul.standardIntervallMonate)} options={intervallOptionen(modul.standardIntervallMonate)}
                                    onChange={(wert) => aendere((m) => ({ ...m, standardIntervallMonate: Number(wert) }))} />
                            </div>
                            <div className="md:col-span-2">
                                <Label id="modul-symbol-label">Symbol</Label>
                                <div role="radiogroup" aria-labelledby="modul-symbol-label" className="flex flex-wrap gap-2">
                                    {[null, ...MODUL_ICON_NAMEN].map((name) => (
                                        <button
                                            key={name ?? 'standard'}
                                            type="button"
                                            role="radio"
                                            aria-checked={(modul.icon ?? null) === name}
                                            aria-label={name === null ? 'Symbol Standard' : `Symbol ${MODUL_ICON_BEZEICHNUNG[name] ?? name}`}
                                            title={name === null ? 'Standard' : (MODUL_ICON_BEZEICHNUNG[name] ?? name)}
                                            onClick={() => aendere((m) => ({ ...m, icon: name }))}
                                            className={cn(
                                                'flex h-11 w-11 items-center justify-center rounded-lg border transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500',
                                                (modul.icon ?? null) === name ? 'border-rose-400 bg-rose-50 text-rose-700' : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300',
                                            )}
                                        >
                                            <ModulIcon name={name} />
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <label className="flex cursor-pointer items-start gap-2 text-sm text-slate-700 md:col-span-2">
                                <input type="checkbox" className="mt-0.5 h-4 w-4 rounded border-slate-300 accent-rose-600" checked={modul.aktiv}
                                    onChange={(e) => aendere((m) => ({ ...m, aktiv: e.target.checked }))} />
                                <span>
                                    <span className="font-medium">Bei neuen Wartungen zur Auswahl anbieten</span>
                                    <span className="block text-slate-500">Ohne Haken bleibt das Modul gespeichert, steht aber nicht mehr zur Wahl.</span>
                                </span>
                            </label>
                        </div>

                        {modul.kategorie === 'ZUSATZ' && (
                            <fieldset className="rounded-lg border border-slate-200 p-3">
                                <legend className="px-1 text-sm font-medium text-slate-700">Passt zu diesen Basis-Elementen</legend>
                                <p className="mb-2 text-sm text-slate-500">Ohne Auswahl lässt sich das Zusatzmodul an jedes Basis-Element hängen.</p>
                                {basisModule.length === 0 && <p className="text-sm text-slate-500">Es gibt noch kein Basis-Element.</p>}
                                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                                    {basisModule.map((b) => (
                                        <label key={b.id} className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
                                            <input type="checkbox" className="h-4 w-4 rounded border-slate-300 accent-rose-600" checked={modul.andockbarAn.includes(b.id)}
                                                onChange={(e) => aendere((m) => ({ ...m, andockbarAn: e.target.checked ? [...m.andockbarAn, b.id] : m.andockbarAn.filter((x) => x !== b.id) }))} />
                                            {b.name}{!b.aktiv && <span className="text-slate-400"> (deaktiviert)</span>}
                                        </label>
                                    ))}
                                </div>
                            </fieldset>
                        )}
                    </Bereichskarte>

                    <Bereichskarte titel="Bauarten" hinweis="Zum Beispiel „1-flügelig“ oder „Schiebetür“. Ohne Bauarten gilt das Modul für alle gleich. Mit Bauarten muss bei jeder Wartung genau eine gewählt werden, und einzelne Prüfpunkte können auf eine Bauart beschränkt werden.">
                        {modul.untertypen.length === 0 && <p className="text-sm text-slate-500">Keine Bauarten.</p>}
                        <div className="space-y-2">
                            {modul.untertypen.map((u, i) => (
                                <div key={u.clientKey} className="flex items-end gap-2">
                                    <div className="min-w-0 flex-1">
                                        <Label htmlFor={`bauart-${u.clientKey}`}>Bauart {i + 1}</Label>
                                        <Input id={`bauart-${u.clientKey}`} value={u.label} maxLength={100} placeholder="z. B. 2-flügelig"
                                            onChange={(e) => aendere((m) => ({ ...m, untertypen: ersetze(m.untertypen, i, { ...u, label: e.target.value }) }))} />
                                    </div>
                                    <ZeilenKnoepfe bezeichnung={`Bauart ${i + 1}`}
                                        onHoch={i > 0 ? () => aendere((m) => ({ ...m, untertypen: verschiebe(m.untertypen, i, -1) })) : undefined}
                                        onRunter={i < modul.untertypen.length - 1 ? () => aendere((m) => ({ ...m, untertypen: verschiebe(m.untertypen, i, 1) })) : undefined}
                                        onEntfernen={() => {
                                            const { modul: neu, betroffenePruefpunkte } = entferneBauart(modul, i);
                                            aendere(() => neu);
                                            if (betroffenePruefpunkte > 0) {
                                                toast.info(`${betroffenePruefpunkte} ${betroffenePruefpunkte === 1 ? 'Prüfpunkt gilt' : 'Prüfpunkte gelten'} jetzt für alle Bauarten.`);
                                            }
                                        }} />
                                </div>
                            ))}
                        </div>
                        <Button variant="outline" size="sm" onClick={() => aendere((m) => ({ ...m, untertypen: [...m.untertypen, neueZeile({ id: null, schluessel: null, label: '' })] }))}>
                            <Plus className="h-4 w-4" aria-hidden="true" /> Bauart hinzufügen
                        </Button>
                        {modul.untertypen.some((u) => u.schluessel === null) && (
                            <p className="text-sm text-slate-500">Neue Bauarten lassen sich für einzelne Prüfpunkte wählen, sobald das Modul gespeichert ist.</p>
                        )}
                    </Bereichskarte>

                    <AbschnittsBereich bereich="PRUEFPUNKTE" modul={modul} aendere={aendere} />
                    <AbschnittsBereich bereich="DATENFELDER" modul={modul} aendere={aendere} />

                    <div className="flex justify-end">{speicherknopf}</div>
                </div>
            )}
        </PageLayout>
    );
}

// ── Abschnitte (Prüfpunkte und Datenfelder) ─────────────────────────────────

interface AbschnittsBereichProps {
    bereich: WartungBereich;
    modul: ModulDetail;
    aendere: (fn: (alt: ModulDetail) => ModulDetail) => void;
}

function AbschnittsBereich({ bereich, modul, aendere }: AbschnittsBereichProps) {
    const confirm = useConfirm();
    const istPruefung = bereich === 'PRUEFPUNKTE';
    const titel = istPruefung ? 'Prüfpunkte' : 'Datenfelder';
    const hinweis = istPruefung
        ? 'Die Checkliste, die bei der Wartung abgehakt wird (i.O., n.i.O., n.a.). Gruppiert in Abschnitte mit eigener Überschrift.'
        : 'Angaben, die bei der Wartung erfasst werden, zum Beispiel Hersteller, Baujahr oder Zustand.';
    const abschnitte = modul.abschnitte.map((a, index) => ({ a, index })).filter(({ a }) => a.bereich === bereich);
    const bauartOptionen = [
        { value: '', label: 'Für alle Bauarten' },
        ...modul.untertypen.filter((u) => u.schluessel !== null).map((u) => ({ value: u.schluessel as string, label: `Nur bei ${u.label || '(ohne Name)'}` })),
    ];

    const setzeAbschnitt = (index: number, neu: ModulAbschnitt) => aendere((m) => ({ ...m, abschnitte: ersetze(m.abschnitte, index, neu) }));

    const entferneAbschnitt = async (index: number, a: ModulAbschnitt) => {
        const inhalt = a.pruefpunkte.length + a.datenfelder.length;
        if (inhalt > 0) {
            const ja = await confirm({
                title: 'Abschnitt entfernen?',
                message: `Der Abschnitt „${a.label || 'ohne Namen'}“ mit ${inhalt} ${inhalt === 1 ? 'Eintrag' : 'Einträgen'} wird entfernt, sobald Sie speichern.`,
                confirmLabel: 'Entfernen',
                variant: 'danger',
            });
            if (!ja) return;
        }
        aendere((m) => ({ ...m, abschnitte: entferne(m.abschnitte, index) }));
    };

    return (
        <Bereichskarte titel={titel} hinweis={hinweis}>
            {abschnitte.length === 0 && <p className="text-sm text-slate-500">Noch keine Abschnitte.</p>}
            <div className="space-y-5">
                {abschnitte.map(({ a, index }, position) => {
                    const abschnittName = `${istPruefung ? 'Prüfpunkt-Abschnitt' : 'Datenfeld-Abschnitt'} ${position + 1}`;
                    return (
                        <div key={a.clientKey} className="space-y-3 rounded-lg border border-slate-200 bg-slate-50/60 p-3 sm:p-4" data-testid={`abschnitt-${a.clientKey}`}>
                            <div className="flex items-end gap-2">
                                <div className="min-w-0 flex-1">
                                    <Label htmlFor={`abschnitt-${a.clientKey}`}>Überschrift des Abschnitts</Label>
                                    <Input id={`abschnitt-${a.clientKey}`} aria-label={`Überschrift von ${abschnittName}`} value={a.label} maxLength={200} placeholder={istPruefung ? 'z. B. Prüfpunkte Brandschutztür' : 'z. B. Datenaufnahme'}
                                        onChange={(e) => setzeAbschnitt(index, { ...a, label: e.target.value })} />
                                </div>
                                <ZeilenKnoepfe bezeichnung={abschnittName}
                                    onHoch={position > 0 ? () => aendere((m) => ({ ...m, abschnitte: verschiebeImBereich(m.abschnitte, index, -1) })) : undefined}
                                    onRunter={position < abschnitte.length - 1 ? () => aendere((m) => ({ ...m, abschnitte: verschiebeImBereich(m.abschnitte, index, 1) })) : undefined}
                                    onEntfernen={() => void entferneAbschnitt(index, a)} />
                            </div>

                            {istPruefung ? (
                                <div className="space-y-2">
                                    {a.pruefpunkte.length === 0 && <p className="text-sm text-slate-500">Noch keine Prüfpunkte in diesem Abschnitt.</p>}
                                    {a.pruefpunkte.map((p, i) => (
                                        <div key={p.clientKey} className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-3 md:flex-row md:items-end">
                                            <div className="min-w-0 flex-1">
                                                <Label htmlFor={`punkt-${p.clientKey}`}>Prüfpunkt {i + 1}</Label>
                                                <Input id={`punkt-${p.clientKey}`} aria-label={`Prüfpunkt ${i + 1} in ${abschnittName}`} value={p.label} maxLength={500} placeholder="Was ist zu prüfen?"
                                                    onChange={(e) => setzeAbschnitt(index, { ...a, pruefpunkte: ersetze(a.pruefpunkte, i, { ...p, label: e.target.value }) })} />
                                            </div>
                                            {modul.untertypen.length > 0 && (
                                                <div className="md:w-60">
                                                    <Label htmlFor={`punkt-bauart-${p.clientKey}`}>Gilt für</Label>
                                                    <Select id={`punkt-bauart-${p.clientKey}`} aria-label={`Gilt für Prüfpunkt ${i + 1} in ${abschnittName}`} value={p.nurBeiUntertyp ?? ''}
                                                        options={p.nurBeiUntertyp !== null && !bauartOptionen.some((o) => o.value === p.nurBeiUntertyp)
                                                            ? [...bauartOptionen, { value: p.nurBeiUntertyp, label: 'Entfernte Bauart' }] : bauartOptionen}
                                                        onChange={(wert) => setzeAbschnitt(index, { ...a, pruefpunkte: ersetze(a.pruefpunkte, i, { ...p, nurBeiUntertyp: wert === '' ? null : wert }) })} />
                                                </div>
                                            )}
                                            <ZeilenKnoepfe bezeichnung={`Prüfpunkt ${i + 1} in ${abschnittName}`}
                                                onHoch={i > 0 ? () => setzeAbschnitt(index, { ...a, pruefpunkte: verschiebe(a.pruefpunkte, i, -1) }) : undefined}
                                                onRunter={i < a.pruefpunkte.length - 1 ? () => setzeAbschnitt(index, { ...a, pruefpunkte: verschiebe(a.pruefpunkte, i, 1) }) : undefined}
                                                onEntfernen={() => setzeAbschnitt(index, { ...a, pruefpunkte: entferne(a.pruefpunkte, i) })} />
                                        </div>
                                    ))}
                                    <Button variant="outline" size="sm" onClick={() => setzeAbschnitt(index, { ...a, pruefpunkte: [...a.pruefpunkte, neueZeile({ id: null, label: '', nurBeiUntertyp: null })] })}>
                                        <Plus className="h-4 w-4" aria-hidden="true" /> Prüfpunkt hinzufügen
                                    </Button>
                                </div>
                            ) : (
                                <div className="space-y-2">
                                    {a.datenfelder.length === 0 && <p className="text-sm text-slate-500">Noch keine Datenfelder in diesem Abschnitt.</p>}
                                    {a.datenfelder.map((d, i) => (
                                        <DatenfeldZeile
                                            key={d.clientKey}
                                            idPrefix={`feld-${d.clientKey}`}
                                            nummer={i + 1}
                                            kontext={abschnittName}
                                            feld={d}
                                            onChange={(neu) => setzeAbschnitt(index, { ...a, datenfelder: ersetze(a.datenfelder, i, neu) })}
                                            onHoch={i > 0 ? () => setzeAbschnitt(index, { ...a, datenfelder: verschiebe(a.datenfelder, i, -1) }) : undefined}
                                            onRunter={i < a.datenfelder.length - 1 ? () => setzeAbschnitt(index, { ...a, datenfelder: verschiebe(a.datenfelder, i, 1) }) : undefined}
                                            onEntfernen={() => setzeAbschnitt(index, { ...a, datenfelder: entferne(a.datenfelder, i) })}
                                        />
                                    ))}
                                    <Button variant="outline" size="sm" onClick={() => setzeAbschnitt(index, { ...a, datenfelder: [...a.datenfelder, neueZeile(leeresDatenfeld())] })}>
                                        <Plus className="h-4 w-4" aria-hidden="true" /> Datenfeld hinzufügen
                                    </Button>
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
            <Button variant="outline" size="sm" onClick={() => aendere((m) => ({ ...m, abschnitte: [...m.abschnitte, leererAbschnitt(bereich)] }))}>
                <Plus className="h-4 w-4" aria-hidden="true" /> Abschnitt hinzufügen
            </Button>
        </Bereichskarte>
    );
}
