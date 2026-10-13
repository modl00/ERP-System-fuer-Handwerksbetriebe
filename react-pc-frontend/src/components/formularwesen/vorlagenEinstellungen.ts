import type { FormBlock } from '../../types';

/**
 * Einstellungen rund um das firmenweite Briefpapier im Formular-Editor.
 *
 * Wenn `briefpapierEnthaeltKopf` aktiv ist, enthält das Briefpapier bereits
 * Logo und Firmendaten. Der Formular-Editor blendet dann das Logo-Block-Feld
 * in der Vorschau optisch aus (der Block bleibt in der Vorlage erhalten –
 * der PDF-Dienst ignoriert ihn, wenn kein Logo-Byte vorhanden ist).
 */

export interface FirmaBriefpapierInfo {
    /** API-Dateiname des hinterlegten Briefpapiers (null = keins). */
    briefpapierDateiname: string | null;
    /** Wenn true, enthält das Briefpapier bereits Kopf (Logo, Firmendaten). */
    briefpapierEnthaeltKopf: boolean;
}

/**
 * Gibt zurück, ob das firmenweite Briefpapier für die Vorschau/den Editor
 * relevant ist – d. h. ob es vorhanden und dem Editor bekannt ist.
 */
export function hatFirmenBriefpapier(info: FirmaBriefpapierInfo): boolean {
    return !!info.briefpapierDateiname;
}

/**
 * Filtert Logo-Blöcke aus der Block-Liste, wenn das firmenweite Briefpapier
 * auf der aktuellen Seite aktiv ist (kein vorlagenspezifisches Briefpapier)
 * und es bereits einen Kopfbereich enthält.
 *
 * Der Block bleibt in der gespeicherten Vorlage – er wird nur aus der
 * Vorschau/dem Render herausgefiltert, solange beide Bedingungen zutreffen.
 *
 * @param firmenBriefpapierAktiv true, wenn keine Vorlage-eigenes Briefpapier
 *   vorhanden ist und das firmenweite stattdessen verwendet wird.
 */
export function blöckeOhneLogoWennKopfVorhanden(
    items: FormBlock[],
    info: FirmaBriefpapierInfo,
    firmenBriefpapierAktiv: boolean,
): FormBlock[] {
    if (!firmenBriefpapierAktiv || !hatFirmenBriefpapier(info) || !info.briefpapierEnthaeltKopf) return items;
    return items.filter(i => i.type !== 'logo');
}
