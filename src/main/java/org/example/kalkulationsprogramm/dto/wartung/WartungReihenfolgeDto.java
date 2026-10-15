package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Neue Reihenfolge der Module im Katalog: alle Modul-IDs in der gewünschten Reihenfolge. */
public record WartungReihenfolgeDto(
        @NotEmpty(message = "Die Reihenfolge darf nicht leer sein.")
        @Size(max = 500, message = "Zu viele Module.")
        List<@NotNull(message = "Ungültige Modulnummer.") Long> ids
) { }
