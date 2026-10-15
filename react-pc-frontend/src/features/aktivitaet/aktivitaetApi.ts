/** Aktivitätsprotokoll (Issue #93): "Mein Tagesverlauf" und "Aktivitäten im System". Es enthält nie Inhalte, nur Art, Objekt und Uhrzeit. */
export type Art = 'ANGELEGT' | 'GEAENDERT' | 'GELOESCHT' | 'VERSENDET' | 'GEBUCHT' | 'ZUGEORDNET' | 'ABGESCHLOSSEN';

export interface Ereignis {
    id: number;
    zeitpunkt: string;
    art: Art;
    text: string;
    objektTyp: string | null;
    objektId: number | null;
    objektName: string | null;
    benutzer: string | null;
}

export interface MeinTag {
    datum: string;
    aktiv: boolean;
    /** 0 = unbegrenzt */
    aufbewahrungTage: number;
    ereignisse: Ereignis[];
}

export interface Einstellungen {
    mitarbeiterAktiv: boolean;
    aufbewahrungTage: number;
    ereignisseGesamt: number;
}

export interface SystemFilter {
    objektTyp?: string;
    art?: string;
    tage?: number;
    limit?: number;
}

export const ARTEN: { wert: Art; text: string }[] = [
    { wert: 'ANGELEGT', text: 'Angelegt' },
    { wert: 'GEAENDERT', text: 'Geändert' },
    { wert: 'GELOESCHT', text: 'Gelöscht' },
    { wert: 'VERSENDET', text: 'Versendet' },
    { wert: 'GEBUCHT', text: 'Gebucht' },
    { wert: 'ZUGEORDNET', text: 'Zugeordnet' },
    { wert: 'ABGESCHLOSSEN', text: 'Abgeschlossen' },
];

export const OBJEKT_TYPEN = ['Projekt', 'Kunde', 'Lieferant', 'Artikel', 'Anfrage', 'Dokument', 'Bestellung', 'Beleg', 'Mitarbeiter'];

async function anfrage<T>(url: string, init?: RequestInit): Promise<T> {
    let antwort: Response;
    try {
        antwort = await fetch(url, init);
    } catch {
        throw new Error('Keine Verbindung zum Server. Bitte Netz prüfen und noch einmal versuchen.');
    }
    if (!antwort.ok) {
        let text = '';
        try {
            const daten = (await antwort.json()) as { message?: string };
            text = typeof daten?.message === 'string' ? daten.message : '';
        } catch {
            text = '';
        }
        throw new Error(text || (antwort.status === 403 ? 'Das ist nur für Administratoren.' : 'Das hat nicht geklappt. Bitte noch einmal versuchen.'));
    }
    return (await antwort.json()) as T;
}

const JSON_KOPF = { 'Content-Type': 'application/json' };

export const ladeMeinenTag = (datum: string) => anfrage<MeinTag>(`/api/aktivitaet/mein?datum=${encodeURIComponent(datum)}`);

export function ladeImSystem(filter: SystemFilter): Promise<Ereignis[]> {
    const p = new URLSearchParams();
    if (filter.objektTyp) p.set('objektTyp', filter.objektTyp);
    if (filter.art) p.set('art', filter.art);
    if (filter.tage) p.set('tage', String(filter.tage));
    if (filter.limit) p.set('limit', String(filter.limit));
    const q = p.toString();
    return anfrage<Ereignis[]>(q ? `/api/admin/aktivitaet?${q}` : '/api/admin/aktivitaet');
}

export const ladeEinstellungen = () => anfrage<Einstellungen>('/api/admin/aktivitaet/einstellungen');

export const speichereEinstellungen = (e: { mitarbeiterAktiv?: boolean; aufbewahrungTage?: number }) =>
    anfrage<Einstellungen>('/api/admin/aktivitaet/einstellungen', { method: 'PUT', headers: JSON_KOPF, body: JSON.stringify(e) });

export const auskunft = (profilId: number) => anfrage<Ereignis[]>(`/api/admin/aktivitaet/person/${profilId}`);

export const loescheVon = (profilId: number) => anfrage<{ geloescht: number }>(`/api/admin/aktivitaet/person/${profilId}`, { method: 'DELETE' });

/** Der Satz für die Anzeige: Handlung, wenn bekannt mit dem Namen des Objekts ("Projekt geändert: 2026/014 Anbau Halle"). */
export function ereignisText(e: Pick<Ereignis, 'text' | 'objektName' | 'objektId' | 'objektTyp'>): string {
    if (e.objektName) return `${e.text}: ${e.objektName}`;
    if (e.objektId !== null && e.objektTyp) return `${e.text} (${e.objektTyp} ${e.objektId})`;
    return e.text;
}

export function uhrzeit(iso: string): string {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
}

export function datumUndUhrzeit(iso: string): string {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/** Heute als JJJJ-MM-TT in der Ortszeit (nicht UTC). */
export function heute(): string {
    return tagVerschieben('', 0, new Date());
}

export function tagVerschieben(datum: string, tage: number, basis?: Date): string {
    const d = basis ?? new Date(`${datum}T12:00:00`);
    const kopie = new Date(d.getFullYear(), d.getMonth(), d.getDate() + tage, 12);
    const m = String(kopie.getMonth() + 1).padStart(2, '0');
    const t = String(kopie.getDate()).padStart(2, '0');
    return `${kopie.getFullYear()}-${m}-${t}`;
}

export function aufbewahrungText(tage: number): string {
    return tage === 0 ? 'unbegrenzt' : tage === 1 ? '1 Tag' : `${tage} Tage`;
}
