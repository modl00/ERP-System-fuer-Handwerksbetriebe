package org.example.kalkulationsprogramm.dto.Fremdleistung;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Eingabe für eine Fremdleistung. Leere Kostenarten gibt es beim Veredler nicht. {@code einheit}: KG, QUADRATMETER, METER, STUECK; {@code transportArt}: KEINE, PAUSCHAL, JE_KG, JE_FAHRT, JE_KM. */
public record FremdleistungEingabeDto(
        @NotNull(message = "Bitte den Veredler (Lieferanten) wählen.") Long lieferantId,
        @NotBlank(message = "Bitte angeben, was der Veredler macht (zum Beispiel Feuerverzinken).") @Size(max = 200, message = "Die Bezeichnung ist zu lang (höchstens 200 Zeichen).") String bezeichnung,
        @NotBlank(message = "Bitte die Abrechnungseinheit wählen.") String einheit,
        BigDecimal preisJeEinheit, BigDecimal mindestpreis, BigDecimal einmalBetrag, BigDecimal anteilBetrag, BigDecimal anteilChargenmenge, BigDecimal prozentZuschlag,
        String transportArt, BigDecimal transportBetrag, BigDecimal transportKm, Integer transportFahrten,
        @Size(max = 100, message = "Die Bezeichnung der sonstigen Kosten ist zu lang.") String sonstigeBezeichnung, BigDecimal sonstigeBetrag,
        LocalDate gueltigAb, Boolean aktiv) { }
