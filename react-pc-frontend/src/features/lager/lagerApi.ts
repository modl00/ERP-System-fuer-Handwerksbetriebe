/** Lagerverwaltung: Lagerorte, Bestand, Buchungen und Mindestbestand. */

export interface Lagerort {
    id: number;
    name: string;
    beschreibung: string | null;
    aktiv: boolean;
    sortOrder: number;
    archiviert: boolean;
    archiviertAm: string | null;
}

export interface OrtBestand {
    lagerortId: number;
    lagerortName: string;
    bestand: number;
}

export interface LagerBestand {
    artikelId: number;
    bezeichnung: string;
    artikelnummer: string | null;
    /** "Stk", "m", "m²" oder "kg" – die Verrechnungseinheit des Artikels */
    einheit: string;
    bestand: number;
    mindestbestand: number | null;
    unterMindestbestand: boolean;
    /** Es wurde mehr entnommen als eingebucht (Zugang oder Inventur fehlt). */
    negativ: boolean;
    orte: OrtBestand[];
    kgProMeter?: number | null;
    handelseinheitBezeichnung?: string | null;
    handelseinheitMeter?: number | null;
    handelseinheitVarianten?: string | null;
}

export interface LagerZusammenfassung {
    anzahlArtikel: number;
    unterMindestbestand: number;
    negativ: number;
}

export type Buchungsart = 'ZUGANG' | 'ABGANG' | 'KORREKTUR';

export interface LagerBuchung {
    id: number;
    art: Buchungsart;
    /** Mit Vorzeichen: Zugang +, Abgang −, Korrektur die Differenz. */
    menge: number;
    lagerortId: number;
    lagerortName: string;
    gebuchtAm: string;
    projektId: number | null;
    projekt: string | null;
    notiz: string | null;
    benutzer: string | null;
}

export interface BuchungEingabe {
    art: Buchungsart;
    artikelId: number;
    lagerortId: number;
    menge: number;
    projektId: number | null;
    notiz: string | null;
}

async function anfrage<T>(url: string, init: RequestInit | undefined, fallback: string): Promise<T> {
    const res = await fetch(url, init);
    if (res.ok) return res.status === 204 ? (undefined as T) : (await res.json() as T);
    let text = fallback;
    try {
        const body = await res.json() as { message?: string };
        if (body?.message) text = body.message;
    } catch { /* kein JSON: Standardtext */ }
    throw new Error(text);
}

const json = { 'Content-Type': 'application/json' };

export const ladeOrte = () => anfrage<Lagerort[]>('/api/lager/orte', undefined, 'Die Lagerorte konnten nicht geladen werden.');

export const legeOrtAn = (name: string, beschreibung: string) =>
    anfrage<Lagerort>('/api/lager/orte', { method: 'POST', headers: json, body: JSON.stringify({ name, beschreibung }) }, 'Der Lagerort konnte nicht angelegt werden.');

export const aendereOrt = (id: number, name: string, beschreibung: string) =>
    anfrage<Lagerort>(`/api/lager/orte/${id}`, { method: 'PUT', headers: json, body: JSON.stringify({ name, beschreibung }) }, 'Der Lagerort konnte nicht gespeichert werden.');

export const setzeOrtAktiv = (id: number, aktiv: boolean) =>
    anfrage<Lagerort>(`/api/lager/orte/${id}/aktiv`, { method: 'PUT', headers: json, body: JSON.stringify({ aktiv }) }, 'Der Lagerort konnte nicht geändert werden.');

export const archiviereOrt = (id: number) =>
    anfrage<Lagerort>(`/api/lager/orte/${id}/archivieren`, { method: 'POST' }, 'Der Lagerort konnte nicht archiviert werden.');

export const stelleOrtWieder = (id: number) =>
    anfrage<Lagerort>(`/api/lager/orte/${id}/wiederherstellen`, { method: 'POST' }, 'Der Lagerort konnte nicht wiederhergestellt werden.');

export const loescheOrt = async (id: number): Promise<void> => {
    const res = await fetch(`/api/lager/orte/${id}`, { method: 'DELETE' });
    if (!res.ok) {
        const text = await res.text().catch(() => '');
        throw new Error(text || 'Der Lagerort konnte nicht gelöscht werden.');
    }
};

export const ladeBestand = (suche: string, nurHandlungsbedarf: boolean) => {
    const params = new URLSearchParams();
    if (suche.trim()) params.set('q', suche.trim());
    if (nurHandlungsbedarf) params.set('nurHandlungsbedarf', 'true');
    return anfrage<LagerBestand[]>(`/api/lager/bestand?${params.toString()}`, undefined, 'Der Bestand konnte nicht geladen werden.');
};

export const ladeZusammenfassung = () =>
    anfrage<LagerZusammenfassung>('/api/lager/zusammenfassung', undefined, 'Die Übersicht konnte nicht geladen werden.');

