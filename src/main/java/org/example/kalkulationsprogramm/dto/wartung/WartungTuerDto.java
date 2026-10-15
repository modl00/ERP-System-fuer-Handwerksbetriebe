package org.example.kalkulationsprogramm.dto.wartung;

import java.time.LocalDate;
import java.util.List;

/** Eine Tür/ein Tor mit Wartungsplan und den Modulen, die an ihr geprüft werden. */
public record WartungTuerDto(
        Long id,
        Long objektId,
        String bezeichnung,
        String notizen,
        Integer intervallMonate,
        LocalDate naechsteFaelligkeit,
        boolean aktiv,
        List<WartungModulAuswahlDto> module,
        /** Projekt der Wartung, oder null. Neue Feldeinsätze erben es. */
        Long projektId,
        String projektName
) { }
