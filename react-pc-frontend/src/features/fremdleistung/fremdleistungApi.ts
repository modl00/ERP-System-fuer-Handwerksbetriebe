/** Fremdleistungen: die Preisliste der Veredler und Fremdbetriebe (Verzinken, Pulverbeschichten, Fräsen …) mit Preisvergleich. */

export type FremdEinheit = 'KG' | 'QUADRATMETER' | 'METER' | 'STUECK';
export type TransportArt = 'KEINE' | 'PAUSCHAL' | 'JE_KG' | 'JE_FAHRT' | 'JE_KM';

export const EINHEITEN: { wert: FremdEinheit; kurz: string; text: string }[] = [
    { wert: 'KG', kurz: 'kg', text: 'je Kilogramm' },
    { wert: 'QUADRATMETER', kurz: 'm²', text: 'je Quadratmeter' },
    { wert: 'METER', kurz: 'm', text: 'je Meter' },
    { wert: 'STUECK', kurz: 'Stk', text: 'je Stück' },
];

export const TRANSPORTARTEN: { wert: TransportArt; text: string }[] = [
    { wert: 'KEINE', text: 'Kein Transport' },
    { wert: 'PAUSCHAL', text: 'Pauschal je Auftrag' },
    { wert: 'JE_KG', text: 'Je Kilogramm' },
    { wert: 'JE_FAHRT', text: 'Je Fahrt' },
    { wert: 'JE_KM', text: 'Je Kilometer (hin und zurück)' },
];

export const einheitKurz = (e: string) => EINHEITEN.find(x => x.wert === e)?.kurz ?? e;

export interface Fremdleistung {
    id: number;
    lieferantId: number;
    lieferantName: string;
    bezeichnung: string;
    einheit: FremdEinheit;
    preisJeEinheit: number | null;
    mindestpreis: number | null;
    einmalBetrag: number | null;
    anteilBetrag: number | null;
    anteilChargenmenge: number | null;
    prozentZuschlag: number | null;
    transportArt: TransportArt;
    transportBetrag: number | null;
    transportKm: number | null;
    transportFahrten: number;
    sonstigeBezeichnung: string | null;
    sonstigeBetrag: number | null;
    gueltigAb: string;
    aktiv: boolean;
    verwendet: number;
}

export interface FremdleistungEingabe {
    lieferantId: number;
    bezeichnung: string;
    einheit: FremdEinheit;
    preisJeEinheit: number | null;
    mindestpreis: number | null;
    einmalBetrag: number | null;
    anteilBetrag: number | null;
    anteilChargenmenge: number | null;
    prozentZuschlag: number | null;
    transportArt: TransportArt;
    transportBetrag: number | null;
    transportKm: number | null;
    transportFahrten: number;
    sonstigeBezeichnung: string | null;
    sonstigeBetrag: number | null;
    gueltigAb: string;
    aktiv: boolean;
}

export interface VergleichKostenart { art: string; text: string; betrag: number }
export interface Vergleich {
    fremdleistungId: number;
    lieferantId: number;
    lieferantName: string;
    bezeichnung: string;
    einheit: FremdEinheit;
    summe: number;
    summeJeEinheit: number;
    gewichtFehlt: boolean;
    guenstigster: boolean;
    kostenarten: VergleichKostenart[];
}

async function fehlertext(res: Response, fallback: string): Promise<string> {
    try {
        const body = await res.json() as { message?: string };
        if (body?.message) return body.message;
    } catch { /* kein JSON */ }
    return fallback;
}

const JSON_KOPF = { 'Content-Type': 'application/json' };

export async function ladeFremdleistungen(): Promise<Fremdleistung[]> {
    const res = await fetch('/api/fremdleistungen');
    if (!res.ok) throw new Error(await fehlertext(res, 'Die Fremdleistungen konnten nicht geladen werden.'));
    return res.json() as Promise<Fremdleistung[]>;
}

export async function speichereFremdleistung(id: number | null, eingabe: FremdleistungEingabe): Promise<Fremdleistung> {
    const res = await fetch(id == null ? '/api/fremdleistungen' : `/api/fremdleistungen/${id}`, { method: id == null ? 'POST' : 'PUT', headers: JSON_KOPF, body: JSON.stringify(eingabe) });
    if (!res.ok) throw new Error(await fehlertext(res, 'Die Fremdleistung konnte nicht gespeichert werden.'));
    return res.json() as Promise<Fremdleistung>;
}

export async function loescheFremdleistung(id: number): Promise<void> {
    const res = await fetch(`/api/fremdleistungen/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(await fehlertext(res, 'Die Fremdleistung konnte nicht gelöscht werden.'));
}

/** Dieselbe Fremdleistung bei mehreren Veredlern für eine Menge; der günstigste steht vorn. */
export async function vergleicheFremdleistung(bezeichnung: string, menge: number, gewichtKg?: number | null): Promise<Vergleich[]> {
    const q = new URLSearchParams({ bezeichnung, menge: String(menge) });
    if (gewichtKg != null) q.set('gewichtKg', String(gewichtKg));
    const res = await fetch(`/api/fremdleistungen/vergleich?${q}`);
    if (!res.ok) throw new Error(await fehlertext(res, 'Der Vergleich konnte nicht berechnet werden.'));
    return res.json() as Promise<Vergleich[]>;
}