export const ladeVerlauf = (artikelId: number) =>
    anfrage<LagerBuchung[]>(`/api/lager/artikel/${encodeURIComponent(String(artikelId))}/buchungen`, undefined, 'Der Verlauf konnte nicht geladen werden.');

export const buche = (eingabe: BuchungEingabe) =>
    anfrage<LagerBestand>('/api/lager/buchungen', { method: 'POST', headers: json, body: JSON.stringify(eingabe) }, 'Die Buchung konnte nicht gespeichert werden.');

export const setzeMindestbestand = (artikelId: number, mindestbestand: number | null) =>
    anfrage<LagerBestand>(`/api/lager/artikel/${encodeURIComponent(String(artikelId))}/mindestbestand`,
        { method: 'PUT', headers: json, body: JSON.stringify({ mindestbestand }) }, 'Der Mindestbestand konnte nicht gespeichert werden.');

/** Die Einheit, in der der Bestand eines Artikels geführt wird: die Verrechnungseinheit des Artikels. */
export interface LagerEinheit {
    /** Kürzel wie im Bestand angezeigt: Stk, m, m², kg, l */
    kurz: string;
    /** Name zum Ausschreiben: Stück, Meter, Quadratmeter, Kilogramm, Liter */
    lang: string;
}

const EINHEITEN: Record<string, LagerEinheit> = {
    STUECK: { kurz: 'Stk', lang: 'Stück' },
    LAUFENDE_METER: { kurz: 'm', lang: 'Meter' },
    QUADRATMETER: { kurz: 'm²', lang: 'Quadratmeter' },
    KILOGRAMM: { kurz: 'kg', lang: 'Kilogramm' },
    LITER: { kurz: 'l', lang: 'Liter' },
};

/** Einheit aus der Verrechnungseinheit eines Artikels (Name oder Objekt). Ohne Angabe: Stück, wie im Backend. */
export function einheitAusArtikel(einheit?: string | { name: string } | null): LagerEinheit {
    const name = typeof einheit === 'string' ? einheit : einheit?.name;
    return (name && EINHEITEN[name]) || EINHEITEN.STUECK;
}

/** Einheit aus dem Kürzel, das das Backend liefert ("m", "l" ...). */
export function einheitAusKuerzel(kurz: string): LagerEinheit {
    return Object.values(EINHEITEN).find(e => e.kurz === kurz) ?? EINHEITEN.STUECK;
}

/** Menge mit Einheit, deutsch formatiert ("12,5 m"). */
export function mengeText(menge: number, einheit: string): string {
    return `${menge.toLocaleString('de-DE', { maximumFractionDigits: 3 })} ${einheit}`;
}

export interface UmbuchungEingabe {
    artikelId: number;
    vonLagerortId: number;
    nachLagerortId: number;
    menge: number;
    notiz: string | null;
}

/** Ware von einem Lagerort an einen anderen legen. Der Gesamtbestand bleibt gleich. */
export const umbuche = (eingabe: UmbuchungEingabe) =>
    anfrage<LagerBestand>('/api/lager/umbuchungen', { method: 'POST', headers: json, body: JSON.stringify(eingabe) }, 'Die Umbuchung konnte nicht gespeichert werden.');

/** Die Einheiten zur Auswahl, in der Reihenfolge der Anzeige. */
export const EINHEIT_AUSWAHL: { schluessel: string; einheit: LagerEinheit }[] =
    Object.entries(EINHEITEN).map(([schluessel, einheit]) => ({ schluessel, einheit }));

/** Der Schlüssel der Verrechnungseinheit zum Kürzel aus dem Bestand ("m" → LAUFENDE_METER). */
export function einheitSchluesselAusKuerzel(kurz: string): string {
    return EINHEIT_AUSWAHL.find(e => e.einheit.kurz === kurz)?.schluessel ?? 'STUECK';
}

/** Bezeichnung und Einheit eines Artikels ändern. Vorhandene Mengen bleiben, sie gelten dann in der neuen Einheit. */
export const aendereArtikel = (artikelId: number, bezeichnung: string, einheit: string) =>
    anfrage<LagerBestand>(`/api/lager/artikel/${encodeURIComponent(String(artikelId))}`,
        { method: 'PUT', headers: json, body: JSON.stringify({ bezeichnung, einheit }) }, 'Der Artikel konnte nicht gespeichert werden.');

/** Der Bestand eines einzelnen Artikels, auch wenn noch nichts gebucht wurde (dann 0). */
export const ladeBestandVon = (artikelId: number) =>
    anfrage<LagerBestand>(`/api/lager/artikel/${encodeURIComponent(String(artikelId))}/bestand`, undefined, 'Der Lagerbestand konnte nicht geladen werden.');
