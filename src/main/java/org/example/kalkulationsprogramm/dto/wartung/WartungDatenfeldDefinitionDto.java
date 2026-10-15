package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Neue Bezeichnung (und bei einer Auswahl die Möglichkeiten) eines Datenfelds im laufenden Feldeinsatz.
 * Ändert nur dieses Protokoll. Die Art (Text, Ja/Nein, Auswahl) bleibt: dafür das Feld löschen und neu anlegen.
 */
public record WartungDatenfeldDefinitionDto(
        @NotBlank(message = "Bitte das Datenfeld benennen.")
        @Size(max = 200, message = "Die Bezeichnung ist zu lang (höchstens 200 Zeichen).") String label,
        @Size(max = 50, message = "Eine Auswahl kann höchstens 50 Möglichkeiten haben.") List<@Size(max = 200, message = "Eine Möglichkeit ist zu lang (höchstens 200 Zeichen).") String> optionen
) { }
