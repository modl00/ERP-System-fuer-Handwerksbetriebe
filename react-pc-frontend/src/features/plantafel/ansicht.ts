import type { Auftrag, BelegungEintrag, BelegungZeile, Prioritaet, Probe, Vorgang } from './plantafelApi';
import { formatDatum, parseDatum, plusTage, tageZwischen } from './zeitachse';

/** Die Zeitstufen der Ansicht: sichtbare Tage und Breite eines Tages in Pixeln. Die größte zeigt zwei Jahre. */
export const ZEITSTUFEN = [
    { tage: 7, text: '7 Tage', breite: 150 },
    { tage: 14, text: '14 Tage', breite: 108 },
    { tage: 28, text: '4 Wochen', breite: 64 },
    { tage: 91, text: '3 Monate', breite: 24 },
    { tage: 182, text: '6 Monate', breite: 12 },
    { tage: 364, text: '1 Jahr', breite: 6 },
    { tage: 728, text: '2 Jahre', breite: 3 },
] as const;

export const breiteJeTag = (zoom: number): number => ZEITSTUFEN.find(z => z.tage === zoom)?.breite ?? 64;

/** Ab dieser Tagesbreite zeigt ein Tag Wochentag und Datum (sonst nur die Zahl oder gar nichts, dafür stehen Monate darüber). */
export const BREITE_MIT_NAMEN = 40;
/** Unter dieser Tagesbreite sind einzelne Termine nicht mehr lesbar: Die Ansicht zeigt nur die Anzahl je Woche. */
export const BREITE_TERMINE_EINZELN = 30;
/** Ab dieser Tagesbreite steht die Tageszahl im Kopf. */
export const BREITE_MIT_ZAHL = 14;

export type Sortierung = 'LIEFERZEIT' | 'VERSPAETUNG' | 'WICHTIGKEIT';

const RANG: Record<Prioritaet, number> = { DRINGEND: 0, NORMAL: 1, NICHT_DRINGEND: 2 };

/** Wichtigste Stufe unter den noch nicht erledigten Vorgängen eines Auftrags (0 = dringend, 9 = nichts offen). */
export function wichtigkeit(a: Auftrag): number {
    const offene = a.vorgaenge.filter(v => v.status !== 'FERTIG');
    return offene.length === 0 ? 9 : Math.min(...offene.map(v => RANG[v.prioritaet] ?? 1));
}

export const istErledigt = (a: Auftrag) => a.vorgaenge.length > 0 && a.vorgaenge.every(v => v.status === 'FERTIG');

/** Aufträge filtern und sortieren: nach Lieferzeit, nach Verspätung (größte zuerst) oder nach Wichtigkeit (dringend zuerst). */
export function ordneAuftraege(auftraege: readonly Auftrag[], sortierung: Sortierung, nurVerspaetete: boolean, erledigteZeigen: boolean): Auftrag[] {
    const nachLieferzeit = (a: Auftrag, b: Auftrag) => (a.liefertermin ?? '9999').localeCompare(b.liefertermin ?? '9999') || a.projektId - b.projektId;
    return auftraege
        .filter(a => a.vorgaenge.some(v => v.start && v.ende))
        .filter(a => erledigteZeigen || !istErledigt(a))
        .filter(a => !nurVerspaetete || a.ampel === 'VERSPAETET')
        .sort((a, b) => {
            if (sortierung === 'VERSPAETUNG') return (b.verspaetungTage ?? 0) - (a.verspaetungTage ?? 0) || nachLieferzeit(a, b);
            if (sortierung === 'WICHTIGKEIT') return wichtigkeit(a) - wichtigkeit(b) || nachLieferzeit(a, b);
            return nachLieferzeit(a, b);
        });
}

