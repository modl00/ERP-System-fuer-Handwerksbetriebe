package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Kopfdaten eines Protokolls. Es werden immer alle Felder gesendet (kein Teil-Update), damit ein
 * fehlendes Feld nie unbemerkt etwas überschreibt. Die nächste Wartung berechnet der Server.
 */
public record WartungProtokollKopfDto(
        @Size(max = 200, message = "Der Titel ist zu lang (höchstens 200 Zeichen).") String titel,
        @NotBlank(message = "Bitte eine Protokollart eintragen.")
        @Size(max = 100, message = "Die Protokollart ist zu lang (höchstens 100 Zeichen).") String protokollTyp,
        @NotNull(message = "Bitte ein Datum eintragen.") LocalDate datum,
        @NotNull(message = "Bitte ein Prüfintervall eintragen.")
        @Min(value = 1, message = "Das Prüfintervall muss mindestens 1 Monat betragen.")
        @Max(value = 120, message = "Das Prüfintervall darf höchstens 120 Monate betragen.") Integer intervallMonate,
        Long technikerId,
        Long projektId,
        /** Objekt des Feldeinsatzes. Gehört er zu einer Wartung, ergibt sich das Objekt aus ihr (andere Angabe: 409; ohne Angabe bleibt es). */
        Long objektId,
        @Size(max = 10000, message = "Die Bemerkung ist zu lang (höchstens 10.000 Zeichen).") String bemerkungen,
        @Size(max = 200, message = "Der Name des Ansprechpartners ist zu lang.") String ansprechpartnerName
) { }
