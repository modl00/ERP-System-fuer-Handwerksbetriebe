package org.example.kalkulationsprogramm.dto.Lager;

import java.math.BigDecimal;

/** Neuer Mindestbestand eines Artikels, leer ({@code null}) entfernt ihn. */
public record MindestbestandEingabe(BigDecimal mindestbestand) {
}
