/** Die Angaben für den Vorschlag eines Maschinenstundensatzes; alles pro Jahr, außer Anschaffung (einmalig) und Nutzungsdauer (Jahre). */
export interface MaschinenRechnung {
    anschaffung: number;
    nutzungsdauerJahre: number;
    auslastungStundenJahr: number;
    wartungJahr: number;
    energieJahr: number;
    raumJahr: number;
}

/**
 * Kosten je Maschinenstunde: (Anschaffung ÷ Nutzungsdauer + Wartung + Energie + Raum) ÷ Stunden, die die Maschine im Jahr wirklich läuft. Auf Cent gerundet;
 * {@code null}, wenn Nutzungsdauer oder Auslastung nicht größer als 0 sind (dann lässt sich nicht rechnen).
 */
export function maschinenstundensatz(r: MaschinenRechnung): number | null {
    if (!(r.nutzungsdauerJahre > 0) || !(r.auslastungStundenJahr > 0)) return null;
    const jahreskosten = r.anschaffung / r.nutzungsdauerJahre + r.wartungJahr + r.energieJahr + r.raumJahr;
    return Math.round((jahreskosten / r.auslastungStundenJahr) * 100) / 100;
}
