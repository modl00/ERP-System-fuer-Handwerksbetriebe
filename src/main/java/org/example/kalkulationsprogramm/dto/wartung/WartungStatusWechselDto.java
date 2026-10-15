package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;

/** Wunsch-Status für einen Statuswechsel, optional mit Grund (kommt in den Verlauf). Die erlaubten Übergänge prüft der Service. */
public record WartungStatusWechselDto(
        @NotNull(message = "Bitte einen Status angeben.") WartungStatus status,
        @Size(max = 500, message = "Der Grund ist zu lang (höchstens 500 Zeichen).") String grund
) {
    public WartungStatusWechselDto(WartungStatus status) {
        this(status, null);
    }
}
