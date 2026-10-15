package org.example.kalkulationsprogramm.dto.Bestellwesen;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.BestellHerkunft;

/**
 * Ein Vorschlag, was zu bestellen wäre: aus dem Mindestbestand des Lagers (Menge = Mindestbestand minus Bestand minus schon Unterwegs-Menge) oder aus
 * dem Material eines Projekts. {@code lieferant*} ist der Lieferant mit dem günstigsten aktuellen Preis bzw. der an der Projektposition hinterlegte.
 */
public record BestellVorschlagDto(Long artikelId, String artikelnummer, String bezeichnung, String einheit, BigDecimal menge, BestellHerkunft herkunft,
        Long projektId, String projektName, Long artikelInProjektId, Long lieferantId, String lieferantName, BigDecimal einzelpreis, String kommentar,
        String hinweis) { }
