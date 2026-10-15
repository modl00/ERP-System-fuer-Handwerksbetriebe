package org.example.kalkulationsprogramm.dto.wartung;

import java.util.List;

/** Ein Objekt eines Kunden samt seinen Türen/Toren. */
public record WartungObjektDto(
        Long id,
        Long kundeId,
        String kundeName,
        String bezeichnung,
        String strasse,
        String plz,
        String ort,
        String ansprechpartnerName,
        String ansprechpartnerTelefon,
        String notizen,
        boolean aktiv,
        List<WartungTuerDto> tueren
) { }
