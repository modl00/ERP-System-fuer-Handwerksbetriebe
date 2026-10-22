import { useEffect, useMemo, useState } from "react";
import { Loader2, Save, X } from "lucide-react";
import { Button } from "../ui/button";
import { Input } from "../ui/input";
import { InfoButton } from "../ui/info-button";
import { Label } from "../ui/label";
import { Select } from "../ui/select-custom";
import { useToast } from "../ui/toast";
import { CategoryTreeModal } from "../CategoryTreeModal";
import { KategorieAuswahlFeld } from "./KategorieAuswahlFeld";
import { ArtikelFormelModal, type ArtikelFormel } from "./ArtikelFormelModal";
import { formatDecimalInput, validateDecimalInput } from "../../lib/numberInput";
import { validateNumberDrafts } from "../../lib/numberDrafts";
import type { Artikel } from "../../types";

interface Option { wert: string; name: string; erklaerung?: string }
interface Filteroptionen { herstellverfahren: Option[]; fertigungszustand: Option[]; profilform: Option[] }

const VERRECHNUNGSEINHEITEN = [
    { value: "LAUFENDE_METER", label: "Laufende Meter" },
    { value: "QUADRATMETER", label: "Quadratmeter" },
    { value: "KILOGRAMM", label: "Kilogramm" },
    { value: "STUECK", label: "Stück" },
    { value: "LITER", label: "Liter" },
];

const KEINE = "";

/** Eignung für ein Verfahren: vom Werkstoff übernehmen (Standard) oder für diesen Artikel festlegen. */
type Eignungswahl = "UEBERNEHMEN" | "JA" | "NEIN";
const wahlAus = (eigen?: boolean | null): Eignungswahl => (eigen == null ? "UEBERNEHMEN" : eigen ? "JA" : "NEIN");
const wertAus = (wahl: Eignungswahl): boolean | null => (wahl === "UEBERNEHMEN" ? null : wahl === "JA");
const eignungsOptionen = (werkstoffWert?: boolean) => [
    { value: "UEBERNEHMEN", label: werkstoffWert == null ? "Vom Werkstoff übernehmen" : `Vom Werkstoff übernehmen (${werkstoffWert ? "geeignet" : "nicht geeignet"})` },
    { value: "JA", label: "Geeignet" },
    { value: "NEIN", label: "Nicht geeignet" },
];

/** Wie das Gewicht eines Artikels gerechnet wird: je Meter, je m² (Blech) oder je Stück. */
type Art = "METER" | "BLECH" | "STUECK";
const BLECHFORMEN = ["BLECH", "RIFFELBLECH", "LOCHBLECH"];
const ART_OPTIONEN = [
    { value: "METER", label: "Meterware (je Meter)" },
    { value: "BLECH", label: "Blech (je m²)" },
    { value: "STUECK", label: "Stückware (je Stück)" },
];
const HERKUNFT_TEXT: Record<string, string> = { TABELLE: "aus der Profiltabelle", BERECHNET: "aus den Maßen berechnet", HAND: "von Hand eingetragen" };

const zahlText = (w?: number | null) => (w == null ? "" : formatDecimalInput(w));
const einheitName = (e: Artikel["verrechnungseinheit"]) => (typeof e === "string" ? e : e?.name ?? "");

/** Die Maße, die nur Halbzeug (Rohre, Profile, Bleche) hat. */
const MASSE = [
    { schluessel: "durchmesser", label: "Durchmesser (mm)" },
    { schluessel: "hoehe", label: "Höhe (mm)" },
    { schluessel: "breite", label: "Breite (mm)" },
    { schluessel: "wandstaerke", label: "Wandstärke (mm)" },
    { schluessel: "stegdicke", label: "Stegdicke (mm)" },
    { schluessel: "flanschdicke", label: "Flanschdicke (mm)" },
] as const;
type MassSchluessel = typeof MASSE[number]["schluessel"];

/** Ein Profil der mitgelieferten Profiltabelle (IPE, HEA, HEB, UPN). */
interface ProfilEintrag { bezeichnung: string; hoeheMm: number; breiteMm: number; kgJeMeter: number; m2JeMeter: number }

interface Props {
    artikel: Artikel;
    onGespeichert: () => void;
    onAbbrechen: () => void;
}

/**
 * Alle Stammdaten eines Artikels ändern: Name, Nummern, Einheit, Kategorie, Werkstoff, technische Merkmale und Maße.
 * Texte für Angebote, Lieferanten und Preise haben weiter ihre eigenen Bereiche. Speichern ersetzt die Stammdaten vollständig,
 * deshalb sind hier alle Felder vorbelegt, auch die, die man nicht ändert.
 */
