import { useEffect, useMemo, useState } from "react";
import { Eye, Layers, Loader2 } from "lucide-react";
import { Button } from "../ui/button";
import { Input } from "../ui/input";
import { Label } from "../ui/label";
import { InfoAbschnitt, InfoButton } from "../ui/info-button";
import { useToast } from "../ui/toast";
import { parseErrorMessage } from "../settings/settingsApi";

interface Vorgabe {
    profilform: string;
    form: string;
    achseA: string | null;
    hinweisA: string | null;
    aMitZweitemMass: boolean;
    achseB: string | null;
    hinweisB: string | null;
}

interface Kombination {
    schluessel: string;
    name: string;
    artikelnummer: string;
    status: "NEU" | "SCHON_DA" | "NICHT_MOEGLICH";
    grund: string | null;
    kgProMeter: number | null;
    kgProQm: number | null;
}

interface Props {
    /** Der Grundartikel, zu dem die Varianten angelegt werden. */
    artikelId: number;
    /** Wird nach dem Anlegen aufgerufen (Zahl der neuen Varianten). */
    onAngelegt: (anzahl: number) => void;
    onAbbrechen: () => void;
}

const kg = (w: number | null, einheit: string) => (w == null ? "–" : `${new Intl.NumberFormat("de-DE", { maximumFractionDigits: 3 }).format(w)} ${einheit}`);

/**
 * Varianten eines Grundartikels als Raster anlegen: Maßreihen eintippen (zum Beispiel Kantenlängen „20 25 30 40“ und Wandstärken „1,5 2 3“),
 * die Vorschau zeigt alle Kombinationen mit Name, Artikelnummer und Gewicht, abwählen was nicht gebraucht wird, dann in einem Zug anlegen.
 */
