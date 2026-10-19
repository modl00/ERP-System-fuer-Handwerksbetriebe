package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Modul-Auswahl einer Anlage, wie sie die Handy-App setzt. */
public record AppModulAuswahlListeDto(
        @NotNull(message = "Bitte mindestens ein Modul angeben.") @Valid List<WartungModulAuswahlDto> module
) { }
