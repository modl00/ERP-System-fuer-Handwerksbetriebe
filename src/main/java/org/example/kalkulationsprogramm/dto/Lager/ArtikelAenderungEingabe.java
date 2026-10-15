package org.example.kalkulationsprogramm.dto.Lager;

/**
 * Neue Stammdaten eines Artikels aus dem Lager heraus.
 *
 * @param bezeichnung Name des Artikels (nicht leer)
 * @param einheit     Verrechnungseinheit: {@code STUECK}, {@code LAUFENDE_METER}, {@code QUADRATMETER}, {@code KILOGRAMM} oder {@code LITER}
 */
public record ArtikelAenderungEingabe(String bezeichnung, String einheit) {
}
