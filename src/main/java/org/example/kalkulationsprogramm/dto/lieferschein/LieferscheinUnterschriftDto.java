package org.example.kalkulationsprogramm.dto.lieferschein;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Die Unterschrift des Empfängers: PNG als Data-URL und der Name. */
public record LieferscheinUnterschriftDto(
        @NotBlank(message = "Bitte unterschreiben.") @Size(max = 700_000, message = "Die Unterschrift ist zu groß. Bitte noch einmal kleiner zeichnen.") String bild,
        @Size(max = 200, message = "Der Name ist zu lang (höchstens 200 Zeichen).") String name
) { }
