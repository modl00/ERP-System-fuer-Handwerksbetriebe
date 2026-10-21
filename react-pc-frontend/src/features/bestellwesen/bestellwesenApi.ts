/** Bestellungen an Lieferanten (Bestellwesen Stufe 1, Issue #86). */
export type BestellStatus = 'ENTWURF' | 'BESTELLT' | 'STORNIERT';
export type Lieferstand = 'ENTWURF' | 'BESTELLT' | 'TEILWEISE' | 'GELIEFERT' | 'STORNIERT';
export type Herkunft = 'LAGER' | 'PROJEKT' | 'FREI';

export interface BestellPosition {
    id: number;
    artikelId: number | null;
    artikelnummer: string | null;
    bezeichnung: string;
    menge: number;
    einheit: string;
    einzelpreis: number | null;
    summe: number | null;
    herkunft: Herkunft;
    projektId: number | null;
    projektName: string | null;
    artikelInProjektId: number | null;
    kommentar: string | null;
    geliefertAm: string | null;
    anhaenge: import('../../types').Fertigungsunterlage[];
}

export interface Bestellung {
    id: number;
    bestellnummer: string | null;
    status: BestellStatus;
    lieferstand: Lieferstand;
    lieferantId: number;
    lieferantName: string;
    lieferantEmails: string[];
    notiz: string | null;
    erstelltAm: string;
    erstelltVon: string | null;
    bestelltAm: string | null;
    bestelltVon: string | null;
    bestelltPer: 'EMAIL' | 'ANDERS' | null;
    storniertAm: string | null;
    stornoGrund: string | null;
    summe: number;
    anzahlPositionen: number;
    anzahlGeliefert: number;
    tageSeitBestellung: number | null;
    ueberfaellig: boolean;
    ueberfaelligNachTagen: number;
    projekte: string[];
    positionen: BestellPosition[];
}

export interface Vorschlag {
    artikelId: number;
    artikelnummer: string | null;
    bezeichnung: string;
    einheit: string;
    menge: number;
    herkunft: Herkunft;
    projektId: number | null;
    projektName: string | null;
    artikelInProjektId: number | null;
    lieferantId: number | null;
    lieferantName: string | null;
    einzelpreis: number | null;
    kommentar: string | null;
    hinweis: string | null;
}

/** Eine Zeile, wie sie der Server zum Speichern erwartet (Bezeichnung und Nummer kommen dort aus dem Artikelstamm). */
export interface PositionEingabe {
    artikelId: number;
    menge: number;
    einheit: string | null;
    einzelpreis: number | null;
    herkunft: Herkunft;
    projektId: number | null;
    artikelInProjektId: number | null;
    kommentar: string | null;
}

export interface BestellungEingabe {
    lieferantId: number;
    notiz: string | null;
    positionen: PositionEingabe[];
}

export interface Filter {
    stand?: string;
    lieferantId?: number;
    projektId?: number;
    nurUeberfaellig?: boolean;
}

/** Fehler mit dem Satz, den der Server mitschickt (oder einem verständlichen Ersatz). */
export class BestellFehler extends Error {
    readonly status: number;

    constructor(message: string, status: number) {
        super(message);
        this.name = 'BestellFehler';
        this.status = status;
    }
}

const BASIS = '/api/lieferanten-bestellungen';

async function anfrage<T>(url: string, init?: RequestInit): Promise<T> {
    let antwort: Response;
    try {
        antwort = await fetch(url, init);
    } catch {
        throw new BestellFehler('Keine Verbindung zum Server. Bitte Netz prüfen und noch einmal versuchen.', 0);
    }
    if (!antwort.ok) {
        let text = '';
        try {
            const daten = (await antwort.json()) as { message?: string };
            text = typeof daten?.message === 'string' ? daten.message : '';
        } catch {
            text = '';
        }
        throw new BestellFehler(
            text || (antwort.status === 403 ? 'Dafür fehlt die Berechtigung (Rechteverwaltung: Bestellungen).' : 'Das hat nicht geklappt. Bitte noch einmal versuchen.'),
            antwort.status,
        );
    }
    if (antwort.status === 204) return undefined as T;
    return (await antwort.json()) as T;
}

const JSON_KOPF = { 'Content-Type': 'application/json' };
const post = <T>(url: string, body?: unknown) =>
    anfrage<T>(url, { method: 'POST', headers: JSON_KOPF, body: body === undefined ? undefined : JSON.stringify(body) });

export function ladeBestellungen(filter: Filter = {}): Promise<Bestellung[]> {
    const p = new URLSearchParams();
    if (filter.stand) p.set('stand', filter.stand);
    if (filter.lieferantId) p.set('lieferantId', String(filter.lieferantId));
    if (filter.projektId) p.set('projektId', String(filter.projektId));
    if (filter.nurUeberfaellig) p.set('nurUeberfaellig', 'true');
    const q = p.toString();
    return anfrage<Bestellung[]>(q ? `${BASIS}?${q}` : BASIS);
}

