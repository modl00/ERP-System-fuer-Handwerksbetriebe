import { useRef, useState } from 'react';
import { CheckCircle2, Gem, Loader2, PackagePlus, ScanLine, XCircle } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { DecimalInput } from '../../components/ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { InfoButton } from '../../components/ui/info-button';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { formatDecimalInput } from '../../lib/numberInput';
import type { Lagerort } from './lagerApi';

// ---- API-Typen ----------------------------------------------------------------

interface ScanPosition {
    artikelnummer: string | null;
    bezeichnung: string;
    menge: number;
    einheit: string;
    artikelId: number | null;
    artikelName: string | null;
    gefunden: boolean;
}

interface ScanErgebnis {
    positionen: ScanPosition[];
    lieferantName: string | null;
    lieferscheinNummer: string | null;
    hinweise: string[];
}

interface BuchenPosition {
    artikelId: number | null;
    menge: number;
    artikelnummer: string | null;
    bezeichnung: string;
    einheit: string;
    neuAnlegen: boolean;
}

interface BuchenErgebnis {
    gebucht: number;
    neuAngelegt: number;
    meldungen: string[];
}

// ---- Zustand einer bearbeitbaren Zeile ----------------------------------------

interface Zeile {
    pos: ScanPosition;
    ausgewaehlt: boolean;
    menge: string;
    neuAnlegen: boolean;
}

const BASE = '/api/lager';

async function scanDatei(datei: File): Promise<ScanErgebnis> {
    const form = new FormData();
    form.append('datei', datei);
    const res = await fetch(`${BASE}/lieferschein-scan`, { method: 'POST', body: form });
    if (!res.ok) {
        const text = await res.text().catch(() => '');
        throw new Error(text || `Fehler ${res.status}`);
    }
    return res.json();
}

async function buchenApi(positionen: BuchenPosition[], lagerortId: number, notiz: string): Promise<BuchenErgebnis> {
    const res = await fetch(`${BASE}/lieferschein-scan/buchen`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ positionen, lagerortId, notiz }),
    });
    if (!res.ok) {
        const text = await res.text().catch(() => '');
        throw new Error(text || `Fehler ${res.status}`);
    }
    return res.json();
}

// ---- Props --------------------------------------------------------------------

export interface LieferscheinScanDialogProps {
    offen: boolean;
    orte: Lagerort[];
    onSchliessen: () => void;
    onGebucht: () => void;
}

// ---- Schritt-Typen ------------------------------------------------------------

type Schritt = 'upload' | 'ergebnis' | 'fertig';

// ---- Komponente ---------------------------------------------------------------

