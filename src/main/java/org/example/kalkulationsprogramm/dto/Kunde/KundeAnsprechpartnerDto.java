package org.example.kalkulationsprogramm.dto.Kunde;

/** Ein Ansprechpartner eines Kunden. */
public record KundeAnsprechpartnerDto(
        Long id,
        String name,
        String funktion,
        String telefon,
        String mobil,
        String email,
        String notizen,
        boolean haupt
) { }
