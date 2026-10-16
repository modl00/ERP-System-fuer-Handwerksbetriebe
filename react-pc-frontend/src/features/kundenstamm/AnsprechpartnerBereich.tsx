import { useCallback, useEffect, useState } from 'react';
import { Loader2, Mail, Pencil, Phone, Plus, Smartphone, Star, Trash2, UserRound } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { AnsprechpartnerDialog } from './AnsprechpartnerDialog';
import { ladeAnsprechpartner, loescheAnsprechpartner, setzeHauptansprechpartner } from './api';
import type { Ansprechpartner } from './types';

interface AnsprechpartnerBereichProps {
    kundeId: number;
    /** Meldet die aktuelle Anzahl, damit der Reiter sie anzeigen kann. */
    onAnzahl?: (anzahl: number) => void;
}

/** Reiter "Ansprechpartner" im Kunden: Liste, anlegen, ändern, Hauptansprechpartner wählen, löschen. */
export function AnsprechpartnerBereich({ kundeId, onAnzahl }: AnsprechpartnerBereichProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const [liste, setListe] = useState<Ansprechpartner[] | null>(null);
    const [ladefehler, setLadefehler] = useState<string | undefined>();
    const [dialog, setDialog] = useState<{ offen: boolean; ansprechpartner?: Ansprechpartner }>({ offen: false });
    const [arbeitet, setArbeitet] = useState<number | null>(null);

    const laden = useCallback(async () => {
        setLadefehler(undefined);
        try {
            const geladen = await ladeAnsprechpartner(kundeId);
            setListe(geladen);
            onAnzahl?.(geladen.length);
        } catch (e) {
            setLadefehler(e instanceof Error ? e.message : 'Die Ansprechpartner konnten nicht geladen werden.');
        }
    }, [kundeId, onAnzahl]);

    useEffect(() => {
        void laden();
        // laden hängt an onAnzahl; ein neuer Aufruf bei jedem Rendern des Elternteils wäre unnötig
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [kundeId]);

    const haupt = async (a: Ansprechpartner) => {
        setArbeitet(a.id);
        try {
            await setzeHauptansprechpartner(kundeId, a.id);
            toast.success(`${a.name} ist jetzt der Hauptansprechpartner.`);
            await laden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Hauptansprechpartner konnte nicht geändert werden.');
        } finally {
            setArbeitet(null);
        }
    };

    const loeschen = async (a: Ansprechpartner) => {
        const ja = await confirm({
            title: 'Ansprechpartner löschen?',
            message: a.haupt && (liste?.length ?? 0) > 1
                ? `„${a.name}“ wird gelöscht. Der nächste Ansprechpartner wird dann zum Hauptansprechpartner.`
                : `„${a.name}“ wird gelöscht.`,
            confirmLabel: 'Löschen',
            variant: 'danger',
        });
        if (!ja) return;
        setArbeitet(a.id);
        try {
            await loescheAnsprechpartner(kundeId, a.id);
            toast.success('Ansprechpartner gelöscht.');
            await laden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Ansprechpartner konnte nicht gelöscht werden.');
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

    if (liste === null) {
        return (
            <div className="flex flex-col gap-3" aria-busy="true" aria-label="Die Ansprechpartner werden geladen">
                {[0, 1].map((n) => <div key={n} className="h-24 rounded-lg bg-slate-100 motion-safe:animate-pulse" />)}
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-3" data-testid="ansprechpartner-bereich">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm text-slate-500">
                    Personen beim Kunden. Der Hauptansprechpartner steht in E-Mails, Mahnungen und Dokumenten.
                </p>
                <Button size="sm" onClick={() => setDialog({ offen: true })}>
                    <Plus className="h-4 w-4" aria-hidden="true" /> Ansprechpartner hinzufügen
                </Button>
            </div>

            {liste.length === 0 ? (
                <Card className="p-8 text-center text-slate-500">
                    <UserRound className="mx-auto mb-2 h-8 w-8 text-slate-300" aria-hidden="true" />
                    <p>Für diesen Kunden ist noch kein Ansprechpartner eingetragen.</p>
                </Card>
            ) : (
                <ul className="flex flex-col gap-3">
                    {liste.map((a) => (
                        <li key={a.id}>
                            <Card className="p-4" data-testid={`ansprechpartner-${a.id}`}>
                                <div className="flex flex-wrap items-start justify-between gap-3">
                                    <div className="min-w-0">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <h3 className="break-words font-semibold text-slate-900">{a.name}</h3>
                                            {a.haupt && (
                                                <span className="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-rose-50 px-2 py-0.5 text-xs font-semibold text-rose-700">
                                                    <Star className="h-3 w-3" aria-hidden="true" /> Hauptansprechpartner
                                                </span>
                                            )}
                                        </div>
                                        {a.funktion && <p className="text-sm text-slate-500">{a.funktion}</p>}
                                    </div>
                                    <div className="flex flex-wrap gap-1">
                                        {!a.haupt && (
                                            <Button size="sm" variant="ghost" disabled={arbeitet === a.id} onClick={() => void haupt(a)}
                                                aria-label={`${a.name} zum Hauptansprechpartner machen`}>
                                                <Star className="h-4 w-4" aria-hidden="true" /> Zum Hauptansprechpartner
                                            </Button>
                                        )}
                                        <Button size="sm" variant="ghost" onClick={() => setDialog({ offen: true, ansprechpartner: a })} aria-label={`${a.name} bearbeiten`}>
                                            <Pencil className="h-4 w-4" aria-hidden="true" />
                                        </Button>
                                        <Button size="sm" variant="ghost" disabled={arbeitet === a.id} onClick={() => void loeschen(a)} aria-label={`${a.name} löschen`}>
                                            {arbeitet === a.id ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Trash2 className="h-4 w-4" aria-hidden="true" />}
                                        </Button>
                                    </div>
                                </div>
                                {(a.telefon || a.mobil || a.email) && (
                                    <div className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-sm">
                                        {a.telefon && (
                                            <a href={`tel:${a.telefon.replace(/\s+/g, '')}`} className="inline-flex min-h-8 items-center gap-1.5 text-slate-700 hover:text-rose-700">
                                                <Phone className="h-4 w-4 text-slate-400" aria-hidden="true" /> {a.telefon}
                                            </a>
                                        )}
                                        {a.mobil && (
                                            <a href={`tel:${a.mobil.replace(/\s+/g, '')}`} className="inline-flex min-h-8 items-center gap-1.5 text-slate-700 hover:text-rose-700">
                                                <Smartphone className="h-4 w-4 text-slate-400" aria-hidden="true" /> {a.mobil}
                                            </a>
                                        )}
                                        {a.email && (
                                            <a href={`mailto:${a.email}`} className="inline-flex min-h-8 items-center gap-1.5 break-all text-slate-700 hover:text-rose-700">
                                                <Mail className="h-4 w-4 text-slate-400" aria-hidden="true" /> {a.email}
                                            </a>
                                        )}
                                    </div>
                                )}
                                {a.notizen && <p className="mt-3 whitespace-pre-line break-words text-sm text-slate-600">{a.notizen}</p>}
                            </Card>
                        </li>
                    ))}
                </ul>
            )}

            <AnsprechpartnerDialog
                open={dialog.offen}
                onOpenChange={(offen) => setDialog((alt) => ({ ...alt, offen }))}
                kundeId={kundeId}
                ansprechpartner={dialog.ansprechpartner}
                istErster={liste.length === 0}
                onGespeichert={() => void laden()}
            />
        </div>
    );
}
