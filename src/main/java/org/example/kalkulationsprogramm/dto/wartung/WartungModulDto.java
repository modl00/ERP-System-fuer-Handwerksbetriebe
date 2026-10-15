package org.example.kalkulationsprogramm.dto.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungModulKategorie;

import java.util.List;

/** Ein Modul des Vorlagenkatalogs, so wie die Auswahl beim Anlegen es braucht. */
public record WartungModulDto(
        Long id,
        String modulKey,
        String name,
        String beschreibung,
        String icon,
        WartungModulKategorie kategorie,
        boolean systemModul,
        int standardIntervallMonate,
        /** Nur Zusatzmodule: an welche Basis-Module es andocken darf. Leer = an jedes. */
        List<Long> andockbarAnBasisModulIds,
        List<Untertyp> untertypen,
        int anzahlPruefpunkte
) {
    public record Untertyp(String schluessel, String label) { }
}
