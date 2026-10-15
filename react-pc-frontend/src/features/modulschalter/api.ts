export interface Features {
    wartung: boolean;
    /** Fehlt die Angabe (älterer Server), gilt das Lager als eingeschaltet. */
    lager?: boolean;
    /** Wie beim Lager: fehlt die Angabe, gilt das Modul als eingeschaltet. */
    miete?: boolean;
    website?: boolean;
}

async function lese(res: Response, fallback: string): Promise<Features> {
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
    return (await res.json()) as Features;
}

/** Welche Module sind eingeschaltet? Dürfen alle Angemeldeten lesen, damit Menü und Seiten sich danach richten. */
export async function ladeFeatures(): Promise<Features> {
    return lese(await fetch('/api/features'), 'Die Modul-Einstellungen konnten nicht geladen werden.');
}

/** Schaltet das Wartungsmodul ein oder aus (nur Admin). Es werden keine Daten gelöscht. */
export async function schalteWartungsmodul(aktiv: boolean): Promise<Features> {
    return lese(await fetch('/api/settings/features/wartung', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ aktiv }),
    }), 'Das Wartungsmodul konnte nicht umgeschaltet werden.');
}

/** Schaltet das Lagermodul ein oder aus (nur Admin). Es werden keine Daten gelöscht. */
export async function schalteLagermodul(aktiv: boolean): Promise<Features> {
    return lese(await fetch('/api/settings/features/lager', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ aktiv }),
    }), 'Das Lagermodul konnte nicht umgeschaltet werden.');
}

/** Schaltet eines der allgemeinen Module (zum Beispiel "miete" oder "website") ein oder aus (nur Admin). Es werden keine Daten gelöscht. */
export async function schalteModul(modul: 'miete' | 'website', aktiv: boolean): Promise<Features> {
    return lese(await fetch(`/api/settings/features/${encodeURIComponent(modul)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ aktiv }),
    }), 'Das Modul konnte nicht umgeschaltet werden.');
}
