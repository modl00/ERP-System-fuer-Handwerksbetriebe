import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ClipboardCheck, FileText, Loader2, Pencil, Play, RotateCcw, Unlink } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { useConfirm } from '../components/ui/confirm-dialog';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { useToast } from '../components/ui/toast';
import { useAuth } from '../auth/AuthContext';
import { cn } from '../lib/utils';
import { heuteIso } from '../lib/datum';
import { ladeAnlage, ladeModule, ladeProjekteDesKunden, legeProtokollAn, ordneProjektZu } from '../features/wartung/api';
import { formatDatum, STATUS_ANZEIGE } from '../features/wartung/format';
import { TuerDialog } from '../features/wartung/TuerDialog';
import type { Bezug, WartungAnlage as Anlage, WartungModul, WartungTuer } from '../features/wartung/types';

function nummer2(n: number): string {
    return String(n).padStart(2, '0');
}

/**
 * Eine Wartung (Anlage, Tür oder Tor) mit allem Drumherum: Angaben, Projekt, Feldeinsatz starten oder fortsetzen
 * und die Historie der bisherigen Prüfungen. Von hier aus lässt sich aus der letzten abgeschlossenen oder aus einer
 * früheren Prüfung eine neue starten, die alte bleibt dabei immer unverändert.
 */
