package org.example.kalkulationsprogramm.dto.Lager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Aggregierte Charge eines Lagerartikels mit optionalem Werkstoffzeugnis. */
public record ChargeDto(
        String chargennummer,
        BigDecimal menge,
        LocalDateTime ersteBuchungAm,
        Long zeugnisId,
        String zeugnisDateiname,
        String zeugnisNummer,
        String zeugnisUrl) {
}
