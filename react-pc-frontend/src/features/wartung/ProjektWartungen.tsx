import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ClipboardCheck, FilePlus2, FileText, Link2, Loader2, Plus, Unlink } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { heuteIso } from '../../lib/datum';
import { useWartungAktiv } from '../modulschalter/useFeatures';
import { ladeObjekte, ladeProjektWartungen, ordneProjektZu } from './api';
import { formatDatum, istUeberfaellig } from './format';
import { NeueAnlageDialog } from './NeueAnlageDialog';
import { NeueWartungDialog } from './NeueWartungDialog';
import type { ProjektWartung, WartungObjekt } from './types';

interface Props {
    projektId: number;
    /** Kunde des Projekts: Zugeordnet werden können nur Wartungen dieses Kunden. */
    kundeId: number | null;
    /** Im Reiter "Wartung": ohne Trennlinie und Außenabstand, mit der Überschrift "Objekte und Wartungen". */
    eingebettet?: boolean;
    /** Wird mit den geladenen Wartungen aufgerufen (für die Projektmappe). */
    onGeladen?: (wartungen: ProjektWartung[]) => void;
}

/** Wartungen, die zu einer anderen Wartung dieses Kunden gehören und noch keinem oder einem anderen Projekt zugeordnet sind. */
interface Auswahl {
    tuerId: number;
    name: string;
    objekt: string;
    projektName: string | null;
}

/**
 * Bereich "Wartungen" im Projekt: die Wartungen dieses Projekts mit Fälligkeit, offenem Feldeinsatz und den im Projekt
 * abgelegten Berichten. Eine Wartung öffnen, zuordnen und trennen (auf der Wartungsseite). Ist das Wartungsmodul
 * ausgeschaltet, erscheint der Bereich gar nicht.
 */
