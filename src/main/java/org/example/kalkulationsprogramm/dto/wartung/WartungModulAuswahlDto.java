package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ein gewähltes Modul samt Bauart. Die Bauart ist nur nötig, wenn das Modul Bauarten kennt. */
public record WartungModulAuswahlDto(
        @NotNull(message = "Bitte ein Modul wählen.") Long modulId,
        @Size(max = 30, message = "Die Bauart ist zu lang.") String untertypSchluessel
) { }
