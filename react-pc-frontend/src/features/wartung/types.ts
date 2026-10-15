/** Typen der Wartungs-API (`/api/wartung/**`). Spiegeln die DTOs des Backends. */

export type WartungStatus = 'OFFEN' | 'IN_BEARBEITUNG' | 'ABGESCHLOSSEN' | 'ARCHIVIERT';
export type WartungErgebnis = 'IO' | 'NIO' | 'NA';
export type WartungFeldtyp = 'TEXT' | 'JA_NEIN' | 'AUSWAHL' | 'JAHR';
export type WartungBereich = 'PRUEFPUNKTE' | 'DATENFELDER';
export type WartungModulKategorie = 'BASIS' | 'ZUSATZ';

export interface WartungModul {
    id: number;
    modulKey: string;
    name: string;
    beschreibung: string | null;
    icon: string | null;
    kategorie: WartungModulKategorie;
    systemModul: boolean;
    standardIntervallMonate: number;
    /** Nur Zusatzmodule: an welche Basis-Module es andocken darf. Leer = an jedes. */
    andockbarAnBasisModulIds: number[];
    untertypen: { schluessel: string; label: string }[];
    anzahlPruefpunkte: number;
}

/** Ein gewähltes Modul samt Bauart (Bauart nur, wenn das Modul welche kennt). */
export interface ModulAuswahl {
    modulId: number;
    untertypSchluessel: string | null;
}

export interface WartungTuer {
    id: number;
    objektId: number;
    bezeichnung: string;
    notizen: string | null;
    intervallMonate: number | null;
    naechsteFaelligkeit: string | null;
    aktiv: boolean;
    module: ModulAuswahl[];
    /** Projekt der Wartung, oder null. Neue Feldeinsätze erben es. */
    projektId: number | null;
    projektName: string | null;
}

export interface WartungObjekt {
    id: number;
    kundeId: number;
    kundeName: string;
    bezeichnung: string;
    strasse: string | null;
    plz: string | null;
    ort: string | null;
    ansprechpartnerName: string | null;
    ansprechpartnerTelefon: string | null;
    notizen: string | null;
    aktiv: boolean;
    tueren: WartungTuer[];
}

export interface WartungObjektEingabe {
    bezeichnung: string;
    strasse: string;
    plz: string;
    ort: string;
    ansprechpartnerName: string;
    ansprechpartnerTelefon: string;
    notizen: string;
}

export interface WartungTuerEingabe {
    bezeichnung: string;
    notizen: string;
    intervallMonate: number | null;
    module: ModulAuswahl[];
}

export interface WartungListeneintrag {
    id: number;
    nummer: string;
    titel: string | null;
    protokollTyp: string;
    datum: string;
    naechsteWartung: string | null;
    status: WartungStatus;
    kundeId: number;
    kundeName: string;
    objektId: number | null;
    objektBezeichnung: string | null;
    tuerId: number | null;
    tuerBezeichnung: string | null;
    pruefpunkteGesamt: number;
    pruefpunkteBewertet: number;
    maengel: number;
}

export interface Seite<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export interface Bezug {
    id: number;
    name: string;
}

export interface Unterschrift {
    bild: string | null;
    name: string | null;
}

export interface ProtokollAbschnitt {
    id: number;
    bereich: WartungBereich;
    label: string;
    sortierung: number;
}

export interface ProtokollPruefpunkt {
    id: number;
    abschnittId: number | null;
    modulKey: string | null;
    label: string;
    ergebnis: WartungErgebnis | null;
    bemerkung: string | null;
    vorherigesErgebnis: WartungErgebnis | null;
    sortierung: number;
}

/** Neues Datenfeld in einem laufenden Feldeinsatz. {@code modulKey} null = "Allgemein". */
export interface DatenfeldNeuEingabe {
    modulKey: string | null;
    label: string;
    feldtyp: WartungFeldtyp;
    /** Nur bei einer Auswahl, mindestens zwei. */
    optionen: string[];
}

export interface ProtokollDatenfeld {
    id: number;
    abschnittId: number | null;
    modulKey: string | null;
    label: string;
    feldtyp: WartungFeldtyp;
    optionen: string[];
    wert: string | null;
    sortierung: number;
}

