package org.example.kalkulationsprogramm.dto.Lager;

import java.math.BigDecimal;

/**
 * Eine neue Buchung.
 *
 * @param art    {@code ZUGANG} (Wareneingang), {@code ABGANG} (Entnahme) oder {@code KORREKTUR} (Inventur)
 * @param menge  bei Zugang und Abgang die bewegte Menge (größer 0), bei der Korrektur der <b>gezählte Bestand</b> (0 oder mehr)
 * @param projektId optional: für welches Projekt entnommen wird
 */
public record LagerBuchungEingabe(String art, Long artikelId, Long lagerortId, BigDecimal menge, Long projektId, String notiz, String chargennummer, Long werkstoffzeugnisId) {
}
