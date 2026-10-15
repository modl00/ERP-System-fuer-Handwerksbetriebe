package org.example.kalkulationsprogramm.dto.Kunde;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Eingabe zum Anlegen oder Ändern eines Ansprechpartners. */
public record KundeAnsprechpartnerSpeichernDto(
        @NotBlank(message = "Bitte einen Namen eintragen.")
        @Size(max = 200, message = "Der Name ist zu lang (höchstens 200 Zeichen).") String name,
        @Size(max = 120, message = "Die Funktion ist zu lang (höchstens 120 Zeichen).") String funktion,
        @Size(max = 60, message = "Die Telefonnummer ist zu lang (höchstens 60 Zeichen).") String telefon,
        @Size(max = 60, message = "Die Mobilnummer ist zu lang (höchstens 60 Zeichen).") String mobil,
        @Size(max = 200, message = "Die E-Mail-Adresse ist zu lang (höchstens 200 Zeichen).") String email,
        @Size(max = 10000, message = "Die Notiz ist zu lang (höchstens 10.000 Zeichen).") String notizen,
        /** Zum Hauptansprechpartner machen (der bisherige verliert die Markierung). */
        Boolean haupt
) { }
