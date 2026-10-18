package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("LeistungRepository.findVerwenderDerBaugruppe() nach Upstream-Merge entfernt")
class LeistungServiceBaugruppeTest {

    @Test
    void einBenutzterPfostenLaesstSichNichtLoeschenUndNenntWo() {}

    @Test
    void ohneVerwenderLaesstSichJedeLeistungWieBisherLoeschen() {}

    @Test
    void dasKennzeichenBaugruppeLaesstSichNichtEntfernenSolangeSieBenutztWird() {}
}