export default function WartungAnlage() {
    const { tuerId } = useParams();
    const navigate = useNavigate();
    const toast = useToast();
    const confirm = useConfirm();
    const { user } = useAuth();
    const mitarbeiterId = user?.mitarbeiter?.id ?? null;

    const [anlage, setAnlage] = useState<Anlage | null>(null);
    const [ladeStatus, setLadeStatus] = useState<'laedt' | 'ok' | 'fehler'>('laedt');
    const [startet, setStartet] = useState(false);
    const [werteUebernehmen, setWerteUebernehmen] = useState(true);
    const [projekte, setProjekte] = useState<Bezug[]>([]);
    const [gewaehltesProjekt, setGewaehltesProjekt] = useState('');
    const [projektArbeitet, setProjektArbeitet] = useState(false);
    const [module, setModule] = useState<WartungModul[]>([]);
    const [dialogOffen, setDialogOffen] = useState(false);

    const laden = useCallback(async () => {
        setLadeStatus('laedt');
        try {
            const geladen = await ladeAnlage(Number(tuerId));
            setAnlage(geladen);
            setLadeStatus('ok');
            ladeProjekteDesKunden(geladen.kunde.id).then(setProjekte).catch(() => setProjekte([]));
        } catch (e) {
            setLadeStatus('fehler');
            toast.error(e instanceof Error ? e.message : 'Die Wartung konnte nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [tuerId]);

    useEffect(() => { void laden(); }, [laden]);

    const starte = async (vorlageProtokollId: number | null, werte: boolean) => {
        if (!anlage || startet) return;
        setStartet(true);
        try {
            const protokoll = await legeProtokollAn({
                kundeId: anlage.kunde.id,
                objektId: anlage.objekt.id,
                tuerId: anlage.id,
                technikerId: mitarbeiterId,
                titel: '',
                datum: heuteIso(),
                intervallMonate: null,
                // Mit Vorlage kommen die Module aus der früheren Prüfung, ohne die aus der Wartung
                module: vorlageProtokollId !== null ? [] : anlage.module.map((m) => ({ modulId: m.modulId, untertypSchluessel: m.bauartSchluessel })),
                bemerkungen: '',
                vorlageProtokollId,
                werteUebernehmen: werte,
            });
            toast.success(`Feldeinsatz ${protokoll.nummer} gestartet.`);
            navigate(`/wartung/${protokoll.id}`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Feldeinsatz konnte nicht gestartet werden.');
            // Ein inzwischen gestarteter Feldeinsatz soll sichtbar werden
            void laden();
        } finally {
            setStartet(false);
        }
    };

    const projektSetzen = async (projektId: number | null) => {
        if (!anlage) return;
        if (projektId === null) {
            const ja = await confirm({
                title: 'Projekt entfernen?',
                message: 'Die Wartung wird vom Projekt getrennt. Bestehende Prüfungen und ihre Berichte bleiben, wie sie sind.',
                confirmLabel: 'Vom Projekt trennen',
                variant: 'warning',
            });
            if (!ja) return;
        }
        setProjektArbeitet(true);
        try {
            setAnlage(await ordneProjektZu(anlage.id, projektId));
            setGewaehltesProjekt('');
            toast.success(projektId === null ? 'Die Wartung ist keinem Projekt mehr zugeordnet.' : 'Die Wartung ist dem Projekt zugeordnet.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Projekt konnte nicht zugeordnet werden.');
        } finally {
            setProjektArbeitet(false);
        }
    };

    const bearbeiten = async () => {
        if (module.length === 0) {
            try {
                setModule(await ladeModule());
            } catch (e) {
                toast.error(e instanceof Error ? e.message : 'Die Module konnten nicht geladen werden.');
                return;
            }
        }
        setDialogOffen(true);
    };

    if (ladeStatus === 'laedt' && !anlage) {
        return (
            <PageLayout ribbonCategory="Wartung" title="Wartung">
                <div className="flex items-center justify-center gap-2 py-16 text-slate-500" role="status">
                    <Loader2 className="h-5 w-5 motion-safe:animate-spin" aria-hidden="true" /> Wartung wird geladen …
                </div>
            </PageLayout>
        );
    }
    if (!anlage) {
        return (
            <PageLayout ribbonCategory="Wartung" title="Wartung">
                <Card className="p-8 text-center">
                    <p className="font-medium text-slate-800">Die Wartung konnte nicht geladen werden.</p>
                    <div className="mt-3 flex justify-center gap-2">
                        <Button size="sm" className="min-h-11" onClick={() => void laden()}>Noch einmal versuchen</Button>
                        <Link to="/wartung" className="inline-flex items-center rounded border border-rose-200 bg-white px-3 py-1.5 text-sm text-rose-700 hover:bg-rose-50">Alle Wartungen</Link>
                    </div>
                </Card>
            </PageLayout>
        );
    }

    const offen = anlage.offenerFeldeinsatzId !== null;
    const letzte = anlage.historie.find((p) => p.id === anlage.letztePruefungId) ?? null;
    const historieNeueste = [...anlage.historie].reverse();
    const adresse = [anlage.objekt.strasse, [anlage.objekt.plz, anlage.objekt.ort].filter(Boolean).join(' ')].filter(Boolean).join(', ');
    const tuerFuerDialog: WartungTuer = {
        id: anlage.id, objektId: anlage.objekt.id, bezeichnung: anlage.bezeichnung, notizen: anlage.notizen,
        intervallMonate: anlage.intervallMonate, naechsteFaelligkeit: anlage.naechsteFaelligkeit, aktiv: anlage.aktiv,
        module: anlage.module.map((m) => ({ modulId: m.modulId, untertypSchluessel: m.bauartSchluessel })),
        projektId: anlage.projekt?.id ?? null, projektName: anlage.projekt?.name ?? null,
    };
    const projektOptionen = projekte.filter((p) => p.id !== anlage.projekt?.id).map((p) => ({ value: String(p.id), label: p.name }));

    return (
        <PageLayout
            ribbonCategory="Wartung"
            title={anlage.bezeichnung}
            subtitle={`${anlage.kunde.name} · ${anlage.objekt.bezeichnung}`}
            actions={
                <div className="flex flex-wrap gap-2">
                    <Link to="/wartung?tab=objekte" className="inline-flex items-center gap-2 rounded-lg border border-rose-200 bg-white px-4 py-2 text-sm text-rose-700 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500">
                        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Alle Wartungen
                    </Link>
                    <Button variant="outline" className="min-h-11" onClick={() => void bearbeiten()}>
                        <Pencil className="h-4 w-4" aria-hidden="true" /> Bearbeiten
                    </Button>
                </div>
            }
        >
            <div className="space-y-6">
                {/* ── Feldeinsatz ── */}
                <Card className="p-4 sm:p-6">
                    <h2 className="text-base font-semibold text-slate-900">Feldeinsatz</h2>
                    {offen ? (
                        <div className="mt-3 flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 sm:flex-row sm:items-center sm:justify-between" role="status">
                            <p className="text-sm text-amber-900">
                                Es läuft schon ein Feldeinsatz: <strong className="font-semibold">{anlage.offenerFeldeinsatzNummer}</strong>.
                                Bitte erst diesen abschließen, bevor ein neuer beginnt.
                            </p>
                            <Button className="min-h-11" onClick={() => navigate(`/wartung/${anlage.offenerFeldeinsatzId}`)}>
                                <Play className="h-4 w-4" aria-hidden="true" /> Feldeinsatz fortsetzen
                            </Button>
                        </div>
                    ) : (
                        <div className="mt-3 space-y-3">
                            {letzte ? (
                                <>
                                    <p className="text-sm text-slate-600">
                                        Die letzte abgeschlossene Prüfung (<strong className="font-semibold">Prüfung {nummer2(letzte.pruefung)}</strong> vom {formatDatum(letzte.datum)})
                                        dient als Vorlage. Sie bleibt unverändert, der neue Feldeinsatz ist eine Kopie, die sich frei ändern lässt.
                                    </p>
                                    <label className="flex cursor-pointer items-start gap-2 text-sm text-slate-700">
                                        <input type="checkbox" className="mt-0.5 h-4 w-4 rounded border-slate-300 accent-rose-600" checked={werteUebernehmen}
                                            onChange={(e) => setWerteUebernehmen(e.target.checked)} />
                                        <span>
                                            <span className="font-medium">Bisherige Ergebnisse, Bemerkungen und Werte übernehmen</span>
                                            <span className="block text-slate-500">Ohne Haken beginnt die Prüfliste leer, die letzten Ergebnisse stehen nur als Hinweis „Letztes Mal" dabei.</span>
                                        </span>
                                    </label>
                                    <div className="flex flex-wrap gap-2">
                                        <Button className="min-h-11" onClick={() => void starte(letzte.id, werteUebernehmen)} disabled={startet}>
                                            {startet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Play className="h-4 w-4" aria-hidden="true" />}
                                            Feldeinsatz starten
                                        </Button>
                                        <Button variant="ghost" className="min-h-11" onClick={() => void starte(null, false)} disabled={startet}>
                                            <RotateCcw className="h-4 w-4" aria-hidden="true" /> Neu aus dem Katalog starten
                                        </Button>
                                    </div>
                                </>
                            ) : (
                                <>
                                    <p className="text-sm text-slate-600">Noch keine Prüfung. Der erste Feldeinsatz entsteht aus der Prüfliste der gewählten Module.</p>
                                    <Button className="min-h-11" onClick={() => void starte(null, false)} disabled={startet || anlage.module.length === 0}>
                                        {startet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Play className="h-4 w-4" aria-hidden="true" />}
                                        Feldeinsatz starten
                                    </Button>
                                    {anlage.module.length === 0 && <p className="text-sm text-amber-700">Bitte zuerst unter „Bearbeiten" ein Modul wählen.</p>}
                                </>
                            )}
                        </div>
                    )}
                </Card>

                <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                    {/* ── Angaben ── */}
                    <Card className="p-4 sm:p-6">
                        <h2 className="text-base font-semibold text-slate-900">Angaben</h2>
                        <dl className="mt-3 grid grid-cols-[8rem_1fr] gap-x-3 gap-y-2 text-sm">
                            <dt className="text-slate-500">Kunde</dt><dd className="text-slate-900">{anlage.kunde.name}</dd>
                            <dt className="text-slate-500">Objekt</dt><dd className="text-slate-900">{anlage.objekt.bezeichnung}{adresse && <span className="block text-slate-500">{adresse}</span>}</dd>
                            <dt className="text-slate-500">Geprüft wird</dt>
                            <dd className="text-slate-900">{anlage.module.length === 0 ? '–' : anlage.module.map((m) => (m.bauart ? `${m.name} (${m.bauart})` : m.name)).join(', ')}</dd>
                            <dt className="text-slate-500">Intervall</dt><dd className="text-slate-900">{anlage.intervallMonate ? `${anlage.intervallMonate} Monate` : '–'}</dd>
                            <dt className="text-slate-500">Nächste Prüfung</dt><dd className="text-slate-900">{formatDatum(anlage.naechsteFaelligkeit)}</dd>
                            {anlage.notizen && <><dt className="text-slate-500">Notizen</dt><dd className="whitespace-pre-line text-slate-900">{anlage.notizen}</dd></>}
                        </dl>
                    </Card>

                    {/* ── Projekt ── */}
                    <Card className="p-4 sm:p-6">
                        <h2 className="text-base font-semibold text-slate-900">Projekt</h2>
                        {anlage.projekt ? (
                            <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                                <Link to={`/projekte?projektId=${encodeURIComponent(String(anlage.projekt.id))}&tab=wartungen`} className="text-sm font-medium text-rose-700 underline underline-offset-2 hover:text-rose-800">
                                    {anlage.projekt.name}
                                </Link>
                                <Button variant="ghost" size="sm" className="min-h-11" disabled={projektArbeitet} onClick={() => void projektSetzen(null)} aria-label="Wartung vom Projekt trennen">
                                    <Unlink className="h-4 w-4" aria-hidden="true" /> Trennen
                                </Button>
                            </div>
                        ) : (
                            <p className="mt-3 text-sm text-slate-500">Diese Wartung gehört noch zu keinem Projekt.</p>
                        )}
                        <div className="mt-4">
                            <Label htmlFor="anlage-projekt">{anlage.projekt ? 'Anderem Projekt zuordnen' : 'Einem Projekt zuordnen'}</Label>
                            <div className="flex flex-col gap-2 sm:flex-row">
                                <div className="min-w-0 flex-1">
                                    <Select id="anlage-projekt" value={gewaehltesProjekt} onChange={setGewaehltesProjekt} options={projektOptionen}
                                        placeholder={projektOptionen.length === 0 ? 'Keine weiteren Projekte dieses Kunden' : 'Projekt wählen'} disabled={projektOptionen.length === 0 || projektArbeitet} />
                                </div>
                                <Button variant="outline" className="min-h-11" disabled={gewaehltesProjekt === '' || projektArbeitet} onClick={() => void projektSetzen(Number(gewaehltesProjekt))}>
                                    {projektArbeitet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : null}
                                    Zuordnen
                                </Button>
                            </div>
                            <p className="mt-2 text-xs text-slate-500">Neue Feldeinsätze übernehmen das Projekt. Bestehende Prüfungen behalten das ihre.</p>
                        </div>
                    </Card>
                </div>

                {/* ── Historie ── */}
                <Card className="p-4 sm:p-6">
                    <h2 className="text-base font-semibold text-slate-900">Historie der Prüfungen</h2>
                    {historieNeueste.length === 0 && <p className="mt-3 text-sm text-slate-500">Noch keine Prüfung durchgeführt.</p>}
                    <ul className="mt-3 divide-y divide-slate-100">
                        {historieNeueste.map((p) => {
                            const status = STATUS_ANZEIGE[p.status];
                            const abgeschlossen = p.status === 'ABGESCHLOSSEN' || p.status === 'ARCHIVIERT';
                            const vorlage = p.vorlageProtokollId === null ? null : anlage.historie.find((x) => x.id === p.vorlageProtokollId);
                            return (
                                <li key={p.id} className="flex flex-col gap-2 py-3 md:flex-row md:items-center md:justify-between" data-testid={`pruefung-${p.id}`}>
                                    <div className="min-w-0">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <span className="font-semibold text-slate-900">Prüfung {nummer2(p.pruefung)}</span>
                                            <span className="text-slate-500">–</span>
                                            <span className="text-slate-700">{formatDatum(p.datum)}</span>
                                            {p.technikerName && <span className="text-slate-700">– {p.technikerName}</span>}
                                            <span className={cn('inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold', status.klasse)}>{status.label}</span>
                                            {p.maengel > 0 && (
                                                <span className="inline-flex items-center rounded-full border border-rose-200 bg-rose-50 px-2 py-0.5 text-xs font-semibold text-rose-700">
                                                    {p.maengel} {p.maengel === 1 ? 'Mangel' : 'Mängel'}
                                                </span>
                                            )}
                                        </div>
                                        <p className="mt-0.5 text-sm text-slate-500">
                                            {p.nummer} · {p.pruefpunkteBewertet}/{p.pruefpunkteGesamt} Punkte bewertet
                                            {vorlage && <> · gestartet aus Prüfung {nummer2(vorlage.pruefung)}</>}
                                        </p>
                                    </div>
                                    <div className="flex flex-wrap items-center gap-2">
                                        <Link to={`/wartung/${p.id}`} className="inline-flex items-center gap-2 rounded border border-rose-200 bg-white px-3 py-1.5 text-sm text-rose-700 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500"
                                            aria-label={`Prüfung ${nummer2(p.pruefung)} öffnen`}>
                                            <ClipboardCheck className="h-4 w-4" aria-hidden="true" /> Öffnen
                                        </Link>
                                        {p.berichtUrl && (
                                            <a href={p.berichtUrl} target="_blank" rel="noopener noreferrer" aria-label={`Bericht zu Prüfung ${nummer2(p.pruefung)} öffnen`}
                                                className="inline-flex items-center gap-2 rounded border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-rose-500">
                                                <FileText className="h-4 w-4" aria-hidden="true" /> Bericht
                                            </a>
                                        )}
                                        {abgeschlossen && !offen && (
                                            <Button variant="ghost" size="sm" className="min-h-11" disabled={startet} onClick={() => void starte(p.id, true)} aria-label={`Neue Prüfung aus Prüfung ${nummer2(p.pruefung)} starten`}>
                                                <Play className="h-4 w-4" aria-hidden="true" /> Neue Prüfung hieraus
                                            </Button>
                                        )}
                                    </div>
                                </li>
                            );
                        })}
                    </ul>
                </Card>
            </div>

            <TuerDialog
                open={dialogOffen}
                onOpenChange={setDialogOffen}
                objektId={anlage.objekt.id}
                module={module}
                tuer={tuerFuerDialog}
                onGespeichert={() => { setDialogOffen(false); void laden(); }}
            />
        </PageLayout>
    );
}
