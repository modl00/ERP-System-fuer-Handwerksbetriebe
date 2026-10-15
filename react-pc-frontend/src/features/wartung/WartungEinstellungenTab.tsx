import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Copy, Eye, EyeOff, Loader2, Pencil, Plus, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { ladeModulUebersicht, loescheModul, setzeModulAktiv, setzeModulReihenfolge } from './api';
import { GlobaleFelderEditor } from './editor/GlobaleFelderEditor';
import { verschiebe } from './editor/modulBearbeiten';
import { ZeilenKnoepfe } from './editor/ZeilenKnoepfe';
import { ModulIcon } from './ModulIcon';
import { loeschenGesperrtGrund } from './modulListe';
import type { ModulUebersicht } from './types';

function zaehle(anzahl: number, einzahl: string, mehrzahl: string): string {
    return `${anzahl} ${anzahl === 1 ? einzahl : mehrzahl}`;
}

/** Einstellungen der Wartung für Admins: Module und gemeinsame Datenfelder. */
export function WartungEinstellungenTab() {
    const toast = useToast();
    const confirm = useConfirm();
    const navigate = useNavigate();
    const [module, setModule] = useState<ModulUebersicht[] | null>(null);
    const [fehler, setFehler] = useState(false);
    const [arbeitet, setArbeitet] = useState<number | 'reihenfolge' | null>(null);

    const laden = useCallback(async () => {
        setFehler(false);
        try {
            setModule(await ladeModulUebersicht());
        } catch (e) {
            setFehler(true);
            toast.error(e instanceof Error ? e.message : 'Die Module konnten nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => { void laden(); }, [laden]);

    const aktivWechseln = async (m: ModulUebersicht) => {
        setArbeitet(m.id);
        try {
            await setzeModulAktiv(m.id, !m.aktiv);
            setModule((alt) => alt && alt.map((x) => (x.id === m.id ? { ...x, aktiv: !m.aktiv } : x)));
            toast.success(m.aktiv ? `„${m.name}“ ist deaktiviert und steht bei neuen Wartungen nicht mehr zur Wahl.` : `„${m.name}“ ist wieder aktiv.`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Modul konnte nicht geändert werden.');
        } finally {
            setArbeitet(null);
        }
    };

    const loeschen = async (m: ModulUebersicht) => {
        const ja = await confirm({
            title: 'Modul löschen?',
            message: `„${m.name}“ mit allen Bauarten, Prüfpunkten und Datenfeldern wird gelöscht. Das lässt sich nicht rückgängig machen.`,
            confirmLabel: 'Löschen',
            variant: 'danger',
        });
        if (!ja) return;
        setArbeitet(m.id);
        try {
            await loescheModul(m.id);
            setModule((alt) => alt && alt.filter((x) => x.id !== m.id));
            toast.success(`„${m.name}“ wurde gelöscht.`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Modul konnte nicht gelöscht werden.');
        } finally {
            setArbeitet(null);
        }
    };

    const bewege = async (index: number, richtung: -1 | 1) => {
        if (!module) return;
        const neu = verschiebe(module, index, richtung);
        if (neu === module) return;
        const vorher = module;
        setModule(neu);
        setArbeitet('reihenfolge');
        try {
            setModule(await setzeModulReihenfolge(neu.map((m) => m.id)));
        } catch (e) {
            setModule(vorher);
            toast.error(e instanceof Error ? e.message : 'Die Reihenfolge konnte nicht gespeichert werden.');
        } finally {
            setArbeitet(null);
        }
    };

    return (
        <section role="tabpanel" id="wartung-panel-einstellungen" aria-labelledby="wartung-tab-einstellungen" className="space-y-8">
            <div className="space-y-4">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                    <div className="max-w-2xl">
                        <h2 className="text-lg font-semibold text-slate-900">Module</h2>
                        <p className="mt-0.5 text-sm text-slate-500">
                            Ein Modul ist ein Baustein der Checkliste, zum Beispiel „Brandschutztür“ oder „Feststellanlage“. Beim Anlegen einer Wartung
                            wird der Inhalt <strong className="font-semibold text-slate-700">kopiert</strong>: Änderungen hier gelten nur für neue Wartungen, bestehende Protokolle bleiben unverändert.
                        </p>
                    </div>
                    <Button onClick={() => navigate('/wartung/module/neu')}>
                        <Plus className="h-4 w-4" aria-hidden="true" /> Neues Modul
                    </Button>
                </div>

                {fehler && !module && (
                    <Card className="p-6 text-center">
                        <p className="text-slate-700">Die Module konnten nicht geladen werden.</p>
                        <Button size="sm" className="mt-3" onClick={() => void laden()}>Noch einmal versuchen</Button>
                    </Card>
                )}
                {!module && !fehler && (
                    <div className="flex items-center justify-center gap-2 py-10 text-slate-500" role="status">
                        <Loader2 className="h-5 w-5 motion-safe:animate-spin" aria-hidden="true" /> Module werden geladen …
                    </div>
                )}
                {module && module.length === 0 && (
                    <Card className="p-8 text-center">
                        <p className="font-medium text-slate-800">Noch keine Module</p>
                        <p className="mt-1 text-sm text-slate-500">Legen Sie das erste Modul an, damit sich Wartungen erstellen lassen.</p>
                    </Card>
                )}
                {module && module.length > 0 && (
                    <ul className="flex flex-col gap-3">
                        {module.map((m, i) => {
                            const gesperrt = loeschenGesperrtGrund(m);
                            return (
                                <li key={m.id}>
                                    <Card className={cn('p-4', !m.aktiv && 'bg-slate-50')} data-testid={`modul-${m.id}`}>
                                        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                                            <div className="flex min-w-0 items-start gap-3">
                                                <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-full', m.aktiv ? 'bg-rose-100 text-rose-600' : 'bg-slate-200 text-slate-500')}>
                                                    <ModulIcon name={m.icon} />
                                                </span>
                                                <div className="min-w-0">
                                                    <div className="flex flex-wrap items-center gap-2">
                                                        <h3 className="text-base font-bold text-slate-900 break-words">{m.name}</h3>
                                                        <span className="inline-flex items-center rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs font-semibold text-slate-600">
                                                            {m.kategorie === 'BASIS' ? 'Basis-Element' : 'Zusatzmodul'}
                                                        </span>
                                                        {m.systemModul && <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600">Mitgeliefert</span>}
                                                        {!m.aktiv && <span className="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700">Deaktiviert</span>}
                                                    </div>
                                                    {m.beschreibung && <p className="mt-0.5 text-sm text-slate-500 break-words">{m.beschreibung}</p>}
                                                    <p className="mt-1 text-sm text-slate-600">
                                                        {zaehle(m.anzahlUntertypen, 'Bauart', 'Bauarten')} · {zaehle(m.anzahlPruefpunkte, 'Prüfpunkt', 'Prüfpunkte')} · {zaehle(m.anzahlDatenfelder, 'Datenfeld', 'Datenfelder')}
                                                        <span className="text-slate-400"> · </span>
                                                        {m.anzahlTueren > 0 ? `an ${zaehle(m.anzahlTueren, 'Tür', 'Türen')} im Einsatz` : 'an keiner Tür im Einsatz'}
                                                    </p>
                                                </div>
                                            </div>
                                            <div className="flex shrink-0 flex-wrap items-center gap-2">
                                                <Link to={`/wartung/module/${m.id}`} aria-label={`${m.name} bearbeiten`}
                                                    className="inline-flex items-center justify-center gap-2 rounded border border-rose-200 bg-white px-3 py-1.5 text-sm text-rose-700 transition-colors hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500">
                                                    <Pencil className="h-4 w-4" aria-hidden="true" /> Bearbeiten
                                                </Link>
                                                <Button variant="ghost" size="sm" onClick={() => navigate(`/wartung/module/neu?kopie=${m.id}`)} aria-label={`${m.name} als Vorlage für ein neues Modul kopieren`}>
                                                    <Copy className="h-4 w-4" aria-hidden="true" /> Kopieren
                                                </Button>
                                                <Button variant="ghost" size="sm" disabled={arbeitet === m.id} onClick={() => void aktivWechseln(m)}
                                                    aria-label={`${m.name} ${m.aktiv ? 'deaktivieren' : 'aktivieren'}`}>
                                                    {m.aktiv ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
                                                    {m.aktiv ? 'Deaktivieren' : 'Aktivieren'}
                                                </Button>
                                                <Button variant="ghost" size="sm" disabled={gesperrt !== undefined || arbeitet === m.id} title={gesperrt}
                                                    className="text-rose-700 hover:bg-rose-50" onClick={() => void loeschen(m)} aria-label={`${m.name} löschen`}>
                                                    <Trash2 className="h-4 w-4" aria-hidden="true" /> Löschen
                                                </Button>
                                                <ZeilenKnoepfe bezeichnung={`Modul ${m.name}`}
                                                    onHoch={i > 0 && arbeitet === null ? () => void bewege(i, -1) : undefined}
                                                    onRunter={i < module.length - 1 && arbeitet === null ? () => void bewege(i, 1) : undefined} />
                                            </div>
                                        </div>
                                    </Card>
                                </li>
                            );
                        })}
                    </ul>
                )}
            </div>

            <GlobaleFelderEditor />
        </section>
    );
}
