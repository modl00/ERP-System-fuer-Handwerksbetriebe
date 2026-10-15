package org.example.kalkulationsprogramm.dto.wartung;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Der Wartungsvertrag eines Projekts.
 *
 * @param status     {@code NOCH_NICHT_GESTARTET}, {@code LAEUFT} oder {@code ABGELAUFEN} (am heutigen Tag gemessen)
 * @param endetBald  die Laufzeit endet in den nächsten 90 Tagen (nur bei {@code LAEUFT} und befristetem Vertrag)
 */
public record WartungVertragDto(
        Long id,
        Long projektId,
        String vertragsnummer,
        LocalDate beginn,
        LocalDate ende,
        int intervallMonate,
        BigDecimal preisNetto,
        String notiz,
        String status,
        boolean endetBald) {
}