export const ladeBestellung = (id: number) => anfrage<Bestellung>(`${BASIS}/${id}`);
export const legeAn = (e: BestellungEingabe) => post<Bestellung>(BASIS, e);
export const aendere = (id: number, e: BestellungEingabe) => anfrage<Bestellung>(`${BASIS}/${id}`, { method: 'PUT', headers: JSON_KOPF, body: JSON.stringify(e) });
export const loesche = (id: number) => anfrage<void>(`${BASIS}/${id}`, { method: 'DELETE' });
export const bestelle = (id: number, per: 'EMAIL' | 'ANDERS') => post<Bestellung>(`${BASIS}/${id}/bestellen`, { per });
export const liefere = (id: number, positionId: number, geliefert: boolean) => post<Bestellung>(`${BASIS}/${id}/positionen/${positionId}/geliefert`, { geliefert });
export const alleGeliefert = (id: number) => post<Bestellung>(`${BASIS}/${id}/alles-geliefert`);
export const storniere = (id: number, grund: string) => post<Bestellung>(`${BASIS}/${id}/stornieren`, { grund });
export const vorschlaegeMindestbestand = () => anfrage<Vorschlag[]>(`${BASIS}/vorschlaege/mindestbestand`);
export const vorschlaegeProjekt = (projektId: number) => anfrage<Vorschlag[]>(`${BASIS}/vorschlaege/projekt/${projektId}`);
export const pdfAdresse = (id: number) => `${BASIS}/${id}/pdf`;

export interface LieferantKurz {
    id: number;
    name: string;
    ort: string | null;
}

/** Lieferanten suchen (für die Auswahl). */
export async function sucheLieferanten(q: string): Promise<LieferantKurz[]> {
    const p = new URLSearchParams({ size: '20' });
    if (q.trim()) p.set('q', q.trim());
    const daten = await anfrage<{ lieferanten?: { id: number; lieferantenname: string; ort?: string | null }[] }>(`/api/lieferanten?${p.toString()}`);
    return (daten.lieferanten ?? []).map((l) => ({ id: l.id, name: l.lieferantenname, ort: l.ort ?? null }));
}

export const LIEFERSTAND_TEXT: Record<Lieferstand, string> = {
    ENTWURF: 'Entwurf',
    BESTELLT: 'Bestellt',
    TEILWEISE: 'Teilweise geliefert',
    GELIEFERT: 'Geliefert',
    STORNIERT: 'Storniert',
};

/** Aus einer Zeile der Bestellung wieder eine Eingabe machen (für das Speichern des Entwurfs). */
export function alsEingabe(p: BestellPosition): PositionEingabe {
    return {
        artikelId: p.artikelId as number,
        menge: p.menge,
        einheit: p.einheit,
        einzelpreis: p.einzelpreis,
        herkunft: p.herkunft,
        projektId: p.projektId,
        artikelInProjektId: p.artikelInProjektId,
        kommentar: p.kommentar,
    };
}

/** Menge deutsch formatiert, ohne unnötige Nachkommastellen ("12,5"). */
export function mengeText(menge: number): string {
    return menge.toLocaleString('de-DE', { maximumFractionDigits: 3 });
}

export function geld(betrag: number | null): string {
    return betrag === null ? '–' : betrag.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });
}

export function datum(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

// ---- Fertigungsunterlagen am Artikelstamm ----

import type { Fertigungsunterlage } from '../../types';

export function ladeFertigungsunterlagenFuerArtikel(artikelId: number): Promise<Fertigungsunterlage[]> {
    return anfrage<Fertigungsunterlage[]>(`/api/artikel/${artikelId}/fertigungsunterlagen`);
}

export async function ladeFertigungsunterlageHoch(artikelId: number, datei: File): Promise<Fertigungsunterlage> {
    const form = new FormData();
    form.append('datei', datei);
    let antwort: Response;
    try {
        antwort = await fetch(`/api/artikel/${artikelId}/fertigungsunterlagen`, { method: 'POST', body: form });
    } catch {
        throw new BestellFehler('Keine Verbindung zum Server.', 0);
    }
    if (!antwort.ok) {
        let msg = '';
        try { msg = ((await antwort.json()) as { message?: string }).message ?? ''; } catch { /* ignore */ }
        throw new BestellFehler(msg || 'Die Datei konnte nicht hochgeladen werden.', antwort.status);
    }
    return (await antwort.json()) as Fertigungsunterlage;
}

export function loescheFertigungsunterlage(id: number): Promise<void> {
    return anfrage<void>(`/api/artikel/fertigungsunterlagen/${id}`, { method: 'DELETE' });
}

// ---- Anhänge an Bestellpositionen ----

export function ladeAnhaengeVorschlaege(bestellungId: number, positionId: number): Promise<Fertigungsunterlage[]> {
    return anfrage<Fertigungsunterlage[]>(`${BASIS}/${bestellungId}/positionen/${positionId}/anhaenge/vorschlaege`);
}

export async function ladeAnhangHoch(bestellungId: number, positionId: number, datei: File): Promise<Fertigungsunterlage> {
    const form = new FormData();
    form.append('datei', datei);
    let antwort: Response;
    try {
        antwort = await fetch(`${BASIS}/${bestellungId}/positionen/${positionId}/anhaenge`, { method: 'POST', body: form });
    } catch {
        throw new BestellFehler('Keine Verbindung zum Server.', 0);
    }
    if (!antwort.ok) {
        let msg = '';
        try { msg = ((await antwort.json()) as { message?: string }).message ?? ''; } catch { /* ignore */ }
        throw new BestellFehler(msg || 'Die Datei konnte nicht hochgeladen werden.', antwort.status);
    }
    return (await antwort.json()) as Fertigungsunterlage;
}

export function uebernehmeVomArtikel(bestellungId: number, positionId: number, fertigungsunterlageId: number): Promise<Fertigungsunterlage> {
    return post<Fertigungsunterlage>(`${BASIS}/${bestellungId}/positionen/${positionId}/anhaenge/vom-artikel/${fertigungsunterlageId}`);
}

export function loescheAnhang(id: number): Promise<void> {
    return anfrage<void>(`${BASIS}/position-anhaenge/${id}`, { method: 'DELETE' });
}
