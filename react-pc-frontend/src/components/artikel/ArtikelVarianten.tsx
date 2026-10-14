import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Layers } from "lucide-react";
import { Button } from "../ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "../ui/dialog";
import { useToast } from "../ui/toast";
import { VariantenRaster } from "./VariantenRaster";
import { formatCurrency } from "./formatCurrency";
import { parseErrorMessage } from "../settings/settingsApi";

interface Zeile {
    id: number;
    produktname: string;
    artikelnummer: string | null;
    abmessung: string | null;
    kgProMeter: number | null;
    kgProQm: number | null;
    guenstigsterPreis: number | null;
}

const zahl = (w: number | null, einheit: string) => (w == null ? "–" : `${new Intl.NumberFormat("de-DE", { maximumFractionDigits: 3 }).format(w)} ${einheit}`);

/** Hinweis auf der Seite einer Variante: zu welchem Grundartikel sie gehört. */
export function VarianteHinweis({ grundartikelId, grundartikelName }: { grundartikelId: number; grundartikelName?: string | null }) {
    return (
        <p className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-2 text-sm text-slate-700" data-testid="variante-hinweis">
            Variante von{" "}
            <Link to={`/artikel/${encodeURIComponent(String(grundartikelId))}`} className="font-medium text-rose-700 underline-offset-2 hover:underline">
                {grundartikelName || "Grundartikel"}
            </Link>
        </p>
    );
}

interface Props {
    artikelId: number;
    /** Nach dem Anlegen neuer Varianten (die Seite lädt dann den Grundartikel neu). */
    onGeaendert: () => void;
}

/** Abschnitt „Varianten“ eines Grundartikels: alle Varianten mit Gewicht und Preis, dazu der Knopf zum Anlegen per Raster. */
export function ArtikelVarianten({ artikelId, onGeaendert }: Props) {
    const toast = useToast();
    const [zeilen, setZeilen] = useState<Zeile[] | null>(null);
    const [fehler, setFehler] = useState("");
    const [rasterOffen, setRasterOffen] = useState(false);

    const laden = useCallback(async (signal?: AbortSignal) => {
        try {
            const res = await fetch(`/api/artikel/${encodeURIComponent(String(artikelId))}/varianten`, { signal });
            if (!res.ok) throw new Error(await parseErrorMessage(res, "Die Varianten konnten nicht geladen werden."));
            setZeilen(await res.json() as Zeile[]);
            setFehler("");
        } catch (e) {
            if (signal?.aborted) return;
            const text = e instanceof Error ? e.message : "Die Varianten konnten nicht geladen werden.";
            setFehler(text);
            toast.error(text);
        }
    }, [artikelId, toast]);

    useEffect(() => {
        const ac = new AbortController();
        void laden(ac.signal);
        return () => ac.abort();
    }, [laden]);

    return (
        <section className="bg-white border border-slate-200 rounded-lg p-5" data-testid="artikel-varianten">
            <div className="flex flex-wrap items-end justify-between gap-3 mb-4">
                <div>
                    <h2 className="text-lg font-semibold text-slate-900">Varianten</h2>
                    <p className="text-sm text-slate-500">Alle Größen dieses Grundartikels. Jede Variante hat ihre eigenen Preise und Einstellungen.</p>
                </div>
                <Button size="sm" onClick={() => setRasterOffen(true)}>
                    <Layers className="w-4 h-4" aria-hidden="true" />
                    Varianten anlegen
                </Button>
            </div>

            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {!zeilen && !fehler && <p className="text-sm text-slate-500">Die Varianten werden geladen …</p>}
            {zeilen && zeilen.length === 0 && (
                <p className="py-6 text-center text-sm text-slate-500">Es gibt noch keine Varianten. Mit „Varianten anlegen“ entstehen alle Größen auf einmal.</p>
            )}
            {zeilen && zeilen.length > 0 && (
                <div className="overflow-x-auto rounded-lg border border-slate-200">
                    <table className="w-full text-sm">
                        <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                            <tr>
                                <th scope="col" className="px-3 py-2 font-medium">Name</th>
                                <th scope="col" className="px-3 py-2 font-medium">Artikel-Nr.</th>
                                <th scope="col" className="px-3 py-2 font-medium">Abmessung</th>
                                <th scope="col" className="px-3 py-2 text-right font-medium">Gewicht</th>
                                <th scope="col" className="px-3 py-2 text-right font-medium">Bester Preis</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {zeilen.map(z => (
                                <tr key={z.id}>
                                    <th scope="row" className="whitespace-nowrap px-3 py-2 text-left font-medium">
                                        <Link to={`/artikel/${encodeURIComponent(String(z.id))}`} className="text-rose-700 underline-offset-2 hover:underline">{z.produktname}</Link>
                                    </th>
                                    <td className="whitespace-nowrap px-3 py-2 font-mono text-xs text-slate-600">{z.artikelnummer ?? "–"}</td>
                                    <td className="px-3 py-2 text-slate-600">{z.abmessung ?? "–"}</td>
                                    <td className="whitespace-nowrap px-3 py-2 text-right tabular-nums text-slate-600">{z.kgProMeter != null ? zahl(z.kgProMeter, "kg/m") : zahl(z.kgProQm, "kg/m²")}</td>
                                    <td className="px-3 py-2 text-right tabular-nums">{z.guenstigsterPreis != null ? formatCurrency(z.guenstigsterPreis) : <span className="text-slate-400">kein Preis</span>}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            <Dialog open={rasterOffen} onOpenChange={setRasterOffen} className="w-full max-w-4xl max-h-[90vh]" aria-labelledby="varianten-raster-titel">
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle id="varianten-raster-titel">Varianten anlegen</DialogTitle>
                        <DialogDescription>Aus Maßreihen entstehen alle Größen auf einmal.</DialogDescription>
                    </DialogHeader>
                    <div className="min-h-0 flex-1 overflow-y-auto -m-1 p-1">
                        {rasterOffen && (
                            <VariantenRaster artikelId={artikelId} onAbbrechen={() => setRasterOffen(false)}
                                onAngelegt={() => { setRasterOffen(false); void laden(); onGeaendert(); }} />
                        )}
                    </div>
                </DialogContent>
            </Dialog>
        </section>
    );
}
