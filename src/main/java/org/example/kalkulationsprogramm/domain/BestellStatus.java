package org.example.kalkulationsprogramm.domain;

/** Stand einer Bestellung beim Lieferanten. Ob schon geliefert wurde, steht an den Positionen. */
public enum BestellStatus {
    /** Wird noch zusammengestellt, hat noch keine Bestellnummer und ist beim Lieferanten nicht bekannt. */
    ENTWURF,
    /** Ist beim Lieferanten bestellt (Bestellnummer vergeben, nicht mehr änderbar). */
    BESTELLT,
    /** Zurückgezogen; die Bestellnummer bleibt vergeben. */
    STORNIERT
}
