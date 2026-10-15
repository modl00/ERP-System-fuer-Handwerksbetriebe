/** Kalkulation einer Leistung: Material (Artikel) und Arbeitszeit (Arbeitsgang) je Einheit, dazu ein Zuschlag. */

export type KalkulationHinweis = 'OK' | 'KEIN_PREIS' | 'KEIN_GEWICHT' | 'KEIN_AUFSCHLAG' | 'KEIN_STUNDENSATZ' | 'BAUGRUPPE_UNVOLLSTAENDIG' | 'KEIN_MASCHINENSATZ';

export interface KalkulationPosition {
    id: number | null;
    typ: 'ARTIKEL' | 'ARBEIT' | 'BAUGRUPPE' | 'FREMDLEISTUNG';
    artikelId: number | null;
    arbeitsgangId: number | null;
    /** Nur bei Fremdleistung: das gewählte Angebot des Veredlers, sein Name, die Aufstellung der Kostenarten (Summe = {@code summe}) und das Gewicht je Einheit der Leistung. */
    fremdleistungId?: number | null;
    veredler?: string | null;
    kostenarten?: { art: string; text: string; betrag: number }[] | null;
    gewichtKgJeEinheit?: number | null;
    /** Nur bei Material aus Profilen (Stammdaten kg je m, Mantelfläche je m): Gewicht in kg und Oberfläche in m² dieser Zeile je Einheit der Leistung, ohne Verschnitt. */
    gewichtKg?: number | null;
    oberflaecheM2?: number | null;
    /** Nur bei Fremdleistung: woher die Menge kommt (leer oder HAND = von Hand, GEWICHT oder OBERFLAECHE = berechnet). */
    mengeAus?: 'HAND' | 'GEWICHT' | 'OBERFLAECHE' | null;
    /** Nur bei Arbeit mit Formel: die eingegebenen Werte der Eingabefelder (Feldname in Kleinbuchstaben); die Zeiten sind daraus errechnet. Bei Material mit Zuschnitt-Formel die Maße. */
    variablen?: Record<string, number> | null;
    /** Nur bei Material mit Zuschnitt-Formel (#228): die Eingabefelder für die Maße */
    formelFelder?: import('../../types').ArbeitsgangFormelFeld[] | null;
    /** Nur bei Baugruppen: die benutzte Baugruppe (eine Leistung); der Einzelpreis ist der einer Einheit der Baugruppe ohne deren Zuschlag. */
    baugruppeId?: number | null;
    bezeichnung: string;
    menge: number;
    einheit: string;
    einzelpreis: number | null;
    summe: number;
    hinweis: KalkulationHinweis;
    notiz: string | null;
    /** Nur bei Arbeit mit Zeiten: Rüstzeit in Minuten, einmal je Los. {@code menge} sind dann die errechneten Stunden je Einheit. */
    ruestzeitMinuten?: number | null;
    /** Nur bei Arbeit mit Zeiten: Bearbeitungszeit in Minuten je Einheit der Leistung. */
    bearbeitungszeitMinuten?: number | null;
    /** Nur bei Arbeit mit Zeiten: wie oft der Vorgang je Einheit vorkommt (die Bearbeitungszeit gilt je Vorgang). */
    anzahlVorgaenge?: number | null;
    /** Nur bei Material mit Verschnitt: der Zuschlag in Prozent und die Menge einschließlich Verschnitt, mit der der Preis gerechnet ist. */
    verschnittProzent?: number | null;
    mengeMitVerschnitt?: number | null;
    /** Nur bei Arbeit, deren Arbeitsgang eine Maschine hat: Kosten je Maschinenstunde, Maschinenkosten der Zeile (in der Summe enthalten) und Maschinenzeit in Prozent der Arbeitszeit. */
    maschinenSatz?: number | null;
    maschinenSumme?: number | null;
    maschinenanteilProzent?: number | null;
}

export interface Kalkulation {
    leistungId: number;
    kalkuliert: boolean;
    zuschlagProzent: number | null;
    /** Auf wie viele Einheiten sich die Rüstzeit verteilt; fehlt die Angabe, gilt 1. */
    losgroesse?: number;
    positionen: KalkulationPosition[];
    materialSumme: number;
    arbeitSumme: number;
    /** Maschinenkosten der Arbeitsgänge mit Maschine; fehlt bei älteren Antworten. */
    maschineSumme?: number;
    /** Kosten der Fremdleistungen (Veredler) je Einheit; fehlt bei älteren Antworten. */
    fremdleistungSumme?: number;
    /** Gewicht (kg) und Oberfläche (m²) der Leistung je Einheit aus den Artikeln; {@code gewichtVollstaendig} = von jedem Material bekannt. */
    gewichtKg?: number | null;
    oberflaecheM2?: number | null;
    gewichtVollstaendig?: boolean;
    zwischensumme: number;
    zuschlag: number;
    preisBerechnet: number;
    preisGespeichert: number | null;
    preisVeraltet: boolean;
    vollstaendig: boolean;
}

