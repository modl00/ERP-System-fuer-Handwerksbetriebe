package org.example.kalkulationsprogramm.dto.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungModulKategorie;

/** Eintrag der Modulliste im Editor, mit Zählern, damit man sieht, was ein Modul enthält und wo es benutzt wird. */
public record WartungModulUebersichtDto(
        Long id,
        String modulKey,
        String name,
        String beschreibung,
        String icon,
        WartungModulKategorie kategorie,
        boolean systemModul,
        boolean aktiv,
        int standardIntervallMonate,
        int anzahlUntertypen,
        int anzahlPruefpunkte,
        int anzahlDatenfelder,
        int anzahlTueren,
        int anzahlProtokolle
) { }
