/**
 * Wareneingang aus Bestellungs-Dokumenten:
 * Zeigt eingegangene Lieferscheine / Auftragsbestätigungen / Rechnungen aus dem
 * Bestellwesen und erlaubt, deren Positionen direkt ins Lager zu buchen.
 * Optionaler Preis-Update bei erkanntem Artikel.
 */
import { useEffect, useState } from 'react';
import { Check, ChevronRight, Gem, Loader2, PackagePlus, RefreshCw, X } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { DecimalInput } from '../../components/ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { useToast } from '../../components/ui/toast';
import { formatDecimalInput } from '../../lib/numberInput';
import type { Lagerort } from './lagerApi';

// ---- Typen -----------------------------------------------------------------------

interface DokRef {
    id: number;
    typ: string;
    dokumentNummer: string | null;
    dokumentDatum: string | null;
    betragNetto: number | null;
    dateiname: string;
}

interface Kette {
    id: string;
    lieferantId: number | null;
    lieferantName: string | null;
    dokumente: DokRef[];
}

interface DokumentPosition {
    id: number;
    positionNr: number;
    positionsArt: string;
    externeArtikelnummer: string | null;
    bezeichnung: string | null;
    menge: number | null;
    mengeneinheit: string | null;
    einzelpreis: number | null;
}

interface Zeile {
    pos: DokumentPosition;
    ausgewaehlt: boolean;
    menge: string;
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

interface ArtikelTreffer {
    id: number;
    produktname: string;
    artikelnummer: string | null;
    einzelpreis: number | null;
}

// ---- API -------------------------------------------------------------------------

const TYP_TEXT: Record<string, string> = {
    AUFTRAGSBESTAETIGUNG: 'Auftragsbestätigung',
    LIEFERSCHEIN: 'Lieferschein',
    RECHNUNG: 'Rechnung',
    ANGEBOT: 'Angebot',
    WERKSTOFFZEUGNIS: 'Werkstoffzeugnis',
    GUTSCHRIFT: 'Gutschrift',
    SONSTIG: 'Sonstiges',
    BELEG: 'Beleg',
};

const BUCHBARE_TYPEN = new Set(['AUFTRAGSBESTAETIGUNG', 'LIEFERSCHEIN', 'RECHNUNG']);

async function ladeKetten(): Promise<Kette[]> {
    const res = await fetch('/api/bestellungen-uebersicht?anzahl=100');
    if (!res.ok) throw new Error('Bestellungen konnten nicht geladen werden.');
    const d = await res.json() as {
        laufendeBestellungen?: Kette[];
        abgeschlossen?: Kette[];
        offeneAnfragen?: Kette[];
    };
    return [
        ...(d.laufendeBestellungen ?? []),
        ...(d.abgeschlossen ?? []),
        ...(d.offeneAnfragen ?? []),
    ];
}

async function ladePositionen(dokumentId: number): Promise<DokumentPosition[]> {
    const res = await fetch(`/api/bestellungen-uebersicht/positionen/${encodeURIComponent(String(dokumentId))}`);
    if (res.status === 404) return [];
    if (!res.ok) throw new Error('Positionen konnten nicht geladen werden.');
    const d = await res.json() as { positionen?: unknown[] };
    return Array.isArray(d?.positionen) ? (d.positionen as DokumentPosition[]) : [];
}

async function buchenApi(positionen: BuchenPosition[], lagerortId: number, notiz: string): Promise<BuchenErgebnis> {
    const res = await fetch('/api/lager/lieferschein-scan/buchen', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ positionen, lagerortId, notiz }),
    });
    if (!res.ok) {
        const text = await res.text().catch(() => '');
        throw new Error(text || `Fehler ${res.status}`);
    }
    return res.json() as Promise<BuchenErgebnis>;
}

async function sucheArtikel(bezeichnung: string): Promise<ArtikelTreffer[]> {
    const q = encodeURIComponent(bezeichnung.substring(0, 50));
    const res = await fetch(`/api/artikel?suche=${q}&size=5`);
    if (!res.ok) return [];
    const d = await res.json() as { artikel?: ArtikelTreffer[] };
    return d.artikel ?? [];
}

