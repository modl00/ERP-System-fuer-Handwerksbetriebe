package org.example.kalkulationsprogramm.dto.wartung;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Neue Angaben zum Wartungsvertrag eines Projekts (ersetzt die bisherigen). Beginn ist Pflicht, Ende leer = unbefristet. */
public record WartungVertragSpeichernDto(
        String vertragsnummer,
        LocalDate beginn,
        LocalDate ende,
        Integer intervallMonate,
        BigDecimal preisNetto,
        String notiz) {
}