export interface ProtokollStatistik {
    gesamt: number;
    bewertet: number;
    inOrdnung: number;
    nichtInOrdnung: number;
    nichtAnwendbar: number;
}

export interface ProtokollBericht {
    dokumentId: number;
    url: string;
    erstelltAm: string | null;
}

export interface WartungProtokoll {
    id: number;
    version: number;
    nummer: string;
    kunde: Bezug;
    objekt: Bezug | null;
    tuer: Bezug | null;
    projekt: Bezug | null;
    techniker: Bezug | null;
    titel: string | null;
    protokollTyp: string;
    datum: string;
    intervallMonate: number;
    naechsteWartung: string | null;
    status: WartungStatus;
    bearbeitbar: boolean;
    moeglicheStatus: WartungStatus[];
    bemerkungen: string | null;
    ansprechpartnerName: string | null;
    unterschriftTechniker: Unterschrift;
    unterschriftKunde: Unterschrift;
    abgeschlossenAm: string | null;
    /** Aus welcher früheren Prüfung dieser Feldeinsatz entstand. */
    vorlageProtokollId: number | null;
    /** Der im Projekt abgelegte PDF-Bericht. */
    bericht: ProtokollBericht | null;
    angelegtAm: string;
    geaendertAm: string;
    module: { modulId: number; modulKey: string; name: string; untertypSchluessel: string | null; untertypLabel: string | null }[];
    abschnitte: ProtokollAbschnitt[];
    pruefpunkte: ProtokollPruefpunkt[];
    datenfelder: ProtokollDatenfeld[];
    statistik: ProtokollStatistik;
}

/** Antwort auf eine einzelne Änderung (ein Prüfpunkt oder ein Datenfeld). */
export interface InhaltAenderung {
    status: WartungStatus;
    statistik: ProtokollStatistik;
    pruefpunkt: ProtokollPruefpunkt | null;
    datenfeld: ProtokollDatenfeld | null;
}

export interface AnlegenEingabe {
    kundeId: number;
    objektId: number | null;
    tuerId: number | null;
    technikerId: number | null;
    titel: string;
    datum: string;
    intervallMonate: number | null;
    /** Leer erlaubt, wenn eine Vorlage gewählt ist: Dann kommen die Module aus der früheren Prüfung. */
    module: ModulAuswahl[];
    bemerkungen: string;
    projektId?: number | null;
    /** Eine frühere, abgeschlossene Prüfung derselben Wartung als Vorlage. Sie bleibt unverändert. */
    vorlageProtokollId?: number | null;
    /** Bei einer Vorlage: Ergebnisse, Bemerkungen und Werte übernehmen (Standard: ja). */
    werteUebernehmen?: boolean;
}

export interface KopfEingabe {
    titel: string;
    protokollTyp: string;
    datum: string;
    intervallMonate: number;
    technikerId: number | null;
    projektId: number | null;
    /** Objekt des Feldeinsatzes; bei einer Wartung ergibt es sich aus ihr. */
    objektId: number | null;
    bemerkungen: string;
    ansprechpartnerName: string;
}

export interface ListenFilter {
    kundeId?: number;
    objektId?: number;
    tuerId?: number;
    status?: WartungStatus;
    mitArchiv?: boolean;
    datumVon?: string;
    datumBis?: string;
    suche?: string;
    seite?: number;
    groesse?: number;
}

// ── Modul-Editor (Einstellungen) ────────────────────────────────────────────


/** Eintrag der Modulliste im Editor, mit Zählern. */
export interface ModulUebersicht {
    id: number;
    modulKey: string;
    name: string;
    beschreibung: string | null;
    icon: string | null;
    kategorie: WartungModulKategorie;
    systemModul: boolean;
    aktiv: boolean;
    standardIntervallMonate: number;
    anzahlUntertypen: number;
    anzahlPruefpunkte: number;
    anzahlDatenfelder: number;
    anzahlTueren: number;
    anzahlProtokolle: number;
}

export interface ModulBauart {
    /** Nur im Browser: stabiler Schlüssel je Zeile, der Server ignoriert ihn. */
    clientKey?: string;
    id: number | null;
    schluessel: string | null;
    label: string;
}

