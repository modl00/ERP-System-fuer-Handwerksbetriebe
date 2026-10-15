package org.example.kalkulationsprogramm.dto.Maschine;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Eingabe zum Anlegen oder Ändern einer Maschine. Alle Zahlen dürfen fehlen; der Satz ergibt sich aus dem Rechner oder wird von Hand festgelegt. */
public record MaschineEingabeDto(
        @NotBlank(message = "Bitte einen Namen eintragen.")
        @Size(max = 100, message = "Der Name ist zu lang (höchstens 100 Zeichen).") String name,
        @DecimalMin(value = "0", message = "Der Anschaffungswert darf nicht negativ sein.") @DecimalMax(value = "100000000", message = "Der Anschaffungswert ist zu groß.") BigDecimal anschaffungswert,
        @DecimalMin(value = "0", message = "Der Restwert darf nicht negativ sein.") @DecimalMax(value = "100000000", message = "Der Restwert ist zu groß.") BigDecimal restwert,
        @DecimalMin(value = "0", message = "Die Nutzungsdauer darf nicht negativ sein.") @DecimalMax(value = "100", message = "Die Nutzungsdauer ist zu groß (höchstens 100 Jahre).") BigDecimal nutzungsdauerJahre,
        @DecimalMin(value = "0", message = "Der Zinssatz darf nicht negativ sein.") @DecimalMax(value = "100", message = "Der Zinssatz ist zu groß (höchstens 100 Prozent).") BigDecimal zinssatzProzent,
        @DecimalMin(value = "0", message = "Die Wartungskosten dürfen nicht negativ sein.") @DecimalMax(value = "100000000", message = "Die Wartungskosten sind zu groß.") BigDecimal wartungJeJahr,
        @DecimalMin(value = "0", message = "Die Platzkosten dürfen nicht negativ sein.") @DecimalMax(value = "100000000", message = "Die Platzkosten sind zu groß.") BigDecimal platzJeJahr,
        @DecimalMin(value = "0", message = "Die Versicherung darf nicht negativ sein.") @DecimalMax(value = "100000000", message = "Die Versicherung ist zu groß.") BigDecimal versicherungJeJahr,
        @DecimalMin(value = "0", message = "Die Leistung darf nicht negativ sein.") @DecimalMax(value = "100000", message = "Die Leistung ist zu groß.") BigDecimal leistungKw,
        @DecimalMin(value = "0", message = "Der Strompreis darf nicht negativ sein.") @DecimalMax(value = "100", message = "Der Strompreis ist zu groß.") BigDecimal strompreisJeKwh,
        @DecimalMin(value = "0", message = "Die Auslastung darf nicht negativ sein.") @DecimalMax(value = "100", message = "Die Auslastung ist zu groß (höchstens 100 Prozent).") BigDecimal auslastungProzent,
        @DecimalMin(value = "0", message = "Die Laufstunden dürfen nicht negativ sein.") @DecimalMax(value = "8784", message = "Ein Jahr hat höchstens 8.784 Stunden.") BigDecimal laufstundenJeJahr,
        @DecimalMin(value = "0", message = "Der Satz darf nicht negativ sein.") @DecimalMax(value = "1000", message = "Der Satz darf höchstens 1.000 Euro je Stunde betragen.") BigDecimal satzVonHand,
        Boolean aktiv,
        @Size(max = 500, message = "Die Bemerkung ist zu lang (höchstens 500 Zeichen).") String bemerkung,
        /** Die Arbeitsgänge, die diese Maschine nutzen; fehlt die Angabe, bleiben die Zuordnungen wie sie sind. */
        List<Long> arbeitsgangIds
) { }
