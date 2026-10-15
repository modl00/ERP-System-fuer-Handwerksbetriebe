package org.example.kalkulationsprogramm.dto.Leistung;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Die Kalkulation einer Leistung mit allen Zahlen, wie sie die Oberfläche anzeigt.
 *
 * @param kalkuliert        folgt der gespeicherte Preis der Kalkulation?
 * @param losgroesse        auf wie viele Einheiten der Leistung sich die Rüstzeit der Arbeits-Zeilen verteilt
 * @param preisBerechnet    das Ergebnis der Kalkulation mit den heutigen Artikelpreisen und Stundensätzen
 * @param preisGespeichert  der Preis, der an der Leistung steht (und den Dokumente übernehmen)
 * @param preisVeraltet     gespeicherter und berechneter Preis weichen ab (zum Beispiel weil ein Artikel teurer wurde)
 * @param vollstaendig      alle Zeilen haben einen Preis; sonst ist {@code preisBerechnet} zu niedrig
 */
public record LeistungKalkulationDto(
        Long leistungId,
        boolean kalkuliert,
        BigDecimal zuschlagProzent,
        BigDecimal losgroesse,
        List<Position> positionen,
        BigDecimal materialSumme,
        BigDecimal arbeitSumme,
        BigDecimal zwischensumme,
        BigDecimal zuschlag,
        BigDecimal preisBerechnet,
        BigDecimal preisGespeichert,
        boolean preisVeraltet,
        boolean vollstaendig,
        BigDecimal maschineSumme,
        BigDecimal fremdleistungSumme,
        BigDecimal gewichtKg,
        BigDecimal oberflaecheM2,
        boolean gewichtVollstaendig) {
    /** Abwärtskompatibel: ohne Gewicht und Oberfläche. */
    public LeistungKalkulationDto(Long leistungId, boolean kalkuliert, BigDecimal zuschlagProzent, BigDecimal losgroesse, List<Position> positionen,
                                  BigDecimal materialSumme, BigDecimal arbeitSumme, BigDecimal zwischensumme, BigDecimal zuschlag,
                                  BigDecimal preisBerechnet, BigDecimal preisGespeichert, boolean preisVeraltet, boolean vollstaendig, BigDecimal maschineSumme,
                                  BigDecimal fremdleistungSumme) {
        this(leistungId, kalkuliert, zuschlagProzent, losgroesse, positionen, materialSumme, arbeitSumme, zwischensumme, zuschlag, preisBerechnet,
                preisGespeichert, preisVeraltet, vollstaendig, maschineSumme, fremdleistungSumme, null, null, true);
    }

    /** Abwärtskompatibel: ohne Fremdleistung. */
    public LeistungKalkulationDto(Long leistungId, boolean kalkuliert, BigDecimal zuschlagProzent, BigDecimal losgroesse, List<Position> positionen,
                                  BigDecimal materialSumme, BigDecimal arbeitSumme, BigDecimal zwischensumme, BigDecimal zuschlag,
                                  BigDecimal preisBerechnet, BigDecimal preisGespeichert, boolean preisVeraltet, boolean vollstaendig, BigDecimal maschineSumme) {
        this(leistungId, kalkuliert, zuschlagProzent, losgroesse, positionen, materialSumme, arbeitSumme, zwischensumme, zuschlag, preisBerechnet,
                preisGespeichert, preisVeraltet, vollstaendig, maschineSumme, BigDecimal.ZERO.setScale(2));
    }

    /** Abwärtskompatibel: ohne Maschinenkosten. */
    public LeistungKalkulationDto(Long leistungId, boolean kalkuliert, BigDecimal zuschlagProzent, BigDecimal losgroesse, List<Position> positionen,
                                  BigDecimal materialSumme, BigDecimal arbeitSumme, BigDecimal zwischensumme, BigDecimal zuschlag,
                                  BigDecimal preisBerechnet, BigDecimal preisGespeichert, boolean preisVeraltet, boolean vollstaendig) {
        this(leistungId, kalkuliert, zuschlagProzent, losgroesse, positionen, materialSumme, arbeitSumme, zwischensumme, zuschlag, preisBerechnet,
                preisGespeichert, preisVeraltet, vollstaendig, BigDecimal.ZERO.setScale(2));
    }


    /** Abwärtskompatibel: ohne Losgröße (1). */
    public LeistungKalkulationDto(Long leistungId, boolean kalkuliert, BigDecimal zuschlagProzent, List<Position> positionen,
                                  BigDecimal materialSumme, BigDecimal arbeitSumme, BigDecimal zwischensumme, BigDecimal zuschlag,
                                  BigDecimal preisBerechnet, BigDecimal preisGespeichert, boolean preisVeraltet, boolean vollstaendig) {
        this(leistungId, kalkuliert, zuschlagProzent, BigDecimal.ONE, positionen, materialSumme, arbeitSumme, zwischensumme, zuschlag,
                preisBerechnet, preisGespeichert, preisVeraltet, vollstaendig);
    }

    /**
     * Eine Zeile der Kalkulation.
     *
     * @param fremdleistungId nur bei {@code FREMDLEISTUNG}: das gewählte Angebot des Veredlers
     * @param veredler  nur bei {@code FREMDLEISTUNG}: Name des Veredlers (Lieferanten)
     * @param kostenarten nur bei {@code FREMDLEISTUNG}: die Aufstellung (Einheitspreis, Mindestpreis, einmalig, anteilig, Transport, sonstige, Prozent), Summe = {@code summe}
     * @param gewichtKgJeEinheit nur bei {@code FREMDLEISTUNG}: Gewicht in kg je Einheit der Leistung (für Transport je kg)
     * @param typ       {@code ARTIKEL}, {@code ARBEIT}, {@code BAUGRUPPE} oder {@code FREMDLEISTUNG} (bei Baugruppe ist {@code einzelpreis} der Preis einer Einheit der Baugruppe, ohne deren Zuschlag)
     * @param maschinenSatz nur bei Arbeit mit Maschine: Kosten der Maschine je Stunde
     * @param maschinenSumme nur bei Arbeit mit Maschine: Maschinenkosten dieser Zeile (in {@code summe} enthalten: Lohn plus Maschine)
     * @param maschinenanteilProzent nur bei Arbeit mit Maschine: Maschinenzeit in Prozent der Arbeitszeit (100 = läuft die ganze Zeit)
     * @param verschnittProzent nur bei Material: Zuschlag auf die Menge für Verschnitt, leer = keiner
     * @param mengeMitVerschnitt nur bei Material mit Verschnitt: die Menge einschließlich Verschnitt (mit der der Preis gerechnet wird)
     * @param baugruppeId die benutzte Baugruppe (nur bei {@code BAUGRUPPE})
     * @param anzahlVorgaenge nur bei Arbeit mit Zeiten: wie oft der Vorgang je Einheit vorkommt (die Bearbeitungszeit gilt je Vorgang), sonst leer
     * @param menge     bei Arbeit die Stunden je Einheit der Leistung (bei Zeiten daraus errechnet)
     * @param ruestzeitMinuten        nur bei Arbeit mit Zeiten: Rüstzeit in Minuten, einmal je Los
     * @param bearbeitungszeitMinuten nur bei Arbeit mit Zeiten: Bearbeitungszeit in Minuten je Einheit
     * @param bezeichnung Name des Artikels bzw. des Arbeitsgangs
     * @param einheit   Einheit der Menge ("lfm", "Stk", "Std.")
     * @param einzelpreis Preis je Einheit, {@code null} wenn nicht ermittelbar
     * @param summe     Menge mal Einzelpreis (0, wenn kein Preis)
     * @param hinweis   {@code OK} oder was fehlt: {@code KEIN_PREIS}, {@code KEIN_GEWICHT}, {@code KEIN_AUFSCHLAG}, {@code KEIN_STUNDENSATZ}
     */
    public record Position(
            Long id,
            String typ,
            Long artikelId,
            Long arbeitsgangId,
            String bezeichnung,
            BigDecimal menge,
            String einheit,
            BigDecimal einzelpreis,
            BigDecimal summe,
            String hinweis,
            String notiz,
            BigDecimal ruestzeitMinuten,
            BigDecimal bearbeitungszeitMinuten,
            Long baugruppeId,
            BigDecimal anzahlVorgaenge,
            BigDecimal verschnittProzent,
            BigDecimal mengeMitVerschnitt,
            BigDecimal maschinenSatz,
            BigDecimal maschinenSumme,
            BigDecimal maschinenanteilProzent,
            Long fremdleistungId,
            String veredler,
            List<FremdKostenart> kostenarten,
            BigDecimal gewichtKgJeEinheit,
            BigDecimal gewichtKg,
            BigDecimal oberflaecheM2,
            String mengeAus,
            Map<String, BigDecimal> variablen,
            List<org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld> formelFelder) {
        /** Abwärtskompatibel: ohne die Eingabefelder der Zuschnitt-Formel eines Artikels. */
        public Position(Long id, String typ, Long artikelId, Long arbeitsgangId, String bezeichnung, BigDecimal menge, String einheit,
                        BigDecimal einzelpreis, BigDecimal summe, String hinweis, String notiz, BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten,
                        Long baugruppeId, BigDecimal anzahlVorgaenge, BigDecimal verschnittProzent, BigDecimal mengeMitVerschnitt,
                        BigDecimal maschinenSatz, BigDecimal maschinenSumme, BigDecimal maschinenanteilProzent,
                        Long fremdleistungId, String veredler, List<FremdKostenart> kostenarten, BigDecimal gewichtKgJeEinheit,
                        BigDecimal gewichtKg, BigDecimal oberflaecheM2, String mengeAus, Map<String, BigDecimal> variablen) {
            this(id, typ, artikelId, arbeitsgangId, bezeichnung, menge, einheit, einzelpreis, summe, hinweis, notiz, ruestzeitMinuten, bearbeitungszeitMinuten,
                    baugruppeId, anzahlVorgaenge, verschnittProzent, mengeMitVerschnitt, maschinenSatz, maschinenSumme, maschinenanteilProzent,
                    fremdleistungId, veredler, kostenarten, gewichtKgJeEinheit, gewichtKg, oberflaecheM2, mengeAus, variablen, null);
        }

        /** Abwärtskompatibel: ohne Werte der Formel-Eingabefelder. */
        public Position(Long id, String typ, Long artikelId, Long arbeitsgangId, String bezeichnung, BigDecimal menge, String einheit,
                        BigDecimal einzelpreis, BigDecimal summe, String hinweis, String notiz, BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten,
                        Long baugruppeId, BigDecimal anzahlVorgaenge, BigDecimal verschnittProzent, BigDecimal mengeMitVerschnitt,
                        BigDecimal maschinenSatz, BigDecimal maschinenSumme, BigDecimal maschinenanteilProzent,
                        Long fremdleistungId, String veredler, List<FremdKostenart> kostenarten, BigDecimal gewichtKgJeEinheit,
                        BigDecimal gewichtKg, BigDecimal oberflaecheM2, String mengeAus) {
            this(id, typ, artikelId, arbeitsgangId, bezeichnung, menge, einheit, einzelpreis, summe, hinweis, notiz, ruestzeitMinuten, bearbeitungszeitMinuten,
                    baugruppeId, anzahlVorgaenge, verschnittProzent, mengeMitVerschnitt, maschinenSatz, maschinenSumme, maschinenanteilProzent,
                    fremdleistungId, veredler, kostenarten, gewichtKgJeEinheit, gewichtKg, oberflaecheM2, mengeAus, null, null);
        }

        /** Abwärtskompatibel: ohne Gewicht, Oberfläche und Mengenherkunft. */
        public Position(Long id, String typ, Long artikelId, Long arbeitsgangId, String bezeichnung, BigDecimal menge, String einheit,
                        BigDecimal einzelpreis, BigDecimal summe, String hinweis, String notiz, BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten,
                        Long baugruppeId, BigDecimal anzahlVorgaenge, BigDecimal verschnittProzent, BigDecimal mengeMitVerschnitt,
                        BigDecimal maschinenSatz, BigDecimal maschinenSumme, BigDecimal maschinenanteilProzent,
                        Long fremdleistungId, String veredler, List<FremdKostenart> kostenarten, BigDecimal gewichtKgJeEinheit) {
            this(id, typ, artikelId, arbeitsgangId, bezeichnung, menge, einheit, einzelpreis, summe, hinweis, notiz, ruestzeitMinuten, bearbeitungszeitMinuten,
                    baugruppeId, anzahlVorgaenge, verschnittProzent, mengeMitVerschnitt, maschinenSatz, maschinenSumme, maschinenanteilProzent,
                    fremdleistungId, veredler, kostenarten, gewichtKgJeEinheit, null, null, null);
        }

        /** Abwärtskompatibel: ohne Fremdleistung. */
        public Position(Long id, String typ, Long artikelId, Long arbeitsgangId, String bezeichnung, BigDecimal menge, String einheit,
                        BigDecimal einzelpreis, BigDecimal summe, String hinweis, String notiz, BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten,
                        Long baugruppeId, BigDecimal anzahlVorgaenge, BigDecimal verschnittProzent, BigDecimal mengeMitVerschnitt,
                        BigDecimal maschinenSatz, BigDecimal maschinenSumme, BigDecimal maschinenanteilProzent) {
            this(id, typ, artikelId, arbeitsgangId, bezeichnung, menge, einheit, einzelpreis, summe, hinweis, notiz, ruestzeitMinuten, bearbeitungszeitMinuten,
                    baugruppeId, anzahlVorgaenge, verschnittProzent, mengeMitVerschnitt, maschinenSatz, maschinenSumme, maschinenanteilProzent, null, null, null, null, null, null, null);
        }

        /** Abwärtskompatibel: ohne Maschine. */
        public Position(Long id, String typ, Long artikelId, Long arbeitsgangId, String bezeichnung, BigDecimal menge, String einheit,
                        BigDecimal einzelpreis, BigDecimal summe, String hinweis, String notiz, BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten,
                        Long baugruppeId, BigDecimal anzahlVorgaenge, BigDecimal verschnittProzent, BigDecimal mengeMitVerschnitt) {
            this(id, typ, artikelId, arbeitsgangId, bezeichnung, menge, einheit, einzelpreis, summe, hinweis, notiz, ruestzeitMinuten, bearbeitungszeitMinuten,
                    baugruppeId, anzahlVorgaenge, verschnittProzent, mengeMitVerschnitt, null, null, null, null, null, null, null);
        }

        /** Abwärtskompatibel: ohne Verschnitt. */
        public Position(Long id, String typ, Long artikelId, Long arbeitsgangId, String bezeichnung, BigDecimal menge, String einheit,
                        BigDecimal einzelpreis, BigDecimal summe, String hinweis, String notiz, BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten,
                        Long baugruppeId, BigDecimal anzahlVorgaenge) {
            this(id, typ, artikelId, arbeitsgangId, bezeichnung, menge, einheit, einzelpreis, summe, hinweis, notiz, ruestzeitMinuten, bearbeitungszeitMinuten,
                    baugruppeId, anzahlVorgaenge, null, null);
        }

        /** Abwärtskompatibel: ohne Baugruppe und Anzahl der Vorgänge. */
        public Position(Long id, String typ, Long artikelId, Long arbeitsgangId, String bezeichnung, BigDecimal menge, String einheit,
                        BigDecimal einzelpreis, BigDecimal summe, String hinweis, String notiz, BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten) {
            this(id, typ, artikelId, arbeitsgangId, bezeichnung, menge, einheit, einzelpreis, summe, hinweis, notiz, ruestzeitMinuten, bearbeitungszeitMinuten, null, null, null, null);
        }


        /** Abwärtskompatibel: ohne Zeiten. */
        public Position(Long id, String typ, Long artikelId, Long arbeitsgangId, String bezeichnung, BigDecimal menge, String einheit,
                        BigDecimal einzelpreis, BigDecimal summe, String hinweis, String notiz) {
            this(id, typ, artikelId, arbeitsgangId, bezeichnung, menge, einheit, einzelpreis, summe, hinweis, notiz, null, null, null, null, null, null);
        }
    }

    /** Eine Kostenart der Fremdleistung in der Aufstellung. */
    public record FremdKostenart(String art, String text, BigDecimal betrag) { }
}
