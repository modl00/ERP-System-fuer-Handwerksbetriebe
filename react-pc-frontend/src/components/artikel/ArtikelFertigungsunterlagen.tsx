import { useCallback, useEffect, useRef, useState } from 'react';
import { Download, FileCode, Loader2, Paperclip, Trash2, Upload } from 'lucide-react';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';
import { InfoButton } from '../ui/info-button';
import type { Fertigungsunterlage } from '../../types';
import { ladeFertigungsunterlagenFuerArtikel, ladeFertigungsunterlageHoch, loescheFertigungsunterlage } from '../../features/bestellwesen/bestellwesenApi';

interface Props {
    artikelId: number;
}

const ACCEPT = '.dxf,.step,.stp,.dwg,.dxb,.pdf,application/pdf';

function dateigroesse(bytes?: number): string {
    if (!bytes) return '';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function datumKurz(iso?: string): string {
    return iso ? new Date(iso).toLocaleDateString('de-DE') : '';
}

/**
 * Fertigungsunterlagen (DXF, STEP, DWG, STP, DXB, PDF) am Artikelstamm.
 * Diese Dateien werden beim Bestellen des Artikels automatisch als Vorlage für die Bestellposition vorgeschlagen.
 */
export function ArtikelFertigungsunterlagen({ artikelId }: Props) {
    const toast = useToast();
    const confirm = useConfirm();
    const inputRef = useRef<HTMLInputElement>(null);

    const [unterlagen, setUnterlagen] = useState<Fertigungsunterlage[]>([]);
    const [laedt, setLaedt] = useState(true);
    const [hochladend, setHochladend] = useState(false);
    const [loeschtId, setLoeschtId] = useState<number | null>(null);

    const laden = useCallback(async () => {
        setLaedt(true);
        try {
            setUnterlagen(await ladeFertigungsunterlagenFuerArtikel(artikelId));
        } catch {
            toast.error('Die Fertigungsunterlagen konnten nicht geladen werden.');
        } finally {
            setLaedt(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [artikelId]);

    useEffect(() => { void laden(); }, [laden]);

    const hochladen = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const datei = e.target.files?.[0];
        e.target.value = '';
        if (!datei) return;
        setHochladend(true);
        try {
            await ladeFertigungsunterlageHoch(artikelId, datei);
            toast.success('Die Datei ist gespeichert.');
            await laden();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Datei konnte nicht hochgeladen werden.');
        } finally {
            setHochladend(false);
        }
    };

    const loeschen = async (u: Fertigungsunterlage) => {
        const ok = await confirm({
            title: 'Datei löschen?',
            message: `„${u.originalDateiname}" wirklich löschen? Das kann nicht rückgängig gemacht werden.`,
            confirmLabel: 'Ja, löschen',
            cancelLabel: 'Abbrechen',
            variant: 'danger',
        });
        if (!ok) return;
        setLoeschtId(u.id);
        try {
            await loescheFertigungsunterlage(u.id);
            setUnterlagen(prev => prev.filter(f => f.id !== u.id));
            toast.success('Die Datei ist gelöscht.');
        } catch {
            toast.error('Die Datei konnte nicht gelöscht werden.');
        } finally {
            setLoeschtId(null);
        }
    };

    return (
        <section className="bg-white border border-slate-200 rounded-lg p-5">
            <div className="flex items-center gap-2 mb-1">
                <h2 className="text-lg font-semibold text-slate-900">Fertigungsunterlagen</h2>
                <InfoButton titel="Fertigungsunterlagen">
                    <div className="space-y-2 text-sm">
                        <div>
                            <p className="font-medium text-slate-700">Was passiert im Hintergrund?</p>
                            <p className="text-slate-600">Die Dateien werden dem Artikel fest zugeordnet und auf dem Server gespeichert.</p>
                        </div>
                        <div>
                            <p className="font-medium text-slate-700">Was bewirkt das?</p>
                            <p className="text-slate-600">Beim Anlegen einer Bestellung für diesen Artikel werden die Fertigungsunterlagen automatisch als Vorlage für den Anhang der Bestellposition angeboten. Bei individuellen Maßfertigungen (z. B. lasergeschnittene Teile) können Sie die Zeichnung hier hinterlegen.</p>
                        </div>
                    </div>
                </InfoButton>
            </div>
            <p className="text-sm text-slate-500 mb-4">
                DXF, STEP, STP, DWG, DXB oder PDF – max. 25 MB je Datei. Werden beim Bestellen automatisch vorgeschlagen.
            </p>

            {laedt ? (
                <div className="motion-safe:animate-pulse space-y-2" aria-busy="true">
                    <div className="h-10 bg-slate-100 rounded-lg" />
                    <div className="h-10 bg-slate-100 rounded-lg" />
                </div>
            ) : (
                <>
                    {unterlagen.length === 0 ? (
                        <div className="flex flex-col items-center justify-center gap-2 py-8 mb-4 border border-dashed border-slate-200 rounded-lg bg-slate-50">
                            <FileCode className="w-6 h-6 text-slate-300" aria-hidden="true" />
                            <p className="text-sm text-slate-500">Noch keine Fertigungsunterlage hinterlegt.</p>
                        </div>
                    ) : (
                        <ul className="divide-y divide-slate-100 border border-slate-200 rounded-lg mb-4">
                            {unterlagen.map(u => (
                                <li key={u.id} className="flex items-center gap-3 px-3 py-2.5">
                                    <Paperclip className="w-4 h-4 text-slate-400 shrink-0" aria-hidden="true" />
                                    <span className="flex-1 truncate text-sm text-slate-900">{u.originalDateiname}</span>
                                    {u.dateigroesseBytes && (
                                        <span className="shrink-0 text-xs text-slate-400">{dateigroesse(u.dateigroesseBytes)}</span>
                                    )}
                                    <span className="shrink-0 text-xs text-slate-400">{datumKurz(u.erstelltAm)}</span>
                                    <a
                                        href={u.url}
                                        download={u.originalDateiname}
                                        title="Herunterladen"
                                        aria-label={`${u.originalDateiname} herunterladen`}
                                        className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded shrink-0"
                                    >
                                        <Download className="w-4 h-4" aria-hidden="true" />
                                    </a>
                                    <button
                                        type="button"
                                        onClick={() => void loeschen(u)}
                                        disabled={loeschtId === u.id}
                                        title="Löschen"
                                        aria-label={`${u.originalDateiname} löschen`}
                                        className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded shrink-0 disabled:opacity-50"
                                    >
                                        {loeschtId === u.id
                                            ? <Loader2 className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                                            : <Trash2 className="w-4 h-4" aria-hidden="true" />}
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}

                    <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        onClick={() => inputRef.current?.click()}
                        disabled={hochladend}
                    >
                        {hochladend
                            ? <Loader2 className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                            : <Upload className="w-4 h-4" aria-hidden="true" />}
                        Fertigungsunterlage hochladen
                    </Button>
                    <input
                        ref={inputRef}
                        type="file"
                        className="hidden"
                        aria-label="Fertigungsunterlage hochladen"
                        accept={ACCEPT}
                        onChange={e => void hochladen(e)}
                    />
                </>
            )}
        </section>
    );
}