export interface ModulPruefpunkt {
    clientKey?: string;
    id: number | null;
    label: string;
    nurBeiUntertyp: string | null;
}

export interface ModulDatenfeld {
    clientKey?: string;
    id: number | null;
    label: string;
    feldtyp: WartungFeldtyp;
    optionen: string[];
}

export interface ModulAbschnitt {
    clientKey?: string;
    id: number | null;
    bereich: WartungBereich;
    label: string;
    pruefpunkte: ModulPruefpunkt[];
    datenfelder: ModulDatenfeld[];
}

/** Ein Modul mit seinem ganzen Inhalt, so liest und speichert es der Editor. */
export interface ModulDetail {
    id: number | null;
    version: number | null;
    modulKey: string | null;
    name: string;
    beschreibung: string | null;
    icon: string | null;
    kategorie: WartungModulKategorie;
    systemModul: boolean;
    aktiv: boolean;
    standardIntervallMonate: number;
    andockbarAn: number[];
    untertypen: ModulBauart[];
    abschnitte: ModulAbschnitt[];
    anzahlTueren: number;
    anzahlProtokolle: number;
}

export interface GlobalesDatenfeld {
    id: number | null;
    label: string;
    feldtyp: WartungFeldtyp;
    optionen: string[];
}

export interface SammelberichtEingabe {
    protokollIds: number[];
    titel: string;
    mitDeckblatt: boolean;
}

// ── Wartung (Anlage) mit Historie, Projekt-Bereich, Bericht, Unterschrift ──

export interface AnlagePruefung {
    id: number;
    /** Laufende Nummer ab 1, älteste zuerst ("Prüfung 01"). */
    pruefung: number;
    nummer: string;
    datum: string;
    status: WartungStatus;
    technikerName: string | null;
    pruefpunkteGesamt: number;
    pruefpunkteBewertet: number;
    maengel: number;
    vorlageProtokollId: number | null;
    berichtDokumentId: number | null;
    berichtUrl: string | null;
}

export interface WartungAnlage {
    id: number;
    bezeichnung: string;
    notizen: string | null;
    intervallMonate: number | null;
    naechsteFaelligkeit: string | null;
    aktiv: boolean;
    kunde: Bezug;
    objekt: { id: number; bezeichnung: string; strasse: string | null; plz: string | null; ort: string | null };
    projekt: Bezug | null;
    module: { modulId: number; name: string; bauart: string | null; bauartSchluessel: string | null }[];
    offenerFeldeinsatzId: number | null;
    offenerFeldeinsatzNummer: string | null;
    letztePruefungId: number | null;
    historie: AnlagePruefung[];
}

export interface ProjektWartung {
    id: number;
    objektId: number;
    bezeichnung: string;
    objektBezeichnung: string;
    kundeName: string;
    module: string;
    naechsteFaelligkeit: string | null;
    letztePruefungDatum: string | null;
    anzahlPruefungen: number;
    offenerFeldeinsatzId: number | null;
    offenerFeldeinsatzNummer: string | null;
    berichte: { protokollId: number; nummer: string; datum: string; url: string; dateiname: string }[];
}

export interface WartungVerlaufEintrag {
    zeit: string;
    benutzer: string | null;
    vonStatus: WartungStatus;
    nachStatus: WartungStatus;
    grund: string | null;
}

export interface WartungBericht {
    dokumentId: number;
    dateiname: string;
    url: string;
    erstelltAm: string | null;
}

export interface GespeicherteUnterschrift {
    bild: string;
    name: string | null;
    geaendertAm: string;
}

/** Der Wartungsvertrag eines Projekts. */
export interface WartungVertrag {
    id: number;
    projektId: number;
    vertragsnummer: string | null;
    beginn: string;
    ende: string | null;
    intervallMonate: number;
    preisNetto: number | null;
    notiz: string | null;
    status: 'NOCH_NICHT_GESTARTET' | 'LAEUFT' | 'ABGELAUFEN';
    endetBald: boolean;
}

export interface WartungVertragEingabe {
    vertragsnummer: string | null;
    beginn: string;
    ende: string | null;
    intervallMonate: number;
    preisNetto: number | null;
    notiz: string | null;
}
