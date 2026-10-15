package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Neuer Wortlaut eines Prüfpunkts im laufenden Feldeinsatz (ändert nur dieses Protokoll, nicht den Katalog). */
public record WartungPruefpunktTextDto(
        @NotBlank(message = "Bitte den Prüfpunkt beschreiben.")
        @Size(max = 500, message = "Der Prüfpunkt ist zu lang (höchstens 500 Zeichen).") String label
) { }
