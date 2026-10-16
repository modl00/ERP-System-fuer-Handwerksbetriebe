export interface StartStand {
    /** Vorgabe der Firma (Kennungen der Bausteine in Reihenfolge); {@code null}, solange der Admin keine gespeichert hat. */
    vorgabe: string[] | null;
    /** Eigener Aufbau des Benutzers; {@code null}, solange er nichts geändert hat. */
    eigene: string[] | null;
}

function liste(wert: unknown): string[] | null {
    return Array.isArray(wert) ? wert.filter((a): a is string => typeof a === 'string') : null;
}

async function lese(res: Response, fallback: string): Promise<StartStand> {
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

export async function ladeStart(): Promise<StartStand> {
    return lese(await fetch('/api/startbildschirm'), 'Der Startbildschirm konnte nicht geladen werden.');
}

export async function speichereEigenenStart(eintraege: string[]): Promise<StartStand> {
    return lese(await fetch('/api/startbildschirm/eigene', { method: 'PUT', headers: JSON_KOPF, body: JSON.stringify({ eintraege }) }),
        'Dein Startbildschirm konnte nicht gespeichert werden.');
}

/** Zurück zur Vorgabe der Firma. */
export async function loescheEigenenStart(): Promise<StartStand> {
    return lese(await fetch('/api/startbildschirm/eigene', { method: 'DELETE' }), 'Dein Startbildschirm konnte nicht zurückgesetzt werden.');
}

/** Speichert die Vorgabe für alle (nur Admin). */
export async function speichereStartVorgabe(eintraege: string[]): Promise<StartStand> {
    return lese(await fetch('/api/settings/startbildschirm/vorgabe', { method: 'PUT', headers: JSON_KOPF, body: JSON.stringify({ eintraege }) }),
        'Die Vorgabe konnte nicht gespeichert werden.');
}
