package org.example.kalkulationsprogramm.dto.Lager;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bestand eines Artikels im Lager.
 *
 * @param einheit              Einheit der Mengen ("Stk", "m", "m²", "kg"), die Verrechnungseinheit des Artikels
 * @param bestand              Summe über alle Lagerorte
 * @param mindestbestand       gewünschter Mindestbestand, {@code null} wenn keiner gesetzt ist
 * @param unterMindestbestand  Bestand liegt unter dem Mindestbestand
 * @param negativ              Bestand ist negativ: es wurde mehr entnommen als eingebucht (Zugang oder Inventur fehlt)
 */
public record LagerBestandDto(
        Long artikelId,
        String bezeichnung,
        String artikelnummer,
        String einheit,
        BigDecimal bestand,
        BigDecimal mindestbestand,
        boolean unterMindestbestand,
        boolean negativ,
        List<OrtBestand> orte,
        BigDecimal kgProMeter,
        String handelseinheitBezeichnung,
        BigDecimal handelseinheitMeter) {

    /** Bestand an einem Lagerort. */
    public record OrtBestand(Long lagerortId, String lagerortName, BigDecimal bestand) {
    }
}
