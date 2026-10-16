/** Die lesbare Ablage: ein Ordnerbaum mit verständlichen Namen auf dieselben Dateien (nur für Administratoren). */

export interface LesbareAblageStatus {
    zielordner: string;
    /** Wird die lesbare Ablage jede Nacht automatisch aufgebaut? */
    automatisch: boolean;
}

export interface LesbareAblageErgebnis {
    probelauf: boolean;
    zielordner: string;
    dateien: number;
    neu: number;
    unveraendert: number;
    ersetzt: number;
    entfernt: number;
    /** Dateien, die kopiert werden mussten, weil der Speicher keine Verknüpfungen kann (belegen doppelten Platz) */
    kopiert: number;
    /** Dokumente, deren Datei nicht gefunden wurde */
    fehlend: number;
    beispiele: string[];
    fehler: string[];
}

async function antwort<T>(res: Response, fallback: string): Promise<T> {
    if (res.ok) return res.json() as Promise<T>;
    let text = fallback;
    try {
        const body = await res.json() as { message?: string };
        if (body?.message) text = body.message;
    } catch { /* kein JSON: Standardtext */ }
    throw new Error(text);
}

export async function ladeStatus(): Promise<LesbareAblageStatus> {
    return antwort(await fetch('/api/admin/ablage/lesbar'), 'Der Stand der lesbaren Ablage konnte nicht geladen werden.');
}

export async function baueAuf(probelauf: boolean): Promise<LesbareAblageErgebnis> {
    return antwort(await fetch(`/api/admin/ablage/lesbar/aufbauen?probelauf=${probelauf}`, { method: 'POST' }),
        probelauf ? 'Die Vorschau konnte nicht erstellt werden.' : 'Die lesbare Ablage konnte nicht aufgebaut werden.');
}

/** Abgleich mit einer Netzwerkfreigabe (SMB), in beide Richtungen (nur für Administratoren). Das Passwort kommt nie zurück. */
export interface SmbEinstellungen {
    aktiv: boolean;
    server: string;
    freigabe: string;
    unterordner: string;
    benutzer: string;
    domaene: string;
    passwortGesetzt: boolean;
    /** Liegt das Passwort verschlüsselt in der Datenbank? */
    verschluesselt: boolean;
    /** Automatischer Abgleich alle n Minuten, 0 = nur auf Knopfdruck */
    intervallMinuten: number;
    /** Zeitpunkt des letzten Abgleichs (ISO) oder leer */
    letzterLauf: string;
    /** Kurzergebnis oder Fehlertext des letzten Abgleichs */
    letztesErgebnis: string;
}

export interface SmbEingabe {
    aktiv: boolean;
    server: string;
    freigabe: string;
    unterordner: string;
    benutzer: string;
    domaene: string;
    /** Leer = das gespeicherte Passwort bleibt */
    passwort: string;
    passwortLoeschen: boolean;
    intervallMinuten: number;
}

export interface SmbAbgleichErgebnis {
    probelauf: boolean;
    hochgeladen: number;
    vomGeraetUebernommen: number;
    neuVomGeraet: number;
    konflikte: number;
    unveraendert: number;
    hinweise: string[];
    fehler: string[];
}

export async function ladeSmb(): Promise<SmbEinstellungen> {
    return antwort(await fetch('/api/admin/ablage/smb'), 'Die Einstellungen der Netzwerkfreigabe konnten nicht geladen werden.');
}

const json = { 'Content-Type': 'application/json' };

export async function speichereSmb(e: SmbEingabe): Promise<void> {
    await antwort(await fetch('/api/admin/ablage/smb', { method: 'PUT', headers: json, body: JSON.stringify(e) }), 'Das Speichern hat nicht geklappt.');
}

export async function testeSmb(e: SmbEingabe): Promise<{ success: boolean; message: string }> {
    return antwort(await fetch('/api/admin/ablage/smb/test', { method: 'POST', headers: json, body: JSON.stringify(e) }), 'Der Test konnte nicht ausgeführt werden.');
}

export async function gleicheAb(probelauf: boolean): Promise<SmbAbgleichErgebnis> {
    return antwort(await fetch(`/api/admin/ablage/smb/abgleichen?probelauf=${probelauf}`, { method: 'POST' }),
        probelauf ? 'Die Vorschau konnte nicht erstellt werden.' : 'Der Abgleich hat nicht geklappt.');
}

/** Anmelden und auswählen: erst die Freigaben des Geräts, dann die Ordner darin. Ein Fehler kommt als {@code success: false} mit Klartext. */
export async function ladeFreigaben(e: SmbEingabe): Promise<{ success: boolean; message?: string; freigaben: string[] }> {
    return antwort(await fetch('/api/admin/ablage/smb/freigaben', { method: 'POST', headers: json, body: JSON.stringify(e) }), 'Die Anmeldung am Gerät hat nicht geklappt.');
}

export async function ladeOrdner(e: SmbEingabe, freigabe: string, pfad: string): Promise<{ success: boolean; message?: string; ordner: string[] }> {
    const q = new URLSearchParams({ freigabe, pfad });
    return antwort(await fetch(`/api/admin/ablage/smb/ordner?${q.toString()}`, { method: 'POST', headers: json, body: JSON.stringify(e) }), 'Die Ordner konnten nicht geladen werden.');
}
