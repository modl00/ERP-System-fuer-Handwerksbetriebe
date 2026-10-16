import { erzeugePalette, istGueltigeFarbe, STUFEN } from './farbpalette';

/** Merkt die Farbe im Browser, damit die Anmeldeseite und der erste Bildaufbau schon richtig gefärbt sind. */
const SPEICHER_SCHLUESSEL = 'programmfarbe';

export interface Darstellung {
    /** {@code #rrggbb} oder {@code null} für das Standard-Rosé. */
    farbe: string | null;
}

/** Setzt die Farbvariablen am Wurzelelement. {@code null} entfernt sie, dann gilt wieder das Standard-Rosé. */
export function wendeFarbeAn(farbe: string | null, ziel: HTMLElement = document.documentElement): void {
    if (farbe === null || !istGueltigeFarbe(farbe)) {
        for (const stufe of STUFEN) ziel.style.removeProperty(`--farbe-${stufe}`);
        return;
    }
    const palette = erzeugePalette(farbe);
    for (const stufe of STUFEN) ziel.style.setProperty(`--farbe-${stufe}`, palette[stufe]);
}

export function merkeFarbe(farbe: string | null): void {
    try {
        if (farbe === null) localStorage.removeItem(SPEICHER_SCHLUESSEL);
        else localStorage.setItem(SPEICHER_SCHLUESSEL, farbe);
    } catch {
        // Speicher gesperrt (privates Fenster): die Farbe kommt dann vom Server
    }
}

function gemerkteFarbe(): string | null {
    try {
        const wert = localStorage.getItem(SPEICHER_SCHLUESSEL);
        return wert !== null && istGueltigeFarbe(wert) ? wert : null;
    } catch {
        return null;
    }
}

/** Beim Start aufrufen: nimmt die zuletzt gemerkte Farbe, bevor der Server geantwortet hat. */
export function wendeGemerkteFarbeAn(): void {
    wendeFarbeAn(gemerkteFarbe());
}

async function lese(res: Response, fallback: string): Promise<Darstellung> {
    if (!res.ok) {
        let text = '';
        try {
            const body = await res.json();
            text = typeof body?.message === 'string' ? body.message : '';
        } catch {
            // kein JSON
        }
        throw new Error(text || fallback);
    }
    const body = (await res.json()) as { farbe?: string | null };
    return { farbe: typeof body.farbe === 'string' && istGueltigeFarbe(body.farbe) ? body.farbe : null };
}

/** Die Programmfarbe der Firma. Dürfen alle Angemeldeten lesen. */
export async function ladeDarstellung(): Promise<Darstellung> {
    return lese(await fetch('/api/darstellung'), 'Die Programmfarbe konnte nicht geladen werden.');
}

/** Speichert die Programmfarbe für die ganze Firma (nur Admin). {@code null} stellt das Standard-Rosé wieder her. */
export async function speichereFarbe(farbe: string | null): Promise<Darstellung> {
    return lese(await fetch('/api/settings/darstellung', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ farbe }),
    }), 'Die Programmfarbe konnte nicht gespeichert werden.');
}

/** Lädt die Farbe vom Server und wendet sie an. Fehler werden still ignoriert: Dann bleibt die gemerkte oder Standardfarbe. */
export async function synchronisiereFarbe(): Promise<void> {
    try {
        const { farbe } = await ladeDarstellung();
        wendeFarbeAn(farbe);
        merkeFarbe(farbe);
    } catch {
        // Netz weg oder nicht angemeldet: Farbe bleibt, wie sie ist
    }
}
