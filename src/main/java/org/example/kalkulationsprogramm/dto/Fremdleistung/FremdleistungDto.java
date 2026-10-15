package org.example.kalkulationsprogramm.dto.Fremdleistung;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Eine Fremdleistung (Angebot eines Veredlers) mit allen Kostenarten; {@code verwendet} = in wie vielen Kalkulationszeilen sie vorkommt. */
public record FremdleistungDto(Long id, Long lieferantId, String lieferantName, String bezeichnung, String einheit, BigDecimal preisJeEinheit, BigDecimal mindestpreis,
        BigDecimal einmalBetrag, BigDecimal anteilBetrag, BigDecimal anteilChargenmenge, BigDecimal prozentZuschlag, String transportArt, BigDecimal transportBetrag,
        BigDecimal transportKm, int transportFahrten, String sonstigeBezeichnung, BigDecimal sonstigeBetrag, LocalDate gueltigAb, boolean aktiv, long verwendet) { }
