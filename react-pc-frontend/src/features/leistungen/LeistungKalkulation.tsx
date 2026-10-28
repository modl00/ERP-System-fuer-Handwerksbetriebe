import { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, Calculator, Factory, Hammer, Layers, Loader2, Package, Plus, Save, Trash2, TrendingDown } from 'lucide-react';

import { ArtikelAuswahlDialog, type ArtikelAuswahl } from '../../components/artikel/ArtikelAuswahlDialog';
import { ArtikelKurzDialog } from '../../components/artikel/ArtikelKurzDialog';
import { formatCurrency } from '../../components/artikel/formatCurrency';
import { Button } from '../../components/ui/button';
import { DecimalInput } from '../../components/ui/decimal-input';
import { SearchableSelect } from '../../components/ui/searchable-select';
import { useToast } from '../../components/ui/toast';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { minutenText } from '../../components/minutenUtils';
import type { Arbeitsgang, ArbeitsgangFormelFeld } from '../../types';
import { FormelEingabe } from './FormelEingabe';
import { MassEingabe } from './MassEingabe';
import { einheitKurz, ladeFremdleistungen, vergleicheFremdleistung, type Fremdleistung } from '../fremdleistung/fremdleistungApi';
import {
    hinweisText, ladeKalkulation, ladeStaffel, speichereKalkulation, uebernehmePreis, vorschauKalkulation,
    type Kalkulation, type KalkulationEingabe, type StaffelZeile,
} from './kalkulationApi';

/** Eine Zeile beim Bearbeiten: die Menge bleibt als Text, damit Zwischenstände wie "1," möglich sind. */
interface Zeile {
    schluessel: string;
    typ: 'ARTIKEL' | 'ARBEIT' | 'BAUGRUPPE' | 'FREMDLEISTUNG';
    artikelId: number | null;
    arbeitsgangId: number | null;
    baugruppeId?: number | null;
    /** Nur bei Fremdleistung: das gewählte Angebot des Veredlers und das Gewicht je Einheit der Leistung als Text (nur für Transport je kg nötig) */
    fremdleistungId?: number | null;
    gewichtText?: string;
    /** Nur bei Fremdleistung: woher die Menge kommt (leer = von Hand) */
    mengeAus?: 'GEWICHT' | 'OBERFLAECHE' | null;
    bezeichnung: string;
    mengeText: string;
    notiz: string | null;
    /** Nur bei Arbeit mit Zeiten (statt Stunden): Rüstzeit und Bearbeitungszeit in Minuten als Text; beide leer-undefined = Stundenmodus */
    ruestText?: string;
    bearbeitungText?: string;
    /** Nur bei Arbeit mit Zeiten: wie oft der Vorgang je Einheit vorkommt (leer = 1) */
    vorgaengeText?: string;
    /** Nur bei Arbeit mit Formel (#216): Werte der Eingabefelder als Text, Schlüssel = Feldname in Kleinbuchstaben; die Zeiten rechnet der Server */
    variablenText?: Record<string, string>;
    /** Nur bei Material: Verschnitt in Prozent als Text (leer = keiner) */
    verschnittText?: string;
    /** Nur bei Material mit Zuschnitt-Formel (#228): die Eingabefelder und die eingetragenen Maße als Text (Schlüssel = Feldname in Kleinbuchstaben); Gewicht, Fläche und Preis rechnet der Server */
    massFelder?: ArbeitsgangFormelFeld[];
    massText?: Record<string, string>;
    /** Nur bei Arbeit: Maschinenzeit in Prozent der Arbeitszeit als Text (leer = 100) */
    maschinenText?: string;
}

/** Eine Leistung, die als Baugruppe (Baustein) gewählt werden kann. */
interface BaugruppeKurz {
    id: number;
    name: string;
    unit?: string;
}

interface Props {
    leistungId: number | string;
    /** Wird aufgerufen, wenn sich der gespeicherte Preis oder die Art der Preisermittlung geändert hat. */
    onPreisGeaendert: (preis: number, kalkuliert: boolean) => void;
}

const MENGE_REGEL = { label: 'Menge', required: true, min: 0.001, max: 1000000 } as const;
const ZUSCHLAG_REGEL = { label: 'Zuschlag', required: false, min: 0, max: 1000 } as const;
const LOS_REGEL = { label: 'Losgröße', required: false, min: 0.001, max: 1000000 } as const;
const MINUTEN_REGEL = { required: false, min: 0, max: 100000 } as const;
const VERSCHNITT_REGEL = { required: false, min: 0, max: 100 } as const;
const MASCHINE_REGEL = { required: false, min: 0, max: 1000 } as const;
const GEWICHT_REGEL = { required: false, min: 0, max: 1000000 } as const;
const VORGAENGE_REGEL = { required: false, min: 0.001, max: 1000000 } as const;

/** Rechnet die Zeile mit Rüstzeit und Bearbeitungszeit statt mit Stunden? */
const hatZeiten = (z: Zeile) => z.typ === 'ARBEIT' && z.ruestText !== undefined;
const hatFormel = (z: Zeile) => z.typ === 'ARBEIT' && z.variablenText !== undefined;
const gangHatFormel = (g: Arbeitsgang | undefined) => !!g && !!g.formelBearbeitung;

let zaehler = 0;
const neuerSchluessel = () => `z${++zaehler}`;

