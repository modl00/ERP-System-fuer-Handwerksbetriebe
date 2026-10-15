/** Maschinenliste (Stufe 2 von #166): Maschinen mit Kostenrechner, Zuordnung der Arbeitsgänge und Verlauf des Satzes. */

export interface Aufstellung {
    abschreibung: number | null;
    zins: number | null;
    wartung: number | null;
    platz: number | null;
    versicherung: number | null;
    energie: number | null;
    /** Der errechnete Satz je Stunde; {@code null}, wenn Angaben fehlen (dann sagt {@code hinweis} welche). */
    summe: number | null;
    hinweis: string | null;
}

export interface ArbeitsgangKurz { id: number; beschreibung: string }

export interface Maschine {
    id: number;
    name: string;
    anschaffungswert: number | null;
    restwert: number | null;
    nutzungsdauerJahre: number | null;
    zinssatzProzent: number | null;
    wartungJeJahr: number | null;
    platzJeJahr: number | null;
    versicherungJeJahr: number | null;
    leistungKw: number | null;
    strompreisJeKwh: number | null;
    auslastungProzent: number | null;
    laufstundenJeJahr: number | null;
    satzVonHand: number | null;
    kostensatz: number;
    vonHand: boolean;
    aktiv: boolean;
    bemerkung: string | null;
    aufstellung: Aufstellung;
    arbeitsgaenge: ArbeitsgangKurz[];
}

export interface MaschineEingabe {
    name: string;
    anschaffungswert: number | null;
    restwert: number | null;
    nutzungsdauerJahre: number | null;
    zinssatzProzent: number | null;
    wartungJeJahr: number | null;
    platzJeJahr: number | null;
    versicherungJeJahr: number | null;
    leistungKw: number | null;
    strompreisJeKwh: number | null;
    auslastungProzent: number | null;
    laufstundenJeJahr: number | null;
    satzVonHand: number | null;
    aktiv: boolean;
    bemerkung: string | null;
    arbeitsgangIds: number[];
}

/** Die Angaben für die Vorschau des Rechners (ohne Name und Zuordnung). */
export type RechnerEingabe = Pick<MaschineEingabe, 'anschaffungswert' | 'restwert' | 'nutzungsdauerJahre' | 'zinssatzProzent' | 'wartungJeJahr' | 'platzJeJahr'
    | 'versicherungJeJahr' | 'leistungKw' | 'strompreisJeKwh' | 'auslastungProzent' | 'laufstundenJeJahr'>;

export interface Verlaufseintrag { satz: number; vonHand: boolean; gueltigAb: string; geaendertVon: string | null; geaendertAm: string }

export interface ArbeitsgangAuswahl { id: number; beschreibung: string; maschineId: number | null; maschinenBezeichnung: string | null }

async function fehlertext(res: Response, fallback: string): Promise<string> {
    try {
        const body = await res.json() as { message?: string; error?: string; errors?: { defaultMessage?: string }[] };
        if (body?.message) return body.message;
        if (body?.error && body.error !== 'Bad Request') return body.error;
        const feld = body?.errors?.find(e => e.defaultMessage)?.defaultMessage;
        if (feld) return feld;
    } catch { /* kein JSON */ }
    return fallback;
}

const JSON_KOPF = { 'Content-Type': 'application/json' };

export async function ladeMaschinen(): Promise<Maschine[]> {
    const res = await fetch('/api/maschinen');
    if (!res.ok) throw new Error(await fehlertext(res, 'Die Maschinen konnten nicht geladen werden.'));
    return res.json() as Promise<Maschine[]>;
}

export async function speichereMaschine(id: number | null, eingabe: MaschineEingabe): Promise<Maschine> {
    const res = await fetch(id == null ? '/api/maschinen' : `/api/maschinen/${id}`, { method: id == null ? 'POST' : 'PUT', headers: JSON_KOPF, body: JSON.stringify(eingabe) });
    if (!res.ok) throw new Error(await fehlertext(res, 'Die Maschine konnte nicht gespeichert werden.'));
    return res.json() as Promise<Maschine>;
}

export async function loescheMaschine(id: number): Promise<void> {
    const res = await fetch(`/api/maschinen/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(await fehlertext(res, 'Die Maschine konnte nicht gelöscht werden.'));
}

export async function ladeVerlauf(id: number): Promise<Verlaufseintrag[]> {
    const res = await fetch(`/api/maschinen/${id}/verlauf`);
    if (!res.ok) throw new Error(await fehlertext(res, 'Der Verlauf konnte nicht geladen werden.'));
    return res.json() as Promise<Verlaufseintrag[]>;
}

export async function rechneMaschine(eingabe: RechnerEingabe, signal?: AbortSignal): Promise<Aufstellung> {
    const res = await fetch('/api/maschinen/rechner', { method: 'POST', headers: JSON_KOPF, body: JSON.stringify(eingabe), signal });
    if (!res.ok) throw new Error(await fehlertext(res, 'Der Satz konnte nicht berechnet werden.'));
    return res.json() as Promise<Aufstellung>;
}

export async function ladeArbeitsgaenge(): Promise<ArbeitsgangAuswahl[]> {
    const res = await fetch('/api/arbeitsgaenge');
    if (!res.ok) throw new Error(await fehlertext(res, 'Die Arbeitsgänge konnten nicht geladen werden.'));
    const liste = await res.json() as { id: number; beschreibung: string; maschineId?: number | null; maschinenBezeichnung?: string | null }[];
    return liste.map(a => ({ id: a.id, beschreibung: a.beschreibung, maschineId: a.maschineId ?? null, maschinenBezeichnung: a.maschinenBezeichnung ?? null }))
        .sort((x, y) => x.beschreibung.localeCompare(y.beschreibung, 'de'));
}
