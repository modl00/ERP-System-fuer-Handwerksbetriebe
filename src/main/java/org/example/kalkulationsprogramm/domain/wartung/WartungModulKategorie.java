package org.example.kalkulationsprogramm.domain.wartung;

/**
 * Art eines Wartungs-Moduls.
 *
 * <p>{@link #BASIS}: der eigentliche Prüfgegenstand (Brandschutztür, Rauchschutztür, Tor ...).
 * Pro Protokoll ist genau ein Basis-Element sinnvoll. {@link #ZUSATZ}: andockbare Erweiterung
 * (Feststellanlage, Glasfüllung ...), mehrere gleichzeitig möglich, aber nur an passende
 * Basis-Elemente.</p>
 */
public enum WartungModulKategorie {
    BASIS,
    ZUSATZ
}
