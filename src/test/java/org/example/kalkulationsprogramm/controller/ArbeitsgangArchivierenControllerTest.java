package org.example.kalkulationsprogramm.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("ArbeitsgangManagementService.aktualisiereArchiviert() und ArbeitsgangResponseDto.setArchiviert() nach Upstream-Merge entfernt")
class ArbeitsgangArchivierenControllerTest {

    @Test
    void einBenutzterArbeitsgangGibtKonfliktMitGrund() {}

    @Test
    void einFreierArbeitsgangWirdGeloescht() {}

    @Test
    void ausblendenGibtDenArbeitsgangMitKennzeichenZurueck() {}

    @Test
    void ohneAngabeWirdWiedereingeblendet() {}
}
