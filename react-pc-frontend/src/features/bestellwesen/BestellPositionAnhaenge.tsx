import { useCallback, useEffect, useRef, useState } from 'react';
import { ChevronDown, ChevronRight, Download, FileCode, Loader2, Paperclip, PlusCircle, Trash2, Upload } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { InfoButton } from '../../components/ui/info-button';
import { useToast } from '../../components/ui/toast';
import { useConfirm } from '../../components/ui/confirm-dialog';
import type { Fertigungsunterlage } from '../../types';
import type { Bestellung } from './bestellwesenApi';
import {
    ladeAnhaengeVorschlaege, ladeAnhangHoch, loescheAnhang, uebernehmeVomArtikel,
} from './bestellwesenApi';

interface Props {
    bestellung: Bestellung;
    /** Wird aufgerufen, sobald ein Anhang hochgeladen oder gelöscht wurde, damit der übergeordnete Stand neu geladen werden kann. */
    onGeaendert: () => void;
}

const ACCEPT = '.dxf,.step,.stp,.dwg,.dxb,.pdf,application/pdf';

function dateigroesse(bytes?: number): string {
    if (!bytes) return '';
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

interface PositionAnhaengeState {
    offen: boolean;
    vorschlaege: Fertigungsunterlage[];
    vorschlaegeGeladen: boolean;
    laedt: boolean;
}

/**
 * Abschnitt "Fertigungsunterlagen" in der Bestellungsansicht.
 * Zeigt je gespeicherter Bestellposition die Anhänge und ermöglicht Upload und Übernahme vom Artikelstamm.
 */
export function BestellPositionAnhaenge({ bestellung, onGeaendert }: Props) {
    const toast = useToast();
    const confirm = useConfirm();

    // Nur gespeicherte Positionen (mit ID) können Anhänge haben
    const positionen = bestellung.positionen.filter(p => p.id > 0);

    const [zustand, setZustand] = useState<Record<number, PositionAnhaengeState>>(() =>
        Object.fromEntries(positionen.map(p => [p.id, { offen: false, vorschlaege: [], vorschlaegeGeladen: false, laedt: false }]))
    );

    const inputRefs = useRef<Record<number, HTMLInputElement | null>>({});

    // Zustand für neue Positionen synchronisieren
    useEffect(() => {
        setZustand(prev => {
            const next = { ...prev };
            positionen.forEach(p => {
                if (!next[p.id]) {
                    next[p.id] = { offen: false, vorschlaege: [], vorschlaegeGeladen: false, laedt: false };
                }
            });
            return next;
        });
    }, [positionen.map(p => p.id).join(',')]); // eslint-disable-line react-hooks/exhaustive-deps

    const toggleOffen = useCallback(async (positionId: number, hatArtikelId: boolean) => {
        setZustand(prev => ({ ...prev, [positionId]: { ...prev[positionId], offen: !prev[positionId].offen } }));

        // Vorschläge laden, wenn noch nicht geschehen und ein Artikel verknüpft ist
        if (hatArtikelId && !zustand[positionId]?.vorschlaegeGeladen) {
            try {
                const vorschlaege = await ladeAnhaengeVorschlaege(bestellung.id, positionId);
                setZustand(prev => ({
                    ...prev,
                    [positionId]: { ...prev[positionId], vorschlaege, vorschlaegeGeladen: true },
                }));
            } catch {
                // Vorschläge konnten nicht geladen werden – kein showstopper
            }
        }
    }, [bestellung.id, zustand]);

    const hochladen = useCallback(async (positionId: number, datei: File) => {
        setZustand(prev => ({ ...prev, [positionId]: { ...prev[positionId], laedt: true } }));
        try {
            await ladeAnhangHoch(bestellung.id, positionId, datei);
            toast.success('Datei angehängt.');
            onGeaendert();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Datei konnte nicht hochgeladen werden.');
        } finally {
            setZustand(prev => ({ ...prev, [positionId]: { ...prev[positionId], laedt: false } }));
        }
    }, [bestellung.id, onGeaendert, toast]);

    const uebernehmen = useCallback(async (positionId: number, fertigungsunterlageId: number) => {
        setZustand(prev => ({ ...prev, [positionId]: { ...prev[positionId], laedt: true } }));
        try {
            await uebernehmeVomArtikel(bestellung.id, positionId, fertigungsunterlageId);
            toast.success('Fertigungsunterlage übernommen.');
            onGeaendert();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Übernahme hat nicht geklappt.');
        } finally {
            setZustand(prev => ({ ...prev, [positionId]: { ...prev[positionId], laedt: false } }));
        }
    }, [bestellung.id, onGeaendert, toast]);

    const loeschen = useCallback(async (anhang: Fertigungsunterlage) => {
        const ok = await confirm({
            title: 'Anhang entfernen?',
            message: `„${anhang.originalDateiname}" aus dieser Bestellposition entfernen?`,
            confirmLabel: 'Ja, entfernen',
            cancelLabel: 'Abbrechen',
            variant: 'danger',
        });
        if (!ok) return;
        try {
            await loescheAnhang(anhang.id);
            toast.success('Anhang entfernt.');
            onGeaendert();
        } catch {
            toast.error('Der Anhang konnte nicht entfernt werden.');
        }
    }, [confirm, toast, onGeaendert]);

    if (positionen.length === 0) return null;

    // Gesamtanzahl aller Anhänge aller Positionen
    const anzahlGesamt = bestellung.positionen.reduce((s, p) => s + (p.anhaenge?.length ?? 0), 0);

    return (
        <section className="bg-white border border-slate-200 rounded-lg overflow-hidden">
            <div className="flex items-center gap-2 px-5 py-4 border-b border-slate-100">
                <FileCode className="h-5 w-5 text-slate-400 shrink-0" aria-hidden="true" />
                <h3 className="font-semibold text-slate-900">Fertigungsunterlagen</h3>
                {anzahlGesamt > 0 && (
                    <span className="ml-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">{anzahlGesamt}</span>
                )}
                <InfoButton
                    titel="Fertigungsunterlagen an Bestellpositionen"
                    content={
                        <div className="space-y-2 text-sm">
                            <div>
                                <p className="font-medium text-slate-700">Was passiert im Hintergrund?</p>
                                <p className="text-slate-600">Die Dateien werden der jeweiligen Bestellposition zugeordnet. Bei Vorschlägen wird die Datei aus dem Artikelstamm kopiert und dieser Position fest zugeordnet.</p>
                            </div>
                            <div>
                                <p className="font-medium text-slate-700">Was bewirkt das?</p>
                                <p className="text-slate-600">Beim Versenden der Bestellung per E-Mail werden alle Anhänge automatisch beigefügt – DXF, STEP und ähnliche Zeichnungen, die der Lieferant zur Fertigung braucht.</p>
                            </div>
                        </div>
                    }
                />
            </div>
            <ul className="divide-y divide-slate-100">
                {positionen.map(p => {
                    const z = zustand[p.id] ?? { offen: false, vorschlaege: [], vorschlaegeGeladen: false, laedt: false };
                    const anhaenge = p.anhaenge ?? [];
                    const offeneVorschlaege = z.vorschlaege.filter(v => !anhaenge.some(a => a.quellId === v.id));

                    return (
                        <li key={p.id}>
                            <button
                                type="button"
                                onClick={() => void toggleOffen(p.id, p.artikelId != null)}
                                className="flex w-full items-center gap-3 px-5 py-3 text-left hover:bg-slate-50"
                                aria-expanded={z.offen}
                            >
                                {z.offen
                                    ? <ChevronDown className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                                    : <ChevronRight className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />}
                                <span className="flex-1 truncate font-medium text-slate-900 text-sm">{p.bezeichnung}</span>
                                {anhaenge.length > 0 && (
                                    <span className="shrink-0 flex items-center gap-1 text-xs text-slate-500">
                                        <Paperclip className="h-3 w-3" aria-hidden="true" />
                                        {anhaenge.length}
                                    </span>
                                )}
                            </button>

                            {z.offen && (
                                <div className="px-5 pb-4 pt-1 space-y-3 bg-slate-50/60">
                                    {/* Vorhandene Anhänge */}
                                    {anhaenge.length > 0 && (
                                        <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white">
                                            {anhaenge.map(a => (
                                                <li key={a.id} className="flex items-center gap-3 px-3 py-2">
                                                    <Paperclip className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                                                    <span className="flex-1 truncate text-sm text-slate-900">{a.originalDateiname}</span>
                                                    {a.dateigroesseBytes && (
                                                        <span className="shrink-0 text-xs text-slate-400">{dateigroesse(a.dateigroesseBytes)}</span>
                                                    )}
                                                    <a href={a.url} download={a.originalDateiname} title="Herunterladen" aria-label={`${a.originalDateiname} herunterladen`}
                                                        className="p-1 text-slate-400 hover:text-slate-700 rounded shrink-0">
                                                        <Download className="h-4 w-4" aria-hidden="true" />
                                                    </a>
                                                    <button type="button" onClick={() => void loeschen(a)}
                                                        title="Entfernen" aria-label={`${a.originalDateiname} entfernen`}
                                                        className="p-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded shrink-0">
                                                        <Trash2 className="h-4 w-4" aria-hidden="true" />
                                                    </button>
                                                </li>
                                            ))}
                                        </ul>
                                    )}

                                    {/* Vorschläge vom Artikelstamm */}
                                    {offeneVorschlaege.length > 0 && (
                                        <div>
                                            <p className="mb-1.5 text-xs font-medium text-slate-500 uppercase tracking-wide">Aus dem Artikelstamm übernehmen</p>
                                            <ul className="space-y-1">
                                                {offeneVorschlaege.map(v => (
                                                    <li key={v.id} className="flex items-center gap-2 rounded-lg border border-dashed border-slate-300 bg-white px-3 py-2 text-sm">
                                                        <FileCode className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                                                        <span className="flex-1 truncate text-slate-700">{v.originalDateiname}</span>
                                                        <button type="button" disabled={z.laedt}
                                                            onClick={() => void uebernehmen(p.id, v.id)}
                                                            className="shrink-0 flex items-center gap-1 rounded-lg bg-slate-100 px-2 py-1 text-xs text-slate-700 hover:bg-rose-50 hover:text-rose-700 disabled:opacity-50">
                                                            <PlusCircle className="h-3.5 w-3.5" aria-hidden="true" />
                                                            Übernehmen
                                                        </button>
                                                    </li>
                                                ))}
                                            </ul>
                                        </div>
                                    )}

                                    {/* Upload */}
                                    <div className="flex items-center gap-2">
                                        <Button type="button" size="sm" variant="outline"
                                            className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                            onClick={() => inputRefs.current[p.id]?.click()}
                                            disabled={z.laedt}>
                                            {z.laedt
                                                ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />
                                                : <Upload className="h-4 w-4" aria-hidden="true" />}
                                            Datei hochladen
                                        </Button>
                                        <span className="text-xs text-slate-400">DXF, STEP, STP, DWG, DXB, PDF – max. 25 MB</span>
                                        <input
                                            type="file" className="hidden" accept={ACCEPT}
                                            ref={el => { inputRefs.current[p.id] = el; }}
                                            aria-label={`Datei für ${p.bezeichnung} hochladen`}
                                            onChange={e => {
                                                const datei = e.target.files?.[0];
                                                e.target.value = '';
                                                if (datei) void hochladen(p.id, datei);
                                            }}
                                        />
                                    </div>
                                </div>
                            )}
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}
