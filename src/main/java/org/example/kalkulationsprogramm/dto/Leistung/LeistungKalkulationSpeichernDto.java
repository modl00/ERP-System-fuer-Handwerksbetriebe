package org.example.kalkulationsprogramm.dto.Leistung;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Neue Kalkulation einer Leistung. Die Zeilen ersetzen die bisherigen komplett.
 *
 * @param kalkuliert     soll der Preis künftig der Kalkulation folgen? Dann wird er beim Speichern gesetzt.
 * @param zuschlagProzent Zuschlag in Prozent auf Material und Arbeit, leer = 0
 * @param losgroesse     auf wie viele Einheiten der Leistung sich die Rüstzeit verteilt, leer = 1
 */
public record LeistungKalkulationSpeichernDto(
        boolean kalkuliert,
        BigDecimal zuschlagProzent,
        BigDecimal losgroesse,
        List<Position> positionen) {

    /** Abwärtskompatibel: ohne Losgröße (Standard 1). */
    public LeistungKalkulationSpeichernDto(boolean kalkuliert, BigDecimal zuschlagProzent, List<Position> positionen) {
        this(kalkuliert, zuschlagProzent, null, positionen);
    }

    /**
     * Eine Zeile: {@code typ} ist {@code ARTIKEL} (mit {@code artikelId}), {@code ARBEIT} (mit {@code arbeitsgangId}) oder {@code BAUGRUPPE} (mit
     * {@code baugruppeId}; {@code menge} ist die Menge der Baugruppe je Einheit der Leistung).
     * Bei Arbeit gelten entweder {@code menge} (Stunden je Einheit) oder, sobald eine der beiden Zeiten angegeben ist, Rüst- und Bearbeitungszeit in Minuten;
     * {@code anzahlVorgaenge} (leer = 1) sagt dann, wie oft der Vorgang je Einheit vorkommt: Die Bearbeitungszeit gilt je Vorgang.
     * Bei Arbeit mit einer Maschine ist {@code maschinenanteilProzent} (leer = 100) die Maschinenzeit in Prozent der Arbeitszeit; 0 rechnet ohne Maschine.
     * Bei {@code FREMDLEISTUNG} (mit {@code fremdleistungId}) ist {@code menge} die Menge in der Abrechnungseinheit des Veredlers je Einheit der Leistung (zum Beispiel kg je lfm);
     * {@code gewichtKgJeEinheit} ist das Gewicht je Einheit der Leistung und nur für Transport je kg nötig, wenn der Veredler nicht nach kg abrechnet.
     * Bei Material ist {@code verschnittProzent} (leer = keiner, 0 bis 100) der Zuschlag auf die Menge für Verschnitt.
     */
    public record Position(String typ, Long artikelId, Long arbeitsgangId, BigDecimal menge, String notiz,
                           BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten, Long baugruppeId, BigDecimal anzahlVorgaenge,
                           BigDecimal verschnittProzent, BigDecimal maschinenanteilProzent, Long fremdleistungId, BigDecimal gewichtKgJeEinheit,
                           String mengeAus, Map<String, BigDecimal> variablen) {

        /** Abwärtskompatibel: ohne Werte der Formel-Eingabefelder. */
        public Position(String typ, Long artikelId, Long arbeitsgangId, BigDecimal menge, String notiz,
                        BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten, Long baugruppeId, BigDecimal anzahlVorgaenge,
                        BigDecimal verschnittProzent, BigDecimal maschinenanteilProzent, Long fremdleistungId, BigDecimal gewichtKgJeEinheit,
                        String mengeAus) {
            this(typ, artikelId, arbeitsgangId, menge, notiz, ruestzeitMinuten, bearbeitungszeitMinuten, baugruppeId, anzahlVorgaenge, verschnittProzent, maschinenanteilProzent, fremdleistungId, gewichtKgJeEinheit, mengeAus, null);
        }

        /** Abwärtskompatibel: ohne Mengenherkunft (Menge von Hand). */
        public Position(String typ, Long artikelId, Long arbeitsgangId, BigDecimal menge, String notiz,
                        BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten, Long baugruppeId, BigDecimal anzahlVorgaenge,
                        BigDecimal verschnittProzent, BigDecimal maschinenanteilProzent, Long fremdleistungId, BigDecimal gewichtKgJeEinheit) {
            this(typ, artikelId, arbeitsgangId, menge, notiz, ruestzeitMinuten, bearbeitungszeitMinuten, baugruppeId, anzahlVorgaenge, verschnittProzent, maschinenanteilProzent, fremdleistungId, gewichtKgJeEinheit, null);
        }

        /** Abwärtskompatibel: ohne Fremdleistung. */
        public Position(String typ, Long artikelId, Long arbeitsgangId, BigDecimal menge, String notiz,
                        BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten, Long baugruppeId, BigDecimal anzahlVorgaenge,
                        BigDecimal verschnittProzent, BigDecimal maschinenanteilProzent) {
            this(typ, artikelId, arbeitsgangId, menge, notiz, ruestzeitMinuten, bearbeitungszeitMinuten, baugruppeId, anzahlVorgaenge, verschnittProzent, maschinenanteilProzent, null, null, null);
        }

        /** Abwärtskompatibel: ohne Maschinenanteil. */
        public Position(String typ, Long artikelId, Long arbeitsgangId, BigDecimal menge, String notiz,
                        BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten, Long baugruppeId, BigDecimal anzahlVorgaenge, BigDecimal verschnittProzent) {
            this(typ, artikelId, arbeitsgangId, menge, notiz, ruestzeitMinuten, bearbeitungszeitMinuten, baugruppeId, anzahlVorgaenge, verschnittProzent, null, null, null, null);
        }

        /** Abwärtskompatibel: ohne Verschnitt. */
        public Position(String typ, Long artikelId, Long arbeitsgangId, BigDecimal menge, String notiz,
                        BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten, Long baugruppeId, BigDecimal anzahlVorgaenge) {
            this(typ, artikelId, arbeitsgangId, menge, notiz, ruestzeitMinuten, bearbeitungszeitMinuten, baugruppeId, anzahlVorgaenge, null);
        }

        /** Abwärtskompatibel: ohne Baugruppe und Anzahl der Vorgänge. */
        public Position(String typ, Long artikelId, Long arbeitsgangId, BigDecimal menge, String notiz,
                        BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten) {
            this(typ, artikelId, arbeitsgangId, menge, notiz, ruestzeitMinuten, bearbeitungszeitMinuten, null, null, null);
        }

        /** Abwärtskompatibel: ohne Zeiten (Stunden in {@code menge}). */
        public Position(String typ, Long artikelId, Long arbeitsgangId, BigDecimal menge, String notiz) {
            this(typ, artikelId, arbeitsgangId, menge, notiz, null, null, null, null, null);
        }
    }
}
