package org.example.kalkulationsprogramm.dto.wartung;

import java.time.LocalDate;
import java.util.List;

/** Eine Wartung im Bereich "Wartungen" eines Projekts, mit den im Projekt abgelegten Berichten. */
public record WartungProjektWartungDto(
        Long id,
        Long objektId,
        String bezeichnung,
        String objektBezeichnung,
        String kundeName,
        String module,
        LocalDate naechsteFaelligkeit,
        LocalDate letztePruefungDatum,
        int anzahlPruefungen,
        Long offenerFeldeinsatzId,
        String offenerFeldeinsatzNummer,
        List<Bericht> berichte
) {

    /** Ein als PDF im Projekt abgelegter Bericht einer abgeschlossenen Prüfung. */
    public record Bericht(Long protokollId, String nummer, LocalDate datum, String url, String dateiname) { }
}
