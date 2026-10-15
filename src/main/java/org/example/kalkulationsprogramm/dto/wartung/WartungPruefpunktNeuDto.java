package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Neuer Prüfpunkt in einem laufenden Feldeinsatz. Ohne {@code abschnittId} landet er in "Zusätzliche Prüfpunkte". */
public record WartungPruefpunktNeuDto(
        Long abschnittId,
        @NotBlank(message = "Bitte den Prüfpunkt beschreiben.")
        @Size(max = 500, message = "Der Prüfpunkt ist zu lang (höchstens 500 Zeichen).") String label
) { }
