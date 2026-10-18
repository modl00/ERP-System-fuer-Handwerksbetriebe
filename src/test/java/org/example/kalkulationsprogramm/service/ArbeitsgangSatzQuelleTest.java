package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Woher der Kalkulationssatz am Arbeitsgang kommt: nur gültige Werte, ändert selbst keinen Satz. Nur Dummy-Daten. */
@Disabled("aktualisiereSatzQuelle() in ArbeitsgangManagementService nach Upstream-Merge entfernt")
class ArbeitsgangSatzQuelleTest {

    @Test
    void esFolgtDemVerrechnungssatzEinerQualifikation() {}

    @Test
    void ohneQualifikationGiltDerSatzDesGanzenBetriebs() {}

    @Test
    void vonHandLoeschtDieQualifikation() {}

    @Test
    void ungueltigeWerteGebenEinen400MitKlartext() {}
}
