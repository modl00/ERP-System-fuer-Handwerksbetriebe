/** Vorschau der Übernahme von Wartungsprotokollen aus HandwerkPro (schreibt nichts). */
export type Aktion = 'ZUGEORDNET' | 'LEER' | 'NEU_IN_LISTE' | 'FREITEXT' | 'UNGUELTIG';

export interface Zusammenfassung {
    wartungen: number;
    abgeschlossen: number;
    offen: number;
    modulErgaenzt: number;
    modulUnklar: number;
    kundenNeu: number;
    objekteNeu: number;
    tuerenNeu: number;
    pruefpunkteGesamt: number;
    pruefpunkteZugeordnet: number;
    pruefpunkteEigene: number;
    werteNeuInListe: number;
    werteUngueltig: number;
}

export interface KundeZeile { altId: number; name: string; erpId: number | null; erpName: string | null; vorschlag: 'ZUORDNEN' | 'NEU'; wartungen: number }

export interface ProtokollZeile {
    altId: number;
    altNummer: string;
    titel: string;
    status: string;
    protokollart: string | null;
    module: string[];
    modulQuelle: 'PROTOKOLL' | 'TITEL' | 'PRUEFPUNKTE' | 'TITEL_UND_PRUEFPUNKTE' | 'UNKLAR';
    ergaenzteModule: string[];
    bauart: string | null;
    pruefpunkte: number;
    pruefpunkteZugeordnet: number;
    pruefpunkteEigene: number;
    fragen: string[];
}

export interface WertZeile { feld: string; altWert: string; neuerWert: string; anzahl: number; aktion: Aktion }

export interface EigenerPunkt { label: string; anzahl: number }

export interface Vorschau {
    zusammenfassung: Zusammenfassung;
    kunden: KundeZeile[];
    protokolle: ProtokollZeile[];
    werte: WertZeile[];
    eigenePruefpunkte: EigenerPunkt[];
    hinweise: string[];
}

export interface Ergebnisbericht {
    protokolleNeu: number;
    protokolleSchonDa: number;
    kundenNeu: number;
    objekteNeu: number;
    tuerenNeu: number;
    pruefpunkteEigene: number;
    werteVerworfen: number;
    katalogErgaenzt: number;
    uebersprungen: string[];
    hinweise: string[];
}

function leseJson(dateiText: string): unknown {
    try {
        return JSON.parse(dateiText);
    } catch {
        throw new Error('Die Datei ist keine gültige JSON-Datei. Bitte die Exportdatei von HandwerkPro wählen.');
    }
}

async function sende<T>(pfad: string, dateiText: string, fehlerText: string): Promise<T> {
    const json = leseJson(dateiText);
    let antwort: Response;
    try {
        antwort = await fetch(pfad, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(json) });
    } catch {
        throw new Error('Keine Verbindung zum Server. Bitte Netz prüfen und noch einmal versuchen.');
    }
    if (!antwort.ok) {
        let text = '';
        try {
            const d = (await antwort.json()) as { message?: string };
            text = typeof d?.message === 'string' ? d.message : '';
        } catch {
            text = '';
        }
        throw new Error(text || (antwort.status === 403 ? 'Das ist nur für Administratoren.' : fehlerText));
    }
    return (await antwort.json()) as T;
}

/** Übernimmt die Protokolle wirklich (eine Transaktion; wiederholbar, nichts wird doppelt angelegt). */
export function uebernehme(dateiText: string, neueWerteInKatalog: boolean): Promise<Ergebnisbericht> {
    return sende<Ergebnisbericht>(`/api/admin/wartung-import/ausfuehren?neueWerteInKatalog=${neueWerteInKatalog}`, dateiText, 'Die Übernahme ist fehlgeschlagen. Es wurde nichts übernommen.');
}

/** Schickt die Exportdatei (Text) an den Server und liefert die Vorschau; liest nur, schreibt nichts. */
export async function erstelleVorschau(dateiText: string): Promise<Vorschau> {
    return sende<Vorschau>('/api/admin/wartung-import/vorschau', dateiText, 'Die Vorschau konnte nicht erstellt werden.');
}

export const AKTION_TEXT: Record<Aktion, string> = {
    ZUGEORDNET: 'zugeordnet',
    LEER: 'keine Angabe',
    NEU_IN_LISTE: 'neu in die Liste',
    FREITEXT: 'bleibt Text',
    UNGUELTIG: 'ungültig, bleibt leer',
};

export const QUELLE_TEXT: Record<ProtokollZeile['modulQuelle'], string> = {
    PROTOKOLL: 'aus dem Protokoll',
    TITEL: 'aus dem Titel',
    PRUEFPUNKTE: 'aus den Prüfpunkten',
    TITEL_UND_PRUEFPUNKTE: 'aus Titel und Prüfpunkten',
    UNKLAR: 'unklar',
};