export function LieferscheinScanDialog({ offen, orte, onSchliessen, onGebucht }: LieferscheinScanDialogProps) {
    const toast = useToast();
    const inputRef = useRef<HTMLInputElement>(null);

    const [schritt, setSchritt] = useState<Schritt>('upload');
    const [laedt, setLaedt] = useState(false);
    const [ergebnis, setErgebnis] = useState<ScanErgebnis | null>(null);
    const [zeilen, setZeilen] = useState<Zeile[]>([]);
    const [ortId, setOrtId] = useState('');
    const [notiz, setNotiz] = useState('');
    const [fertig, setFertig] = useState<BuchenErgebnis | null>(null);

    const aktiveOrte = orte.filter(o => o.aktiv);

    function zuruecksetzen() {
        setSchritt('upload');
        setLaedt(false);
        setErgebnis(null);
        setZeilen([]);
        setOrtId('');
        setNotiz('');
        setFertig(null);
        if (inputRef.current) {
            inputRef.current.value = '';
        }
    }

    function schliessen() {
        zuruecksetzen();
        onSchliessen();
    }

    async function onDateigewaehlt(e: React.ChangeEvent<HTMLInputElement>) {
        const datei = e.target.files?.[0];
        if (!datei) return;
        setLaedt(true);
        setErgebnis(null);
        try {
            const res = await scanDatei(datei);
            setErgebnis(res);
            setZeilen(res.positionen.map(p => ({
                pos: p,
                ausgewaehlt: true,
                menge: formatDecimalInput(p.menge),
                neuAnlegen: !p.gefunden,
            })));
            if (aktiveOrte.length > 0 && !ortId) {
                setOrtId(String(aktiveOrte[0].id));
            }
            setSchritt('ergebnis');
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Der Lieferschein konnte nicht gescannt werden.');
        } finally {
            setLaedt(false);
            if (inputRef.current) inputRef.current.value = '';
        }
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

    async function buchen() {
        if (!ortId) {
            toast.error('Bitte einen Lagerort auswählen.');
            return;
        }
        const positionen: BuchenPosition[] = zeilen
            .filter(z => z.ausgewaehlt)
            .map(z => ({
                artikelId: z.pos.artikelId,
                menge: parseFloat(z.menge.replace(',', '.')) || 0,
                artikelnummer: z.pos.artikelnummer,
                bezeichnung: z.pos.bezeichnung,
                einheit: z.pos.einheit,
                neuAnlegen: z.neuAnlegen && !z.pos.gefunden,
            }))
            .filter(p => p.menge > 0);

        if (positionen.length === 0) {
            toast.error('Keine gültige Position zum Buchen ausgewählt.');
            return;
        }
        setLaedt(true);
        try {
            const res = await buchenApi(positionen, Number(ortId), notiz);
            setFertig(res);
            setSchritt('fertig');
            onGebucht();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Buchung ist fehlgeschlagen.');
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
                        <DialogTitle>Lieferschein scannen</DialogTitle>
                    </div>
                    <DialogDescription>
                        KI erkennt die Artikel und Mengen – Sie buchen sie direkt ins Lager.
                    </DialogDescription>
                </DialogHeader>

                {schritt === 'upload' && (
                    <div className="space-y-4 py-2">
                        <p className="text-sm text-slate-600">
                            Laden Sie ein PDF oder ein Foto des Lieferscheins hoch. Die KI erkennt Artikel,
                            Artikelnummern und Mengen automatisch.
                        </p>
                        <label
                            className={cn(
                                'flex flex-col items-center gap-3 p-8 border-2 border-dashed rounded-xl cursor-pointer transition-colors',
                                laedt ? 'border-slate-300 bg-slate-50' : 'border-rose-200 hover:border-rose-400 hover:bg-rose-50/40'
                            )}
                        >
                            {laedt ? (
                                <Loader2 className="w-8 h-8 text-rose-400 motion-safe:animate-spin" aria-hidden />
                            ) : (
                                <ScanLine className="w-8 h-8 text-rose-400" aria-hidden />
                            )}
                            <span className="text-sm font-medium text-slate-700">
                                {laedt ? 'Wird analysiert …' : 'PDF oder Foto auswählen'}
                            </span>
                            <span className="text-xs text-slate-400">PDF, JPG, PNG, WEBP · max. 15 MB</span>
                            <input
                                ref={inputRef}
                                type="file"
                                accept=".pdf,.jpg,.jpeg,.png,.webp,.gif"
                                className="sr-only"
                                onChange={onDateigewaehlt}
                                disabled={laedt}
                                aria-label="Lieferschein hochladen"
                            />
                        </label>
                    </div>
                )}

                {schritt === 'ergebnis' && ergebnis && (
                    <div className="space-y-4 py-2">
                        {/* Hinweise */}
                        {ergebnis.hinweise.length > 0 && (
                            <div className="rounded-lg bg-amber-50 border border-amber-200 px-4 py-3 text-sm text-amber-800 space-y-1">
                                {ergebnis.hinweise.map((h, i) => <p key={i}>{h}</p>)}
                            </div>
                        )}

                        {/* Metadaten */}
                        {(ergebnis.lieferantName || ergebnis.lieferscheinNummer) && (
                            <div className="flex flex-wrap gap-4 text-sm text-slate-600">
                                {ergebnis.lieferantName && (
                                    <span><span className="font-medium">Lieferant:</span> {ergebnis.lieferantName}</span>
                                )}
                                {ergebnis.lieferscheinNummer && (
                                    <span><span className="font-medium">Nr.:</span> {ergebnis.lieferscheinNummer}</span>
                                )}
                            </div>
                        )}

                        {/* Zeilen */}
                        {zeilen.length > 0 && (
                            <div className="border border-slate-200 rounded-xl overflow-hidden text-sm">
                                <div className="grid grid-cols-[auto_1fr_auto_auto_auto] gap-0 bg-slate-50 px-4 py-2 text-xs font-medium text-slate-500 border-b border-slate-200">
                                    <span />
                                    <span>Artikel</span>
                                    <span className="text-right pr-4">Menge</span>
                                    <span>Einh.</span>
                                    <span className="text-right" />
                                </div>
                                <div className="divide-y divide-slate-100 max-h-64 overflow-y-auto">
                                    {zeilen.map((z, idx) => (
                                        <div
                                            key={idx}
                                            className={cn(
                                                'grid grid-cols-[auto_1fr_auto_auto_auto] gap-2 items-center px-4 py-2.5 transition-colors',
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
                                                    {z.pos.artikelnummer && (
                                                        <span className="text-xs text-slate-400">Nr. {z.pos.artikelnummer}</span>
                                                    )}
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
                                            <span />
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Lagerort + Notiz */}
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <div className="space-y-1">
                                <div className="flex items-center gap-1.5">
                                    <Label htmlFor="scan-ort">Lagerort</Label>
                                    <InfoButton titel="Lagerort">
                                        <p>Was passiert im Hintergrund? Die ausgewählten Mengen werden als Zugänge auf diesen Lagerort gebucht.</p>
                                        <p>Was bewirkt das? Der Bestand steigt auf dem gewählten Ort. Andere Lagerorte bleiben unverändert.</p>
                                    </InfoButton>
                                </div>
                                <Select
                                    id="scan-ort"
                                    value={ortId}
                                    onChange={val => setOrtId(val)}
                                    options={aktiveOrte.map(o => ({ value: String(o.id), label: o.name }))}
                                    placeholder="Lagerort wählen …"
                                />
                            </div>
                            <div className="space-y-1">
                                <Label htmlFor="scan-notiz">Notiz (optional)</Label>
                                <input
                                    id="scan-notiz"
                                    type="text"
                                    value={notiz}
                                    onChange={e => setNotiz(e.target.value)}
                                    placeholder="z. B. Lieferschein-Nr. 12345"
                                    maxLength={200}
                                    className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400"
                                />
                            </div>
                        </div>
                    </div>
                )}

                {schritt === 'fertig' && fertig && (
                    <div className="py-4 space-y-4">
                        <div className="flex items-start gap-3 p-4 rounded-xl bg-emerald-50 border border-emerald-200">
                            <CheckCircle2 className="w-5 h-5 text-emerald-600 mt-0.5 shrink-0" aria-hidden />
                            <div className="text-sm text-emerald-800">
                                <p className="font-medium">
                                    {fertig.gebucht} {fertig.gebucht === 1 ? 'Position' : 'Positionen'} gebucht
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
                        <Button variant="outline" onClick={schliessen} disabled={laedt}>Abbrechen</Button>
                    )}
                    {schritt === 'ergebnis' && (
                        <>
                            <Button variant="outline" onClick={() => { zuruecksetzen(); }} disabled={laedt}>
                                Neuer Scan
                            </Button>
                            <Button variant="outline" onClick={schliessen} disabled={laedt}>
                                Abbrechen
                            </Button>
                            <Button
                                onClick={buchen}
                                disabled={laedt || ausgewaehltCount === 0 || !ortId}
                                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                            >
                                {laedt ? (
                                    <><Loader2 className="w-4 h-4 motion-safe:animate-spin" aria-hidden /> Wird gebucht …</>
                                ) : (
                                    <><PackagePlus className="w-4 h-4" aria-hidden />
                                        {ausgewaehltCount} {ausgewaehltCount === 1 ? 'Position' : 'Positionen'} buchen
                                    </>
                                )}
                            </Button>
                        </>
                    )}
                    {schritt === 'fertig' && (
                        <>
                            <Button variant="outline" onClick={() => zuruecksetzen()}>Weiterer Scan</Button>
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
