package org.example.kalkulationsprogramm.dto.Lager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Eine Buchung im Verlauf eines Artikels. {@code menge} hat ein Vorzeichen (Zugang +, Abgang -). */
public record LagerBuchungDto(
        Long id,
        String art,
        BigDecimal menge,
        Long lagerortId,
        String lagerortName,
        LocalDateTime gebuchtAm,
        Long projektId,
        String projekt,
        String notiz,
        String benutzer,
        String chargennummer,
        Long zeugnisId,
        String zeugnisDateiname,
        String zeugnisNummer,
        String zeugnisUrl) {
}
