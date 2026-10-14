import { useEffect, useState } from "react";
import { Loader2, Trash2 } from "lucide-react";
import { Button } from "../ui/button";
import { useConfirm } from "../ui/confirm-dialog";
import { InfoAbschnitt, InfoButton } from "../ui/info-button";
import { useToast } from "../ui/toast";
import { ladeVerwendungen, loescheArtikelEndgueltig, type Verwendungen } from "./artikelArchivApi";

interface Props {
    artikelId: number;
    name: string;
    /** Wird nach dem Löschen aufgerufen. */
    onGeloescht: () => void;
    onAbbrechen: () => void;
}

const mehrzahl = (n: number, eins: string, viele: string) => `${n} ${n === 1 ? eins : viele}`;

/**
 * Endgültig löschen: zeigt zuerst, wo der Artikel überall verwendet wird, und fragt erst danach nach. Nur für Administratoren gedacht
 * (die Seite zeigt den Knopf sonst nicht; das Backend lehnt andere ohnehin ab).
 */
export function ArtikelLoeschen({ artikelId, name, onGeloescht, onAbbrechen }: Props) {
    const toast = useToast();
    const bestaetigen = useConfirm();
    const [daten, setDaten] = useState<Verwendungen | null>(null);
    const [fehler, setFehler] = useState("");
    const [loescht, setLoescht] = useState(false);

    useEffect(() => {
        const ac = new AbortController();
        ladeVerwendungen(artikelId, ac.signal)
            .then(setDaten)
            .catch(e => {
                if (ac.signal.aborted) return;
                const text = e instanceof Error ? e.message : "Die Verwendung des Artikels konnte nicht geladen werden.";
                setFehler(text);
                toast.error(text);
            });
        return () => ac.abort();
    }, [artikelId, toast]);

    const inProjekten = daten?.projekte.length ?? 0;

    const loeschen = async () => {
        if (!daten) return;
        const folgen = inProjekten > 0
            ? `Der Artikel steckt in ${mehrzahl(inProjekten, "Projekt", "Projekten")}. Dort bleibt er als Text mit Preis stehen. Alles andere (Preise, Dokumente) entfällt.`
            : "Der Artikel samt seinen Preisen und Dokumenten wird entfernt.";
        const ja = await bestaetigen({
            title: "Artikel endgültig löschen?",
            message: `„${name}“ wird endgültig gelöscht und lässt sich nicht wiederherstellen. ${folgen}`,
            confirmLabel: "Endgültig löschen",
            variant: "danger",
        });
        if (!ja) return;
        setLoescht(true);
        try {
            const ergebnis = await loescheArtikelEndgueltig(artikelId);
            toast.success(ergebnis.alsPlatzhalter
                ? `„${name}“ ist gelöscht. In ${mehrzahl(ergebnis.projekte, "Projektzeile", "Projektzeilen")} bleibt er als Text mit Preis stehen.`
                : `„${name}“ ist gelöscht.`);
            onGeloescht();
        } catch (e) {
            const text = e instanceof Error ? e.message : "Der Artikel konnte nicht gelöscht werden.";
            setFehler(text);
            toast.error(text);
        } finally {
            setLoescht(false);
        }
    };

    return (
        <div className="space-y-4" data-testid="artikel-loeschen">
            <p className="flex items-center gap-2 text-sm text-slate-600">
                Endgültig gelöscht wird „{name}“. Das lässt sich nicht rückgängig machen.
                <InfoButton titel="Endgültig löschen">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Der Artikel wird mit seinen Lieferantenpreisen, dem Preisverlauf und seinen Dokumenten entfernt. Steckt er noch in einem Projekt, bleibt er dort mit Namen und Preis stehen, damit Stückliste, Bestellung und Nachkalkulation stimmen. Er taucht aber nirgends mehr auf und lässt sich nicht zurückholen.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>Bereits erstellte Angebote und Rechnungen bleiben unverändert, sie enthalten nur eine Kopie der Angaben. Wer den Artikel nur aus der Liste haben möchte, lässt ihn besser im Archiv.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </p>

            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {!daten && !fehler && <p className="text-sm text-slate-500">Es wird nachgesehen, wo der Artikel verwendet wird …</p>}

            {daten && (
                <div className="space-y-3 rounded-lg border border-slate-200 p-4" data-testid="artikel-verwendungen">
                    <h3 className="text-sm font-semibold text-slate-900">Hier wird der Artikel verwendet</h3>
                    {inProjekten === 0 ? (
                        <p className="text-sm text-slate-600">In keinem Projekt.</p>
                    ) : (
                        <ul className="divide-y divide-slate-100 text-sm">
                            {daten.projekte.map((p, i) => (
                                <li key={`${p.projektId}-${i}`} className="flex flex-wrap justify-between gap-2 py-1.5">
                                    <span className="font-medium text-slate-800">{p.bauvorhaben || "Projekt ohne Namen"}</span>
                                    <span className="text-slate-600">{p.menge}</span>
                                </li>
                            ))}
                        </ul>
                    )}
                    <p className="text-sm text-slate-600">
                        Dazu {mehrzahl(daten.lieferantenpreise, "Lieferantenpreis", "Lieferantenpreise")} und {mehrzahl(daten.dokumente, "Dokument", "Dokumente")}, die mit gelöscht werden.
                    </p>
                    {daten.varianten > 0 && (
                        <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
                            Dieser Grundartikel hat noch {mehrzahl(daten.varianten, "Variante", "Varianten")}. Sie müssen zuerst gelöscht werden.
                        </p>
                    )}
                </div>
            )}

            <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" size="sm" onClick={onAbbrechen} disabled={loescht}>Abbrechen</Button>
                <Button type="button" size="sm" onClick={loeschen} disabled={!daten || loescht || (daten?.varianten ?? 0) > 0}
                    title={!daten ? "Bitte kurz warten" : (daten.varianten > 0 ? "Zuerst die Varianten löschen" : undefined)}>
                    {loescht ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Trash2 className="w-4 h-4" aria-hidden="true" />}
                    Endgültig löschen
                </Button>
            </div>
        </div>
    );
}