/** Die Zeile eines Auftrags in der Gruppe „Allgemein“: seine Vorgänge als Balken, Schlüssel {@code P<Nr>}. */
export function auftragsZeile(a: Auftrag): BelegungZeile {
    const eintraege: BelegungEintrag[] = a.vorgaenge.filter(v => v.start && v.ende).map(v => ({
        id: `V${v.id}`,
        quelle: 'VORGANG',
        titel: v.bezeichnung,
        untertitel: v.mitarbeiterIds.length === 0 && !v.wartet ? 'noch niemand zugewiesen' : v.typName,
        von: v.start as string,
        bis: v.ende as string,
        vonZeit: null,
        bisZeit: null,
        farbe: v.farbe,
        projektId: a.projektId,
        prioritaet: v.prioritaet,
        fest: v.festerStart != null,
        status: v.status,
        hinweis: v.konflikt ? 'Der feste Start liegt vor dem Ende der Vorgänger.' : v.hinweis,
        aufwandStunden: v.wartet ? null : v.aufwandStunden,
        istStunden: v.istStunden,
        vorgaengerIds: v.vorgaenger.map(d => d.vorgaengerId),
    }));
    return { schluessel: `P${a.projektId}`, gruppe: 'ALLGEMEIN', name: a.bezeichnung, eintraege };
}

/**
 * Bei großen Zeitstufen sind einzelne Termine nicht lesbar: Sie werden je Woche zu einem Eintrag „3 Termine“ zusammengefasst.
 * Alles andere bleibt unverändert.
 */
export function verdichteTermine(eintraege: readonly BelegungEintrag[]): BelegungEintrag[] {
    const rest: BelegungEintrag[] = [];
    const jeWoche = new Map<string, number>();
    for (const e of eintraege) {
        if (e.quelle !== 'TERMIN') {
            rest.push(e);
            continue;
        }
        const tag = parseDatum(e.von);
        const montag = formatDatum(plusTage(tag, -((tag.getDay() + 6) % 7)));
        jeWoche.set(montag, (jeWoche.get(montag) ?? 0) + 1);
    }
    for (const [montag, n] of jeWoche) {
        rest.push({
            id: `S${montag}`, quelle: 'TERMIN_SAMMEL', titel: n === 1 ? '1 Termin' : `${n} Termine`, untertitel: null, von: montag, bis: formatDatum(plusTage(parseDatum(montag), 6)),
            vonZeit: null, bisZeit: null, farbe: '#64748b', projektId: null, prioritaet: null, fest: false, status: null, hinweis: null,
        });
    }
    return rest;
}

export interface Pfeil { von: string; nach: string; x1: number; y1: number; x2: number; y2: number; pfad: string }

/**
 * Die Pfeile zwischen den Vorgängen einer Auftragszeile wie in einem Gantt-Diagramm: vom Ende des Vorgängers (rechter Rand, Mitte des Balkens)
 * zum Beginn des Nachfolgers (linker Rand). Die Koordinaten sind Pixel in der Zeile; {@code balken} liefert für jeden Vorgang (Nummer) seine Lage.
 */
export function pfeile(balken: ReadonlyMap<number, { x1: number; x2: number; y: number }>, abhaengigkeiten: ReadonlyMap<number, readonly number[]>): Pfeil[] {
    const liste: Pfeil[] = [];
    for (const [nachfolger, vorgaenger] of abhaengigkeiten) {
        const nach = balken.get(nachfolger);
        if (!nach) continue;
        for (const v of vorgaenger) {
            const von = balken.get(v);
            if (!von) continue;
            const x1 = von.x2;
            const x2 = nach.x1;
            const y1 = von.y;
            const y2 = nach.y;
            const knick = 7;
            // Liegt der Nachfolger weit genug rechts, genügt ein Knick in der Mitte; sonst führt der Pfeil nach unten um den Balken herum
            const pfad = x2 >= x1 + 2 * knick
                ? `M ${x1} ${y1} H ${x1 + knick} V ${y2} H ${x2}`
                : `M ${x1} ${y1} H ${x1 + knick} V ${(y1 + y2) / 2 + (y1 === y2 ? 14 : 0)} H ${x2 - knick} V ${y2} H ${x2}`;
            liste.push({ von: String(v), nach: String(nachfolger), x1, y1, x2, y2, pfad });
        }
    }
    return liste;
}

