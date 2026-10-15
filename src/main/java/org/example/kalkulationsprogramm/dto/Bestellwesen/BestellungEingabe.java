package org.example.kalkulationsprogramm.dto.Bestellwesen;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.BestellHerkunft;

/** Eine Bestellung anlegen oder (als Entwurf) neu zusammenstellen. */
public record BestellungEingabe(Long lieferantId, String notiz, List<Position> positionen) {

    /**
     * Der Artikel muss im Artikelstamm stehen; Bezeichnung und Nummer übernimmt der Server von dort. Die Einheit ist optional (Stk, m, m², kg, l);
     * ohne Angabe gilt die des Artikels. Sie ist nötig, wenn zum Beispiel ein Profil in Stück statt in Metern bestellt wird.
     */
    public record Position(Long artikelId, BigDecimal menge, String einheit, BigDecimal einzelpreis, BestellHerkunft herkunft, Long projektId,
            Long artikelInProjektId, String kommentar) { }
}