async function aktualisiereArtikelPreis(lieferantId: number, artikelId: number, preis: number): Promise<void> {
    const res = await fetch(`/api/lieferanten/${lieferantId}/artikelpreise/${artikelId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ preis }),
    });
    if (!res.ok) {
        const d = await res.json().catch(() => ({})) as { message?: string };
        throw new Error(d.message ?? 'Preis konnte nicht aktualisiert werden.');
    }
}

// ---- Props -----------------------------------------------------------------------

export interface WareneingangAusBestellungenDialogProps {
    offen: boolean;
    orte: Lagerort[];
    onSchliessen: () => void;
    onGebucht: () => void;
}

// ---- Komponente ------------------------------------------------------------------

type Schritt = 'dokument' | 'positionen' | 'fertig';

export function WareneingangAusBestellungenDialog({
    offen, orte, onSchliessen, onGebucht,
}: WareneingangAusBestellungenDialogProps) {
    const toast = useToast();

    const [schritt, setSchritt] = useState<Schritt>('dokument');
    const [ketten, setKetten] = useState<Kette[] | null>(null);
    const [laedtKetten, setLaedtKetten] = useState(false);
    const [gewaehlteKette, setGewaehlteKette] = useState<Kette | null>(null);
    const [gewaehltesDok, setGewaehltesDok] = useState<DokRef | null>(null);
    const [zeilen, setZeilen] = useState<Zeile[]>([]);
    const [laedtPositionen, setLaedtPositionen] = useState(false);
    const [ortId, setOrtId] = useState('');
    const [notiz, setNotiz] = useState('');
    const [laedt, setLaedt] = useState(false);
    const [fertig, setFertig] = useState<BuchenErgebnis | null>(null);

    const aktiveOrte = orte.filter(o => o.aktiv);

    // Beim Öffnen: Ketten laden und Standardlagerort setzen
    useEffect(() => {
        if (!offen) return;
        setSchritt('dokument');
        setGewaehlteKette(null);
        setGewaehltesDok(null);
        setZeilen([]);
        setFertig(null);
        setNotiz('');
        if (aktiveOrte.length > 0 && !ortId) setOrtId(String(aktiveOrte[0].id));
        setLaedtKetten(true);
        ladeKetten()
            .then(setKetten)
            .catch(err => toast.error(err instanceof Error ? err.message : 'Bestellungen konnten nicht geladen werden.'))
            .finally(() => setLaedtKetten(false));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [offen]);

    const waehleKettenDok = async (kette: Kette, dok: DokRef) => {
        setGewaehlteKette(kette);
        setGewaehltesDok(dok);
        setLaedtPositionen(true);
        try {
            const positionen = await ladePositionen(dok.id);
            const warenPos = positionen.filter(p => p.positionsArt === 'WARE');
            if (warenPos.length === 0) {
                toast.error('Dieses Dokument hat keine Warenpositionen. Bitte zuerst per KI auslesen.');
                return;
            }
            setZeilen(warenPos.map(p => ({
                pos: p,
                ausgewaehlt: true,
                menge: formatDecimalInput(p.menge ?? 0),
            })));
            setSchritt('positionen');
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Positionen konnten nicht geladen werden.');
        } finally {
            setLaedtPositionen(false);
        }
    };

    const setAusgewaehlt = (idx: number, wert: boolean) =>
        setZeilen(prev => prev.map((z, i) => i === idx ? { ...z, ausgewaehlt: wert } : z));

    const setMenge = (idx: number, wert: string) =>
        setZeilen(prev => prev.map((z, i) => i === idx ? { ...z, menge: wert } : z));

    const alleUmschalten = () => {
        const alleAn = zeilen.every(z => z.ausgewaehlt);
        setZeilen(prev => prev.map(z => ({ ...z, ausgewaehlt: !alleAn })));
    };

    const buchen = async () => {
        const ausgewaehlt = zeilen.filter(z => z.ausgewaehlt);
        if (ausgewaehlt.length === 0) { toast.error('Bitte mindestens eine Position auswählen.'); return; }
        if (!ortId) { toast.error('Bitte einen Lagerort wählen.'); return; }

        const positionen: BuchenPosition[] = ausgewaehlt.map(z => ({
            artikelId: null,
            menge: parseFloat(z.menge.replace(',', '.')) || 1,
            artikelnummer: z.pos.externeArtikelnummer ?? null,
            bezeichnung: z.pos.bezeichnung ?? `Position ${z.pos.positionNr}`,
            einheit: z.pos.mengeneinheit ?? 'Stk',
            neuAnlegen: false,
        }));

        setLaedt(true);
        try {
            const ergebnis = await buchenApi(positionen, Number(ortId), notiz);
            setFertig(ergebnis);
            setSchritt('fertig');
            onGebucht();

            // Preise für Positionen mit Einzelpreis anbieten
            if (gewaehlteKette?.lieferantId) {
                const mitPreis = ausgewaehlt.filter(z => z.pos.einzelpreis != null && z.pos.bezeichnung);
                for (const z of mitPreis) {
                    try {
                        const treffer = await sucheArtikel(z.pos.bezeichnung!);
                        if (treffer.length === 1 && treffer[0].id) {
                            const a = treffer[0];
                            const neuerPreis = z.pos.einzelpreis!;
                            if (a.einzelpreis !== neuerPreis) {
                                toast.success(
                                    `„${a.produktname}": Preis auf ${neuerPreis.toLocaleString('de-DE', { minimumFractionDigits: 2 })} € aktualisiert.`,
                                );
                                await aktualisiereArtikelPreis(gewaehlteKette.lieferantId!, a.id, neuerPreis).catch(() => null);
                            }
                        }
                    } catch {
                        // Preis-Update ist optional – kein Fehler an Nutzer
                    }
                }
            }
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Buchung fehlgeschlagen.');
        } finally {
            setLaedt(false);
        }
    };

    const zurueckZuDokumenten = () => {
        setSchritt('dokument');
        setGewaehltesDok(null);
        setZeilen([]);
    };

    return (
        <Dialog open={offen} onOpenChange={v => { if (!v) onSchliessen(); }}>
            <DialogContent className="max-w-2xl w-full max-h-[90vh] flex flex-col overflow-hidden">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <PackagePlus className="w-5 h-5 text-rose-600" aria-hidden="true" />
                        Wareneingang aus Bestellungen
                    </DialogTitle>
                    <DialogDescription>
                        {schritt === 'dokument' && 'Dokument aus dem Bestellwesen wählen.'}
                        {schritt === 'positionen' && `${gewaehltesDok ? TYP_TEXT[gewaehltesDok.typ] ?? gewaehltesDok.typ : 'Dokument'}: Positionen auswählen und ins Lager buchen.`}
                        {schritt === 'fertig' && 'Buchung abgeschlossen.'}
                    </DialogDescription>
                </DialogHeader>

                {/* ---- Schritt 1: Dokument wählen ---- */}
                {schritt === 'dokument' && (
                    <div className="flex-1 overflow-y-auto space-y-2 py-2">
                        {laedtKetten && (
                            <div className="flex items-center justify-center py-8 text-slate-400">
                                <Loader2 className="w-5 h-5 animate-spin mr-2" aria-hidden="true" />
                                <span className="text-sm">Bestellungen werden geladen …</span>
                            </div>
                        )}
                        {!laedtKetten && ketten?.length === 0 && (
                            <p className="text-sm text-slate-500 text-center py-8">Keine laufenden Bestellungen vorhanden.</p>
                        )}
                        {!laedtKetten && ketten && ketten.map(kette => {
                            const buchbareDoks = kette.dokumente.filter(d => BUCHBARE_TYPEN.has(d.typ));
                            if (buchbareDoks.length === 0) return null;
                            return (
                                <div key={kette.id} className="border border-slate-200 rounded-lg overflow-hidden">
                                    <div className="bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700">
                                        {kette.lieferantName ?? 'Unbekannter Lieferant'}
                                    </div>
                                    {buchbareDoks.map(dok => (
                                        <button
                                            key={dok.id}
                                            type="button"
                                            onClick={() => waehleKettenDok(kette, dok)}
                                            disabled={laedtPositionen}
                                            className="w-full flex items-center justify-between px-3 py-2.5 text-left hover:bg-rose-50 hover:text-rose-700 transition-colors border-t border-slate-100 first:border-t-0 disabled:opacity-50 group"
                                        >
                                            <div>
                                                <span className="text-sm font-medium">
                                                    {TYP_TEXT[dok.typ] ?? dok.typ}
                                                    {dok.dokumentNummer && ` · ${dok.dokumentNummer}`}
                                                </span>
                                                {dok.dokumentDatum && (
                                                    <span className="ml-2 text-xs text-slate-400">
                                                        {new Date(dok.dokumentDatum).toLocaleDateString('de-DE')}
                                                    </span>
                                                )}
                                            </div>
                                            <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-rose-500 flex-shrink-0" aria-hidden="true" />
                                        </button>
                                    ))}
                                </div>
                            );
                        })}
                    </div>
                )}

                {/* ---- Schritt 2: Positionen bearbeiten ---- */}
                {schritt === 'positionen' && (
                    <>
                        <div className="flex-1 overflow-y-auto space-y-3 py-2">
                            {/* Alle / Keine */}
                            <div className="flex items-center justify-between">
                                <button type="button" onClick={alleUmschalten} className="text-xs text-rose-700 hover:underline">
                                    {zeilen.every(z => z.ausgewaehlt) ? 'Alle abwählen' : 'Alle auswählen'}
                                </button>
                                <span className="text-xs text-slate-400">
                                    {zeilen.filter(z => z.ausgewaehlt).length} von {zeilen.length} ausgewählt
                                </span>
                            </div>

                            {/* Positionstabelle */}
                            <div className="overflow-x-auto rounded-lg border border-slate-200">
                                <table className="w-full text-xs">
                                    <thead className="bg-slate-50 text-slate-500">
                                        <tr>
                                            <th scope="col" className="w-8 px-2 py-2"></th>
                                            <th scope="col" className="px-2 py-2 text-left font-medium">Bezeichnung</th>
                                            <th scope="col" className="px-2 py-2 text-right font-medium">Menge</th>
                                            <th scope="col" className="px-2 py-2 text-right font-medium">EP</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-slate-100 text-slate-700">
                                        {zeilen.map((z, idx) => (
                                            <tr key={z.pos.id} className={z.ausgewaehlt ? 'bg-rose-50/40' : ''}>
                                                <td className="px-2 py-2 text-center">
                                                    <input
                                                        type="checkbox"
                                                        checked={z.ausgewaehlt}
                                                        onChange={e => setAusgewaehlt(idx, e.target.checked)}
                                                        aria-label={`Position ${z.pos.positionNr} auswählen`}
                                                        className="accent-rose-600"
                                                    />
                                                </td>
                                                <td className="px-2 py-2">
                                                    <div className="font-medium">{z.pos.bezeichnung ?? '–'}</div>
                                                    {z.pos.externeArtikelnummer && (
                                                        <div className="text-slate-400">Art.-Nr. {z.pos.externeArtikelnummer}</div>
                                                    )}
                                                </td>
                                                <td className="px-2 py-2 text-right whitespace-nowrap">
                                                    <div className="flex items-center justify-end gap-1">
                                                        <DecimalInput
                                                            value={z.menge}
                                                            onChange={v => setMenge(idx, v)}
                                                            className="w-20 text-right text-xs h-7"
                                                            aria-label={`Menge für ${z.pos.bezeichnung ?? 'Position'}`}
                                                        />
                                                        <span className="text-slate-400">{z.pos.mengeneinheit ?? 'Stk'}</span>
                                                    </div>
                                                </td>
                                                <td className="px-2 py-2 text-right whitespace-nowrap tabular-nums text-slate-500">
                                                    {z.pos.einzelpreis != null
                                                        ? `${z.pos.einzelpreis.toLocaleString('de-DE', { minimumFractionDigits: 2 })} €`
                                                        : '–'}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>

                            {/* Lagerort + Notiz */}
                            <div className="grid gap-3 pt-1">
                                <div className="space-y-1">
                                    <Label htmlFor="wareneingang-ort">Lagerort</Label>
                                    <Select
                                        id="wareneingang-ort"
                                        value={ortId}
                                        onChange={e => setOrtId(e.target.value)}
                                        options={aktiveOrte.map(o => ({ value: String(o.id), label: o.name }))}
                                    />
                                </div>
                                <div className="space-y-1">
                                    <Label htmlFor="wareneingang-notiz">Notiz (optional)</Label>
                                    <input
                                        id="wareneingang-notiz"
                                        type="text"
                                        value={notiz}
                                        onChange={e => setNotiz(e.target.value)}
                                        placeholder="z. B. Lieferschein 2024-1234"
                                        className="w-full rounded-md border border-slate-200 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                                    />
                                </div>
                            </div>
                        </div>

                        <DialogFooter className="gap-2">
                            <Button variant="outline" size="sm" onClick={zurueckZuDokumenten} disabled={laedt}>
                                <ChevronRight className="w-4 h-4 rotate-180 mr-1" aria-hidden="true" />
                                Anderes Dokument
                            </Button>
                            <Button
                                size="sm"
                                onClick={buchen}
                                disabled={laedt || !zeilen.some(z => z.ausgewaehlt) || !ortId}
                                className="bg-rose-600 hover:bg-rose-700 text-white"
                            >
                                {laedt
                                    ? <><Loader2 className="w-4 h-4 animate-spin mr-1" aria-hidden="true" /> Wird gebucht …</>
                                    : <><PackagePlus className="w-4 h-4 mr-1" aria-hidden="true" /> Ins Lager buchen</>}
                            </Button>
                        </DialogFooter>
                    </>
                )}

                {/* ---- Schritt 3: Fertig ---- */}
                {schritt === 'fertig' && fertig && (
                    <>
                        <div className="flex-1 flex flex-col items-center justify-center gap-3 py-8 text-center">
                            <div className="w-12 h-12 rounded-full bg-emerald-100 flex items-center justify-center">
                                <Check className="w-6 h-6 text-emerald-600" aria-hidden="true" />
                            </div>
                            <p className="text-base font-semibold text-slate-800">
                                {fertig.gebucht} Position{fertig.gebucht !== 1 ? 'en' : ''} ins Lager gebucht
                            </p>
                            {fertig.neuAngelegt > 0 && (
                                <p className="text-sm text-slate-500">{fertig.neuAngelegt} neue Artikel angelegt.</p>
                            )}
                            {fertig.meldungen.map((m, i) => (
                                <p key={i} className="text-xs text-slate-400">{m}</p>
                            ))}
                        </div>
                        <DialogFooter className="gap-2">
                            <Button variant="outline" size="sm" onClick={() => { setSchritt('dokument'); setFertig(null); }}>
                                <RefreshCw className="w-4 h-4 mr-1" aria-hidden="true" />
                                Weiteres Dokument
                            </Button>
                            <Button size="sm" onClick={onSchliessen} className="bg-rose-600 hover:bg-rose-700 text-white">
                                <X className="w-4 h-4 mr-1" aria-hidden="true" />
                                Schließen
                            </Button>
                        </DialogFooter>
                    </>
                )}

                {/* ---- Schritt 1 Footer ---- */}
                {schritt === 'dokument' && (
                    <DialogFooter>
                        <Button variant="outline" size="sm" onClick={onSchliessen}>Schließen</Button>
                        {ketten && (
                            <Button variant="outline" size="sm" onClick={() => { setLaedtKetten(true); ladeKetten().then(setKetten).catch(() => null).finally(() => setLaedtKetten(false)); }}>
                                <RefreshCw className="w-4 h-4 mr-1" aria-hidden="true" />
                                Aktualisieren
                            </Button>
                        )}
                    </DialogFooter>
                )}
            </DialogContent>
        </Dialog>
    );
}
