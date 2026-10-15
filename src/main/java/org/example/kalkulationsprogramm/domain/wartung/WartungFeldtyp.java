package org.example.kalkulationsprogramm.domain.wartung;

/**
 * Art eines Datenfelds in der Datenaufnahme. {@code AUSWAHL} speichert die Optionen zeilenweise, {@code JAHR} ist eine vierstellige Jahreszahl
 * (Baujahr, Einbaujahr).
 */
public enum WartungFeldtyp {
    TEXT,
    JA_NEIN,
    AUSWAHL,
    JAHR;

    /** Der Wert, der in Auswahllisten "weiß ich nicht" bedeutet. Er zählt wie leer und erscheint nicht im gedruckten Protokoll. */
    public static final String KEINE_ANGABE = "keine Angabe";

    /** Heißt der Wert "keine Angabe" (Groß- und Kleinschreibung egal)? Leere Werte zählen hier nicht. */
    public static boolean istKeineAngabe(String wert) {
        return wert != null && wert.strip().equalsIgnoreCase(KEINE_ANGABE);
    }
}
