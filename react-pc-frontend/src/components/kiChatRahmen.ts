/**
 * Lage und Größe des KI-Chatfensters (Issue #251): frei verschiebbar und in der Größe änderbar, zum Beispiel neben einem anderen Fenster. Reine Rechenhilfen,
 * damit sich die Regeln ohne Oberfläche prüfen lassen: Das Fenster bleibt immer so im Bild, dass man es am Kopf wieder anfassen kann.
 */

export interface Rahmen { x: number; y: number; w: number; h: number }

export const MIN_BREITE = 320;
export const MIN_HOEHE = 360;
const RAND = 16;
/** So viel vom Kopf muss sichtbar bleiben, damit man das Fenster zurückholen kann. */
const SICHTBAR = 80;

/** Unten rechts, 420 × 600 (kleiner, wenn der Bildschirm kleiner ist). */
export function standardRahmen(vw: number, vh: number): Rahmen {
    const w = Math.max(Math.min(420, vw - 2 * RAND), Math.min(MIN_BREITE, vw));
    const h = Math.max(Math.min(600, vh - 4 * RAND), Math.min(MIN_HOEHE, vh));
    return { x: Math.max(0, vw - w - 24), y: Math.max(0, vh - h - 24), w, h };
}

/** Hält Größe und Lage im erlaubten Bereich (Bildschirmgröße, Mindestgröße, Kopf sichtbar). */
export function begrenze(r: Rahmen, vw: number, vh: number): Rahmen {
    const w = Math.round(Math.min(Math.max(r.w, Math.min(MIN_BREITE, vw)), vw));
    const h = Math.round(Math.min(Math.max(r.h, Math.min(MIN_HOEHE, vh)), vh));
    const x = Math.round(Math.min(Math.max(r.x, SICHTBAR - w), vw - SICHTBAR));
    const y = Math.round(Math.min(Math.max(r.y, 0), vh - SICHTBAR));
    return { x, y, w, h };
}

/** Ein gespeicherter Wert, der nicht wie ein Rahmen aussieht, gilt nicht. */
export function leseRahmen(wert: unknown): Rahmen | null {
    if (!wert || typeof wert !== 'object') return null;
    const { x, y, w, h } = wert as Record<string, unknown>;
    return [x, y, w, h].every((n) => typeof n === 'number' && Number.isFinite(n)) ? { x: x as number, y: y as number, w: w as number, h: h as number } : null;
}

/** Ecke unten rechts ziehen: Größe ändert sich, die obere linke Ecke bleibt. */
export function groesseRechts(start: Rahmen, dx: number, dy: number, vw: number, vh: number): Rahmen {
    return begrenze({ ...start, w: start.w + dx, h: start.h + dy }, vw, vh);
}

/** Ecke unten links ziehen: Die rechte Kante bleibt stehen, Breite und Lage ändern sich. */
export function groesseLinks(start: Rahmen, dx: number, dy: number, vw: number, vh: number): Rahmen {
    const w = Math.min(Math.max(start.w - dx, Math.min(MIN_BREITE, vw)), vw);
    return begrenze({ x: start.x + start.w - w, y: start.y, w, h: start.h + dy }, vw, vh);
}

/** Das ganze Fenster verschieben. */
export function verschiebe(start: Rahmen, dx: number, dy: number, vw: number, vh: number): Rahmen {
    return begrenze({ ...start, x: start.x + dx, y: start.y + dy }, vw, vh);
}