/** Prüft alle Eingaben und baut daraus die Daten für das Backend. Gibt eine Meldung zurück, wenn etwas nicht stimmt. */
function baueEingabe(zeilen: Zeile[], zuschlagText: string, losText: string, kalkuliert: boolean): { eingabe: KalkulationEingabe } | { fehler: string } {
    const zuschlag = validateDecimalInput(zuschlagText, ZUSCHLAG_REGEL);
    if (!zuschlag.valid) return { fehler: zuschlag.message };
    const los = validateDecimalInput(losText, LOS_REGEL);
    if (!los.valid) return { fehler: los.message };
    const positionen: KalkulationEingabe['positionen'] = [];
    for (const z of zeilen) {
        if (hatFormel(z)) {
            const variablen: Record<string, number> = {};
            for (const [name, text] of Object.entries(z.variablenText ?? {})) {
                const wert = validateDecimalInput(text, { label: `${name.toUpperCase()} bei "${z.bezeichnung}"`, required: false, min: -1000000000, max: 1000000000 });
                if (!wert.valid) return { fehler: wert.message };
                if (wert.value != null) variablen[name] = wert.value;
            }
            const vorgaenge = validateDecimalInput(z.vorgaengeText ?? '', { ...VORGAENGE_REGEL, label: `Anzahl der Vorgänge bei "${z.bezeichnung}"` });
            if (!vorgaenge.valid) return { fehler: vorgaenge.message };
            const maschine = validateDecimalInput(z.maschinenText ?? '', { ...MASCHINE_REGEL, label: `Maschinenanteil bei "${z.bezeichnung}"` });
            if (!maschine.valid) return { fehler: maschine.message };
            positionen.push({ typ: z.typ, artikelId: z.artikelId, arbeitsgangId: z.arbeitsgangId, menge: null, notiz: z.notiz, variablen,
                ruestzeitMinuten: 0, bearbeitungszeitMinuten: 0, anzahlVorgaenge: vorgaenge.value ?? 1, maschinenanteilProzent: maschine.value });
            continue;
        }
        if (hatZeiten(z)) {
            const ruest = validateDecimalInput(z.ruestText ?? '', { ...MINUTEN_REGEL, label: `Rüstzeit bei "${z.bezeichnung}"` });
            if (!ruest.valid) return { fehler: ruest.message };
            const bearbeitung = validateDecimalInput(z.bearbeitungText ?? '', { ...MINUTEN_REGEL, label: `Bearbeitungszeit bei "${z.bezeichnung}"` });
            if (!bearbeitung.valid) return { fehler: bearbeitung.message };
            if (!(ruest.value ?? 0) && !(bearbeitung.value ?? 0)) {
                return { fehler: `Bei "${z.bezeichnung}" bitte eine Rüstzeit oder eine Bearbeitungszeit größer als 0 angeben.` };
            }
            const vorgaenge = validateDecimalInput(z.vorgaengeText ?? '', { ...VORGAENGE_REGEL, label: `Anzahl der Vorgänge bei "${z.bezeichnung}"` });
            if (!vorgaenge.valid) return { fehler: vorgaenge.message };
            const maschine = validateDecimalInput(z.maschinenText ?? '', { ...MASCHINE_REGEL, label: `Maschinenanteil bei "${z.bezeichnung}"` });
            if (!maschine.valid) return { fehler: maschine.message };
            positionen.push({ typ: z.typ, artikelId: z.artikelId, arbeitsgangId: z.arbeitsgangId, menge: null, notiz: z.notiz,
                ruestzeitMinuten: ruest.value ?? 0, bearbeitungszeitMinuten: bearbeitung.value ?? 0, anzahlVorgaenge: vorgaenge.value ?? 1, maschinenanteilProzent: maschine.value });
            continue;
        }
        if (z.typ === 'FREMDLEISTUNG' && z.mengeAus) {
            const gewicht = validateDecimalInput(z.gewichtText ?? '', { ...GEWICHT_REGEL, label: `Gewicht bei "${z.bezeichnung}"` });
            if (!gewicht.valid) return { fehler: gewicht.message };
            positionen.push({ typ: z.typ, artikelId: null, arbeitsgangId: null, fremdleistungId: z.fremdleistungId ?? null, gewichtKgJeEinheit: gewicht.value, mengeAus: z.mengeAus, menge: null, notiz: z.notiz });
            continue;
        }
        const menge = validateDecimalInput(z.mengeText, { ...MENGE_REGEL, label: `Menge bei "${z.bezeichnung}"` });
        if (!menge.valid) return { fehler: menge.message };
        if (z.typ === 'FREMDLEISTUNG') {
            const gewicht = validateDecimalInput(z.gewichtText ?? '', { ...GEWICHT_REGEL, label: `Gewicht bei "${z.bezeichnung}"` });
            if (!gewicht.valid) return { fehler: gewicht.message };
            positionen.push({ typ: z.typ, artikelId: null, arbeitsgangId: null, fremdleistungId: z.fremdleistungId ?? null, gewichtKgJeEinheit: gewicht.value, menge: menge.value as number, notiz: z.notiz });
            continue;
        }
        let maschinenanteil: number | null | undefined;
        if (z.typ === 'ARBEIT') {
            const maschine = validateDecimalInput(z.maschinenText ?? '', { ...MASCHINE_REGEL, label: `Maschinenanteil bei "${z.bezeichnung}"` });
            if (!maschine.valid) return { fehler: maschine.message };
            maschinenanteil = maschine.value;
        }
        let verschnittProzent: number | null | undefined;
        let massVariablen: Record<string, number> | undefined;
        if (z.typ === 'ARTIKEL') {
            const verschnitt = validateDecimalInput(z.verschnittText ?? '', { ...VERSCHNITT_REGEL, label: `Verschnitt bei "${z.bezeichnung}"` });
            if (!verschnitt.valid) return { fehler: verschnitt.message };
            verschnittProzent = verschnitt.value;
            if (z.massFelder && z.massFelder.length > 0) {
                massVariablen = {};
                for (const [name, text] of Object.entries(z.massText ?? {})) {
                    const wert = validateDecimalInput(text, { label: `${name.toUpperCase()} bei "${z.bezeichnung}"`, required: false, min: 0, max: 1000000000 });
                    if (!wert.valid) return { fehler: wert.message };
                    if (wert.value != null) massVariablen[name] = wert.value;
                }
            }
        }
        positionen.push({ typ: z.typ, artikelId: z.artikelId, arbeitsgangId: z.arbeitsgangId, ...(z.typ === 'BAUGRUPPE' ? { baugruppeId: z.baugruppeId ?? null } : {}),
            ...(z.typ === 'ARTIKEL' ? { verschnittProzent: verschnittProzent ?? null } : {}),
            ...(massVariablen ? { variablen: massVariablen } : {}),
            ...(z.typ === 'ARBEIT' ? { maschinenanteilProzent: maschinenanteil ?? null } : {}),
            menge: menge.value as number, notiz: z.notiz });
    }
    return { eingabe: { kalkuliert, zuschlagProzent: zuschlag.value, losgroesse: los.value, positionen } };
}

