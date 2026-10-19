package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("LeistungService.uebernehmeTemporaere() nach Upstream-Merge entfernt")
class LeistungServiceTemporaerTest {

    @Test
    void eineTemporaereLeistungHatKeineKategorieIstKeineBaugruppeUndKennzeichnetSich() {}

    @Test
    void derKatalogZeigtKeineTemporaerenLeistungen() {}

    @Test
    void uebernehmenMachtSieZurKatalogLeistungMitKategorieUndBehaeltDenKalkuliertenPreis() {}

    @Test
    void uebernehmenBrauchtEineKategorieUndEineTemporaereLeistung() {}
}
