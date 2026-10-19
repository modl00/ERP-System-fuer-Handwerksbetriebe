package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Felder einer Wartungsanlage, die der Techniker im Feldeinsatz vor Ort ändern kann. */
public record AppAnlageAktualisierenDto(
        @NotBlank(message = "Bitte eine Bezeichnung eintragen.")
        @Size(max = 200, message = "Die Bezeichnung ist zu lang (höchstens 200 Zeichen).") String bezeichnung,
        @Size(max = 10000, message = "Die Notizen sind zu lang (höchstens 10.000 Zeichen).") String notizen,
        @Min(value = 1, message = "Das Prüfintervall muss mindestens 1 Monat betragen.")
        @Max(value = 120, message = "Das Prüfintervall darf höchstens 120 Monate betragen.") Integer intervallMonate
) { }
