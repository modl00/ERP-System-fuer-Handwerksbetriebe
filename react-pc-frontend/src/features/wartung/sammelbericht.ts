import type { WartungListeneintrag } from './types';

export const MAX_PROTOKOLLE = 200;

export interface AuswahlHinweise {
    anzahl: number;
    /** Noch nicht abgeschlossen: erscheinen im Bericht als Entwurf. */
    entwuerfe: number;
    /** Mit unbewerteten Prüfpunkten. */
    mitOffenenPunkten: number;
    mitMaengeln: number;
    zuViele: boolean;
}

/** Fasst zusammen, worauf man vor dem Erstellen hinweisen sollte. */
export function hinweiseZurAuswahl(eintraege: WartungListeneintrag[]): AuswahlHinweise {
    return {
        anzahl: eintraege.length,
        entwuerfe: eintraege.filter((e) => e.status === 'OFFEN' || e.status === 'IN_BEARBEITUNG').length,
        mitOffenenPunkten: eintraege.filter((e) => e.pruefpunkteBewertet < e.pruefpunkteGesamt).length,
        mitMaengeln: eintraege.filter((e) => e.maengel > 0).length,
        zuViele: eintraege.length > MAX_PROTOKOLLE,
    };
}
