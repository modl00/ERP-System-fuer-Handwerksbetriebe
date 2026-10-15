package org.example.kalkulationsprogramm.dto.wartung;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Die Protokollart mehrerer Wartungsprotokolle auf einmal ändern. */
public record WartungProtokollartSammelDto(
        @NotEmpty(message = "Bitte mindestens eine Wartung auswählen.") @Size(max = 200, message = "Es lassen sich höchstens 200 Wartungen auf einmal ändern.") List<Long> ids,
        @NotBlank(message = "Bitte eine Protokollart wählen.") String protokollTyp) {

    /** Was geändert wurde und was nicht (mit Grund), damit nichts stillschweigend ausfällt. */
    public record Ergebnis(int geaendert, List<Uebersprungen> uebersprungen) { }

    public record Uebersprungen(Long id, String nummer, String grund) { }
}
