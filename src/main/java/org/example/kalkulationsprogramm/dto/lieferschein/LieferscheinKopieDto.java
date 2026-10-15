package org.example.kalkulationsprogramm.dto.lieferschein;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Kopie des Lieferscheins per E-Mail. Verschickt wird nur mit {@code bestaetigt = true} (der Mitarbeiter hat Empfänger und Versand bestätigt). */
public record LieferscheinKopieDto(
        @NotBlank(message = "Bitte die E-Mail-Adresse angeben.") @Email(message = "Das ist keine gültige E-Mail-Adresse.") @Size(max = 255) String an,
        boolean bestaetigt
) { }