export function ArtikelStammdatenForm({ artikel, onGespeichert, onAbbrechen }: Props) {
    const toast = useToast();
    const hatMasse = !!artikel.profilform || MASSE.some(m => artikel[m.schluessel] != null) || artikel.standardlaenge != null;

    const [produktname, setProduktname] = useState(artikel.produktname ?? "");
    const [produktlinie, setProduktlinie] = useState(artikel.produktlinie ?? "");
    const [produkttext, setProdukttext] = useState(artikel.produkttext ?? "");
    const [artikelnummer, setArtikelnummer] = useState(artikel.artikelnummer ?? "");
    const [ean, setEan] = useState(artikel.ean ?? "");
    const [einheit, setEinheit] = useState(einheitName(artikel.verrechnungseinheit));
    const [verpackung, setVerpackung] = useState(zahlText(artikel.verpackungseinheit));
    const [preiseinheit, setPreiseinheit] = useState(artikel.preiseinheit ?? "");
    const [kategorieId, setKategorieId] = useState<number | null>(artikel.kategorieId ?? null);
    const [kategorieName, setKategorieName] = useState(artikel.kategoriePfad ?? "");
    const [werkstoffId, setWerkstoffId] = useState(artikel.werkstoffId != null ? String(artikel.werkstoffId) : KEINE);
    // Eignung für die Oberflächenbehandlung: vom Werkstoff übernehmen (Standard) oder für diesen Artikel festlegen
    const [verzinken, setVerzinken] = useState<Eignungswahl>(wahlAus(artikel.verzinkungsgeeignetEigen));
    const [pulvern, setPulvern] = useState<Eignungswahl>(wahlAus(artikel.pulverbeschichtungsgeeignetEigen));
    const [profilform, setProfilform] = useState(artikel.profilform?.name ?? KEINE);
    const [herstellverfahren, setHerstellverfahren] = useState(artikel.herstellverfahren?.name ?? KEINE);
    const [fertigungszustand, setFertigungszustand] = useState(artikel.fertigungszustand?.name ?? KEINE);
    const [massnorm, setMassnorm] = useState(artikel.massnorm ?? "");
    const [werkstoffnorm, setWerkstoffnorm] = useState(artikel.werkstoffnorm ?? "");
    const [masse, setMasse] = useState<Record<MassSchluessel, string>>(() => ({
        durchmesser: zahlText(artikel.durchmesser), hoehe: zahlText(artikel.hoehe), breite: zahlText(artikel.breite),
        wandstaerke: zahlText(artikel.wandstaerke), stegdicke: zahlText(artikel.stegdicke), flanschdicke: zahlText(artikel.flanschdicke),
    }));
    const [laenge, setLaenge] = useState(zahlText(artikel.standardlaenge));

    // Gewicht und Oberfläche je Meter: aus der Profiltabelle gewählt, aus den Maßen berechnet oder von Hand eingetragen
    const [kgJeMeter, setKgJeMeter] = useState(zahlText(artikel.kgProMeter));
    const [m2JeMeter, setM2JeMeter] = useState(zahlText(artikel.mantelflaeche));
    const [art, setArt] = useState<Art>(() => {
        if (artikel.profilform && BLECHFORMEN.includes(artikel.profilform.name)) return "BLECH";
        if (artikel.kgProQm != null && artikel.kgProMeter == null) return "BLECH";
        if (einheitName(artikel.verrechnungseinheit) === "STUECK" && artikel.kgProMeter == null) return "STUECK";
        return "METER";
    });
    const [kgJeQm, setKgJeQm] = useState(zahlText(artikel.kgProQm));
    const [flaecheJeQm, setFlaecheJeQm] = useState(zahlText(artikel.flaecheJeQm));
    const [kgJeStueck, setKgJeStueck] = useState(zahlText(artikel.kgJeStueck));
    const [m2JeStueck, setM2JeStueck] = useState(zahlText(artikel.m2JeStueck));
    const [herkunft, setHerkunft] = useState(artikel.gewichtHerkunft ?? "");
    const [dichte, setDichte] = useState<number | null>(null);
    const [serverVeraltet, setServerVeraltet] = useState(!!artikel.gewichtVeraltet);
    const [basis, setBasis] = useState<string | null>(null);
    const [basisNeu, setBasisNeu] = useState(true);
    const [formel, setFormel] = useState<ArtikelFormel>(() => ({ felder: artikel.formelFelder ?? [], gewicht: artikel.formelGewicht ?? "", flaeche: artikel.formelFlaeche ?? "" }));
    const [formelOffen, setFormelOffen] = useState(false);
    const hatFormel = formel.gewicht.trim() !== "" || formel.flaeche.trim() !== "";
    const [tabellenFormen, setTabellenFormen] = useState<string[]>([]);
    const [tabelle, setTabelle] = useState<ProfilEintrag[]>([]);
    const [rechenHinweis, setRechenHinweis] = useState("");
    const [rechnet, setRechnet] = useState(false);

    const [optionen, setOptionen] = useState<Filteroptionen | null>(null);
    const [werkstoffe, setWerkstoffe] = useState<{ id: number; name: string; verzinkungsgeeignet?: boolean; pulverbeschichtungsgeeignet?: boolean }[]>([]);
    const [zeigeKategorien, setZeigeKategorien] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState("");

    useEffect(() => {
        let abgebrochen = false;
        fetch("/api/artikel/filteroptionen")
            .then(r => (r.ok ? r.json() : Promise.reject(new Error("Auswahllisten konnten nicht geladen werden."))))
            .then(d => { if (!abgebrochen) setOptionen(d as Filteroptionen); })
            .catch(() => { if (!abgebrochen) toast.error("Die Auswahllisten für Form, Verfahren und Zustand konnten nicht geladen werden."); });
        fetch("/api/artikel/werkstoffe/details")
            .then(r => (r.ok ? r.json() : Promise.reject(new Error("Werkstoffe konnten nicht geladen werden."))))
            .then(d => { if (!abgebrochen) setWerkstoffe(Array.isArray(d) ? d : []); })
            .catch(() => { if (!abgebrochen) toast.error("Die Liste der Werkstoffe konnte nicht geladen werden. Der bisherige Werkstoff bleibt gewählt."); });
        return () => { abgebrochen = true; };
    }, [toast]);

    useEffect(() => {
        let abgebrochen = false;
        fetch("/api/artikel/profile/formen")
            .then(r => (r.ok ? r.json() : []))
            .then(d => { if (!abgebrochen) setTabellenFormen(Array.isArray(d) ? d.map((f: { name?: string } | string) => (typeof f === "string" ? f : f.name ?? "")) : []); })
            .catch(() => { /* ohne Tabelle bleibt nur die Rechnung und die Handeingabe */ });
        return () => { abgebrochen = true; };
    }, []);

    const hatTabelle = tabellenFormen.includes(profilform);
    const signatur = JSON.stringify([profilform, werkstoffId, masse.durchmesser, masse.hoehe, masse.breite, masse.wandstaerke]);
    useEffect(() => {
        if (basisNeu) { setBasis(signatur); setBasisNeu(false); }
    }, [basisNeu, signatur]);
    const geaendertSeitBerechnung = (herkunft === "TABELLE" || herkunft === "BERECHNET") && basis !== null && basis !== signatur;
    const veraltet = geaendertSeitBerechnung || serverVeraltet;

    useEffect(() => {
        if (!hatTabelle) return;
        let abgebrochen = false;
        fetch(`/api/artikel/profile/tabelle?profilform=${encodeURIComponent(profilform)}`)
            .then(r => (r.ok ? r.json() : []))
            .then(d => { if (!abgebrochen) setTabelle(Array.isArray(d) ? d : []); })
            .catch(() => { if (!abgebrochen) setTabelle([]); });
        return () => { abgebrochen = true; };
    }, [hatTabelle, profilform]);

    const alsOptionen = (liste?: Option[]) => [{ value: KEINE, label: "– keine Angabe –" }, ...(liste ?? []).map(o => ({ value: o.wert, label: o.name }))];
    const werkstoffOptionen = useMemo(() => {
        const liste = werkstoffe.map(w => ({ value: String(w.id), label: w.name }));
        if (werkstoffId && !liste.some(w => w.value === werkstoffId)) liste.push({ value: werkstoffId, label: artikel.werkstoffName ?? `Werkstoff ${werkstoffId}` });
        return [{ value: KEINE, label: "– kein Werkstoff –" }, ...liste];
    }, [werkstoffe, werkstoffId, artikel.werkstoffName]);

    const werkstoffEignung = useMemo(() => werkstoffe.find(w => String(w.id) === werkstoffId), [werkstoffe, werkstoffId]);
    const einheitGeaendert = einheit !== einheitName(artikel.verrechnungseinheit);

    const gewichtPasst = (neueHerkunft: string, neueDichte: number | null) => {
        setHerkunft(neueHerkunft);
        setDichte(neueDichte);
        setServerVeraltet(false);
        setBasisNeu(true);
    };

    const waehleProfil = (bezeichnung: string) => {
        const p = tabelle.find(t => t.bezeichnung === bezeichnung);
        if (!p) return;
        setMasse(alt => ({ ...alt, hoehe: formatDecimalInput(p.hoeheMm), breite: formatDecimalInput(p.breiteMm) }));
        setKgJeMeter(formatDecimalInput(p.kgJeMeter));
        setM2JeMeter(formatDecimalInput(p.m2JeMeter));
        gewichtPasst("TABELLE", null);
        setRechenHinweis(`${p.bezeichnung}: Werte aus der Profiltabelle (gerundete Normwerte für Stahl). Bei Abweichung gilt das Datenblatt Ihres Lieferanten; Sie können sie hier überschreiben.`);
    };

    const berechne = async () => {
        setFehler("");
        const zahl = (t: string) => { const r = validateDecimalInput(t, { label: "Maß", required: false, min: 0, max: 100000 }); return r.valid ? r.value ?? null : null; };
        const form = art === "BLECH" && !BLECHFORMEN.includes(profilform) ? "BLECH" : profilform;
        setRechnet(true);
        try {
            const res = await fetch("/api/artikel/profile/rechnen", {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ profilform: form || null, durchmesser: zahl(masse.durchmesser), hoehe: zahl(masse.hoehe), breite: zahl(masse.breite), wandstaerke: zahl(masse.wandstaerke), werkstoffId: werkstoffId ? Number(werkstoffId) : null }),
            });
            const d = await res.json().catch(() => ({})) as { kgJeMeter?: number; m2JeMeter?: number; kgJeQm?: number; m2JeQm?: number; dichte?: number; hinweis?: string; message?: string };
            if (!res.ok) { setFehler(d.message ?? "Die Berechnung hat nicht geklappt."); return; }
            if (d.kgJeQm != null) {
                setKgJeQm(formatDecimalInput(d.kgJeQm));
                setFlaecheJeQm(formatDecimalInput(d.m2JeQm ?? 2));
            } else {
                setKgJeMeter(formatDecimalInput(d.kgJeMeter ?? 0));
                setM2JeMeter(formatDecimalInput(d.m2JeMeter ?? 0));
            }
            gewichtPasst("BERECHNET", d.dichte ?? null);
            setRechenHinweis(d.hinweis ?? "");
        } catch {
            setFehler("Die Berechnung hat nicht geklappt. Ist der Server erreichbar?");
        } finally {
            setRechnet(false);
        }
    };

    const gewichtVonHand = (setter: (t: string) => void) => (t: string) => {
        setter(t);
        gewichtPasst("HAND", null);
    };

    const speichern = async () => {
        setFehler("");
        if (!produktname.trim()) { setFehler("Bitte einen Produktnamen eingeben."); return; }
        const entwuerfe: Record<string, string> = {
            verpackung, laenge, ...masse,
            kg: art === "METER" ? kgJeMeter : "", m2: art === "METER" ? m2JeMeter : "",
            kgQm: art === "BLECH" ? kgJeQm : "", flQm: art === "BLECH" ? flaecheJeQm : "",
            kgStk: art === "STUECK" ? kgJeStueck : "", m2Stk: art === "STUECK" ? m2JeStueck : "",
        };
        const regeln: Record<string, { label: string; min?: number; max?: number; integer?: boolean; maxDecimalPlaces?: number }> = {
            verpackung: { label: "die Verpackungseinheit", min: 0, integer: true },
            laenge: { label: "die übliche Länge", min: 1, max: 30000, integer: true },
            kg: { label: "das Gewicht je Meter", min: 0.0001, max: 100000, maxDecimalPlaces: 4 },
            m2: { label: "die Außenfläche je Meter", min: 0.0001, max: 100000, maxDecimalPlaces: 4 },
            kgQm: { label: "das Gewicht je m²", min: 0.0001, max: 100000, maxDecimalPlaces: 4 },
            flQm: { label: "die Fläche je m² Blech", min: 0.0001, max: 10, maxDecimalPlaces: 4 },
            kgStk: { label: "das Gewicht je Stück", min: 0.0001, max: 100000, maxDecimalPlaces: 4 },
            m2Stk: { label: "die Außenfläche je Stück", min: 0.0001, max: 100000, maxDecimalPlaces: 4 },
        };
        for (const m of MASSE) regeln[m.schluessel] = { label: m.label.replace(" (mm)", ""), min: 0, max: 100000, maxDecimalPlaces: 2 };
        const geprueft = validateNumberDrafts(entwuerfe, regeln);
        if (!geprueft.valid) { setFehler(geprueft.message); return; }
        const v = geprueft.values;

        setSpeichert(true);
        try {
            const res = await fetch(`/api/artikel/${encodeURIComponent(String(artikel.id))}/stammdaten`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    version: artikel.version ?? null,
                    produktname: produktname.trim(),
                    produktlinie: produktlinie.trim() || null,
                    produkttext: produkttext.trim() || null,
                    artikelnummer: artikelnummer.trim() || null,
                    ean: ean.replace(/\s+/g, "") || null,
                    verrechnungseinheit: einheit || null,
                    verpackungseinheit: v.verpackung,
                    preiseinheit: preiseinheit.trim() || null,
                    kategorieId,
                    werkstoffId: werkstoffId ? Number(werkstoffId) : null,
                    profilform: profilform || null,
                    herstellverfahren: herstellverfahren || null,
                    fertigungszustand: fertigungszustand || null,
                    massnorm: massnorm.trim() || null,
                    werkstoffnorm: werkstoffnorm.trim() || null,
                    verzinkungsgeeignet: wertAus(verzinken),
                    pulverbeschichtungsgeeignet: wertAus(pulvern),
                    durchmesser: v.durchmesser, hoehe: v.hoehe, breite: v.breite,
                    wandstaerke: v.wandstaerke, stegdicke: v.stegdicke, flanschdicke: v.flanschdicke,
                    standardlaenge: v.laenge,
                    kgProMeter: art === "METER" ? v.kg ?? null : null,
                    mantelflaeche: art === "METER" ? v.m2 ?? null : null,
                    kgProQm: art === "BLECH" ? v.kgQm ?? null : null,
                    flaecheJeQm: art === "BLECH" ? v.flQm ?? null : null,
                    kgJeStueck: art === "STUECK" ? v.kgStk ?? null : null,
                    m2JeStueck: art === "STUECK" ? v.m2Stk ?? null : null,
                    formelFelder: hatFormel ? formel.felder : [],
                    formelGewicht: hatFormel ? formel.gewicht.trim() || null : null,
                    formelFlaeche: hatFormel ? formel.flaeche.trim() || null : null,
                    gewichtHerkunft: herkunft || null,
                }),
            });
            if (!res.ok) {
                let text = "Die Stammdaten konnten nicht gespeichert werden.";
                try {
                    const body = await res.json() as { message?: string };
                    if (body?.message) text = body.message;
                } catch { /* kein JSON: Standardtext */ }
                throw new Error(text);
            }
            toast.success("Die Stammdaten sind gespeichert.");
            onGespeichert();
        } catch (e) {
            const text = e instanceof Error ? e.message : "Die Stammdaten konnten nicht gespeichert werden.";
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    // unused variable suppression
    void dichte;

    return (
        <section className="bg-white border border-rose-200 rounded-lg p-5" aria-label="Stammdaten bearbeiten" data-testid="artikel-stammdaten-form">
            <div className="flex items-start justify-between gap-3 mb-4">
                <div>
                    <h2 className="text-lg font-semibold text-slate-900">Stammdaten bearbeiten</h2>
                    <p className="text-sm text-slate-500">Bestehende Angebote und Rechnungen ändern sich dadurch nicht, sie haben Text und Preis kopiert.</p>
                </div>
                <Button variant="ghost" size="sm" onClick={onAbbrechen} aria-label="Bearbeiten abbrechen" className="text-slate-500 hover:bg-slate-100">
                    <X className="w-4 h-4" aria-hidden="true" />
                </Button>
            </div>

            <div className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-name">Produktname *</Label>
                        <Input id="sd-name" value={produktname} maxLength={255} onChange={e => setProduktname(e.target.value)} placeholder="z. B. Quadratrohr 40x40x2" />
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-linie">Produktlinie</Label>
                        <Input id="sd-linie" value={produktlinie} maxLength={255} onChange={e => setProduktlinie(e.target.value)} placeholder="z. B. Stahlprofile" />
                    </div>
                </div>

                <div className="space-y-1.5">
                    <Label htmlFor="sd-text">Beschreibung (intern)</Label>
                    <Input id="sd-text" value={produkttext} maxLength={255} onChange={e => setProdukttext(e.target.value)} placeholder="Zusätzliche Infos …" />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-nummer">Artikelnummer</Label>
                        <Input id="sd-nummer" value={artikelnummer} maxLength={64} onChange={e => setArtikelnummer(e.target.value)} placeholder="eigene Nummer" />
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-ean">EAN / Strichcode</Label>
                        <Input id="sd-ean" value={ean} inputMode="numeric" maxLength={20} onChange={e => setEan(e.target.value)} placeholder="nur wenn vorhanden" className="font-mono" />
                    </div>
                    <div className="space-y-1.5">
                        <Label>Kategorie</Label>
                        <KategorieAuswahlFeld name={kategorieName} gewaehlt={kategorieId != null} onOeffnen={() => setZeigeKategorien(true)}
                            onEntfernen={() => { setKategorieId(null); setKategorieName(""); }} />
                        <p className="text-xs text-slate-500">Im Fenster lassen sich neue Kategorien anlegen („Kategorie hinzufügen").</p>
                    </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-4 gap-4 bg-slate-50 p-4 rounded-lg border border-slate-100">
                    <div className="space-y-1.5">
                        <div className="flex items-center gap-1">
                            <Label>Verrechnungseinheit</Label>
                            <InfoButton titel="Verrechnungseinheit">
                                <p className="font-medium text-slate-800">Was passiert im Hintergrund?</p>
                                <p>Mit dieser Einheit wird der Artikel eingekauft, verkauft und gerechnet. Das Programm rechnet bereits eingetragene Mengen <strong>nicht</strong> um.</p>
                                <p className="mt-2 font-medium text-slate-800">Was bewirkt das?</p>
                                <p>Neue Angebote und Rechnungen nutzen die neue Einheit. Bestehende Dokumente bleiben, wie sie sind.</p>
                            </InfoButton>
                        </div>
                        <Select options={VERRECHNUNGSEINHEITEN} value={einheit} onChange={setEinheit} />
                        <p className="text-xs text-slate-500">Die Einheit, in der eingekauft, verkauft und gerechnet wird.</p>
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-vpe">Verpackungseinheit (Menge)</Label>
                        <Input id="sd-vpe" value={verpackung} inputMode="numeric" onChange={e => setVerpackung(e.target.value)} />
                        <p className="text-xs text-slate-500">Wie viel in einer Verpackung steckt, z. B. 100 Stück je Karton.</p>
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-pe">Preiseinheit (Text)</Label>
                        <Input id="sd-pe" value={preiseinheit} maxLength={255} onChange={e => setPreiseinheit(e.target.value)} placeholder="z. B. 100 Stk" />
                        <p className="text-xs text-slate-500">Nur ein Anzeigetext. Gerechnet wird mit der Verrechnungseinheit.</p>
                    </div>
                    <div className="space-y-1.5">
                        <div className="flex items-center gap-1"><Label>Werkstoff</Label></div>
                        <Select options={werkstoffOptionen} value={werkstoffId} onChange={setWerkstoffId} />
                    </div>
                    {einheitGeaendert && (
                        <p role="note" className="md:col-span-4 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg p-3">
                            Die Einheit ändert sich. Vorhandene Mengen im Lager, in Leistungen und in Angeboten werden <strong>nicht umgerechnet</strong>, sie gelten dann in der neuen Einheit.
                        </p>
                    )}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 rounded-lg border border-slate-100 bg-slate-50 p-4">
                    <div className="space-y-1.5">
                        <div className="flex items-center gap-1">
                            <Label>Feuerverzinken</Label>
                            <InfoButton titel="Eignung zur Oberflächenbehandlung">
                                <p className="font-medium text-slate-800">Was passiert im Hintergrund?</p>
                                <p>Standardmäßig gilt, was beim Werkstoff steht. Hier lässt sich das für <strong>diesen einen Artikel</strong> überstimmen.</p>
                                <p className="mt-2 font-medium text-slate-800">Was bewirkt das?</p>
                                <p>Es legt nur fest, ob das Teil verzinkt oder pulverbeschichtet werden <strong>kann</strong>. Die Eignung erscheint auf der Artikelseite und in der Artikelsuche als Filter. Preise ändern sich dadurch nicht.</p>
                            </InfoButton>
                        </div>
                        <Select aria-label="Feuerverzinken" options={eignungsOptionen(werkstoffEignung?.verzinkungsgeeignet)} value={verzinken} onChange={v => setVerzinken(v as Eignungswahl)} />
                    </div>
                    <div className="space-y-1.5">
                        <div className="flex items-center gap-1"><Label>Pulverbeschichten</Label></div>
                        <Select aria-label="Pulverbeschichten" options={eignungsOptionen(werkstoffEignung?.pulverbeschichtungsgeeignet)} value={pulvern} onChange={v => setPulvern(v as Eignungswahl)} />
                    </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="space-y-1.5">
                        <Label>Form</Label>
                        <Select options={alsOptionen(optionen?.profilform)} value={profilform} onChange={setProfilform} />
                    </div>
                    <div className="space-y-1.5">
                        <Label>Herstellverfahren</Label>
                        <Select options={alsOptionen(optionen?.herstellverfahren)} value={herstellverfahren} onChange={setHerstellverfahren} />
                    </div>
                    <div className="space-y-1.5">
                        <Label>Fertigungszustand</Label>
                        <Select options={alsOptionen(optionen?.fertigungszustand)} value={fertigungszustand} onChange={setFertigungszustand} />
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-massnorm">Maßnorm</Label>
                        <Input id="sd-massnorm" value={massnorm} maxLength={64} onChange={e => setMassnorm(e.target.value)} placeholder="z. B. EN 10219-2" />
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-wnorm">Werkstoffnorm</Label>
                        <Input id="sd-wnorm" value={werkstoffnorm} maxLength={64} onChange={e => setWerkstoffnorm(e.target.value)} />
                    </div>
                </div>

                {hatMasse && (
                    <div>
                        <h3 className="text-sm font-semibold text-slate-800 mb-2">Maße</h3>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                            {MASSE.map(m => (
                                <div key={m.schluessel} className="space-y-1.5">
                                    <Label htmlFor={`sd-${m.schluessel}`}>{m.label}</Label>
                                    <Input id={`sd-${m.schluessel}`} value={masse[m.schluessel]} inputMode="decimal"
                                        onChange={e => setMasse(alt => ({ ...alt, [m.schluessel]: e.target.value }))} />
                                </div>
                            ))}
                            <div className="space-y-1.5">
                                <Label htmlFor="sd-laenge">Übliche Länge (mm)</Label>
                                <Input id="sd-laenge" value={laenge} inputMode="numeric" onChange={e => setLaenge(e.target.value)} placeholder="z. B. 6000" />
                            </div>
                        </div>
                    </div>
                )}

                <div className="space-y-4 rounded-lg border border-slate-200 bg-slate-50 p-4" data-testid="gewicht-oberflaeche">
                    <div>
                        <h3 className="text-sm font-semibold text-slate-800">Gewicht und Fläche</h3>
                        <p className="text-xs text-slate-600">
                            Die Leistung rechnet <strong>Verzinken immer nach Gewicht (kg)</strong> und <strong>Pulverbeschichten immer nach Außenfläche (m²)</strong>.
                            Beides muss hier eindeutig stehen, sonst meldet die Leistung „Gewicht fehlt".
                        </p>
                    </div>
                    <div className="max-w-xs space-y-1.5">
                        <Label>Gerechnet wird als</Label>
                        <Select aria-label="Gerechnet wird als" options={ART_OPTIONEN} value={art} onChange={v => setArt(v as Art)} />
                    </div>

                    {art === "METER" && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div className="space-y-1.5">
                                <Label htmlFor="sd-kg">Gewicht je Meter (kg/m)</Label>
                                <Input id="sd-kg" value={kgJeMeter} inputMode="decimal" onChange={e => gewichtVonHand(setKgJeMeter)(e.target.value)} />
                                <p className="text-xs text-slate-500">Für das Verzinken.</p>
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="sd-m2">Außenfläche je Meter (m²/m)</Label>
                                <Input id="sd-m2" value={m2JeMeter} inputMode="decimal" onChange={e => setM2JeMeter(e.target.value)} />
                                <p className="text-xs text-slate-500">Zum Pulverbeschichten. Gemeint ist die Außenfläche, bei Rohren ohne Innenseite.</p>
                            </div>
                        </div>
                    )}
                    {art === "BLECH" && (
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                            <div className="space-y-1.5">
                                <Label htmlFor="sd-dicke">Blechdicke (mm)</Label>
                                <Input id="sd-dicke" value={masse.wandstaerke} inputMode="decimal" onChange={e => setMasse(alt => ({ ...alt, wandstaerke: e.target.value }))} />
                                <p className="text-xs text-slate-500">Dieselbe Angabe wie die Wandstärke.</p>
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="sd-kgqm">Gewicht je m² (kg/m²)</Label>
                                <Input id="sd-kgqm" value={kgJeQm} inputMode="decimal" onChange={e => gewichtVonHand(setKgJeQm)(e.target.value)} />
                                <p className="text-xs text-slate-500">Für das Verzinken.</p>
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="sd-flqm">Fläche je m² Blech (m²/m²)</Label>
                                <Input id="sd-flqm" value={flaecheJeQm} inputMode="decimal" placeholder="2" onChange={e => setFlaecheJeQm(e.target.value)} />
                                <p className="text-xs text-slate-500">Zum Pulverbeschichten. 2 = beide Seiten; leer heißt auch 2.</p>
                            </div>
                        </div>
                    )}
                    {art === "STUECK" && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div className="space-y-1.5">
                                <Label htmlFor="sd-kgstk">Gewicht je Stück (kg)</Label>
                                <Input id="sd-kgstk" value={kgJeStueck} inputMode="decimal" onChange={e => gewichtVonHand(setKgJeStueck)(e.target.value)} />
                                <p className="text-xs text-slate-500">Für das Verzinken.</p>
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="sd-m2stk">Außenfläche je Stück (m²)</Label>
                                <Input id="sd-m2stk" value={m2JeStueck} inputMode="decimal" onChange={e => setM2JeStueck(e.target.value)} />
                                <p className="text-xs text-slate-500">Zum Pulverbeschichten.</p>
                            </div>
                        </div>
                    )}

                    {art === "METER" && hatTabelle && (
                        <div className="space-y-1.5 max-w-sm">
                            <Label>Aus der Profiltabelle wählen</Label>
                            <Select aria-label="Profil aus der Tabelle wählen" value="" onChange={waehleProfil} placeholder="z. B. IPE 120"
                                options={tabelle.map(t => ({ value: t.bezeichnung, label: `${t.bezeichnung} (${formatDecimalInput(t.kgJeMeter)} kg/m)` }))} />
                        </div>
                    )}
                    {((art === "METER" && !hatTabelle) || (art === "BLECH" && profilform !== "RIFFELBLECH" && profilform !== "LOCHBLECH")) && (
                        <Button type="button" variant="outline" size="sm" onClick={() => void berechne()} disabled={rechnet}>
                            {rechnet ? <Loader2 className="w-4 h-4 mr-2 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : null}
                            {art === "BLECH" ? "Aus Dicke und Dichte berechnen" : "Aus den Maßen berechnen"}
                        </Button>
                    )}
                    {art === "BLECH" && (profilform === "RIFFELBLECH" || profilform === "LOCHBLECH") && (
                        <p className="text-xs text-slate-600">Riffel- und Lochblech haben keine einfache Formel (Riffelung, Lochanteil): Bitte das Gewicht je m² aus dem Datenblatt eintragen.</p>
                    )}
                    <div className="rounded-md border border-slate-200 bg-white p-3 space-y-1.5" data-testid="zuschnitt-formel">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                            <div>
                                <p className="text-sm font-medium text-slate-800">Zuschnitt mit wechselnden Maßen</p>
                                <p className="text-xs text-slate-500">Für Teile, deren Länge, Breite oder Dicke sich je Auftrag ändert: Gewicht und Fläche rechnet das Programm aus den Maßen, die in der Leistung eingetragen werden.</p>
                            </div>
                            <Button type="button" variant="outline" size="sm" onClick={() => setFormelOffen(true)}>
                                {hatFormel ? "Formel bearbeiten" : "Formel anlegen"}
                            </Button>
                        </div>
                        {hatFormel && (
                            <p className="text-xs text-slate-600" data-testid="zuschnitt-formel-text">
                                Formel hinterlegt: {formel.felder.map(f => f.bezeichnung || f.name).join(", ") || "ohne Eingabefelder"}.
                                Sie gilt in Leistungen statt der festen Werte oben.
                            </p>
                        )}
                    </div>
                    {rechenHinweis && <p role="note" className="text-xs text-slate-600" data-testid="rechen-hinweis">{rechenHinweis}</p>}
                    {herkunft && HERKUNFT_TEXT[herkunft] && !veraltet && (
                        <p className="text-xs text-slate-500" data-testid="gewicht-herkunft">Das Gewicht stammt {HERKUNFT_TEXT[herkunft]}.</p>
                    )}
                    {veraltet && (
                        <p role="alert" className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg p-3" data-testid="gewicht-veraltet">
                            Form, Werkstoff oder Maße haben sich seit der Berechnung geändert. Das Gewicht stammt noch aus dem alten Stand: bitte neu berechnen oder prüfen.
                        </p>
                    )}
                    <p className="text-xs text-slate-500">Alles lässt sich von Hand ändern. Leer heißt: keine Angabe; die Leistung meldet dann, was fehlt.</p>
                </div>
            </div>

            {fehler && <div role="alert" className="mt-4 p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}

            <div className="mt-5 flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={onAbbrechen} disabled={speichert}>Abbrechen</Button>
                <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={speichern} disabled={speichert}>
                    {speichert ? <Loader2 className="w-4 h-4 mr-2 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Save className="w-4 h-4 mr-2" aria-hidden="true" />}
                    Speichern
                </Button>
            </div>

            <ArtikelFormelModal isOpen={formelOffen} onClose={() => setFormelOffen(false)} formel={formel} werkstoffId={werkstoffId ? Number(werkstoffId) : null}
                kgJeMeter={kgJeMeter} m2JeMeter={m2JeMeter} onUebernehmen={setFormel} />

            {zeigeKategorien && (
                <CategoryTreeModal
                    onSelect={(id, name) => { setKategorieId(Number(id)); setKategorieName(name); setZeigeKategorien(false); }}
                    onClose={() => setZeigeKategorien(false)}
                />
            )}
        </section>
    );
}
