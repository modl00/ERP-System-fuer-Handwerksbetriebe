/** Die besondere Adresse für den Knopf „Alle Funktionen“ in der Leiste. */
export const ALLE_FUNKTIONEN = '/alle-funktionen';

export interface LeisteStand {
    /** Vorgabe der Firma (Adressen in Reihenfolge); {@code null}, solange der Admin keine gespeichert hat. */
    vorgabe: string[] | null;
    /** Eigene Leiste des Benutzers; {@code null}, solange er nichts geändert hat. */
    eigene: string[] | null;
}

function liste(wert: unknown): string[] | null {
    return Array.isArray(wert) ? wert.filter((a): a is string => typeof a === 'string') : null;
}

async function lese(res: Response, fallback: string): Promise<LeisteStand> {
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
    const body = (await res.json()) as { vorgabe?: unknown; eigene?: unknown };
    return { vorgabe: liste(body.vorgabe), eigene: liste(body.eigene) };
}

const JSON_KOPF = { 'Content-Type': 'application/json' };

export async function ladeLeiste(): Promise<LeisteStand> {
    return lese(await fetch('/api/schnellzugriff'), 'Die Schnellzugriffsleiste konnte nicht geladen werden.');
}

export async function speichereEigeneLeiste(eintraege: string[]): Promise<LeisteStand> {
    return lese(await fetch('/api/schnellzugriff/eigene', { method: 'PUT', headers: JSON_KOPF, body: JSON.stringify({ eintraege }) }),
        'Deine Leiste konnte nicht gespeichert werden.');
}

/** Zurück zur Vorgabe der Firma. */
export async function loescheEigeneLeiste(): Promise<LeisteStand> {
    return lese(await fetch('/api/schnellzugriff/eigene', { method: 'DELETE' }), 'Deine Leiste konnte nicht zurückgesetzt werden.');
}

/** Speichert die Vorgabe für alle (nur Admin). */
export async function speichereLeistenVorgabe(eintraege: string[]): Promise<LeisteStand> {
    return lese(await fetch('/api/settings/schnellzugriff/vorgabe', { method: 'PUT', headers: JSON_KOPF, body: JSON.stringify({ eintraege }) }),
        'Die Vorgabe konnte nicht gespeichert werden.');
}
