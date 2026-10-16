/** Die früheren Demo-Daten wieder entfernen (/api/demo, nur Administratoren). Das Laden gibt es nicht mehr. */
export interface DemoEntfernt { mitarbeiter: number; kunden: number; lieferanten: number; leistungen: number; auftraege: number; termine: number }

async function lese<T>(res: Response, fallback: string): Promise<T> {
    if (!res.ok) {
        let meldung = fallback;
        try {
            const body = await res.json() as { message?: string };
            if (body?.message) meldung = body.message;
        } catch { /* keine Meldung vom Server */ }
        throw new Error(meldung);
    }
    return res.json() as Promise<T>;
}

/** Gibt es noch Demo-Daten, die sich entfernen lassen? */
export async function ladeDemoStatus(): Promise<boolean> {
    const antwort = await lese<{ vorhanden: boolean }>(await fetch('/api/demo/status'), 'Der Stand der Demo-Daten konnte nicht geladen werden.');
    return antwort.vorhanden;
}

export async function entferneDemoDaten(): Promise<DemoEntfernt> {
    return lese<DemoEntfernt>(await fetch('/api/demo/daten', { method: 'DELETE' }), 'Die Demo-Daten konnten nicht entfernt werden.');
}
