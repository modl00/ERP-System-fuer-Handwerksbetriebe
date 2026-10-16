/** Material "kommt per Rechnung" auflösen (Issue #99, Stufe 1): Plan und Rechnung des Projektmaterials. */
export interface RechnungZeile {
    id: number;
    artikelInProjektId: number | null;
    bezeichnung: string | null;
    betrag: number;
    beschreibung: string | null;
}

export interface MaterialRechnung {
    dokumentId: number;
    dokumentNummer: string | null;
    dokumentDatum: string | null;
    lieferantId: number | null;
    lieferantName: string | null;
    /** Was das Projekt von der Rechnung trägt (netto). */
    anteil: number;
    zugeordnet: number;
    rest: number;
    zeilen: RechnungZeile[];
}

export interface MaterialPosition {
    artikelInProjektId: number;
    artikelId: number;
    artikelnummer: string | null;
    bezeichnung: string;
    menge: number;
    einheit: string;
    lieferantId: number | null;
    lieferantName: string | null;
    bestellt: boolean;
    geplant: number | null;
    ist: number | null;
    abweichungProzent: number | null;
    abweichung: 'TEURER' | 'GUENSTIGER' | null;
    rechnungen: string[];
}

export interface MaterialAbrechnung {
    schwelleProzent: number;
    rechnungen: MaterialRechnung[];
    positionen: MaterialPosition[];
    offenAnzahl: number;
    offenGeplant: number;
}

export interface ZeileEingabe {
    artikelInProjektId: number | null;
    betrag: number;
    beschreibung: string | null;
}

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
        throw new Error(text || 'Das hat nicht geklappt. Bitte noch einmal versuchen.');
    }
    return (await antwort.json()) as T;
}

export const ladeMaterialAbrechnung = (projektId: number) => anfrage<MaterialAbrechnung>(`/api/projekte/${projektId}/material-abrechnung`);

export const speichereZuordnung = (projektId: number, dokumentId: number, zeilen: ZeileEingabe[]) =>
    anfrage<MaterialAbrechnung>(`/api/projekte/${projektId}/material-abrechnung/${dokumentId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ zeilen }),
    });

export const setzeSchwelle = (prozent: number) =>
    anfrage<{ prozent: number }>('/api/admin/material-abrechnung/schwelle', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prozent }),
    });

export const euro = (betrag: number | null) => (betrag === null ? '–' : betrag.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' }));

export function mengeText(menge: number): string {
    return menge.toLocaleString('de-DE', { maximumFractionDigits: 3 });
}

/** Abweichung von Plan und Ist in Prozent und Richtung, wie der Server sie bei der Schwelle bewertet (für die Vorschau im Dialog). */
export function abweichung(geplant: number | null, ist: number, schwelle: number): { prozent: number; art: 'TEURER' | 'GUENSTIGER' } | null {
    if (geplant === null || geplant <= 0) return null;
    const prozent = Math.round(((ist - geplant) * 1000) / geplant) / 10;
    if (Math.abs(prozent) <= schwelle) return null;
    return { prozent, art: prozent > 0 ? 'TEURER' : 'GUENSTIGER' };
}
