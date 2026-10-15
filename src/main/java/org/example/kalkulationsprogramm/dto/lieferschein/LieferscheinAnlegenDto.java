package org.example.kalkulationsprogramm.dto.lieferschein;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ein neuer Lieferschein aus der Handy-App. {@code clientId} macht das Senden wiederholbar (kein zweiter Lieferschein), {@code nummer} ist eine vorab reservierte
 * Nummer (Arbeiten ohne Netz); fehlt sie, vergibt der Server die nächste. Mit {@code unterschriftBild} (PNG als Data-URL) ist der Lieferschein sofort unterschrieben.
 */
public record LieferscheinAnlegenDto(
        @Size(max = 64, message = "Die Kennung ist zu lang.") String clientId,
        @Size(max = 60, message = "Die Nummer ist zu lang.") String nummer,
        Long kundeId,
        Long projektId,
        @NotBlank(message = "Bitte angeben, wer die Lieferung entgegennimmt.") @Size(max = 200, message = "Der Name ist zu lang (höchstens 200 Zeichen).") String empfaengerName,
        LocalDate datum,
        @Size(max = 2000, message = "Die Bemerkung ist zu lang (höchstens 2000 Zeichen).") String bemerkung,
        @NotEmpty(message = "Bitte mindestens eine Position angeben.") @Size(max = 100, message = "Höchstens 100 Positionen je Lieferschein.") List<@Valid Position> positionen,
        @Size(max = 700_000, message = "Die Unterschrift ist zu groß. Bitte noch einmal kleiner zeichnen.") String unterschriftBild
) {
    public record Position(
            @NotBlank(message = "Jede Position braucht eine Bezeichnung.") @Size(max = 300, message = "Die Bezeichnung ist zu lang (höchstens 300 Zeichen).") String bezeichnung,
            @NotNull(message = "Bitte die Menge angeben.") @DecimalMin(value = "0.001", message = "Die Menge muss größer als 0 sein.") BigDecimal menge,
            @Size(max = 20, message = "Die Einheit ist zu lang.") String einheit
    ) { }
}
