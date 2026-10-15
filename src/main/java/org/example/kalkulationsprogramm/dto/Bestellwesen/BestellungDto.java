package org.example.kalkulationsprogramm.dto.Bestellwesen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.BestellHerkunft;
import org.example.kalkulationsprogramm.domain.BestellStatus;

/**
 * Eine Bestellung für die Oberfläche. {@code lieferstand} ist abgeleitet: ENTWURF, STORNIERT, BESTELLT (nichts geliefert), TEILWEISE (ein Teil der
 * Positionen geliefert) oder GELIEFERT. {@code ueberfaellig} heißt: seit mindestens {@code ueberfaelligNachTagen} Tagen bestellt und noch nicht
 * vollständig geliefert (reine Anzeige, es werden keine Erinnerungen verschickt).
 */
public record BestellungDto(Long id, String bestellnummer, BestellStatus status, String lieferstand, Long lieferantId, String lieferantName,
        List<String> lieferantEmails, String notiz, LocalDateTime erstelltAm, String erstelltVon, LocalDateTime bestelltAm, String bestelltVon,
        String bestelltPer, LocalDateTime storniertAm, String stornoGrund, BigDecimal summe, int anzahlPositionen, int anzahlGeliefert,
        Integer tageSeitBestellung, boolean ueberfaellig, int ueberfaelligNachTagen, List<String> projekte, List<Position> positionen) {

    public record Position(Long id, Long artikelId, String artikelnummer, String bezeichnung, BigDecimal menge, String einheit, BigDecimal einzelpreis,
            BigDecimal summe, BestellHerkunft herkunft, Long projektId, String projektName, Long artikelInProjektId, String kommentar,
            LocalDate geliefertAm) { }
}
