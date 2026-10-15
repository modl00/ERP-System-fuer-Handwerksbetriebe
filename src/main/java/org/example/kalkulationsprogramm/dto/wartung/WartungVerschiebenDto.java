package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotNull;

/** Ziel-Objekt, an das eine Tür verschoben wird. */
public record WartungVerschiebenDto(
        @NotNull(message = "Bitte ein Zielobjekt wählen.") Long zielObjektId
) { }
