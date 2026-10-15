package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Speichert die eigene Unterschrift für künftige Feldeinsätze. */
public record WartungUnterschriftSpeichernDto(
        @NotBlank(message = "Bitte zuerst unterschreiben.")
        @Size(max = 700_000, message = "Die Unterschrift ist zu groß. Bitte noch einmal kleiner zeichnen.") String bild,
        @Size(max = 200, message = "Der Name ist zu lang (höchstens 200 Zeichen).") String name
) { }
