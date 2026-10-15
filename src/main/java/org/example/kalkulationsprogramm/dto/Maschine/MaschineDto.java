package org.example.kalkulationsprogramm.dto.Maschine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.service.MaschinenKostenRechner.Aufstellung;

/** Eine Maschine mit allen Angaben, dem errechneten Satz und den Arbeitsgängen, die sie nutzen. */
public record MaschineDto(
        Long id,
        String name,
        BigDecimal anschaffungswert,
        BigDecimal restwert,
        BigDecimal nutzungsdauerJahre,
        BigDecimal zinssatzProzent,
        BigDecimal wartungJeJahr,
        BigDecimal platzJeJahr,
        BigDecimal versicherungJeJahr,
        BigDecimal leistungKw,
        BigDecimal strompreisJeKwh,
        BigDecimal auslastungProzent,
        BigDecimal laufstundenJeJahr,
        BigDecimal satzVonHand,
        /** Der Satz je Laufstunde, mit dem die Kalkulation rechnet. */
        BigDecimal kostensatz,
        /** {@code true}, wenn der Satz von Hand festgelegt ist. */
        boolean vonHand,
        boolean aktiv,
        String bemerkung,
        Aufstellung aufstellung,
        List<ArbeitsgangKurz> arbeitsgaenge
) {
    /** Ein Arbeitsgang, der die Maschine nutzt. */
    public record ArbeitsgangKurz(Long id, String beschreibung) { }

    /** Eine Änderung am Satz. */
    public record Verlauf(BigDecimal satz, boolean vonHand, LocalDate gueltigAb, String geaendertVon, LocalDateTime geaendertAm) { }
}