/** Wie weit ein Arbeitsvorgang schon gestempelt ist, von 0 bis 1; {@code null}, wenn nichts gestempelt ist oder es keinen Aufwand gibt. */
export function fortschritt(e: Pick<BelegungEintrag, 'aufwandStunden' | 'istStunden'>): number | null {
    if (!e.aufwandStunden || e.aufwandStunden <= 0 || !e.istStunden || e.istStunden <= 0) return null;
    return Math.min(1, e.istStunden / e.aufwandStunden);
}

/** Die Monatsbänder über den Tagen: Name, erste Spalte und Anzahl Spalten. */
export function monatsBaender(start: Date, tage: number): { text: string; von: number; anzahl: number }[] {
    const namen = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
    const baender: { text: string; von: number; anzahl: number }[] = [];
    for (let i = 0; i < tage; i++) {
        const d = plusTage(start, i);
        const text = `${namen[d.getMonth()]} ${d.getFullYear()}`;
        const letztes = baender[baender.length - 1];
        if (letztes && letztes.text === text) letztes.anzahl += 1;
        else baender.push({ text, von: i, anzahl: 1 });
    }
    return baender;
}

/** Spaltennummer eines Datums (ab {@code start}), auch außerhalb des Fensters (negativ oder größer als die Tage). */
export const spalteVon = (start: Date, iso: string) => tageZwischen(start, parseDatum(iso));

/** Wie lange das Fenster der geladenen Tage höchstens sein darf (Server: höchstens 800 Tage je Abfrage). */
export const MAX_FENSTER = 780;

/** Um wie viele Arbeitstage sich ein Vorgang verlängert (plus) oder verkürzt (minus), wenn sein Ende um {@code dxTage} Kalendertage gezogen wird. */
export function arbeitstageDelta(endeIso: string, dxTage: number, arbeitstage: readonly number[] = [1, 2, 3, 4, 5]): number {
    const n = Math.round(dxTage);
    if (n === 0) return 0;
    const istArbeitstag = (d: Date) => arbeitstage.includes(d.getDay() === 0 ? 7 : d.getDay());
    let d = parseDatum(endeIso);
    let delta = 0;
    for (let i = 0; i < Math.abs(n); i++) {
        if (n > 0) {
            d = plusTage(d, 1);
            if (istArbeitstag(d)) delta++;
        } else {
            if (istArbeitstag(d)) delta--;
            d = plusTage(d, -1);
        }
    }
    return delta;
}

export interface NeueGroesse {
    /** Was an den Vorgang geschickt wird. */
    aenderung: { dauerTage?: number; aufwandStunden?: number };
    probe: Probe;
    text: string;
}

const alsViertel = (x: number) => Math.max(0.25, Math.round(x * 4) / 4);
const stundenText = (h: number) => String(h).replace('.', ',');

/**
 * Was das Ziehen am rechten Rand bedeutet: Bei einem Wartevorgang ändert sich die Dauer in Arbeitstagen, bei einem Arbeitsvorgang der Aufwand im Verhältnis
 * zur Dauer (zwei Tage länger bei fünf Tagen = 40 % mehr Stunden). {@code null}, wenn sich nichts ändert oder der Vorgang keinen Aufwand hat.
 */
export function neueGroesse(v: Pick<Vorgang, 'wartet' | 'dauerTage' | 'aufwandStunden'>, deltaArbeitstage: number): NeueGroesse | null {
    if (deltaArbeitstage === 0) return null;
    const dauer = Math.max(1, v.dauerTage + deltaArbeitstage);
    if (v.wartet) {
        if (dauer === v.dauerTage) return null;
        return { aenderung: { dauerTage: dauer }, probe: { dauerTage: dauer }, text: `Dauer von ${v.dauerTage} auf ${dauer} Arbeitstage` };
    }
    if (v.aufwandStunden == null || v.aufwandStunden <= 0 || v.dauerTage <= 0) return null;
    const aufwand = alsViertel(v.aufwandStunden * dauer / v.dauerTage);
    if (aufwand === v.aufwandStunden) return null;
    return { aenderung: { aufwandStunden: aufwand }, probe: { aufwandStunden: aufwand }, text: `Aufwand von ${stundenText(v.aufwandStunden)} auf ${stundenText(aufwand)} Stunden` };
}
