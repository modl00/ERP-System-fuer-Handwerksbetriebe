import { useRef, useState } from 'react';
import { CheckCircle2, FileText, Gem, Loader2, ListPlus, ScanLine, XCircle } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { DecimalInput } from '../../components/ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { formatDecimalInput } from '../../lib/numberInput';

// ---- API-Typen ----------------------------------------------------------------

interface ScanPosition {
    bezeichnung: string;
    menge: number;
    einheit: string;
    artikelId: number | null;
    artikelName: string | null;
    gefunden: boolean;
}

interface ScanErgebnis {
    positionen: ScanPosition[];
    hinweise: string[];
}

interface UebernahmePosition {
    artikelId: number | null;
    bezeichnung: string;
    menge: number;
    einheit: string;
    neuAnlegen: boolean;
}

interface UebernahmeErgebnis {
    uebernommen: number;
    neuAngelegt: number;
    meldungen: string[];
}

interface ProjektDokumentKurzinfo {
    id: number;
    originalDateiname: string;
}

// ---- Zustand einer bearbeitbaren Zeile ----------------------------------------

interface Zeile {
    pos: ScanPosition;
    ausgewaehlt: boolean;
    menge: string;
    neuAnlegen: boolean;
}

type Schritt = 'upload' | 'ergebnis' | 'fertig';
type UploadArt = 'datei' | 'projektdok';

// ---- Props --------------------------------------------------------------------

export interface StuecklistenScanDialogProps {
    offen: boolean;
    projektId: number;
    projektDokumente: ProjektDokumentKurzinfo[];
    onSchliessen: () => void;
    onUebernommen: () => void;
}

// ---- Hilfsfunktionen ----------------------------------------------------------

function erlaubteMimeTypes() {
    return '.pdf,.jpg,.jpeg,.png,.webp,.gif';
}

// ---- Komponente ---------------------------------------------------------------

