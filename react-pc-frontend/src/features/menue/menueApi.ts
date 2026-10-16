export interface MenueStand {
    /** Vorgabe der Firma (ausgeblendete Adressen); {@code null}, solange der Admin keine gespeichert hat. */
    vorgabe: string[] | null;
    /** Eigene Auswahl des Benutzers; {@code null}, solange er nichts geändert hat. */
    eigene: string[] | null;
}

function liste(wert: unknown): string[] | null {
    return Array.isArray(wert) ? wert.filter((a): a is string => typeof a === 'string') : null;
}

async function lese(res: Response, fallback: string): Promise<MenueStand> {
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

export async function ladeMenue(): Promise<MenueStand> {
    return lese(await fetch('/api/menue'), 'Das Menü konnte nicht geladen werden.');
}

/** Speichert die ausgeblendeten Menüpunkte des angemeldeten Benutzers. */
export async function speichereEigene(ausgeblendet: string[]): Promise<MenueStand> {
    return lese(await fetch('/api/menue/eigene', { method: 'PUT', headers: JSON_KOPF, body: JSON.stringify({ ausgeblendet }) }),
        'Dein Menü konnte nicht gespeichert werden.');
}

/** Zurück zur Vorgabe der Firma. */
export async function loescheEigene(): Promise<MenueStand> {
    return lese(await fetch('/api/menue/eigene', { method: 'DELETE' }), 'Dein Menü konnte nicht zurückgesetzt werden.');
}

/** Speichert die Vorgabe für alle (nur Admin). */
export async function speichereVorgabe(ausgeblendet: string[]): Promise<MenueStand> {
    return lese(await fetch('/api/settings/menue/vorgabe', { method: 'PUT', headers: JSON_KOPF, body: JSON.stringify({ ausgeblendet }) }),
        'Die Vorgabe konnte nicht gespeichert werden.');
}