export function VariantenRaster({ artikelId, onAngelegt, onAbbrechen }: Props) {
    const toast = useToast();
    const [vorgabe, setVorgabe] = useState<Vorgabe | null>(null);
    const [a, setA] = useState("");
    const [b, setB] = useState("");
    const [vorschau, setVorschau] = useState<Kombination[] | null>(null);
    const [abgewaehlt, setAbgewaehlt] = useState<Set<string>>(new Set());
    const [laedt, setLaedt] = useState(false);
    const [legtAn, setLegtAn] = useState(false);
    const [fehler, setFehler] = useState("");

    useEffect(() => {
        let abgebrochen = false;
        fetch(`/api/artikel/${encodeURIComponent(String(artikelId))}/varianten/raster`)
            .then(async res => {
                if (!res.ok) throw new Error(await parseErrorMessage(res, "Die Eingabe für die Varianten konnte nicht geladen werden."));
                return res.json() as Promise<Vorgabe>;
            })
            .then(v => { if (!abgebrochen) setVorgabe(v); })
            .catch(e => {
                if (abgebrochen) return;
                const text = e instanceof Error ? e.message : "Die Eingabe für die Varianten konnte nicht geladen werden.";
                setFehler(text);
                toast.error(text);
            });
        return () => { abgebrochen = true; };
    }, [artikelId, toast]);

    const aendern = (setze: (w: string) => void) => (w: string) => { setze(w); setVorschau(null); setAbgewaehlt(new Set()); setFehler(""); };

    const body = (ausgeschlossen: string[]) => JSON.stringify({ a: a.trim() ? [a] : [], b: b.trim() ? [b] : [], ausgeschlossen });
    const basis = `/api/artikel/${encodeURIComponent(String(artikelId))}/varianten`;
    const kopf = { "Content-Type": "application/json" };

    const zeigeVorschau = async () => {
        setFehler("");
        setLaedt(true);
        try {
            const res = await fetch(`${basis}/vorschau`, { method: "POST", headers: kopf, body: body([]) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, "Die Vorschau konnte nicht berechnet werden."));
            setVorschau(await res.json() as Kombination[]);
            setAbgewaehlt(new Set());
        } catch (e) {
            const text = e instanceof Error ? e.message : "Die Vorschau konnte nicht berechnet werden.";
            setFehler(text);
            toast.error(text);
            setVorschau(null);
        } finally {
            setLaedt(false);
        }
    };

    const neue = useMemo(() => (vorschau ?? []).filter(k => k.status === "NEU"), [vorschau]);
    const anzulegen = neue.filter(k => !abgewaehlt.has(k.schluessel));

    const anlegen = async () => {
        setFehler("");
        setLegtAn(true);
        try {
            const res = await fetch(basis, { method: "POST", headers: kopf, body: body([...abgewaehlt]) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, "Die Varianten konnten nicht angelegt werden."));
            toast.success(`${anzulegen.length} ${anzulegen.length === 1 ? "Variante ist" : "Varianten sind"} angelegt.`);
            onAngelegt(anzulegen.length);
        } catch (e) {
            const text = e instanceof Error ? e.message : "Die Varianten konnten nicht angelegt werden.";
            setFehler(text);
            toast.error(text);
        } finally {
            setLegtAn(false);
        }
    };

    const umschalten = (schluessel: string) => setAbgewaehlt(alt => {
        const neu = new Set(alt);
        if (neu.has(schluessel)) neu.delete(schluessel); else neu.add(schluessel);
        return neu;
    });

    const eingabeFehlt = !vorgabe || (!!vorgabe.achseA && !a.trim()) || (!!vorgabe.achseB && !b.trim());

    return (
        <div className="space-y-4" data-testid="varianten-raster">
            <p className="flex items-center gap-2 text-sm text-slate-600">
                Trage die Maße als Reihe ein, getrennt durch Leerzeichen. Das Programm bildet daraus alle Kombinationen.
                <InfoButton titel="Varianten anlegen">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Aus jeder Kombination entsteht ein eigener Artikel mit Namen, Artikelnummer, Maßen und berechnetem Gewicht. Die Angaben des Grundartikels (Werkstoff, Kategorie, Aufschlag, Texte, Eignung zum Verzinken) werden dabei kopiert.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>Jede Variante ist danach unabhängig: Preise, Aufschlag und Texte pflegst du je Variante. Spätere Änderungen am Grundartikel ändern die Varianten nicht. Was es schon gibt, legt das Programm nicht doppelt an.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </p>

            {vorgabe && (
                <div className="grid gap-4 md:grid-cols-2">
                    {vorgabe.achseA && (
                        <div className="space-y-1.5">
                            <Label htmlFor="raster-a">{vorgabe.achseA}</Label>
                            <Input id="raster-a" value={a} onChange={e => aendern(setA)(e.target.value)} placeholder={vorgabe.hinweisA ?? ""} />
                        </div>
                    )}
                    {vorgabe.achseB && (
                        <div className="space-y-1.5">
                            <Label htmlFor="raster-b">{vorgabe.achseB}</Label>
                            <Input id="raster-b" value={b} onChange={e => aendern(setB)(e.target.value)} placeholder={vorgabe.hinweisB ?? ""} />
                        </div>
                    )}
                </div>
            )}

            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

            <div className="flex flex-wrap gap-2">
                <Button type="button" variant="outline" size="sm" onClick={zeigeVorschau} disabled={eingabeFehlt || laedt}
                    title={eingabeFehlt && vorgabe ? "Bitte erst die Maße eintragen" : undefined}>
                    {laedt ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Eye className="w-4 h-4" aria-hidden="true" />}
                    Vorschau
                </Button>
            </div>

            {vorschau && (
                <div className="overflow-x-auto rounded-lg border border-slate-200">
                    <table className="w-full text-sm" data-testid="varianten-vorschau">
                        <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                            <tr>
                                <th scope="col" className="px-3 py-2"><span className="sr-only">Anlegen</span></th>
                                <th scope="col" className="px-3 py-2 font-medium">Name</th>
                                <th scope="col" className="px-3 py-2 font-medium">Artikelnummer</th>
                                <th scope="col" className="px-3 py-2 text-right font-medium">Gewicht</th>
                                <th scope="col" className="px-3 py-2 font-medium">Hinweis</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {vorschau.map(k => (
                                <tr key={k.schluessel} className={k.status === "NEU" ? "" : "bg-slate-50 text-slate-500"}>
                                    <td className="px-3 py-2">
                                        {k.status === "NEU" && (
                                            <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                                aria-label={`${k.name} anlegen`} checked={!abgewaehlt.has(k.schluessel)} onChange={() => umschalten(k.schluessel)} />
                                        )}
                                    </td>
                                    <th scope="row" className="whitespace-nowrap px-3 py-2 text-left font-medium">{k.name}</th>
                                    <td className="whitespace-nowrap px-3 py-2 font-mono text-xs">{k.artikelnummer}</td>
                                    <td className="whitespace-nowrap px-3 py-2 text-right tabular-nums">{k.kgProMeter != null ? kg(k.kgProMeter, "kg/m") : kg(k.kgProQm, "kg/m²")}</td>
                                    <td className="px-3 py-2 text-xs">
                                        {k.status === "SCHON_DA" && "Gibt es schon"}
                                        {k.status === "NICHT_MOEGLICH" && <span className="text-amber-700">{k.grund}</span>}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {vorschau && (
                <p className="text-sm text-slate-600" role="status">
                    {neue.length === 0 ? "Hier gibt es nichts Neues anzulegen." : `${anzulegen.length} von ${neue.length} neuen Varianten werden angelegt.`}
                </p>
            )}

            <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" size="sm" onClick={onAbbrechen} disabled={legtAn}>Abbrechen</Button>
                <Button type="button" size="sm" onClick={anlegen} disabled={!vorschau || anzulegen.length === 0 || legtAn}
                    title={!vorschau ? "Bitte erst die Vorschau ansehen" : anzulegen.length === 0 ? "Es ist nichts zum Anlegen gewählt" : undefined}>
                    {legtAn ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Layers className="w-4 h-4" aria-hidden="true" />}
                    {anzulegen.length > 0 ? `${anzulegen.length} ${anzulegen.length === 1 ? "Variante" : "Varianten"} anlegen` : "Varianten anlegen"}
                </Button>
            </div>
        </div>
    );
}
