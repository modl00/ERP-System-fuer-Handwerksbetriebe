package org.example.kalkulationsprogramm.dto.Kunde;

/** Ein Objekt (Liegenschaft, Gebäude oder Standort) eines Kunden. */
public record KundeObjektDto(
        Long id,
        String bezeichnung,
        String strasse,
        String plz,
        String ort,
        String ansprechpartnerName,
        String ansprechpartnerTelefon,
        String notizen,
        boolean aktiv,
        /** Anzahl der aktiven Wartungen an diesem Objekt (0, solange das Wartungsmodul nichts angelegt hat). */
        int anzahlWartungen
) { }
