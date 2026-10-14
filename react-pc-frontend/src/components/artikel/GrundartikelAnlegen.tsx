import { useEffect, useMemo, useState } from "react";
import { Layers, Loader2 } from "lucide-react";
import { Button } from "../ui/button";
import { DecimalInput } from "../ui/decimal-input";
import { Input } from "../ui/input";
import { Label } from "../ui/label";
import { Select } from "../ui/select-custom";
import { InfoAbschnitt, InfoButton } from "../ui/info-button";
import { useToast } from "../ui/toast";
import { CategoryTreeModal } from "../CategoryTreeModal";
import { KategorieAuswahlFeld } from "./KategorieAuswahlFeld";
import { parseErrorMessage } from "../settings/settingsApi";
import { validateDecimalInput } from "../../lib/numberInput";

interface Form { profilform: string; form: string }
interface Werkstoff { id: number; name: string }

const EINHEITEN = [
    { value: "LAUFENDE_METER", label: "Laufende Meter" },
    { value: "QUADRATMETER", label: "Quadratmeter" },
    { value: "KILOGRAMM", label: "Kilogramm" },
    { value: "STUECK", label: "Stück" },
];

interface Props {
    /** Wird mit der ID des neuen Grundartikels aufgerufen. */
    onAngelegt: (id: number) => void;
    onAbbrechen: () => void;
}

/**
 * Einen Grundartikel anlegen: die Vorlage, aus der man danach per Raster alle Varianten erzeugt (zum Beispiel „QH Quadratrohr“
 * mit den Größen 20 bis 50 und den Wandstärken 1,5 bis 4). Er selbst hat keine Preise und kommt in kein Angebot.
 */
