package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Anfrage für einen Sammelbericht: die gewählten Protokolle, ein Titel für das Deckblatt und ob es ein
 * Deckblatt geben soll. {@code mitDeckblatt} ist optional, ohne Angabe gibt es eins.
 */
public record WartungSammelberichtAnfrageDto(
        @NotEmpty(message = "Bitte mindestens ein Protokoll wählen.")
        @Size(max = 200, message = "Ein Sammelbericht kann höchstens 200 Protokolle enthalten.")
        List<@NotNull(message = "Ungültige Protokollnummer.") Long> protokollIds,
        @Size(max = 200, message = "Der Titel ist zu lang (höchstens 200 Zeichen).") String titel,
        Boolean mitDeckblatt
) { }
