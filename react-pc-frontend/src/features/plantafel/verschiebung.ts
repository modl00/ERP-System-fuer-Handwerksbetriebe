import type { BelegungEintrag } from './plantafelApi';
import { formatDatum, istWochenende, parseDatum, plusTage } from './zeitachse';

/** Der Tag in der Ansicht reicht von 8:00 bis 17:00 Uhr; Termine mit Uhrzeit stehen darin, Vorgänge füllen ganze Tage. */
export const TAG_BEGINN = 8 * 60;
export const TAG_ENDE = 17 * 60;
export const TAG_MINUTEN = TAG_ENDE - TAG_BEGINN;
/** Beim Ziehen rastet ein Termin mit Uhrzeit auf halbe Stunden ein. */
export const RASTER_MINUTEN = 30;
export const RASTER_JE_TAG = TAG_MINUTEN / RASTER_MINUTEN;

export const minuten = (z: string): number => {
    const [h, m] = z.split(':').map(Number);
    return h * 60 + m;
};
export const alsZeit = (min: number): string => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;

/** Ein Vorgang beginnt an einem Arbeitstag: Fällt der neue Tag auf ein Wochenende, rückt er auf den Montag. */
export function naechsterArbeitstag(d: Date, arbeitstage?: readonly number[]): Date {
    let tag = d;
    // Eine leere Arbeitswoche gibt es nicht (der Server verlangt mindestens einen Tag); die Grenze verhindert trotzdem eine Endlosschleife
    for (let i = 0; i < 7 && istWochenende(tag, arbeitstage); i++) tag = plusTage(tag, 1);
    return tag;
}

/** Neuer Start eines Vorgangs, wenn er um {@code dxTage} (auch Bruchteile, aus der Mausbewegung) verschoben wird; {@code null} = bleibt am selben Tag. */
export function verschobenerStart(startIso: string, dxTage: number, arbeitstage?: readonly number[]): string | null {
    const ziel = formatDatum(naechsterArbeitstag(plusTage(parseDatum(startIso), Math.round(dxTage)), arbeitstage));
    return ziel === startIso ? null : ziel;
}

export interface TerminZiel { datum: string; von: string | null; bis: string | null }

/**
 * Neuer Tag und neue Uhrzeit eines Termins nach dem Ziehen um {@code dxTage}. Mit Uhrzeit rastet er auf halbe Stunden ein und springt über Tagesgrenzen
 * (der Tag hat 9 Stunden in der Ansicht); ohne Uhrzeit wandert er in ganzen Tagen. {@code null} = unverändert.
 */
export function verschobenerTermin(e: Pick<BelegungEintrag, 'von' | 'vonZeit' | 'bisZeit'>, dxTage: number): TerminZiel | null {
    if (!e.vonZeit) {
        const ziel = formatDatum(plusTage(parseDatum(e.von), Math.round(dxTage)));
        return ziel === e.von ? null : { datum: ziel, von: null, bis: null };
    }
    const schritte = Math.round(dxTage * RASTER_JE_TAG);
    if (schritte === 0) return null;
    const von = minuten(e.vonZeit);
    const dauer = e.bisZeit ? minuten(e.bisZeit) - von : null;
    const absolut = von - TAG_BEGINN + schritte * RASTER_MINUTEN;
    const tage = Math.floor(absolut / TAG_MINUTEN);
    const neuVon = absolut - tage * TAG_MINUTEN + TAG_BEGINN;
    const neuBis = dauer == null ? null : Math.min(neuVon + dauer, 24 * 60 - 1);
    return { datum: formatDatum(plusTage(parseDatum(e.von), tage)), von: alsZeit(neuVon), bis: neuBis == null ? null : alsZeit(neuBis) };
}

export interface Zuweisungsstand { mitarbeiterIds: number[]; ressourceId: number | null }

const nummer = (schluessel: string) => Number(schluessel.slice(1));

/**
 * Was sich an der Zuweisung eines Vorgangs ändert, wenn er aus der Zeile {@code quelle} in die Zeile {@code ziel} gezogen wird (Zeilen: "A" ohne Zuweisung,
 * "M<Nr>" Mitarbeiter, "R<Nr>" Ressource). Aus einer Mitarbeiter-Zeile in eine andere wechselt die Person; in eine Ressourcen-Zeile kommt die Ressource dazu;
 * in "Ohne Zuweisung" fällt die Zuweisung der Quellzeile weg. {@code null} = keine Änderung.
 */
export function neueZuweisung(quelle: string, ziel: string, aktuell: Zuweisungsstand): Zuweisungsstand | null {
    if (quelle === ziel) return null;
    let personen = [...aktuell.mitarbeiterIds];
    let ressource = aktuell.ressourceId;
    const ohneQuelle = () => {
        if (quelle[0] === 'M') personen = personen.filter(id => id !== nummer(quelle));
        if (quelle[0] === 'R') ressource = null;
    };
    if (ziel === 'A') {
        ohneQuelle();
    } else if (ziel[0] === 'M') {
        if (quelle[0] === 'M') ohneQuelle();
        if (!personen.includes(nummer(ziel))) personen.push(nummer(ziel));
    } else if (ziel[0] === 'R') {
        ressource = nummer(ziel);
    }
    const gleich = personen.length === aktuell.mitarbeiterIds.length && personen.every(id => aktuell.mitarbeiterIds.includes(id)) && ressource === aktuell.ressourceId;
    return gleich ? null : { mitarbeiterIds: personen, ressourceId: ressource };
}
