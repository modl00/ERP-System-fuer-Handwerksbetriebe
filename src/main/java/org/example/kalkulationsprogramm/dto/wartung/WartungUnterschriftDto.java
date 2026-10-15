package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Unterschrift von Techniker oder Kunde. {@code bild} ist eine PNG-Data-URL; {@code null} löscht
 * die Unterschrift. Der Name wird immer mitgeschickt.
 */
public record WartungUnterschriftDto(
        @NotNull(message = "Bitte angeben, wer unterschreibt.") Rolle rolle,
        @Size(max = 700_000, message = "Die Unterschrift ist zu groß. Bitte noch einmal kleiner zeichnen.") String bild,
        @Size(max = 200, message = "Der Name ist zu lang (höchstens 200 Zeichen).") String name
) {
    public enum Rolle { TECHNIKER, KUNDE }
}
