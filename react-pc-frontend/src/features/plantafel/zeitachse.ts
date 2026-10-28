/** Die Zeitachse der Plantafel: Tage als Spalten, Datumswerte als "JJJJ-MM-TT" (ohne Zeitzone, damit nichts um einen Tag verrutscht). */

export const TAG_BREITE = 28;
const MS_TAG = 86_400_000;

/** "2026-10-12" als lokales Datum (nicht UTC). */
export function parseDatum(iso: string): Date {
    const [j, m, t] = iso.split('-').map(Number);
    return new Date(j, m - 1, t);
}

export function formatDatum(d: Date): string {
    const z = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${z(d.getMonth() + 1)}-${z(d.getDate())}`;
}

export function plusTage(d: Date, n: number): Date {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
}

/** Ganze Tage von a bis b (b später = positiv). Rundet, damit die Umstellung der Sommerzeit nichts verschiebt. */
export function tageZwischen(a: Date, b: Date): number {
    return Math.round((b.getTime() - a.getTime()) / MS_TAG);
}

/** Arbeitstage als ISO-Zahlen (1 = Montag bis 7 = Sonntag), wie die Einstellungen „Planung" sie liefern. */
export const STANDARD_ARBEITSTAGE = [1, 2, 3, 4, 5];

/** Ein arbeitsfreier Tag nach der Arbeitswoche (ohne Angabe: Samstag und Sonntag). Feiertage kennt die Ansicht nicht. */
export function istWochenende(d: Date, arbeitstage: readonly number[] = STANDARD_ARBEITSTAGE): boolean {
    return !arbeitstage.includes(d.getDay() === 0 ? 7 : d.getDay());
}

export interface Achse { start: Date; tage: number }

/**
 * Die sichtbare Zeitspanne: ein paar Tage vor dem frühesten Datum bis ein paar Tage nach dem spätesten, mindestens 6 Wochen, beginnend an einem Montag.
 * Ohne Datumswerte: 6 Wochen ab der Woche von {@code heute}.
 */
export function berechneAchse(daten: (string | null | undefined)[], heute: Date, mindestTage = 42): Achse {
    const werte = daten.filter((d): d is string => !!d).map(parseDatum);
    werte.push(heute);
    const frueh = new Date(Math.min(...werte.map(d => d.getTime())));
    const spaet = new Date(Math.max(...werte.map(d => d.getTime())));
    const tagDerWoche = (frueh.getDay() + 6) % 7; // Montag = 0
    const start = plusTage(frueh, -(tagDerWoche + 7));
    const tage = Math.max(mindestTage, tageZwischen(start, spaet) + 15);
    return { start, tage: Math.ceil(tage / 7) * 7 };
}

/** Linke Kante und Breite eines Balkens von start bis ende (beide Tage zählen mit). */
export function balken(achse: Achse, startIso: string, endeIso: string): { x: number; breite: number } {
    const von = tageZwischen(achse.start, parseDatum(startIso));
    const bis = tageZwischen(achse.start, parseDatum(endeIso));
    return { x: von * TAG_BREITE, breite: (Math.max(bis, von) - von + 1) * TAG_BREITE };
}

export function xVon(achse: Achse, iso: string): number {
    return tageZwischen(achse.start, parseDatum(iso)) * TAG_BREITE;
}

/** Die arbeitsfreien Tage als Streifen (x, Breite), für den Hintergrund; aufeinanderfolgende freie Tage bilden einen Streifen. */
export function wochenenden(achse: Achse, arbeitstage: readonly number[] = STANDARD_ARBEITSTAGE): { x: number; breite: number }[] {
    const streifen: { x: number; breite: number }[] = [];
    for (let i = 0; i < achse.tage; i++) {
        if (!istWochenende(plusTage(achse.start, i), arbeitstage)) continue;
        const letzter = streifen[streifen.length - 1];
        if (letzter && letzter.x + letzter.breite === i * TAG_BREITE) letzter.breite += TAG_BREITE;
        else streifen.push({ x: i * TAG_BREITE, breite: TAG_BREITE });
    }
    return streifen;
}

const MONATE = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];

/** Die Monatsköpfe (Text, Start-Spalte, Anzahl Tage). */
export function monatskoepfe(achse: Achse): { text: string; x: number; breite: number }[] {
    const koepfe: { text: string; x: number; breite: number }[] = [];
    for (let i = 0; i < achse.tage; i++) {
        const d = plusTage(achse.start, i);
        const letzter = koepfe[koepfe.length - 1];
        const text = `${MONATE[d.getMonth()]} ${d.getFullYear()}`;
        if (letzter && letzter.text === text) letzter.breite += TAG_BREITE;
        else koepfe.push({ text, x: i * TAG_BREITE, breite: TAG_BREITE });
    }
    return koepfe;
}

/** Kurzdatum für die Anzeige: "12.10.2026". */
export function anzeigeDatum(iso: string | null | undefined): string {
    if (!iso) return '–';
    const [j, m, t] = iso.split('-');
    return `${t}.${m}.${j}`;
}

/** Kurzdatum mit zweistelligem Jahr für enge Stellen: "12.10.26". */
export function anzeigeKurz(iso: string | null | undefined): string {
    if (!iso) return '–';
    const [j, m, t] = iso.split('-');
    return `${t}.${m}.${j.slice(2)}`;
}