export function ProjektWartungen({ projektId, kundeId, eingebettet = false, onGeladen }: Props) {
    const aktiv = useWartungAktiv();
    const toast = useToast();
    const [wartungen, setWartungen] = useState<ProjektWartung[] | null>(null);
    const [fehler, setFehler] = useState(false);
    const [dialogOffen, setDialogOffen] = useState(false);
    const [anlageOffen, setAnlageOffen] = useState(false);
    const [startFuer, setStartFuer] = useState<ProjektWartung | null>(null);
    const [markiert, setMarkiert] = useState<Set<number>>(new Set());
    const [frageEntfernen, setFrageEntfernen] = useState(false);
    const [entfernt, setEntfernt] = useState(false);
    const navigate = useNavigate();

    const laden = useCallback(async () => {
        setFehler(false);
        try {
            const geladen = await ladeProjektWartungen(projektId);
            setWartungen(geladen);
            setMarkiert((alt) => new Set([...alt].filter((id) => geladen.some((w) => w.id === id))));
            onGeladen?.(geladen);
        } catch (e) {
            setFehler(true);
            toast.error(e instanceof Error ? e.message : 'Die Wartungen des Projekts konnten nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [projektId]);

    useEffect(() => {
        setWartungen(null);
        if (aktiv === true) void laden();
    }, [aktiv, laden]);

    if (aktiv !== true) return null;
    const heute = heuteIso();
    const alleMarkiert = wartungen !== null && wartungen.length > 0 && markiert.size === wartungen.length;
    const schalte = (id: number) => setMarkiert((alt) => { const neu = new Set(alt); if (neu.has(id)) neu.delete(id); else neu.add(id); return neu; });

    const entfernen = async () => {
        const ids = [...markiert];
        setEntfernt(true);
        let ok = 0;
        const fehlgeschlagen: string[] = [];
        for (const id of ids) {
            try {
                await ordneProjektZu(id, null);
                ok++;
            } catch (e) {
                fehlgeschlagen.push(wartungen?.find((w) => w.id === id)?.bezeichnung ?? String(id));
                if (fehlgeschlagen.length === 1 && e instanceof Error) toast.error(e.message);
            }
        }
        setEntfernt(false);
        setFrageEntfernen(false);
        if (ok > 0) toast.success(ok === 1 ? '1 Wartung ist nicht mehr diesem Projekt zugeordnet.' : `${ok} Wartungen sind nicht mehr diesem Projekt zugeordnet.`);
        if (fehlgeschlagen.length > 0) toast.error(`Nicht entfernt: ${fehlgeschlagen.join(', ')}`);
        setMarkiert(new Set(fehlgeschlagen.length > 0 ? ids.filter((id) => fehlgeschlagen.includes(wartungen?.find((w) => w.id === id)?.bezeichnung ?? String(id))) : []));
        void laden();
    };

    return (
        <section className={eingebettet ? 'rounded-xl border border-slate-200 bg-white p-4' : 'mt-6 border-t border-slate-100 pt-6'} aria-labelledby="projekt-wartungen-titel" data-testid="projekt-wartungen">
            <div className="mb-3 flex items-center justify-between gap-2">
                <h3 id="projekt-wartungen-titel" className="flex items-center gap-2 text-sm font-medium text-slate-900">
                    <ClipboardCheck className="h-4 w-4 text-rose-500" aria-hidden="true" /> {eingebettet ? 'Objekte und Wartungen' : 'Wartungen'}
                </h3>
                {kundeId !== null && (
                    <div className="flex items-center gap-1">
                        <Button variant="ghost" size="sm" onClick={() => setAnlageOffen(true)}>
                            <Plus className="h-4 w-4" aria-hidden="true" /> Neue Anlage
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => setDialogOffen(true)}>
                            <Link2 className="h-4 w-4" aria-hidden="true" /> Zuordnen
                        </Button>
                    </div>
                )}
            </div>

            {fehler && wartungen === null && (
                <div className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600">
                    Die Wartungen konnten nicht geladen werden.{' '}
                    <button type="button" className="text-rose-700 underline underline-offset-2" onClick={() => void laden()}>Noch einmal versuchen</button>
                </div>
            )}
            {!fehler && wartungen === null && (
                <div className="flex items-center gap-2 text-sm text-slate-500" role="status">
                    <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …
                </div>
            )}
            {wartungen !== null && wartungen.length === 0 && (
                <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-500">Diesem Projekt sind noch keine Wartungen zugeordnet.</p>
            )}
            {wartungen !== null && wartungen.length > 0 && (
                <div className="mb-2 flex flex-wrap items-center gap-3 text-sm" data-testid="projekt-wartung-auswahl">
                    <label className="flex items-center gap-2 text-slate-700">
                        <input type="checkbox" className="h-4 w-4 rounded border-slate-300 accent-rose-600 focus:ring-2 focus:ring-rose-500" checked={alleMarkiert} onChange={() => setMarkiert(alleMarkiert ? new Set() : new Set(wartungen.map((w) => w.id)))} />
                        Alle auswählen
                    </label>
                    {markiert.size > 0 && !frageEntfernen && (
                        <>
                            <span className="text-slate-500">{markiert.size} markiert</span>
                            <Button variant="outline" size="sm" onClick={() => setFrageEntfernen(true)}>
                                <Unlink className="h-4 w-4" aria-hidden="true" /> Alle markierten entfernen
                            </Button>
                        </>
                    )}
                    {frageEntfernen && (
                        <span className="flex flex-wrap items-center gap-2 rounded-lg bg-amber-50 px-3 py-1.5 text-amber-900" role="alertdialog" aria-label="Zuordnung entfernen">
                            {markiert.size === 1 ? '1 Wartung' : `${markiert.size} Wartungen`} aus diesem Projekt entfernen? Die Wartungen selbst bleiben erhalten.
                            <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" disabled={entfernt} onClick={() => void entfernen()}>
                                {entfernt && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Ja, entfernen
                            </Button>
                            <Button size="sm" variant="outline" disabled={entfernt} onClick={() => setFrageEntfernen(false)}>Abbrechen</Button>
                        </span>
                    )}
                </div>
            )}
            {wartungen !== null && wartungen.length > 0 && (
                <ul className="space-y-2">
                    {wartungen.map((w) => (
                        <li key={w.id} className="rounded-lg bg-slate-50 p-3 text-sm" data-testid={`projekt-wartung-${w.id}`}>
                            <div className="flex items-start gap-2">
                                <input type="checkbox" className="mt-1 h-4 w-4 shrink-0 rounded border-slate-300 accent-rose-600 focus:ring-2 focus:ring-rose-500" checked={markiert.has(w.id)} onChange={() => schalte(w.id)} aria-label={`${w.bezeichnung} markieren`} />
                                <Link to={`/wartung/anlagen/${w.id}`} className="min-w-0 flex-1 font-medium text-slate-900 break-words hover:text-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500 rounded">
                                    {w.bezeichnung}
                                </Link>
                                {kundeId !== null && w.offenerFeldeinsatzId === null && (
                                    <Button size="sm" variant="outline" onClick={() => setStartFuer(w)} aria-label={`Wartung für ${w.bezeichnung} starten`}>
                                        <FilePlus2 className="h-4 w-4" aria-hidden="true" /> Wartung starten
                                    </Button>
                                )}
                            </div>
                            <p className="mt-0.5 break-words text-slate-500">{w.objektBezeichnung}{w.module && ` · ${w.module}`}</p>
                            <div className="mt-2 flex flex-wrap items-center gap-1.5">
                                {w.offenerFeldeinsatzId !== null && (
                                    <Link to={`/wartung/${w.offenerFeldeinsatzId}`} className="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700 hover:bg-amber-100">
                                        Feldeinsatz läuft
                                    </Link>
                                )}
                                {w.naechsteFaelligkeit && (
                                    <span className={cn('inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold',
                                        istUeberfaellig(w.naechsteFaelligkeit, heute) ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-white text-slate-600')}>
                                        {istUeberfaellig(w.naechsteFaelligkeit, heute) ? 'Überfällig seit' : 'Nächste Prüfung'} {formatDatum(w.naechsteFaelligkeit)}
                                    </span>
                                )}
                                <span className="text-xs text-slate-500">
                                    {w.anzahlPruefungen === 0 ? 'Noch keine Prüfung' : `${w.anzahlPruefungen} ${w.anzahlPruefungen === 1 ? 'Prüfung' : 'Prüfungen'}, letzte ${formatDatum(w.letztePruefungDatum)}`}
                                </span>
                            </div>
                            {w.berichte.length > 0 && (
                                <ul className="mt-2 space-y-1 border-t border-slate-200 pt-2">
                                    {w.berichte.map((b) => (
                                        <li key={b.protokollId}>
                                            <a href={b.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 text-rose-700 hover:underline">
                                                <FileText className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                                                <span className="break-words">Bericht {formatDatum(b.datum)} ({b.nummer})</span>
                                            </a>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </li>
                    ))}
                </ul>
            )}

            {kundeId !== null && (
                <NeueAnlageDialog open={anlageOffen} onOpenChange={setAnlageOffen} projektId={projektId} kundeId={kundeId} onAngelegt={() => { void laden(); }} />
            )}
            {kundeId !== null && startFuer !== null && (
                <NeueWartungDialog
                    open
                    onOpenChange={(offen) => { if (!offen) setStartFuer(null); }}
                    vorbelegung={{ kundeId, kundeName: startFuer.kundeName, objektId: startFuer.objektId, tuerId: startFuer.id }}
                    onAngelegt={(protokoll) => { setStartFuer(null); navigate(`/wartung/${protokoll.id}`); }}
                />
            )}
            {kundeId !== null && (
                <ZuordnenDialog open={dialogOffen} onOpenChange={setDialogOffen} projektId={projektId} kundeId={kundeId} onZugeordnet={() => { setDialogOffen(false); void laden(); }} />
            )}
        </section>
    );
}

interface DialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    projektId: number;
    kundeId: number;
    onZugeordnet: () => void;
}

/** Eine bestehende Wartung des Kunden diesem Projekt zuordnen. Es entsteht keine neue Wartung. */
function ZuordnenDialog({ open, onOpenChange, projektId, kundeId, onZugeordnet }: DialogProps) {
    const toast = useToast();
    const [auswahl, setAuswahl] = useState<Auswahl[] | null>(null);
    const [fehler, setFehler] = useState(false);
    const [arbeitet, setArbeitet] = useState<number | null>(null);
    const [filter, setFilter] = useState('');
    const [markiert, setMarkiert] = useState<Set<number>>(new Set());
    const [frage, setFrage] = useState(false);
    const [sammelt, setSammelt] = useState(false);

    useEffect(() => {
        if (!open) return;
        setAuswahl(null);
        setFehler(false);
        setFilter('');
        setMarkiert(new Set());
        setFrage(false);
        ladeObjekte(kundeId)
            .then((objekte: WartungObjekt[]) => setAuswahl(objekte.flatMap((o) => o.tueren
                .filter((t) => t.aktiv && t.projektId !== projektId)
                .map((t) => ({ tuerId: t.id, name: t.bezeichnung, objekt: o.bezeichnung, projektName: t.projektName })))))
            .catch(() => setFehler(true));
    }, [open, kundeId, projektId]);

    const zuordnen = async (eintrag: Auswahl) => {
        setArbeitet(eintrag.tuerId);
        try {
            await ordneProjektZu(eintrag.tuerId, projektId);
            toast.success(`„${eintrag.name}" ist dem Projekt zugeordnet.`);
            onZugeordnet();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Wartung konnte nicht zugeordnet werden.');
        } finally {
            setArbeitet(null);
        }
    };

    // „Alle auswählen" gilt für die gerade sichtbare (gefilterte) Liste
    const begriff = filter.trim().toLowerCase();
    const sichtbar = (auswahl ?? []).filter((e) => begriff === '' || `${e.name} ${e.objekt}`.toLowerCase().includes(begriff));
    const sichtbarMarkiert = sichtbar.filter((e) => markiert.has(e.tuerId));
    const alleSichtbarMarkiert = sichtbar.length > 0 && sichtbarMarkiert.length === sichtbar.length;
    const schalte = (id: number) => setMarkiert((alt) => { const neu = new Set(alt); if (neu.has(id)) neu.delete(id); else neu.add(id); return neu; });
    const schalteAlle = () => setMarkiert((alt) => {
        const neu = new Set(alt);
        if (alleSichtbarMarkiert) sichtbar.forEach((e) => neu.delete(e.tuerId)); else sichtbar.forEach((e) => neu.add(e.tuerId));
        return neu;
    });
    const andereProjekte = (auswahl ?? []).filter((e) => markiert.has(e.tuerId) && e.projektName !== null).length;

    const alleZuordnen = async () => {
        const eintraege = (auswahl ?? []).filter((e) => markiert.has(e.tuerId));
        setSammelt(true);
        let ok = 0;
        const fehlgeschlagen: Auswahl[] = [];
        for (const e of eintraege) {
            try {
                await ordneProjektZu(e.tuerId, projektId);
                ok++;
            } catch {
                fehlgeschlagen.push(e);
            }
        }
        setSammelt(false);
        setFrage(false);
        if (ok > 0) toast.success(ok === 1 ? '1 Wartung ist dem Projekt zugeordnet.' : `${ok} Wartungen sind dem Projekt zugeordnet.`);
        if (fehlgeschlagen.length > 0) {
            toast.error(`Nicht zugeordnet: ${fehlgeschlagen.map((e) => e.name).join(', ')}`);
            setAuswahl((alt) => (alt ?? []).filter((e) => fehlgeschlagen.some((f) => f.tuerId === e.tuerId)));
            setMarkiert(new Set(fehlgeschlagen.map((e) => e.tuerId)));
            if (ok > 0) onZugeordnet();
        } else {
            onZugeordnet();
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[520px]">
                <DialogHeader>
                    <DialogTitle>Wartung zuordnen</DialogTitle>
                    <DialogDescription>Wählen Sie eine bestehende Wartung dieses Kunden. Es entsteht keine neue Wartung.</DialogDescription>
                </DialogHeader>
                <div className="max-h-[50vh] overflow-y-auto">
                    {fehler && <p role="alert" className="text-sm text-rose-700">Die Wartungen des Kunden konnten nicht geladen werden.</p>}
                    {!fehler && auswahl === null && (
                        <div className="flex items-center gap-2 py-4 text-sm text-slate-500" role="status">
                            <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …
                        </div>
                    )}
                    {auswahl !== null && auswahl.length === 0 && (
                        <p className="py-4 text-sm text-slate-500">Dieser Kunde hat keine weiteren Wartungen, die noch nicht in diesem Projekt sind.</p>
                    )}
                    {auswahl !== null && auswahl.length > 0 && (
                        <div className="mb-2 space-y-2">
                            <Input value={filter} onChange={(ev) => setFilter(ev.target.value)} placeholder="Wartungen filtern (Name oder Objekt)" aria-label="Wartungen filtern" />
                            <label className="flex items-center gap-2 text-sm text-slate-700">
                                <input type="checkbox" className="h-4 w-4 rounded border-slate-300 accent-rose-600 focus:ring-2 focus:ring-rose-500" checked={alleSichtbarMarkiert} disabled={sichtbar.length === 0} onChange={schalteAlle} />
                                Alle auswählen{begriff !== '' ? ` (${sichtbar.length} gefilterte)` : ''}
                            </label>
                        </div>
                    )}
                    {auswahl !== null && auswahl.length > 0 && sichtbar.length === 0 && (
                        <p className="py-4 text-sm text-slate-500">Keine Wartung passt zum Filter.</p>
                    )}
                    {auswahl !== null && auswahl.length > 0 && (
                        <ul className="divide-y divide-slate-100">
                            {sichtbar.map((e) => (
                                <li key={e.tuerId} className="flex items-center justify-between gap-3 py-2">
                                    <input type="checkbox" className="h-4 w-4 shrink-0 rounded border-slate-300 accent-rose-600 focus:ring-2 focus:ring-rose-500" checked={markiert.has(e.tuerId)} onChange={() => schalte(e.tuerId)} aria-label={`${e.name} markieren`} />
                                    <div className="min-w-0 flex-1 text-sm">
                                        <p className="font-medium text-slate-900 break-words">{e.name}</p>
                                        <p className="text-slate-500 break-words">
                                            {e.objekt}{e.projektName && <span className="text-amber-700"> · gehört zu „{e.projektName}"</span>}
                                        </p>
                                    </div>
                                    <Button size="sm" variant="outline" disabled={arbeitet !== null} onClick={() => void zuordnen(e)} aria-label={`${e.name} diesem Projekt zuordnen`}>
                                        {arbeitet === e.tuerId ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : null}
                                        Zuordnen
                                    </Button>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
                {frage && (
                    <div className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900" role="alertdialog" aria-label="Zuordnung bestätigen">
                        {markiert.size === 1 ? '1 Wartung' : `${markiert.size} Wartungen`} diesem Projekt zuordnen?
                        {andereProjekte > 0 && ` ${andereProjekte === 1 ? '1 davon gehört' : `${andereProjekte} davon gehören`} noch zu einem anderen Projekt und wird verschoben.`}
                    </div>
                )}
                <DialogFooter>
                    <Button variant="outline" onClick={() => (frage ? setFrage(false) : onOpenChange(false))} disabled={sammelt}>{frage ? 'Abbrechen' : 'Schließen'}</Button>
                    {markiert.size > 0 && !frage && (
                        <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => setFrage(true)}>
                            Alle markierten zuordnen ({markiert.size})
                        </Button>
                    )}
                    {frage && (
                        <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" disabled={sammelt} onClick={() => void alleZuordnen()}>
                            {sammelt && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Ja, zuordnen
                        </Button>
                    )}
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