export function GrundartikelAnlegen({ onAngelegt, onAbbrechen }: Props) {
    const toast = useToast();
    const [kurz, setKurz] = useState("");
    const [name, setName] = useState("");
    const [form, setForm] = useState("");
    const [werkstoffId, setWerkstoffId] = useState("");
    const [einheit, setEinheit] = useState("LAUFENDE_METER");
    const [aufschlag, setAufschlag] = useState("");
    const [kategorieId, setKategorieId] = useState<number | null>(null);
    const [kategorieName, setKategorieName] = useState("");
    const [zeigeKategorien, setZeigeKategorien] = useState(false);
    const [formen, setFormen] = useState<Form[]>([]);
    const [werkstoffe, setWerkstoffe] = useState<Werkstoff[]>([]);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState("");

    useEffect(() => {
        let abgebrochen = false;
        fetch("/api/artikel/grundartikel/formen")
            .then(r => (r.ok ? r.json() : Promise.reject(new Error("Die Formen konnten nicht geladen werden."))))
            .then(d => { if (!abgebrochen) setFormen(Array.isArray(d) ? d : []); })
            .catch(() => { if (!abgebrochen) toast.error("Die Liste der Formen konnte nicht geladen werden."); });
        fetch("/api/artikel/werkstoffe/details")
            .then(r => (r.ok ? r.json() : Promise.reject(new Error("Die Werkstoffe konnten nicht geladen werden."))))
            .then(d => { if (!abgebrochen) setWerkstoffe(Array.isArray(d) ? d : []); })
            .catch(() => { if (!abgebrochen) toast.error("Die Liste der Werkstoffe konnte nicht geladen werden."); });
        return () => { abgebrochen = true; };
    }, [toast]);

    const formOptionen = useMemo(() => [{ value: "", label: "Form wählen" }, ...formen.map(f => ({ value: f.profilform, label: f.form }))], [formen]);
    const werkstoffOptionen = useMemo(() => [{ value: "", label: "– kein Werkstoff –" }, ...werkstoffe.map(w => ({ value: String(w.id), label: w.name }))], [werkstoffe]);

    const anlegen = async () => {
        setFehler("");
        const meldung = (text: string) => { setFehler(text); toast.error(text); };
        if (!kurz.trim()) return meldung("Bitte eine Kurzbezeichnung eingeben, zum Beispiel QH.");
        if (!name.trim()) return meldung("Bitte einen Namen eingeben, zum Beispiel Quadratrohr.");
        if (!form) return meldung("Bitte die Form wählen. Sie bestimmt, welche Maße die Varianten haben.");
        const p = validateDecimalInput(aufschlag, { label: "Aufschlag", min: 0, max: 999.99 });
        if (!p.valid) return meldung(p.message);

        setSpeichert(true);
        try {
            const res = await fetch("/api/artikel/grundartikel", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    kurzbezeichnung: kurz.trim(), produktname: name.trim(), profilform: form,
                    werkstoffId: werkstoffId ? Number(werkstoffId) : null, kategorieId,
                    verrechnungseinheit: einheit || null, verkaufsaufschlagProzent: p.value,
                }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, "Der Grundartikel konnte nicht angelegt werden."));
            const neu = await res.json() as { id: number };
            toast.success("Der Grundartikel ist angelegt. Jetzt kannst du die Varianten anlegen.");
            onAngelegt(neu.id);
        } catch (e) {
            meldung(e instanceof Error ? e.message : "Der Grundartikel konnte nicht angelegt werden.");
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <div className="space-y-4" data-testid="grundartikel-anlegen">
            <p className="flex items-center gap-2 text-sm text-slate-600">
                Ein Grundartikel fasst viele Größen zusammen, zum Beispiel „QH Quadratrohr“.
                <InfoButton titel="Grundartikel">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Der Grundartikel ist nur die Vorlage. Danach legst du daraus per Raster alle Varianten an (zum Beispiel „QH 40x40x2“). Die Varianten übernehmen die Angaben des Grundartikels beim Anlegen.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>Der Grundartikel selbst hat keinen Preis und kommt in kein Angebot. Preise, Aufschlag und Texte pflegst du je Variante. Spätere Änderungen am Grundartikel ändern bestehende Varianten nicht.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </p>

            <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-1.5">
                    <Label htmlFor="ga-kurz">Kurzbezeichnung *</Label>
                    <Input id="ga-kurz" value={kurz} maxLength={32} onChange={e => setKurz(e.target.value)} placeholder="z. B. QH" />
                    <p className="text-xs text-slate-500">Steht vor jeder Variante, zum Beispiel „QH 40x40x2“.</p>
                </div>
                <div className="space-y-1.5">
                    <Label htmlFor="ga-name">Name *</Label>
                    <Input id="ga-name" value={name} maxLength={255} onChange={e => setName(e.target.value)} placeholder="z. B. Quadratrohr" />
                </div>
                <div className="space-y-1.5">
                    <Label>Form *</Label>
                    <Select aria-label="Form" options={formOptionen} value={form} onChange={setForm} />
                </div>
                <div className="space-y-1.5">
                    <Label>Werkstoff</Label>
                    <Select aria-label="Werkstoff" options={werkstoffOptionen} value={werkstoffId} onChange={setWerkstoffId} />
                </div>
                <div className="space-y-1.5">
                    <Label>Kategorie</Label>
                    <KategorieAuswahlFeld name={kategorieName} gewaehlt={kategorieId != null} onOeffnen={() => setZeigeKategorien(true)}
                        onEntfernen={() => { setKategorieId(null); setKategorieName(""); }} />
                </div>
                <div className="space-y-1.5">
                    <Label>Verrechnungseinheit</Label>
                    <Select aria-label="Verrechnungseinheit" options={EINHEITEN} value={einheit} onChange={setEinheit} />
                </div>
                <div className="space-y-1.5">
                    <DecimalInput id="ga-aufschlag" label="Aufschlag in Prozent" value={aufschlag} onChange={setAufschlag} min={0} max={999.99} nullBehalten placeholder="leer = der allgemeine gilt" />
                </div>
            </div>

            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

            <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" size="sm" onClick={onAbbrechen} disabled={speichert}>Abbrechen</Button>
                <Button type="button" size="sm" onClick={anlegen} disabled={speichert}>
                    {speichert ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Layers className="w-4 h-4" aria-hidden="true" />}
                    Grundartikel anlegen
                </Button>
            </div>

            {zeigeKategorien && (
                <CategoryTreeModal
                    onSelect={(id, n) => { setKategorieId(Number(id)); setKategorieName(n); setZeigeKategorien(false); }}
                    onClose={() => setZeigeKategorien(false)}
                />
            )}
        </div>
    );
}
