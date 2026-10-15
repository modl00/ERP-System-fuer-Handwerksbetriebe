/** Einstellungen der Plantafel (Arbeitswoche, Betriebsurlaub, Ampel). Wochentage sind ISO-Zahlen: 1 = Montag bis 7 = Sonntag. */
export interface BetriebsurlaubZeitraum { von: string; bis: string; bezeichnung: string }
export interface PlanungEinstellungen { arbeitstage: number[]; ampelSchwelleTage: number; betriebsurlaub: BetriebsurlaubZeitraum[] }

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

export async function ladePlanungEinstellungen(): Promise<PlanungEinstellungen> {
    return lese(await fetch('/api/plantafel/einstellungen'), 'Die Planungs-Einstellungen konnten nicht geladen werden.');
}

export async function speicherePlanungEinstellungen(e: PlanungEinstellungen): Promise<PlanungEinstellungen> {
    return lese(await fetch('/api/settings/planung', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(e) }),
        'Die Planungs-Einstellungen konnten nicht gespeichert werden.');
}