export function StuecklistenScanDialog({
    offen,
    projektId,
    projektDokumente,
    onSchliessen,
    onUebernommen,
}: StuecklistenScanDialogProps) {
    const toast = useToast();
    const inputRef = useRef<HTMLInputElement>(null);

    const [schritt, setSchritt] = useState<Schritt>('upload');
    const [laedt, setLaedt] = useState(false);
    const [uploadArt, setUploadArt] = useState<UploadArt>('datei');
    const [gewaehlteDokId, setGewaehlteDokId] = useState('');
    const [zeilen, setZeilen] = useState<Zeile[]>([]);
    const [fertig, setFertig] = useState<UebernahmeErgebnis | null>(null);

    // Nur PDF/Bild-Dokumente für den Scan
    const scannbareDoks = projektDokumente.filter(d => {
        const ext = d.originalDateiname.split('.').pop()?.toLowerCase() ?? '';
        return ['pdf', 'jpg', 'jpeg', 'png', 'webp', 'gif'].includes(ext);
    });

    function zuruecksetzen() {
        setSchritt('upload');
        setLaedt(false);
        setUploadArt('datei');
        setGewaehlteDokId('');
        setZeilen([]);
        setFertig(null);
        if (inputRef.current) inputRef.current.value = '';
    }

    function schliessen() {
        zuruecksetzen();
        onSchliessen();
    }

    async function scannenMitDatei(datei: File) {
        setLaedt(true);
        try {
            const form = new FormData();
            form.append('datei', datei);
            const res = await fetch(`/api/projekte/${projektId}/bedarfsliste/aus-dokument/scan`, {
                method: 'POST',
                body: form,
            });
            if (!res.ok) {
                const text = await res.text().catch(() => '');
                throw new Error(text || `Fehler ${res.status}`);
            }
            const ergebnis: ScanErgebnis = await res.json();
            verarbeiteErgebnis(ergebnis);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Das Dokument konnte nicht gescannt werden.');
        } finally {
            setLaedt(false);
            if (inputRef.current) inputRef.current.value = '';
        }
    }

    async function scannenMitProjektdok() {
        if (!gewaehlteDokId) {
            toast.error('Bitte ein Dokument auswählen.');
            return;
        }
        setLaedt(true);
        try {
            // Datei vom Projekt-Dokument-Endpunkt herunterladen
            const downloadRes = await fetch(`/api/projekte/${projektId}/dokumente/${gewaehlteDokId}/download`);
            if (!downloadRes.ok) {
                throw new Error('Das Dokument konnte nicht geladen werden.');
            }
            const blob = await downloadRes.blob();
            const dok = scannbareDoks.find(d => String(d.id) === gewaehlteDokId);
            const datei = new File([blob], dok?.originalDateiname ?? 'dokument.pdf', { type: blob.type });
            await scannenMitDatei(datei);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Das Dokument konnte nicht gescannt werden.');
            setLaedt(false);
        }
    }

    function verarbeiteErgebnis(ergebnis: ScanErgebnis) {
        setZeilen(ergebnis.positionen.map(p => ({
            pos: p,
            ausgewaehlt: true,
            menge: formatDecimalInput(p.menge),
            neuAnlegen: !p.gefunden,
        })));
        if (ergebnis.hinweise.length > 0 && ergebnis.positionen.length === 0) {
            toast.error(ergebnis.hinweise[0]);
            return;
        }
        setSchritt('ergebnis');
    }

    async function onDateigewaehlt(e: React.ChangeEvent<HTMLInputElement>) {
        const datei = e.target.files?.[0];
        if (!datei) return;
        await scannenMitDatei(datei);
    }

    function toggleZeile(idx: number) {
        setZeilen(prev => prev.map((z, i) => i === idx ? { ...z, ausgewaehlt: !z.ausgewaehlt } : z));
    }

    function setMenge(idx: number, val: string) {
        setZeilen(prev => prev.map((z, i) => i === idx ? { ...z, menge: val } : z));
    }

    function toggleNeuAnlegen(idx: number) {
        setZeilen(prev => prev.map((z, i) => i === idx ? { ...z, neuAnlegen: !z.neuAnlegen } : z));
    }

    async function uebernehmen() {
        const positionen: UebernahmePosition[] = zeilen
            .filter(z => z.ausgewaehlt)
            .map(z => ({
                artikelId: z.pos.artikelId,
                bezeichnung: z.pos.bezeichnung,
                menge: parseFloat(z.menge.replace(',', '.')) || 0,
                einheit: z.pos.einheit,
                neuAnlegen: z.neuAnlegen && !z.pos.gefunden,
            }))
            .filter(p => p.menge > 0);

        if (positionen.length === 0) {
            toast.error('Keine gültige Position zum Übernehmen ausgewählt.');
            return;
        }
        setLaedt(true);
        try {
            const res = await fetch(`/api/projekte/${projektId}/bedarfsliste/aus-dokument/uebernehmen`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(positionen),
            });
            if (!res.ok) {
                const text = await res.text().catch(() => '');
                throw new Error(text || `Fehler ${res.status}`);
            }
            const ergebnis: UebernahmeErgebnis = await res.json();
            setFertig(ergebnis);
            setSchritt('fertig');
            onUebernommen();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Übernahme ist fehlgeschlagen.');
        } finally {
            setLaedt(false);
        }
    }

    const ausgewaehltCount = zeilen.filter(z => z.ausgewaehlt).length;

    return (
        <Dialog open={offen} onOpenChange={open => { if (!open) schliessen(); }}>
            <DialogContent className="max-w-2xl rounded-2xl">
                <DialogHeader>
                    <div className="flex items-center gap-2">
                        <Gem className="w-4 h-4 text-rose-500" aria-hidden />
                        <DialogTitle>Bedarfsliste aus Dokument</DialogTitle>
                    </div>
                    <DialogDescription>
                        KI erkennt Materialpositionen aus einer Stückliste oder Planung und legt sie in der Bedarfsliste an.
                    </DialogDescription>
                </DialogHeader>

                {schritt === 'upload' && (
                    <div className="space-y-4 py-2">
                        {/* Upload-Art wählen */}
                        {scannbareDoks.length > 0 && (
                            <div className="flex gap-2">
                                <button
                                    type="button"
                                    onClick={() => setUploadArt('datei')}
                                    className={cn(
                                        'flex-1 rounded-lg border px-3 py-2 text-sm font-medium transition-colors',
                                        uploadArt === 'datei'
                                            ? 'border-rose-400 bg-rose-50 text-rose-700'
                                            : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                                    )}
                                >
                                    Datei hochladen
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setUploadArt('projektdok')}
                                    className={cn(
                                        'flex-1 rounded-lg border px-3 py-2 text-sm font-medium transition-colors',
                                        uploadArt === 'projektdok'
                                            ? 'border-rose-400 bg-rose-50 text-rose-700'
                                            : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                                    )}
                                >
                                    Aus Projektdokumenten
                                </button>
                            </div>
                        )}

                        {uploadArt === 'datei' && (
                            <label
                                className={cn(
                                    'flex flex-col items-center gap-3 p-8 border-2 border-dashed rounded-xl cursor-pointer transition-colors',
                                    laedt
                                        ? 'border-slate-300 bg-slate-50'
                                        : 'border-rose-200 hover:border-rose-400 hover:bg-rose-50/40'
                                )}
                            >
                                {laedt ? (
                                    <Loader2 className="w-8 h-8 text-rose-400 motion-safe:animate-spin" aria-hidden />
                                ) : (
                                    <ScanLine className="w-8 h-8 text-rose-400" aria-hidden />
                                )}
                                <span className="text-sm font-medium text-slate-700">
                                    {laedt ? 'Wird analysiert …' : 'Stückliste oder Planung auswählen'}
                                </span>
                                <span className="text-xs text-slate-400">PDF, JPG, PNG, WEBP · max. 15 MB</span>
                                <input
                                    ref={inputRef}
                                    type="file"
                                    accept={erlaubteMimeTypes()}
                                    className="sr-only"
                                    onChange={onDateigewaehlt}
                                    disabled={laedt}
                                    aria-label="Stückliste hochladen"
                                />
                            </label>
                        )}

                        {uploadArt === 'projektdok' && scannbareDoks.length > 0 && (
                            <div className="space-y-3">
                                <p className="text-sm text-slate-600">
                                    Wählen Sie ein Dokument aus dem Projekt, das eine Stückliste oder Materialliste enthält.
                                </p>
                                <div className="border border-slate-200 rounded-xl divide-y divide-slate-100 max-h-56 overflow-y-auto">
                                    {scannbareDoks.map(dok => (
                                        <label
                                            key={dok.id}
                                            className={cn(
                                                'flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors hover:bg-slate-50',
                                                gewaehlteDokId === String(dok.id) && 'bg-rose-50'
                                            )}
                                        >
                                            <input
                                                type="radio"
                                                name="projektdok"
                                                value={String(dok.id)}
                                                checked={gewaehlteDokId === String(dok.id)}
                                                onChange={() => setGewaehlteDokId(String(dok.id))}
                                                className="accent-rose-600"
                                            />
                                            <FileText className="w-4 h-4 text-slate-400 shrink-0" aria-hidden />
                                            <span className="text-sm text-slate-800 truncate">{dok.originalDateiname}</span>
                                        </label>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {schritt === 'ergebnis' && (
                    <div className="space-y-4 py-2">
                        {zeilen.length > 0 && (
                            <div className="border border-slate-200 rounded-xl overflow-hidden text-sm">
                                <div className="grid grid-cols-[auto_1fr_auto_auto] gap-0 bg-slate-50 px-4 py-2 text-xs font-medium text-slate-500 border-b border-slate-200">
                                    <span />
                                    <span>Bezeichnung</span>
                                    <span className="text-right pr-4">Menge</span>
                                    <span>Einh.</span>
                                </div>
                                <div className="divide-y divide-slate-100 max-h-72 overflow-y-auto">
                                    {zeilen.map((z, idx) => (
                                        <div
                                            key={idx}
                                            className={cn(
                                                'grid grid-cols-[auto_1fr_auto_auto] gap-2 items-center px-4 py-2.5 transition-colors',
                                                z.ausgewaehlt ? 'bg-white' : 'bg-slate-50/60 opacity-60'
                                            )}
                                        >
                                            <input
                                                type="checkbox"
                                                checked={z.ausgewaehlt}
                                                onChange={() => toggleZeile(idx)}
                                                className="accent-rose-600 w-4 h-4 rounded cursor-pointer"
                                                aria-label={`${z.pos.bezeichnung} auswählen`}
                                            />
                                            <div className="min-w-0">
                                                <div className="font-medium text-slate-800 truncate">{z.pos.bezeichnung}</div>
                                                <div className="flex items-center gap-2 mt-0.5">
                                                    {z.pos.gefunden ? (
                                                        <span className="inline-flex items-center gap-0.5 text-xs text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded-full">
                                                            <CheckCircle2 className="w-3 h-3" aria-hidden /> {z.pos.artikelName}
                                                        </span>
                                                    ) : (
                                                        <label className="inline-flex items-center gap-1.5 text-xs cursor-pointer select-none">
                                                            <input
                                                                type="checkbox"
                                                                checked={z.neuAnlegen}
                                                                onChange={() => toggleNeuAnlegen(idx)}
                                                                disabled={!z.ausgewaehlt}
                                                                className="accent-rose-600 w-3.5 h-3.5 rounded"
                                                            />
                                                            <span className={cn(z.neuAnlegen ? 'text-rose-700' : 'text-slate-400')}>
                                                                Neu anlegen
                                                            </span>
                                                        </label>
                                                    )}
                                                </div>
                                            </div>
                                            <div className="w-24">
                                                <DecimalInput
                                                    aria-label={`Menge für ${z.pos.bezeichnung}`}
                                                    value={z.menge}
                                                    min={0.001}
                                                    max={10_000_000}
                                                    onChange={val => setMenge(idx, val)}
                                                    disabled={!z.ausgewaehlt}
                                                />
                                            </div>
                                            <span className="text-slate-500 text-xs w-8">{z.pos.einheit}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                        {zeilen.length === 0 && (
                            <p className="text-sm text-slate-500 text-center py-4">
                                Keine Positionen erkannt. Bitte ein anderes Dokument wählen.
                            </p>
                        )}
                    </div>
                )}

                {schritt === 'fertig' && fertig && (
                    <div className="py-4 space-y-4">
                        <div className="flex items-start gap-3 p-4 rounded-xl bg-emerald-50 border border-emerald-200">
                            <CheckCircle2 className="w-5 h-5 text-emerald-600 mt-0.5 shrink-0" aria-hidden />
                            <div className="text-sm text-emerald-800">
                                <p className="font-medium">
                                    {fertig.uebernommen} {fertig.uebernommen === 1 ? 'Position' : 'Positionen'} in die Bedarfsliste übernommen
                                    {fertig.neuAngelegt > 0 && `, ${fertig.neuAngelegt} Artikel neu angelegt`}
                                </p>
                            </div>
                        </div>
                        {fertig.meldungen.length > 0 && (
                            <div className="rounded-lg bg-amber-50 border border-amber-200 px-4 py-3 text-sm text-amber-800 space-y-1">
                                {fertig.meldungen.map((m, i) => (
                                    <p key={i} className="flex items-start gap-1.5">
                                        <XCircle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden /> {m}
                                    </p>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                <DialogFooter>
                    {schritt === 'upload' && (
                        <>
                            <Button variant="outline" onClick={schliessen} disabled={laedt}>
                                Abbrechen
                            </Button>
                            {uploadArt === 'projektdok' && (
                                <Button
                                    onClick={scannenMitProjektdok}
                                    disabled={laedt || !gewaehlteDokId}
                                    className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                >
                                    {laedt ? (
                                        <><Loader2 className="w-4 h-4 motion-safe:animate-spin" aria-hidden /> Wird analysiert …</>
                                    ) : (
                                        <><Gem className="w-4 h-4" aria-hidden /> Analysieren</>
                                    )}
                                </Button>
                            )}
                        </>
                    )}
                    {schritt === 'ergebnis' && (
                        <>
                            <Button variant="outline" onClick={() => zuruecksetzen()} disabled={laedt}>
                                Neuer Scan
                            </Button>
                            <Button variant="outline" onClick={schliessen} disabled={laedt}>
                                Abbrechen
                            </Button>
                            <Button
                                onClick={uebernehmen}
                                disabled={laedt || ausgewaehltCount === 0}
                                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                            >
                                {laedt ? (
                                    <><Loader2 className="w-4 h-4 motion-safe:animate-spin" aria-hidden /> Wird übernommen …</>
                                ) : (
                                    <><ListPlus className="w-4 h-4" aria-hidden />
                                        {ausgewaehltCount} {ausgewaehltCount === 1 ? 'Position' : 'Positionen'} übernehmen
                                    </>
                                )}
                            </Button>
                        </>
                    )}
                    {schritt === 'fertig' && (
                        <>
                            <Button variant="outline" onClick={() => zuruecksetzen()}>Weiteres Dokument</Button>
                            <Button onClick={schliessen} className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700">
                                Schließen
                            </Button>
                        </>
                    )}
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
