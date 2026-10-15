package org.example.kalkulationsprogramm.dto.Lager;

import java.math.BigDecimal;

/** Ware von einem Lagerort an einen anderen legen. Der Gesamtbestand ändert sich nicht. */
public record UmbuchungEingabe(Long artikelId, Long vonLagerortId, Long nachLagerortId, BigDecimal menge, String notiz) {
}
