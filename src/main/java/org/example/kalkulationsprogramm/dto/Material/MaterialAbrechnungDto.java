package org.example.kalkulationsprogramm.dto.Material;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Plan und Rechnung des Materials eines Projekts (Issue #99). {@code rechnungen} sind die Eingangsrechnungen, die dem Projekt zugeordnet sind, mit dem
 * Anteil des Projekts (netto), dem schon dem Material zugeordneten Betrag und dem Rest. {@code positionen} sind die geplanten Materialpositionen, die nicht
 * aus dem Lager kommen ("kommt per Rechnung"), offen oder schon durch eine Rechnung bezahlt.
 *
 * <p>{@code abweichung} ist TEURER oder GUENSTIGER, wenn die Rechnung um mehr als {@code schwelleProzent} Prozent vom geplanten Preis abweicht, sonst leer.
 */
public record MaterialAbrechnungDto(int schwelleProzent, List<Rechnung> rechnungen, List<Position> positionen, int offenAnzahl, BigDecimal offenGeplant) {

    public record Rechnung(Long dokumentId, String dokumentNummer, LocalDate dokumentDatum, Long lieferantId, String lieferantName, BigDecimal anteil,
            BigDecimal zugeordnet, BigDecimal rest, List<Zeile> zeilen) { }

    public record Zeile(Long id, Long artikelInProjektId, String bezeichnung, BigDecimal betrag, String beschreibung) { }

    public record Position(Long artikelInProjektId, Long artikelId, String artikelnummer, String bezeichnung, BigDecimal menge, String einheit, Long lieferantId,
            String lieferantName, boolean bestellt, BigDecimal geplant, BigDecimal ist, BigDecimal abweichungProzent, String abweichung, List<String> rechnungen) { }

    /** Eine Zeile der Zuordnung zum Speichern: Position (oder leer = Material ohne Plan), Betrag und bei "ohne Plan" die Beschreibung. */
    public record ZeileEingabe(Long artikelInProjektId, BigDecimal betrag, String beschreibung) { }
}
