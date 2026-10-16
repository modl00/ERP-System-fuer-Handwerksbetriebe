/** Die Einstellungen einer Dokumentvorlage (Abstände in mm, Schriftgrößen in pt). Gleiche Felder wie im Backend (VorlagenEinstellungen). */
export interface VorlagenEinstellungen {
    schriftart: 'HELVETICA' | 'TIMES' | 'COURIER';
    schriftgroesse: number;
    /** Schrift in der Positionstabelle; fehlt bei älteren Vorlagen (dann 10 pt, wie bisher). */
    tabellenSchriftgroesse?: number;
    falzmarken: boolean;
    hauptblockOben: number;
    hauptblockLinks: number;
    hauptblockRechts: number;

    adresszeileAnzeigen: boolean;
    adresszeileText: string;
    adresszeileOben: number;
    adresszeileLinks: number;

    betreff1Anzeigen: boolean;
    betreff1Fett: boolean;
    betreff1Inhalt: 'BAUVORHABEN' | 'BETREFF';
    betreff1Schriftgroesse: number;
    betreff1Praefix: string;

    betreff2Anzeigen: boolean;
    betreff2Fett: boolean;
    betreff2Schriftgroesse: number;
    betreff2Text: string;

    infoblockAnzeigen: boolean;
    infoblockOben: number;
    infoblockLinks: number;
    infoblockBreite: number;
    infoDokumentnummer: boolean;
    infoProjektnummer: boolean;
    infoReferenzdokument: boolean;
    infoKundennummer: boolean;
    infoDatum: boolean;
    infoAnsprechpartner: boolean;
    infoTelefon: boolean;
    infoMobil: boolean;
    infoFax: boolean;
    infoEmail: boolean;

    freitextAnzeigen: boolean;
    freitextOben: number;
    freitextLinks: number;
    freitextBreite: number;
    freitextInhalt: string;

    fusszeileTyp: 'DOKUMENT_FIRMA_SEITE' | 'FIRMENDATEN' | 'KEINE';
    fusszeileAbstand: number;
    fusszeileSchriftgroesse: number;

    briefpapierAnzeigen: boolean;

    positionsBeschreibung: boolean;
    artikelnummerAnzeigen: boolean;
    einzelpreiseAusblenden: boolean;
    tabellenstil: 'GRAU' | 'SCHLICHT';
}

const META = /<meta\s+name="vorlage-einstellungen"\s+content="([^"]*)"/;

/** Liest die Einstellungen aus dem Vorlagen-HTML. Eine ältere Vorlage ohne Einstellungen (oder mit unlesbaren) ergibt {@code null}. */
export function leseVorlagenEinstellungen(html: string | null | undefined): VorlagenEinstellungen | null {
    const treffer = html ? META.exec(html) : null;
    if (!treffer) return null;
    try {
        const wert = JSON.parse(decodeURIComponent(treffer[1])) as unknown;
        return wert && typeof wert === 'object' ? (wert as VorlagenEinstellungen) : null;
    } catch {
        return null;
    }
}

/** Die Einstellungen als Meta-Tag für das Vorlagen-HTML (leer ohne Einstellungen). */
export function vorlagenEinstellungenMeta(einstellungen: VorlagenEinstellungen | null): string {
    return einstellungen ? `<meta name="vorlage-einstellungen" content="${encodeURIComponent(JSON.stringify(einstellungen))}" />` : '';
}
