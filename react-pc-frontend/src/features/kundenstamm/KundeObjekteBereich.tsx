import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Building2, Eye, EyeOff, Loader2, MapPin, Pencil, Plus, UserRound, Wrench } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { useWartungAktiv } from '../modulschalter/useFeatures';
import { KundeObjektDialog } from './KundeObjektDialog';
import { ladeAnsprechpartner, ladeKundeObjekte, setzeKundeObjektAktiv } from './api';
import type { Ansprechpartner, KundeObjekt } from './types';

interface KundeObjekteBereichProps {
    kundeId: number;
    /** Meldet die Anzahl der aktiven Objekte, damit der Reiter sie anzeigen kann. */
    onAnzahl?: (anzahl: number) => void;
}

/** Reiter "Objekte" im Kunden: Liegenschaften, Gebäude und Standorte. Dieselben Objekte nutzt die Wartung. */
export function KundeObjekteBereich({ kundeId, onAnzahl }: KundeObjekteBereichProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const wartungAktiv = useWartungAktiv() === true;
    const [objekte, setObjekte] = useState<KundeObjekt[] | null>(null);
    const [ansprechpartner, setAnsprechpartner] = useState<Ansprechpartner[]>([]);
    const [ladefehler, setLadefehler] = useState<string | undefined>();
    const [mitInaktiven, setMitInaktiven] = useState(false);
    const [dialog, setDialog] = useState<{ offen: boolean; objekt?: KundeObjekt }>({ offen: false });
    const [arbeitet, setArbeitet] = useState<number | null>(null);

    const laden = useCallback(async () => {
        setLadefehler(undefined);
        try {
            const geladen = await ladeKundeObjekte(kundeId, mitInaktiven);
            setObjekte(geladen);
            onAnzahl?.(geladen.filter((o) => o.aktiv).length);
        } catch (e) {
            setLadefehler(e instanceof Error ? e.message : 'Die Objekte konnten nicht geladen werden.');
        }
    }, [kundeId, mitInaktiven, onAnzahl]);

    useEffect(() => {
        void laden();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [kundeId, mitInaktiven]);

    // Die Ansprechpartner brauchen nur der Dialog (zum Übernehmen); fehlen sie, geht alles andere trotzdem
    useEffect(() => {
        let abgebrochen = false;
        ladeAnsprechpartner(kundeId).then((l) => { if (!abgebrochen) setAnsprechpartner(l); }).catch(() => undefined);
        return () => { abgebrochen = true; };
    }, [kundeId]);

    const umschalten = async (o: KundeObjekt) => {
        if (o.aktiv) {
            const ja = await confirm({
                title: 'Objekt deaktivieren?',
                message: `„${o.bezeichnung}" erscheint dann nicht mehr bei neuen Aufträgen und Wartungen. Bisherige Protokolle und Wartungen bleiben erhalten.`,
                confirmLabel: 'Deaktivieren',
                variant: 'warning',
            });
            if (!ja) return;
        }
        setArbeitet(o.id);
        try {
            await setzeKundeObjektAktiv(kundeId, o.id, !o.aktiv);
            toast.success(o.aktiv ? 'Objekt deaktiviert.' : 'Objekt wieder aktiviert.');
            await laden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Objekt konnte nicht geändert werden.');
        } finally {
            setArbeitet(null);
        }
    };

    if (ladefehler) {
        return (
            <Card className="p-6" role="alert">
                <p className="text-sm text-rose-700">{ladefehler}</p>
                <Button size="sm" variant="outline" className="mt-3" onClick={() => void laden()}>Noch einmal versuchen</Button>
            </Card>
        );
    }

    if (objekte === null) {
        return (
            <div className="flex flex-col gap-3" aria-busy="true" aria-label="Die Objekte werden geladen">
                {[0, 1].map((n) => <div key={n} className="h-24 rounded-lg bg-slate-100 motion-safe:animate-pulse" />)}
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-3" data-testid="objekte-bereich">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm text-slate-500">
                    Liegenschaften, Gebäude und Standorte dieses Kunden.{wartungAktiv ? ' Dieselben Objekte nutzt die Wartung.' : ''}
                </p>
                <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="ghost" aria-pressed={mitInaktiven} onClick={() => setMitInaktiven((v) => !v)}>
                        {mitInaktiven ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
                        {mitInaktiven ? 'Inaktive ausblenden' : 'Inaktive anzeigen'}
                    </Button>
                    <Button size="sm" onClick={() => setDialog({ offen: true })}>
                        <Plus className="h-4 w-4" aria-hidden="true" /> Objekt anlegen
                    </Button>
                </div>
            </div>

            {objekte.length === 0 ? (
                <Card className="p-8 text-center text-slate-500">
                    <Building2 className="mx-auto mb-2 h-8 w-8 text-slate-300" aria-hidden="true" />
                    <p>Für diesen Kunden ist noch kein Objekt eingetragen.</p>
                </Card>
            ) : (
                <ul className="flex flex-col gap-3">
                    {objekte.map((o) => {
                        const adresse = [o.strasse, [o.plz, o.ort].filter(Boolean).join(' ')].filter(Boolean).join(', ');
                        return (
                            <li key={o.id}>
                                <Card className={cn('p-4', !o.aktiv && 'bg-slate-50')} data-testid={`objekt-${o.id}`}>
                                    <div className="flex flex-wrap items-start justify-between gap-3">
                                        <div className="min-w-0">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <h3 className={cn('break-words font-semibold', o.aktiv ? 'text-slate-900' : 'text-slate-500')}>{o.bezeichnung}</h3>
                                                {!o.aktiv && <span className="rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs font-semibold text-slate-600">Inaktiv</span>}
                                            </div>
                                            {adresse && (
                                                <p className="mt-1 flex items-start gap-1.5 text-sm text-slate-600">
                                                    <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" /> {adresse}
                                                </p>
                                            )}
                                            {(o.ansprechpartnerName || o.ansprechpartnerTelefon) && (
                                                <p className="mt-1 flex items-start gap-1.5 text-sm text-slate-600">
                                                    <UserRound className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                                                    {[o.ansprechpartnerName, o.ansprechpartnerTelefon].filter(Boolean).join(' · ')}
                                                </p>
                                            )}
                                        </div>
                                        <div className="flex flex-wrap gap-1">
                                            <Button size="sm" variant="ghost" onClick={() => setDialog({ offen: true, objekt: o })} aria-label={`${o.bezeichnung} bearbeiten`}>
                                                <Pencil className="h-4 w-4" aria-hidden="true" /> Bearbeiten
                                            </Button>
                                            <Button size="sm" variant="ghost" disabled={arbeitet === o.id} onClick={() => void umschalten(o)}
                                                aria-label={o.aktiv ? `${o.bezeichnung} deaktivieren` : `${o.bezeichnung} wieder aktivieren`}>
                                                {arbeitet === o.id ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                                                    : o.aktiv ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
                                                {o.aktiv ? 'Deaktivieren' : 'Aktivieren'}
                                            </Button>
                                        </div>
                                    </div>
                                    {o.notizen && <p className="mt-3 whitespace-pre-line break-words text-sm text-slate-600">{o.notizen}</p>}
                                    {wartungAktiv && o.anzahlWartungen > 0 && (
                                        <Link to="/wartung?tab=objekte" className="mt-3 inline-flex min-h-8 items-center gap-1.5 text-sm text-rose-700 hover:underline">
                                            <Wrench className="h-4 w-4" aria-hidden="true" />
                                            {o.anzahlWartungen === 1 ? '1 Wartung' : `${o.anzahlWartungen} Wartungen`} ansehen
                                        </Link>
                                    )}
                                </Card>
                            </li>
                        );
                    })}
                </ul>
            )}

            <KundeObjektDialog
                open={dialog.offen}
                onOpenChange={(offen) => setDialog((alt) => ({ ...alt, offen }))}
                kundeId={kundeId}
                objekt={dialog.objekt}
                ansprechpartner={ansprechpartner}
                onGespeichert={() => void laden()}
            />
        </div>
    );
}