/** Eine Zeile, wie sie zum Speichern und für die Vorschau an das Backend geht. */
export interface KalkulationZeileEingabe {
    typ: 'ARTIKEL' | 'ARBEIT' | 'BAUGRUPPE' | 'FREMDLEISTUNG';
    artikelId: number | null;
    arbeitsgangId: number | null;
    baugruppeId?: number | null;
    /** Nur bei Fremdleistung: das gewählte Angebot und das Gewicht je Einheit (nur für Transport je kg nötig) */
    fremdleistungId?: number | null;
    gewichtKgJeEinheit?: number | null;
    mengeAus?: 'HAND' | 'GEWICHT' | 'OBERFLAECHE' | null;
    menge: number | null;
    notiz: string | null;
    /** Bei Arbeit mit Zeiten (statt Stunden in {@code menge}) */
    ruestzeitMinuten?: number | null;
    bearbeitungszeitMinuten?: number | null;
    anzahlVorgaenge?: number | null;
    /** Nur bei Material: Verschnitt in Prozent (leer = keiner) */
    verschnittProzent?: number | null;
    /** Nur bei Arbeit: Maschinenzeit in Prozent der Arbeitszeit (leer = 100) */
    maschinenanteilProzent?: number | null;
    /** Nur bei Arbeitsgang mit Formel: die Werte der Eingabefelder (Feldname in Kleinbuchstaben); die Zeiten rechnet der Server */
    variablen?: Record<string, number> | null;
}

export interface KalkulationEingabe {
    kalkuliert: boolean;
    zuschlagProzent: number | null;
    losgroesse?: number | null;
    positionen: KalkulationZeileEingabe[];
}

async function antwort(res: Response, fallback: string): Promise<Kalkulation> {
    if (res.ok) return res.json() as Promise<Kalkulation>;
    let text = fallback;
    try {
        const body = await res.json() as { message?: string };
        if (body?.message) text = body.message;
    } catch { /* kein JSON: Standardtext */ }
    throw new Error(text);
}

const basis = (leistungId: number | string) => `/api/leistungen/${encodeURIComponent(String(leistungId))}/kalkulation`;
const json = { 'Content-Type': 'application/json' };

export async function ladeKalkulation(leistungId: number | string): Promise<Kalkulation> {
    return antwort(await fetch(basis(leistungId)), 'Die Kalkulation konnte nicht geladen werden.');
}

export async function vorschauKalkulation(leistungId: number | string, eingabe: KalkulationEingabe): Promise<Kalkulation> {
    return antwort(await fetch(`${basis(leistungId)}/vorschau`, { method: 'POST', headers: json, body: JSON.stringify(eingabe) }),
        'Die Kalkulation konnte nicht berechnet werden.');
}

export async function speichereKalkulation(leistungId: number | string, eingabe: KalkulationEingabe): Promise<Kalkulation> {
    return antwort(await fetch(basis(leistungId), { method: 'PUT', headers: json, body: JSON.stringify(eingabe) }),
        'Die Kalkulation konnte nicht gespeichert werden.');
}

export async function uebernehmePreis(leistungId: number | string): Promise<Kalkulation> {
    return antwort(await fetch(`${basis(leistungId)}/preis-uebernehmen`, { method: 'POST' }),
        'Der Preis konnte nicht übernommen werden.');
}

/** Warum eine Zeile keinen Preis hat, in Handwerker-Sprache. Leer, wenn alles in Ordnung ist. */
export function hinweisText(hinweis: KalkulationHinweis): string {
    switch (hinweis) {
        case 'KEIN_PREIS': return 'Kein Preis hinterlegt';
        case 'KEIN_GEWICHT': return 'Gewicht fehlt';
        case 'KEIN_STUNDENSATZ': return 'Kein Kalkulationssatz';
        case 'KEIN_AUFSCHLAG': return 'Ohne Aufschlag (Einkaufspreis)';
        case 'KEIN_MASCHINENSATZ': return 'Am Arbeitsgang fehlt der Maschinenstundensatz';
        case 'BAUGRUPPE_UNVOLLSTAENDIG': return 'Baugruppe unvollständig (leer, oder es fehlt ein Preis)';
        default: return '';
    }
}

export interface StaffelZeile {
    menge: number;
    materialJeEinheit: number;
    arbeitJeEinheit: number;
    preisJeEinheit: number;
    gesamtpreis: number;
    vollstaendig: boolean;
    fremdleistungJeEinheit?: number;
}

/** Preis je Einheit bei mehreren Angebotsmengen (die gespeicherte Kalkulation, Rüstzeit auf die Menge verteilt). */
export async function ladeStaffel(leistungId: number | string, mengen: number[]): Promise<StaffelZeile[]> {
    const res = await fetch(`${basis(leistungId)}/staffel`, { method: 'POST', headers: json, body: JSON.stringify({ mengen }) });
    if (res.ok) return res.json() as Promise<StaffelZeile[]>;
    let text = 'Die Staffel konnte nicht berechnet werden.';
    try {
        const body = await res.json() as { message?: string };
        if (body?.message) text = body.message;
    } catch { /* kein JSON: Standardtext */ }
    throw new Error(text);
}
