/** "Wer darf was?" (Issue #87, Stufe 1): die Übersicht der Rechte aller aktiven Benutzer. */
export type Stufe = 'ALLES' | 'TEILWEISE' | 'NICHTS';

export interface Bereich {
    schluessel: string;
    name: string;
    gruppe: string;
    beschreibung: string;
}

export interface Feld {
    stufe: Stufe;
    text: string;
    grund: string;
}

export interface UebersichtBenutzer {
    profilId: number;
    anzeigename: string;
    benutzername: string;
    administrator: boolean;
    mitarbeiterId: number | null;
    mitarbeiterName: string | null;
    abteilungen: string[];
    felder: Record<string, Feld>;
}

export interface Warnung {
    art: string;
    text: string;
    ziel: string;
    profilId: number | null;
}

export interface RechteUebersicht {
    bereiche: Bereich[];
    benutzer: UebersichtBenutzer[];
    warnungen: Warnung[];
}

export async function ladeRechteUebersicht(): Promise<RechteUebersicht> {
    let antwort: Response;
    try {
        antwort = await fetch('/api/admin/rechte-uebersicht');
    } catch {
        throw new Error('Keine Verbindung zum Server. Bitte Netz prüfen und noch einmal versuchen.');
    }
    if (antwort.status === 403) throw new Error('Diese Übersicht ist nur für Administratoren.');
    if (!antwort.ok) throw new Error('Die Übersicht konnte nicht geladen werden.');
    return (await antwort.json()) as RechteUebersicht;
}

/** Bereiche nach ihrer Gruppe zusammengefasst, in der Reihenfolge des Servers (für die Kopfzeile der Tabelle). */
export function gruppiere(bereiche: Bereich[]): { gruppe: string; bereiche: Bereich[] }[] {
    const ergebnis: { gruppe: string; bereiche: Bereich[] }[] = [];
    for (const b of bereiche) {
        const letzte = ergebnis[ergebnis.length - 1];
        if (letzte && letzte.gruppe === b.gruppe) letzte.bereiche.push(b);
        else ergebnis.push({ gruppe: b.gruppe, bereiche: [b] });
    }
    return ergebnis;
}
