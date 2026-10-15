import type { BelegungEintrag } from './plantafelApi';
import { RASTER_JE_TAG, TAG_BEGINN, TAG_MINUTEN, minuten } from './verschiebung';
import { parseDatum, tageZwischen } from './zeitachse';

/**
 * {@code erster} und {@code letzter} sind die Tage (Spaltennummern im sichtbaren Zeitraum, abgeschnitten); {@code von} und {@code bis} die genaue Lage in Tagen ab
 * Zeitraumbeginn, also mit der Uhrzeit innerhalb des Tages (ein Termin um 13:30 steht in der Mitte des Tages).
 */
export interface Platziert { eintrag: BelegungEintrag; erster: number; letzter: number; von: number; bis: number; bahn: number }

/** Lage einer Uhrzeit im Tag von 0 (Tagesbeginn) bis 1 (Tagesende). */
const imTag = (zeit: string) => Math.min(1, Math.max(0, (minuten(zeit) - TAG_BEGINN) / TAG_MINUTEN));
const MINDEST = 1 / RASTER_JE_TAG;

/**
 * Legt die Einträge einer Zeile auf Tage und Bahnen. Einträge, die sich zeitlich überschneiden, kommen in verschiedene Bahnen untereinander;
 * Einträge außerhalb des Zeitraums fallen weg, solche am Rand werden abgeschnitten.
 */
export function platziere(eintraege: BelegungEintrag[], start: Date, tage: number): { platziert: Platziert[]; bahnen: number } {
    const sortiert = eintraege
        .map(e => {
            const ersterTag = tageZwischen(start, parseDatum(e.von));
            const letzterTag = tageZwischen(start, parseDatum(e.bis));
            const erster = Math.max(0, ersterTag);
            const letzter = Math.min(tage - 1, letzterTag);
            const mitZeit = e.vonZeit != null;
            let von = erster;
            let bis = letzter + 1;
            if (mitZeit) {
                if (ersterTag >= 0) von = ersterTag + imTag(e.vonZeit as string);
                if (letzterTag <= tage - 1) bis = letzterTag + (e.bisZeit ? imTag(e.bisZeit) : Math.min(1, imTag(e.vonZeit as string) + 60 / TAG_MINUTEN));
                bis = Math.max(bis, von + MINDEST);
            }
            return { e, erster, letzter, von, bis };
        })
        .filter(x => x.letzter >= x.erster)
        .sort((a, b) => a.von - b.von || b.bis - a.bis || a.e.id.localeCompare(b.e.id));
    const bahnEnde: number[] = [];
    const platziert: Platziert[] = [];
    for (const x of sortiert) {
        let bahn = bahnEnde.findIndex(ende => ende <= x.von + 1e-9);
        if (bahn < 0) {
            bahn = bahnEnde.length;
            bahnEnde.push(x.bis);
        } else {
            bahnEnde[bahn] = x.bis;
        }
        platziert.push({ eintrag: x.e, erster: x.erster, letzter: x.letzter, von: x.von, bis: x.bis, bahn });
    }
    return { platziert, bahnen: Math.max(1, bahnEnde.length) };
}
