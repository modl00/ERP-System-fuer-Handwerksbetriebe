/**
 * Schalter für Bereiche, die fachlich noch nicht fertig sind.
 * Aktuell sind alle Bereiche freigegeben; die Funktion bleibt als Erweiterungspunkt.
 */

/** Knopf „E-Mail” je Lieferant (Bestellanfrage per E-Mail). */
export const BESTELLANFRAGE_PER_EMAIL_AKTIV = true;

/** Pfad zur Bestellungs-Detailseite. */
export const BESTELLBEDARF_PFAD = '/bestellungen/:id';

/** Bedarfsliste im Projekt (Lager / Bestellen-Workflow). */
export const BESTELLBEDARF_AKTIV = true;

/** Ist die Seite unter diesem Pfad für die Nutzer freigegeben? */
export function istFreigegeben(pfad: string): boolean {
    void pfad;
    return true;
}
