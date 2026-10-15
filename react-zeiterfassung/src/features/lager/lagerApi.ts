/**
 * Lager für die Handy-App (Issue #69, Stufe 2): Abfragen und Buchungen gegen /api/zeiterfassung/lager.
 *
 * Die App meldet sich über den Zugangslink des Mitarbeiters an. Abfragen tragen ihn im Pfad, Buchungen im Text der Anfrage. Die Rechte
 * prüft der Server bei jedem Aufruf; diese Datei übersetzt nur seine Antworten in Fehler, die die Seite anzeigen kann.
 */

export interface OrtBestand { lagerortId: number; lagerortName: string; bestand: number }

export interface LagerBestand {
    artikelId: number;
    bezeichnung: string;
    artikelnummer: string | null;
    einheit: string | null;
    bestand: number;
    mindestbestand: number | null;
    unterMindestbestand: boolean;
    negativ: boolean;
    orte: OrtBestand[];
}

export interface Lagerort { id: number; name: string; beschreibung: string | null }

export interface EanTreffer { gefunden: boolean; ean: string; artikel: LagerBestand | null }

export type Buchungsart = 'ABGANG' | 'ZUGANG' | 'KORREKTUR';

export interface BuchungsEingabe {
    art: Buchungsart;
    artikelId: number;
    lagerortId: number;
    menge: number;
    projektId?: number | null;
    notiz?: string;
}

export interface UmbuchungsEingabe { artikelId: number; vonLagerortId: number; nachLagerortId: number; menge: number; notiz?: string }

/** Fehler mit Satz für die Anzeige; {@code status} 0 heißt: keine Verbindung. */
export class LagerFehler extends Error {
    readonly status: number;
    constructor(message: string, status: number) {
        super(message);
        this.name = 'LagerFehler';
        this.status = status;
    }
}

const BASIS = '/api/zeiterfassung/lager';
const KEINE_VERBINDUNG = 'Keine Verbindung zum Programm. Lagerbuchungen brauchen Netz, bitte später noch einmal versuchen.';

async function antwort<T>(res: Response): Promise<T> {
    if (res.ok) return await res.json() as T;
    let text = '';
    try {
        const daten = await res.json();
        text = typeof daten?.message === 'string' ? daten.message : '';
    } catch {
        // keine lesbare Antwort
    }
    if (!text) {
        text = res.status === 401 ? 'Der Zugang ist nicht gültig. Bitte neu anmelden.'
            : res.status === 403 ? 'Dafür fehlt die Berechtigung.'
            : 'Das hat nicht geklappt. Bitte noch einmal versuchen.';
    }
    throw new LagerFehler(text, res.status);
}

async function aufruf<T>(url: string, init?: RequestInit): Promise<T> {
    let res: Response;
    try {
        res = await fetch(url, init);
    } catch {
        throw new LagerFehler(KEINE_VERBINDUNG, 0);
    }
    return antwort<T>(res);
}

const post = (body: unknown) => ({
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
});

export const ladeOrte = (token: string) => aufruf<Lagerort[]>(`${BASIS}/${encodeURIComponent(token)}/orte`);

export const sucheArtikel = (token: string, q: string) =>
    aufruf<LagerBestand[]>(`${BASIS}/${encodeURIComponent(token)}/suche?q=${encodeURIComponent(q)}`);

/** Die meistgebuchten Artikel (zuerst die eigenen), damit man sie ohne Suche findet. */
export const ladeHaeufige = (token: string) =>
    aufruf<LagerBestand[]>(`${BASIS}/${encodeURIComponent(token)}/haeufig`);

export const findeNachEan = (token: string, ean: string) =>
    aufruf<EanTreffer>(`${BASIS}/${encodeURIComponent(token)}/ean/${encodeURIComponent(ean.trim())}`);

export const buche = (token: string, e: BuchungsEingabe) =>
    aufruf<LagerBestand>(`${BASIS}/buchung`, post({ token, ...e }));

export const umbuche = (token: string, e: UmbuchungsEingabe) =>
    aufruf<LagerBestand>(`${BASIS}/umbuchung`, post({ token, ...e }));

export const ordneEanZu = (token: string, artikelId: number, ean: string) =>
    aufruf<LagerBestand>(`${BASIS}/ean-zuordnen`, post({ token, artikelId, ean }));

export interface ProjektKurz { id: number; name: string; projektNummer: string | null; kundenName: string | null }

/** Offene Projekte für die Auswahl bei einer Entnahme (höchstens 15, nach Suchbegriff). */
export async function sucheProjekte(q: string): Promise<ProjektKurz[]> {
    const liste = await aufruf<ProjektKurz[]>(`/api/zeiterfassung/projekte?limit=15&search=${encodeURIComponent(q)}`);
    return liste.map(p => ({ id: p.id, name: p.name, projektNummer: p.projektNummer ?? null, kundenName: p.kundenName ?? null }));
}

/** Menge im deutschen Format ohne überflüssige Nullen, zum Beispiel 12,5. */
export function formatMenge(menge: number): string {
    return new Intl.NumberFormat('de-DE', { maximumFractionDigits: 3 }).format(menge);
}
