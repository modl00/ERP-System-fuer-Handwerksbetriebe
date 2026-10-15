package org.example.kalkulationsprogramm.dto.Kunde;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Eingabe zum Anlegen oder Ändern eines Objekts am Kunden. */
public record KundeObjektSpeichernDto(
        @NotBlank(message = "Bitte eine Bezeichnung eintragen.")
        @Size(max = 200, message = "Die Bezeichnung ist zu lang (höchstens 200 Zeichen).") String bezeichnung,
        @Size(max = 200, message = "Die Straße ist zu lang.") String strasse,
        @Size(max = 20, message = "Die Postleitzahl ist zu lang.") String plz,
        @Size(max = 120, message = "Der Ort ist zu lang.") String ort,
        @Size(max = 200, message = "Der Name des Ansprechpartners ist zu lang.") String ansprechpartnerName,
        @Size(max = 60, message = "Die Telefonnummer ist zu lang.") String ansprechpartnerTelefon,
        @Size(max = 10000, message = "Die Notiz ist zu lang (höchstens 10.000 Zeichen).") String notizen
) { }
