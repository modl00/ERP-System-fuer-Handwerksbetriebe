package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;

import java.util.List;

/**
 * Neues Datenfeld in einem laufenden Feldeinsatz (nur in diesem Protokoll, der Katalog bleibt unverändert).
 * Ohne {@code modulKey} landet es unter "Allgemein". Ohne {@code feldtyp} ist es ein Textfeld.
 */
public record WartungDatenfeldNeuDto(
        @Size(max = 60, message = "Der Modul-Schlüssel ist zu lang.") String modulKey,
        @NotBlank(message = "Bitte das Datenfeld benennen.")
        @Size(max = 200, message = "Die Bezeichnung ist zu lang (höchstens 200 Zeichen).") String label,
        WartungFeldtyp feldtyp,
        @Size(max = 50, message = "Eine Auswahl kann höchstens 50 Möglichkeiten haben.") List<@Size(max = 200, message = "Eine Möglichkeit ist zu lang (höchstens 200 Zeichen).") String> optionen
) { }
