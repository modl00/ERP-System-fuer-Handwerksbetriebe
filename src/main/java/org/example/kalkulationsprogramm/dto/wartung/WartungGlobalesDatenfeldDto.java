package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;

import java.util.List;

/** Datenfeld, das in jedes Protokoll kopiert wird, egal welche Module gewählt sind. */
public record WartungGlobalesDatenfeldDto(
        Long id,
        @NotBlank(message = "Bitte jedes Datenfeld benennen.")
        @Size(max = 200, message = "Die Bezeichnung eines Datenfelds ist zu lang (höchstens 200 Zeichen).") String label,
        @NotNull(message = "Bitte die Art des Datenfelds wählen.") WartungFeldtyp feldtyp,
        @Size(max = 50, message = "Ein Datenfeld kann höchstens 50 Optionen haben.") List<@Size(max = 200, message = "Eine Option ist zu lang (höchstens 200 Zeichen).") String> optionen
) { }
