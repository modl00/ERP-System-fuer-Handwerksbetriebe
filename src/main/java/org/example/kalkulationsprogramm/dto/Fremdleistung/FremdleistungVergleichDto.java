package org.example.kalkulationsprogramm.dto.Fremdleistung;

import java.math.BigDecimal;
import java.util.List;

/** Ein Veredler im Preisvergleich: die Aufstellung seiner Kostenarten für die angefragte Menge. Die Liste ist nach Summe sortiert, der günstigste steht vorn. */
public record FremdleistungVergleichDto(Long fremdleistungId, Long lieferantId, String lieferantName, String bezeichnung, String einheit, BigDecimal summe,
        BigDecimal summeJeEinheit, boolean gewichtFehlt, boolean guenstigster, List<Kostenart> kostenarten) {

    public record Kostenart(String art, String text, BigDecimal betrag) { }
}