export function LeistungKalkulation({ leistungId, onPreisGeaendert }: Props) {
    const toast = useToast();
    const [laedt, setLaedt] = useState(true);
    const [ladefehler, setLadefehler] = useState('');
    const [zeilen, setZeilen] = useState<Zeile[]>([]);
    const [zuschlagText, setZuschlagText] = useState('');
    const [losText, setLosText] = useState('1');
    const [kalkuliert, setKalkuliert] = useState(false);
    const [stand, setStand] = useState<Kalkulation | null>(null);
    const [geaendert, setGeaendert] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const [meldung, setMeldung] = useState('');
    const [artikelDialog, setArtikelDialog] = useState(false);
    // Klick auf ein Material öffnet seine Kurzansicht (Texte ändern, Link zur Artikelseite)
    const [artikelKurz, setArtikelKurz] = useState<number | null>(null);
    const [arbeitsgaenge, setArbeitsgaenge] = useState<Arbeitsgang[]>([]);
    const [baugruppen, setBaugruppen] = useState<BaugruppeKurz[]>([]);
    const [fremdleistungen, setFremdleistungen] = useState<Fremdleistung[]>([]);
    const anfrageNr = useRef(0);
    const [staffelText, setStaffelText] = useState('10, 25, 50');
    const [staffel, setStaffel] = useState<StaffelZeile[] | null>(null);
    const [staffelFehler, setStaffelFehler] = useState('');

    const uebernimm = useCallback((k: Kalkulation) => {
        setStand(k);
        setKalkuliert(k.kalkuliert);
        setZuschlagText(k.zuschlagProzent ? formatDecimalInput(k.zuschlagProzent) : '');
        setLosText(formatDecimalInput(k.losgroesse ?? 1));
        setZeilen(k.positionen.map(p => ({
            schluessel: neuerSchluessel(), typ: p.typ, artikelId: p.artikelId, arbeitsgangId: p.arbeitsgangId, baugruppeId: p.baugruppeId ?? null,
            bezeichnung: p.bezeichnung, mengeText: formatDecimalInput(p.menge), notiz: p.notiz,
            ...(p.typ === 'FREMDLEISTUNG' ? { fremdleistungId: p.fremdleistungId ?? null, gewichtText: p.gewichtKgJeEinheit ? formatDecimalInput(p.gewichtKgJeEinheit) : '', mengeAus: p.mengeAus === 'GEWICHT' || p.mengeAus === 'OBERFLAECHE' ? p.mengeAus : null } : {}),
            ...(p.typ === 'ARTIKEL' ? { verschnittText: p.verschnittProzent ? formatDecimalInput(p.verschnittProzent) : '' } : {}),
            ...(p.typ === 'ARTIKEL' && p.formelFelder && p.formelFelder.length > 0
                ? { massFelder: p.formelFelder, massText: Object.fromEntries(Object.entries(p.variablen ?? {}).map(([k, v]) => [k, formatDecimalInput(v)])) }
                : {}),
            // Der Anteil steht nur dann im Feld, wenn er von 100 abweicht: Leer heißt "die Maschine läuft die ganze Zeit"
            ...(p.typ === 'ARBEIT' ? { maschinenText: p.maschinenSatz != null && p.maschinenanteilProzent != null && p.maschinenanteilProzent !== 100 ? formatDecimalInput(p.maschinenanteilProzent) : '' } : {}),
            ...(p.typ === 'ARBEIT' && p.variablen ? { variablenText: Object.fromEntries(Object.entries(p.variablen).map(([k, v]) => [k, formatDecimalInput(v)])) } : {}),
            ...(p.typ === 'ARBEIT' && (p.ruestzeitMinuten != null || p.bearbeitungszeitMinuten != null)
                ? {
                    ruestText: p.ruestzeitMinuten ? formatDecimalInput(p.ruestzeitMinuten) : '', bearbeitungText: p.bearbeitungszeitMinuten ? formatDecimalInput(p.bearbeitungszeitMinuten) : '',
                    vorgaengeText: p.anzahlVorgaenge && p.anzahlVorgaenge !== 1 ? formatDecimalInput(p.anzahlVorgaenge) : '',
                }
                : {}),
        })));
        setGeaendert(false);
    }, []);

    // Beim Öffnen und beim Wechsel der Leistung die gespeicherte Kalkulation laden
    useEffect(() => {
        let abgebrochen = false;
        setLaedt(true);
        setLadefehler('');
        ladeKalkulation(leistungId)
            .then(k => { if (!abgebrochen) uebernimm(k); })
            .catch(e => { if (!abgebrochen) setLadefehler(e instanceof Error ? e.message : 'Die Kalkulation konnte nicht geladen werden.'); })
            .finally(() => { if (!abgebrochen) setLaedt(false); });
        return () => { abgebrochen = true; };
    }, [leistungId, uebernimm]);

    // Zahlen neu berechnen lassen, sobald sich etwas ändert (das Backend rechnet, die Oberfläche nicht)
    useEffect(() => {
        if (!geaendert) return;
        const geprueft = baueEingabe(zeilen, zuschlagText, losText, kalkuliert);
        if ('fehler' in geprueft) { setMeldung(geprueft.fehler); return; }
        setMeldung('');
        const nr = ++anfrageNr.current;
        const timer = setTimeout(() => {
            vorschauKalkulation(leistungId, geprueft.eingabe)
                .then(k => { if (nr === anfrageNr.current) setStand(k); })
                .catch(e => { if (nr === anfrageNr.current) setMeldung(e instanceof Error ? e.message : 'Die Kalkulation konnte nicht berechnet werden.'); });
        }, 350);
        return () => clearTimeout(timer);
    }, [zeilen, zuschlagText, losText, kalkuliert, geaendert, leistungId]);

    const aendern = (aktion: () => void) => { aktion(); setGeaendert(true); };

    const artikelHinzufuegen = (auswahl: ArtikelAuswahl[]) => {
        setArtikelDialog(false);
        if (auswahl.length === 0) return;
        aendern(() => setZeilen(alt => [...alt, ...auswahl.map(a => ({
            schluessel: neuerSchluessel(), typ: 'ARTIKEL' as const, artikelId: a.artikelId, arbeitsgangId: null,
            bezeichnung: a.titel, mengeText: formatDecimalInput(a.menge > 0 ? a.menge : 1), notiz: null,
            ...(a.formelFelder && a.formelFelder.length > 0 ? { massFelder: a.formelFelder, massText: {} } : {}),
        }))]));
    };

    // Die Arbeitsgänge (kurze Liste) gleich mitladen, damit die Auswahl sofort bereitsteht
    useEffect(() => {
        let abgebrochen = false;
        fetch('/api/arbeitsgaenge')
            .then(res => { if (!res.ok) throw new Error(); return res.json() as Promise<Arbeitsgang[]>; })
            .then(daten => { if (!abgebrochen) setArbeitsgaenge(Array.isArray(daten) ? daten : []); })
            .catch(() => { if (!abgebrochen) toast.error('Die Arbeitsgänge konnten nicht geladen werden.'); });
        return () => { abgebrochen = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Die Leistungen, die als Baugruppe nutzbar sind (ohne diese Leistung selbst); ohne die Liste fehlt nur die Auswahl
    useEffect(() => {
        let abgebrochen = false;
        fetch('/api/leistungen')
            .then(res => { if (!res.ok) throw new Error(); return res.json() as Promise<(BaugruppeKurz & { baugruppe?: boolean })[]>; })
            .then(daten => { if (!abgebrochen) setBaugruppen((Array.isArray(daten) ? daten : []).filter(l => l.baugruppe && String(l.id) !== String(leistungId))); })
            .catch(() => { /* ohne die Liste gibt es keine Baugruppen zur Auswahl */ });
        return () => { abgebrochen = true; };
    }, [leistungId]);

    // Die Fremdleistungen der Veredler (nur eingeschaltete); ohne die Liste fehlt nur die Auswahl
    useEffect(() => {
        let abgebrochen = false;
        ladeFremdleistungen()
            .then(d => { if (!abgebrochen) setFremdleistungen(d.filter(f => f.aktiv)); })
            .catch(() => { /* ohne die Liste gibt es keine Fremdleistungen zur Auswahl */ });
        return () => { abgebrochen = true; };
    }, []);

    const fremdleistungHinzufuegen = (wert: string) => {
        const f = fremdleistungen.find(x => String(x.id) === wert);
        if (!f) return;
        aendern(() => setZeilen(alt => [...alt, {
            schluessel: neuerSchluessel(), typ: 'FREMDLEISTUNG', artikelId: null, arbeitsgangId: null, fremdleistungId: f.id,
            bezeichnung: f.bezeichnung, mengeText: '1', notiz: null, gewichtText: '',
        }]));
    };

    /** Vergleicht dieselbe Fremdleistung bei allen Veredlern für die Auftragsmenge dieser Zeile und wählt den günstigsten. */
    const guenstigstenWaehlen = async (z: Zeile) => {
        const menge = validateDecimalInput(z.mengeText, { ...MENGE_REGEL, label: `Menge bei "${z.bezeichnung}"` });
        const los = validateDecimalInput(losText, LOS_REGEL);
        if (!menge.valid) { toast.error(menge.message); return; }
        if (!los.valid) { toast.error(los.message); return; }
        const gewicht = validateDecimalInput(z.gewichtText ?? '', { ...GEWICHT_REGEL, label: 'Gewicht' });
        const einheiten = los.value ?? 1;
        try {
            const v = await vergleicheFremdleistung(z.bezeichnung, (menge.value as number) * einheiten, gewicht.valid && gewicht.value ? gewicht.value * einheiten : null);
            if (v.length === 0) { toast.warning('Für diese Fremdleistung gibt es keinen eingeschalteten Veredler.'); return; }
            const erster = v[0];
            if (v.length > 1) toast.info(`Preisvergleich für ${(menge.value as number) * einheiten} ${einheitKurz(erster.einheit)}: ${v.map(x => `${x.lieferantName} ${formatCurrency(x.summe)}`).join(', ')}`);
            if (!erster.guenstigster) { toast.warning('Beim günstigsten Veredler fehlt das Gewicht für den Transport. Bitte das Gewicht angeben.'); return; }
            if (erster.fremdleistungId === z.fremdleistungId) { toast.success(`${erster.lieferantName} ist schon der günstigste.`); return; }
            aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, fremdleistungId: erster.fremdleistungId } : x)));
            toast.success(`Der günstigste Veredler ist ${erster.lieferantName} (${formatCurrency(erster.summe)}). Er ist eingetragen, bitte speichern.`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Vergleich konnte nicht berechnet werden.');
        }
    };

    const baugruppeHinzufuegen = (wert: string) => {
        const b = baugruppen.find(x => String(x.id) === wert);
        if (!b) return;
        aendern(() => setZeilen(alt => [...alt, {
            schluessel: neuerSchluessel(), typ: 'BAUGRUPPE', artikelId: null, arbeitsgangId: null, baugruppeId: b.id,
            bezeichnung: b.name, mengeText: '1', notiz: null,
        }]));
    };

    const arbeitHinzufuegen = (wert: string) => {
        const gang = arbeitsgaenge.find(a => String(a.id) === wert);
        if (!gang) return;
        // Hat der Arbeitsgang Normzeiten, startet die Zeile mit Rüstzeit und Bearbeitungszeit, sonst mit einer Stunde
        const mitZeiten = gang.ruestzeitMinuten != null || gang.bearbeitungszeitMinuten != null;
        // Hat der Arbeitsgang eine Formel, startet die Zeile mit der Eingabemaske (Standardwerte); die Zeiten rechnet der Server
        const formel = gangHatFormel(gang);
        aendern(() => setZeilen(alt => [...alt, {
            schluessel: neuerSchluessel(), typ: 'ARBEIT', artikelId: null, arbeitsgangId: gang.id,
            bezeichnung: gang.beschreibung, mengeText: '1', notiz: null,
            ...(formel ? { ruestText: '', bearbeitungText: '', vorgaengeText: '', variablenText: {} } : {}),
            ...(!formel && mitZeiten ? { ruestText: minutenText(gang.ruestzeitMinuten), bearbeitungText: minutenText(gang.bearbeitungszeitMinuten), vorgaengeText: '' } : {}),
        }]));
    };

    /** Eine Arbeits-Zeile mit Zeiten auf die Formel des Arbeitsgangs umstellen (Standardwerte, die Zeiten rechnet der Server) oder zurück auf Handzeiten. */
    const formelUmschalten = (z: Zeile, berechnet: Kalkulation['positionen'][number] | undefined) => {
        aendern(() => setZeilen(alt => alt.map(x => {
            if (x.schluessel !== z.schluessel) return x;
            if (hatFormel(x)) {
                const { variablenText: _v, ...ohne } = x;
                void _v;
                return { ...ohne, ruestText: berechnet?.ruestzeitMinuten ? formatDecimalInput(berechnet.ruestzeitMinuten) : '', bearbeitungText: berechnet?.bearbeitungszeitMinuten ? formatDecimalInput(berechnet.bearbeitungszeitMinuten) : '' };
            }
            return { ...x, ruestText: x.ruestText ?? '', bearbeitungText: x.bearbeitungText ?? '', vorgaengeText: x.vorgaengeText ?? '', variablenText: {} };
        })));
    };

    /** Eine Arbeits-Zeile zwischen "Stunden" und "Rüstzeit und Bearbeitungszeit" umschalten. */
    const zeitenUmschalten = (z: Zeile, berechnetStunden: number | undefined) => {
        aendern(() => setZeilen(alt => alt.map(x => {
            if (x.schluessel !== z.schluessel) return x;
            if (hatZeiten(x)) {
                const { ruestText: _r, bearbeitungText: _b, vorgaengeText: _v, ...ohne } = x;
                void _r; void _b; void _v;
                return { ...ohne, mengeText: formatDecimalInput(berechnetStunden && berechnetStunden > 0 ? berechnetStunden : 1) };
            }
            const gang = arbeitsgaenge.find(a => a.id === x.arbeitsgangId);
            return { ...x, ruestText: minutenText(gang?.ruestzeitMinuten), bearbeitungText: minutenText(gang?.bearbeitungszeitMinuten), vorgaengeText: '' };
        })));
    };

    const speichern = async () => {
        const geprueft = baueEingabe(zeilen, zuschlagText, losText, kalkuliert);
        if ('fehler' in geprueft) { toast.error(geprueft.fehler); return; }
        setSpeichert(true);
        try {
            const k = await speichereKalkulation(leistungId, geprueft.eingabe);
            uebernimm(k);
            setMeldung('');
            onPreisGeaendert(k.preisGespeichert ?? 0, k.kalkuliert);
            toast.success(k.kalkuliert ? 'Kalkulation gespeichert, der Preis wurde gesetzt.' : 'Kalkulation gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Kalkulation konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const zeigeStaffel = async () => {
        const mengen = staffelText.split(/[;\s]+|,\s+/).map(t => t.trim()).filter(Boolean).map(t => validateDecimalInput(t, { label: 'Menge', required: true, min: 0.001, max: 1000000 }));
        const falsch = mengen.find(m => !m.valid);
        if (falsch && !falsch.valid) { setStaffelFehler(falsch.message); return; }
        if (mengen.length === 0) { setStaffelFehler('Bitte mindestens eine Menge angeben, zum Beispiel 10, 25, 50.'); return; }
        try {
            setStaffel(await ladeStaffel(leistungId, mengen.map(m => (m.valid ? m.value : null) as number)));
            setStaffelFehler('');
        } catch (e) {
            setStaffelFehler(e instanceof Error ? e.message : 'Die Staffel konnte nicht berechnet werden.');
        }
    };

    const preisUebernehmen = async () => {
        setSpeichert(true);
        try {
            const k = await uebernehmePreis(leistungId);
            uebernimm(k);
            onPreisGeaendert(k.preisGespeichert ?? 0, k.kalkuliert);
            toast.success('Der Preis wurde neu berechnet und übernommen.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Preis konnte nicht übernommen werden.');
        } finally {
            setSpeichert(false);
        }
    };

    if (laedt) {
        return <div className="flex items-center gap-2 text-sm text-slate-500 py-4"><Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> Kalkulation wird geladen…</div>;
    }
    if (ladefehler) {
        return <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{ladefehler}</div>;
    }

    const anzeige = stand;
    const preisFehlt = anzeige != null && !anzeige.vollstaendig;
    const zeigeVeraltet = !geaendert && anzeige?.preisVeraltet && zeilen.length > 0;

    return (
        <section data-testid="leistung-kalkulation" className="rounded-xl border border-slate-200 bg-slate-50/50 p-4 space-y-4" aria-labelledby="kalkulation-titel">
            <div className="flex items-start justify-between gap-3">
                <div>
                    <p className="text-xs uppercase tracking-wide text-slate-500">Preis aus Material und Arbeit</p>
                    <h4 id="kalkulation-titel" className="text-base font-semibold text-slate-900 flex items-center gap-2"><Calculator className="w-4 h-4 text-rose-600" /> Kalkulation</h4>
                </div>
                <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer select-none">
                    <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={kalkuliert}
                        onChange={e => aendern(() => setKalkuliert(e.target.checked))} />
                    Preis aus der Kalkulation berechnen
                </label>
            </div>

            <p className="text-xs text-slate-500">
                Alle Angaben gelten für <strong>eine</strong> Einheit der Leistung. Der Preis der Leistung ändert sich nie von selbst: Wird ein Artikel teurer,
                zeigen wir das hier an, und Sie entscheiden, ob Sie den neuen Preis übernehmen. Bereits geschriebene Angebote und Rechnungen bleiben unberührt.
            </p>

            {zeilen.length === 0 ? (
                <div className="rounded-lg border border-dashed border-slate-300 bg-white p-4 text-sm text-slate-500">
                    Noch keine Zeilen. Fügen Sie das Material (Artikel) und die Arbeitszeit hinzu, die für eine Einheit dieser Leistung nötig sind.
                </div>
            ) : (
                <div className="overflow-x-auto -m-1 p-1">
                    <table className="w-full text-sm bg-white rounded-lg border border-slate-200">
                        <thead>
                            <tr className="text-left text-xs uppercase tracking-wide text-slate-500 border-b border-slate-200">
                                <th scope="col" className="px-3 py-2 font-medium">Position</th>
                                <th scope="col" className="px-3 py-2 font-medium w-32">Menge</th>
                                <th scope="col" className="px-3 py-2 font-medium text-right">Einzelpreis</th>
                                <th scope="col" className="px-3 py-2 font-medium text-right">Summe</th>
                                <th scope="col" className="px-3 py-2 w-10"><span className="sr-only">Entfernen</span></th>
                            </tr>
                        </thead>
                        <tbody>
                            {zeilen.map((z, i) => {
                                const berechnet = anzeige?.positionen[i];
                                const hinweis = berechnet && berechnet.hinweis !== 'OK' ? hinweisText(berechnet.hinweis) : '';
                                return (
                                    <tr key={z.schluessel} className="border-b border-slate-100 last:border-0 align-top">
                                        <td className="px-3 py-2">
                                            <div className="flex items-start gap-2">
                                                {z.typ === 'ARTIKEL'
                                                    ? <Package className="w-4 h-4 mt-0.5 text-slate-400 shrink-0" aria-label="Material" />
                                                    : z.typ === 'BAUGRUPPE'
                                                        ? <Layers className="w-4 h-4 mt-0.5 text-slate-400 shrink-0" aria-label="Baugruppe" />
                                                        : z.typ === 'FREMDLEISTUNG'
                                                            ? <Factory className="w-4 h-4 mt-0.5 text-slate-400 shrink-0" aria-label="Fremdleistung" />
                                                            : <Hammer className="w-4 h-4 mt-0.5 text-slate-400 shrink-0" aria-label="Arbeit" />}
                                                <div>
                                                    {z.typ === 'ARTIKEL' && z.artikelId != null ? (
                                                        <button type="button" className="text-left text-slate-900 hover:text-rose-700 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 rounded"
                                                            title="Artikel ansehen und Beschreibung ändern" onClick={() => setArtikelKurz(z.artikelId)}>{z.bezeichnung}</button>
                                                    ) : (
                                                        <div className="text-slate-900">{z.bezeichnung}</div>
                                                    )}
                                                    {z.typ === 'FREMDLEISTUNG' && berechnet?.veredler && (
                                                        <div className="mt-0.5" data-testid={`fremd-${i}`}>
                                                            <div className="text-xs text-slate-500">bei {berechnet.veredler}</div>
                                                            <ul className="mt-1 space-y-0.5 text-xs text-slate-600" aria-label={`Kosten ${z.bezeichnung}`}>
                                                                {(berechnet.kostenarten ?? []).map(k => (
                                                                    <li key={`${k.art}-${k.text}`} className="flex justify-between gap-4"><span>{k.text}</span><span className="tabular-nums">{formatCurrency(k.betrag)}</span></li>
                                                                ))}
                                                            </ul>
                                                            <button type="button" className="mt-1 inline-flex items-center gap-1 text-xs text-rose-700 hover:underline cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 rounded"
                                                                onClick={() => void guenstigstenWaehlen(z)}><TrendingDown className="w-3 h-3" aria-hidden="true" /> Günstigsten Veredler wählen</button>
                                                        </div>
                                                    )}
                                                    {hinweis && (
                                                        <div className={`text-xs mt-0.5 ${berechnet?.einzelpreis == null ? 'text-rose-700' : 'text-amber-700'}`}>{hinweis}</div>
                                                    )}
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-3 py-2">
                                            {hatFormel(z) ? (
                                                <div className="space-y-1.5 min-w-[13rem]">
                                                    <FormelEingabe arbeitsgang={z.bezeichnung} felder={arbeitsgaenge.find(a => a.id === z.arbeitsgangId)?.formelFelder ?? []} werte={z.variablenText ?? {}}
                                                        bearbeitungMinuten={berechnet?.bearbeitungszeitMinuten} ruestMinuten={berechnet?.ruestzeitMinuten}
                                                        onChange={(name, text) => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, variablenText: { ...(x.variablenText ?? {}), [name]: text } } : x)))} />
                                                    <div className="flex items-center gap-1.5">
                                                        <DecimalInput aria-label={`Anzahl der Vorgänge ${z.bezeichnung}`} value={z.vorgaengeText ?? ''} min={0.001} max={1000000} placeholder="1"
                                                            onChange={wert => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, vorgaengeText: wert } : x)))} />
                                                        <span className="text-xs text-slate-500 whitespace-nowrap">Vorgänge je Einheit</span>
                                                    </div>
                                                    {berechnet && <div className="text-xs text-slate-500" data-testid={`stunden-${i}`}>= {berechnet.menge.toLocaleString('de-DE', { maximumFractionDigits: 3 })} Std. je Einheit</div>}
                                                    <button type="button" className="text-xs text-rose-700 hover:underline cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 rounded"
                                                        onClick={() => formelUmschalten(z, berechnet)}>Zeiten lieber von Hand eintragen</button>
                                                </div>
                                            ) : hatZeiten(z) ? (
                                                <div className="space-y-1.5 min-w-[11rem]">
                                                    <div className="flex items-center gap-1.5">
                                                        <DecimalInput aria-label={`Rüstzeit ${z.bezeichnung}`} value={z.ruestText ?? ''} min={0} max={100000} placeholder="0"
                                                            onChange={wert => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, ruestText: wert } : x)))} />
                                                        <span className="text-xs text-slate-500 whitespace-nowrap">min rüsten</span>
                                                    </div>
                                                    <div className="flex items-center gap-1.5">
                                                        <DecimalInput aria-label={`Bearbeitungszeit ${z.bezeichnung}`} value={z.bearbeitungText ?? ''} min={0} max={100000} placeholder="0"
                                                            onChange={wert => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, bearbeitungText: wert } : x)))} />
                                                        <span className="text-xs text-slate-500 whitespace-nowrap">{(z.vorgaengeText ?? '').trim() === '' || (z.vorgaengeText ?? '').trim() === '1' ? 'min je Einheit' : 'min je Vorgang'}</span>
                                                    </div>
                                                    <div className="flex items-center gap-1.5">
                                                        <DecimalInput aria-label={`Anzahl der Vorgänge ${z.bezeichnung}`} value={z.vorgaengeText ?? ''} min={0.001} max={1000000} placeholder="1"
                                                            onChange={wert => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, vorgaengeText: wert } : x)))} />
                                                        <span className="text-xs text-slate-500 whitespace-nowrap">Vorgänge je Einheit</span>
                                                    </div>
                                                    {berechnet && <div className="text-xs text-slate-500" data-testid={`stunden-${i}`}>= {berechnet.menge.toLocaleString('de-DE', { maximumFractionDigits: 3 })} Std. je Einheit</div>}
                                                    {gangHatFormel(arbeitsgaenge.find(a => a.id === z.arbeitsgangId)) && (
                                                        <button type="button" className="block text-xs text-rose-700 hover:underline cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 rounded"
                                                            onClick={() => formelUmschalten(z, berechnet)}>Mit der Formel des Arbeitsgangs berechnen</button>
                                                    )}
                                                    <button type="button" className="text-xs text-rose-700 hover:underline cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 rounded"
                                                        onClick={() => zeitenUmschalten(z, berechnet?.menge)}>Lieber in Stunden angeben</button>
                                                </div>
                                            ) : (
                                                <div>
                                                    {z.typ === 'FREMDLEISTUNG' && (
                                                        <div className="mb-1.5">
                                                            <label className="sr-only" htmlFor={`menge-aus-${z.schluessel}`}>Woher die Menge kommt, {z.bezeichnung}</label>
                                                            <select id={`menge-aus-${z.schluessel}`} className="h-9 w-full rounded-md border border-slate-300 bg-white px-2 text-xs" value={z.mengeAus ?? 'HAND'}
                                                                onChange={e => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, mengeAus: e.target.value === 'HAND' ? null : e.target.value as 'GEWICHT' | 'OBERFLAECHE' } : x)))}>
                                                                <option value="HAND">Menge von Hand</option>
                                                                {(fremdleistungen.find(f => f.id === z.fremdleistungId)?.einheit ?? 'KG') === 'KG' && <option value="GEWICHT">Menge aus dem Gewicht</option>}
                                                                {(fremdleistungen.find(f => f.id === z.fremdleistungId)?.einheit ?? 'QUADRATMETER') === 'QUADRATMETER' && <option value="OBERFLAECHE">Menge aus der Oberfläche</option>}
                                                            </select>
                                                        </div>
                                                    )}
                                                    {z.typ === 'FREMDLEISTUNG' && z.mengeAus ? (
                                                        <div className="text-xs text-slate-600" data-testid={`menge-berechnet-${i}`}>= {berechnet ? berechnet.menge.toLocaleString('de-DE', { maximumFractionDigits: 3 }) : '…'} {berechnet?.einheit ?? ''} {z.mengeAus === 'GEWICHT' ? 'Gewicht je Einheit (berechnet)' : 'Oberfläche je Einheit (berechnet)'}</div>
                                                    ) : (
                                                    <div className="flex items-center gap-1.5">
                                                        <DecimalInput aria-label={`Menge ${z.bezeichnung}`} value={z.mengeText} min={0.001} max={1000000}
                                                            onChange={wert => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, mengeText: wert } : x)))} />
                                                        <span className="text-xs text-slate-500 whitespace-nowrap">{berechnet?.einheit ?? (z.typ === 'ARBEIT' ? 'Std.' : '')}</span>
                                                    </div>
                                                    )}
                                                    {z.typ === 'ARBEIT' && (
                                                        <button type="button" className="mt-1 text-xs text-rose-700 hover:underline cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 rounded"
                                                            onClick={() => zeitenUmschalten(z, undefined)}>Mit Rüstzeit und Bearbeitungszeit rechnen</button>
                                                    )}
                                                    {z.typ === 'ARBEIT' && (berechnet?.maschinenSatz != null || berechnet?.hinweis === 'KEIN_MASCHINENSATZ') && (
                                                        <div className="mt-1.5" data-testid={`maschine-${i}`}>
                                                            <div className="flex items-center gap-1.5">
                                                                <DecimalInput aria-label={`Maschinenanteil ${z.bezeichnung}`} value={z.maschinenText ?? ''} min={0} max={1000} placeholder="100"
                                                                    onChange={wert => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, maschinenText: wert } : x)))} />
                                                                <span className="text-xs text-slate-500 whitespace-nowrap">% Maschinenzeit</span>
                                                            </div>
                                                            {berechnet?.maschinenSumme != null && (
                                                                <div className="mt-0.5 text-xs text-slate-500">Maschine: {formatCurrency(berechnet.maschinenSumme)} ({formatCurrency(berechnet.maschinenSatz ?? 0)} je Std.)</div>
                                                            )}
                                                        </div>
                                                    )}
                                                    {z.typ === 'FREMDLEISTUNG' && (berechnet?.hinweis === 'KEIN_GEWICHT' || (z.gewichtText ?? '') !== '') && (
                                                        <div className="mt-1.5">
                                                            <div className="flex items-center gap-1.5">
                                                                <DecimalInput aria-label={`Gewicht ${z.bezeichnung}`} value={z.gewichtText ?? ''} min={0} max={1000000} placeholder="0"
                                                                    onChange={wert => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, gewichtText: wert } : x)))} />
                                                                <span className="text-xs text-slate-500 whitespace-nowrap">kg je Einheit der Leistung</span>
                                                            </div>
                                                        </div>
                                                    )}
                                                    {z.typ === 'ARTIKEL' && z.massFelder && z.massFelder.length > 0 && (
                                                        <div className="mt-1.5">
                                                            <MassEingabe material={z.bezeichnung} felder={z.massFelder} werte={z.massText ?? {}}
                                                                onChange={(name, text) => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, massText: { ...(x.massText ?? {}), [name]: text } } : x)))} />
                                                        </div>
                                                    )}
                                                    {z.typ === 'ARTIKEL' && (
                                                        <div className="mt-1.5">
                                                            <div className="flex items-center gap-1.5">
                                                                <DecimalInput aria-label={`Verschnitt ${z.bezeichnung}`} value={z.verschnittText ?? ''} min={0} max={100} placeholder="0"
                                                                    onChange={wert => aendern(() => setZeilen(alt => alt.map(x => x.schluessel === z.schluessel ? { ...x, verschnittText: wert } : x)))} />
                                                                <span className="text-xs text-slate-500 whitespace-nowrap">% Verschnitt</span>
                                                            </div>
                                                            {berechnet?.gewichtKg != null && (
                                                                <div className="mt-0.5 text-xs text-slate-500" data-testid={`gewicht-${i}`}>
                                                                    {berechnet.gewichtKg.toLocaleString('de-DE', { maximumFractionDigits: 3 })} kg{berechnet.oberflaecheM2 ? `, ${berechnet.oberflaecheM2.toLocaleString('de-DE', { maximumFractionDigits: 3 })} m²` : ''} (ohne Verschnitt)
                                                                </div>
                                                            )}
                                                            {berechnet?.mengeMitVerschnitt != null && (
                                                                <div className="mt-0.5 text-xs text-slate-500" data-testid={`verschnitt-${i}`}>
                                                                    = {berechnet.mengeMitVerschnitt.toLocaleString('de-DE', { maximumFractionDigits: 3 })} {berechnet.einheit} mit Verschnitt
                                                                </div>
                                                            )}
                                                        </div>
                                                    )}
                                                </div>
                                            )}
                                        </td>
                                        <td className="px-3 py-2 text-right tabular-nums text-slate-700">{berechnet?.einzelpreis != null ? formatCurrency(berechnet.einzelpreis) : '–'}</td>
                                        <td className="px-3 py-2 text-right tabular-nums font-medium text-slate-900">{berechnet ? formatCurrency(berechnet.summe) : '–'}</td>
                                        <td className="px-3 py-2 text-right">
                                            <button type="button" aria-label={`Zeile ${z.bezeichnung} entfernen`} title="Zeile entfernen"
                                                className="p-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300"
                                                onClick={() => aendern(() => setZeilen(alt => alt.filter(x => x.schluessel !== z.schluessel)))}>
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}

            <div className="flex flex-wrap items-center gap-2">
                <Button type="button" variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => setArtikelDialog(true)}>
                    <Plus className="w-4 h-4" /> Material hinzufügen
                </Button>
                <div className="w-72" aria-label="Baugruppe hinzufügen">
                    <SearchableSelect value="" onChange={baugruppeHinzufuegen} placeholder="+ Baugruppe hinzufügen" searchPlaceholder="Baugruppe suchen…"
                        options={baugruppen.map(b => ({ value: String(b.id), label: b.name, sublabel: 'Baugruppe' }))} />
                </div>
                <div className="w-72" aria-label="Fremdleistung hinzufügen">
                    <SearchableSelect value="" onChange={fremdleistungHinzufuegen} placeholder="+ Fremdleistung hinzufügen" searchPlaceholder="Fremdleistung suchen…"
                        options={fremdleistungen.map(f => ({ value: String(f.id), label: f.bezeichnung, sublabel: `${f.lieferantName} · ${f.preisJeEinheit != null ? `${formatCurrency(f.preisJeEinheit)} je ${einheitKurz(f.einheit)}` : 'nach Aufstellung'}` }))} />
                </div>
                <div className="w-72" aria-label="Arbeitszeit hinzufügen">
                    <SearchableSelect value="" onChange={arbeitHinzufuegen} placeholder="+ Arbeitszeit hinzufügen" searchPlaceholder="Arbeitsgang suchen…"
                        options={arbeitsgaenge.filter(a => !a.archiviert).map(a => ({
                            value: String(a.id),
                            label: a.beschreibung,
                            sublabel: a.stundensatz != null ? `${formatCurrency(a.stundensatz)} je Stunde` : 'kein Kalkulationssatz',
                        }))} />
                </div>
            </div>

            {baugruppen.length === 0 && (
                <p className="text-xs text-slate-500">
                    Tipp: Teile, die in mehreren Leistungen vorkommen (zum Beispiel ein Pfosten), legen Sie als eigene Leistung an und schalten dort „Als Baugruppe nutzbar" ein.
                    Dann lassen sie sich hier mit einer Menge je Einheit einsetzen.
                </p>
            )}

            <div className="grid grid-cols-1 md:grid-cols-[180px_1fr] gap-4 items-start">
                <div className="space-y-4">
                    <DecimalInput label="Zuschlag (%)" value={zuschlagText} min={0} max={1000} placeholder="0"
                        onChange={wert => aendern(() => setZuschlagText(wert))} />
                    {zeilen.some(z => hatZeiten(z) || z.typ === 'FREMDLEISTUNG') && (
                        <div>
                            <DecimalInput label="Losgröße (Einheiten)" value={losText} min={0.001} max={1000000} placeholder="1"
                                onChange={wert => aendern(() => setLosText(wert))} />
                            <p className="mt-1 text-xs text-slate-500">Die Rüstzeit und der Mindestpreis und die einmaligen Kosten der Veredler verteilen sich auf so viele Einheiten der Leistung.</p>
                        </div>
                    )}
                </div>
                {anzeige && zeilen.length > 0 && (
                    <dl className="text-sm bg-white rounded-lg border border-slate-200 p-3 grid grid-cols-[1fr_auto] gap-x-6 gap-y-1" aria-label="Zusammenfassung der Kalkulation">
                        <dt className="text-slate-600">Material</dt><dd className="text-right tabular-nums">{formatCurrency(anzeige.materialSumme)}</dd>
                        <dt className="text-slate-600">Arbeit (Lohn)</dt><dd className="text-right tabular-nums">{formatCurrency(anzeige.arbeitSumme)}</dd>
                        {(anzeige.maschineSumme ?? 0) > 0 && (<><dt className="text-slate-600">Maschine</dt><dd className="text-right tabular-nums" data-testid="kalkulation-maschine">{formatCurrency(anzeige.maschineSumme ?? 0)}</dd></>)}
                        {(anzeige.gewichtKg ?? 0) > 0 && (<><dt className="text-slate-600">Gewicht je Einheit</dt><dd className="text-right tabular-nums" data-testid="kalkulation-gewicht">{(anzeige.gewichtKg ?? 0).toLocaleString('de-DE', { maximumFractionDigits: 3 })} kg{(anzeige.oberflaecheM2 ?? 0) > 0 ? `, ${(anzeige.oberflaecheM2 ?? 0).toLocaleString('de-DE', { maximumFractionDigits: 3 })} m²` : ''}{anzeige.gewichtVollstaendig === false ? ' (unvollständig)' : ''}</dd></>)}
                        {(anzeige.fremdleistungSumme ?? 0) > 0 && (<><dt className="text-slate-600">Fremdleistung</dt><dd className="text-right tabular-nums" data-testid="kalkulation-fremd">{formatCurrency(anzeige.fremdleistungSumme ?? 0)}</dd></>)}
                        {anzeige.zuschlag !== 0 && (<><dt className="text-slate-600">Zuschlag</dt><dd className="text-right tabular-nums">{formatCurrency(anzeige.zuschlag)}</dd></>)}
                        <dt className="font-semibold text-slate-900 border-t border-slate-200 pt-1 mt-1">Preis je Einheit</dt>
                        <dd className="font-semibold text-slate-900 text-right tabular-nums border-t border-slate-200 pt-1 mt-1" data-testid="kalkulation-preis">{formatCurrency(anzeige.preisBerechnet)}</dd>
                    </dl>
                )}
            </div>

            {preisFehlt && (
                <div role="alert" className="flex items-start gap-2 p-3 rounded-lg bg-amber-50 border border-amber-200 text-sm text-amber-900">
                    <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
                    <span>Für mindestens eine Zeile fehlt ein Preis. Der berechnete Preis wäre zu niedrig. Bitte zuerst den Preis bzw. Kalkulationssatz pflegen, bevor Sie den Preis berechnen lassen.</span>
                </div>
            )}
            {meldung && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{meldung}</div>}

            {zeigeVeraltet && anzeige && (
                <div role="status" data-testid="kalkulation-veraltet" className="flex flex-wrap items-center justify-between gap-3 p-3 rounded-lg bg-amber-50 border border-amber-200 text-sm text-amber-900">
                    <span>
                        Der gespeicherte Preis ({formatCurrency(anzeige.preisGespeichert ?? 0)}) weicht vom berechneten ({formatCurrency(anzeige.preisBerechnet)}) ab,
                        zum Beispiel weil sich ein Artikelpreis geändert hat.
                    </span>
                    <Button type="button" size="sm" variant="outline" className="border-amber-400 text-amber-900 hover:bg-amber-100" onClick={preisUebernehmen} disabled={speichert || preisFehlt}
                        title={preisFehlt ? 'Erst fehlende Preise pflegen' : undefined}>
                        Preis übernehmen
                    </Button>
                </div>
            )}

            <div className="flex items-center gap-3">
                <Button type="button" size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={speichern} disabled={speichert || !geaendert}
                    title={!geaendert ? 'Keine Änderung zu speichern' : undefined}>
                    {speichert ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Save className="w-4 h-4" />} Kalkulation speichern
                </Button>
                {geaendert && <span className="text-xs text-slate-500">Nicht gespeicherte Änderungen. Das Speichern setzt den Preis der Leistung{kalkuliert ? '' : ' nur, wenn „Preis aus der Kalkulation berechnen" gewählt ist'}.</span>}
            </div>

            {zeilen.length > 0 && !geaendert && (
                <div className="rounded-lg border border-slate-200 bg-white p-3 space-y-2" data-testid="staffel">
                    <p className="text-sm font-medium text-slate-800">Preis nach Menge</p>
                    <p className="text-xs text-slate-500">Die Rüstzeit verteilt sich auf die ganze Menge, bei Baugruppen auf deren Menge: Wenige Meter sind je Meter teurer als viele. Gerechnet wird die gespeicherte Kalkulation, es wird nichts geändert.</p>
                    <div className="flex flex-wrap items-end gap-2">
                        <div className="w-56">
                            <label htmlFor="staffel-mengen" className="mb-1 block text-xs font-medium text-slate-600">Mengen (mit Leerzeichen oder Semikolon getrennt)</label>
                            <input id="staffel-mengen" value={staffelText} onChange={e => setStaffelText(e.target.value)} className="h-9 w-full rounded-md border border-slate-300 px-2 text-sm" />
                        </div>
                        <Button type="button" size="sm" variant="outline" onClick={() => void zeigeStaffel()}>Preise je Menge zeigen</Button>
                    </div>
                    {staffelFehler && <p role="alert" className="text-sm text-rose-700">{staffelFehler}</p>}
                    {staffel && (
                        <table className="w-full text-sm" aria-label="Preis nach Menge">
                            <thead><tr className="text-left text-xs uppercase tracking-wide text-slate-500"><th scope="col" className="py-1">Menge</th><th scope="col" className="py-1 text-right">Preis je Einheit</th><th scope="col" className="py-1 text-right">Gesamt</th></tr></thead>
                            <tbody>{staffel.map(z => (
                                <tr key={z.menge} className="border-t border-slate-100">
                                    <td className="py-1">{z.menge.toLocaleString('de-DE', { maximumFractionDigits: 3 })}</td>
                                    <td className="py-1 text-right tabular-nums">{formatCurrency(z.preisJeEinheit)}{!z.vollstaendig && <span className="ml-1 text-xs text-amber-700">(unvollständig)</span>}</td>
                                    <td className="py-1 text-right tabular-nums">{formatCurrency(z.gesamtpreis)}</td>
                                </tr>
                            ))}</tbody>
                        </table>
                    )}
                </div>
            )}

            <ArtikelAuswahlDialog offen={artikelDialog} onSchliessen={() => setArtikelDialog(false)} onUebernehmen={artikelHinzufuegen} />
            <ArtikelKurzDialog artikelId={artikelKurz} onSchliessen={() => setArtikelKurz(null)} />
        </section>
    );
}
